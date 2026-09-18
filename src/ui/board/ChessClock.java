package ui.board;

/*
 * Purpose: ChessClock keeps and draws the remaining thinking time of one player. It counts down
 * only while it runs, reports when the time is used up and paints the player's clock bar above or
 * below the board. I measure the elapsed time with the monotonic System.nanoTime clock, while the
 * Swing timer only decides how often the display refreshes, so a busy event thread can never hand
 * a player free time. An unlimited clock simply never counts down.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.model.ClockMode;
import engine.model.ClockStage;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ChessClock {

    private final long START_TIME_MS;
    private static final long LOW_TIME_MS = 30 * 1000L;       // red at < 30 s
    // below ten seconds a player counts in tenths, so the display starts showing them
    private static final long TENTHS_BELOW_MS = 10 * 1000L;

    private final boolean isWhite;
    // remaining time as last shown on screen, refreshed on every tick
    private long timeMs;
    // remaining time at the moment the clock was last started or stopped
    private long bankedMs;
    // System.nanoTime() of the last start, only meaningful while running
    private long runningSinceNanos;
    private boolean running = false;

    // how this clock treats the time around a move
    private final ClockMode mode;
    // added after a move on a Fischer clock, 0 in every other mode
    private final long incrementMs;
    // the delay a Bronstein or simple delay clock works with, 0 in every other mode
    private final long delayMs;
    // how much of this turn's delay has not been used up yet, only meaningful while running
    private long delayLeftMs;

    // the sections of a tournament control, empty for a control that is one time and nothing else
    private List<ClockStage> stages = List.of();
    // moves this player has finished inside the current stage
    private int movesInStage;
    // which stage is being played, an index into the list above
    private int currentStage;

    // set once this clock has warned its player, cleared when time comes back above the mark
    private boolean lowTimeWarned;

    private final Runnable onRepaint;
    private final TimeExpiredCallback onExpired;
    private final Timer timer;

    private final Color ACTIVE_BANNER_COLOR = new Color(81, 168, 0, 200);
    private final Color SEPERATOR_COLOR = new Color(60, 60, 60);
    private final Color PLAYER_WHITE_COLOR = new Color(232, 235, 239);
    private final Color PLAYER_BLACK_COLOR = new Color(40, 40, 42);
    private final Color ALARM_COLOR = new Color(210, 55, 55);
    private final Color CLOCK_COLOR = new Color(90, 90, 90);
    private final Color PLAYER_NAME_COLOR = new Color(90, 90, 90);


    public interface TimeExpiredCallback {
        void onExpired(boolean isWhiteExpired);
    }

    /**
     * Creates a stopped clock with the given starting time.
     * <p>
     * Every player gets their own clock when a board is built. I store the colour, the starting
     * time and the two callbacks, and create the Swing timer that refreshes the display ten times a
     * second while the clock runs. The timer only looks at the clock, the actual time is measured
     * separately.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pIsWhite     true for White's clock, false for Black's
     * @param pStartTimeMs starting time in milliseconds, 0 for an unlimited clock, never negative
     * @param pOnRepaint   called after every refresh so the board can redraw, never null
     * @param pOnExpired   called once when the time runs out, never null
     */
    public ChessClock(boolean pIsWhite, long pStartTimeMs, Runnable pOnRepaint, TimeExpiredCallback pOnExpired) {
        // a clock nobody gave a time control to simply counts down
        this(pIsWhite, pStartTimeMs, ClockMode.SUDDEN_DEATH, 0, 0, pOnRepaint, pOnExpired);
    }

    /**
     * Creates a stopped clock that plays a given time control.
     * <p>
     * A time control is more than a starting time. I store the colour, the starting time and how the
     * clock treats the time around a move, which is the increment of a Fischer clock or the delay of
     * a Bronstein or simple delay one, together with the two callbacks, and create the Swing timer
     * that refreshes the display ten times a second while the clock runs. The timer only looks at the
     * clock, the time itself is measured from the monotonic clock.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pIsWhite     true for White's clock, false for Black's
     * @param pStartTimeMs starting time in milliseconds, 0 for an unlimited clock, never negative
     * @param pMode        how the clock treats the time around a move, never null
     * @param pIncrementMs time added after a move on a Fischer clock, 0 in every other mode
     * @param pDelayMs     the delay of a Bronstein or simple delay clock, 0 in every other mode
     * @param pOnRepaint   called after every refresh so the board can redraw, never null
     * @param pOnExpired   called once when the time runs out, never null
     * @throws NullPointerException if pMode is null
     */
    public ChessClock(boolean pIsWhite, long pStartTimeMs, ClockMode pMode, long pIncrementMs, long pDelayMs,
                      Runnable pOnRepaint, TimeExpiredCallback pOnExpired) {
        this.isWhite = pIsWhite;
        this.START_TIME_MS = pStartTimeMs;
        this.timeMs = pStartTimeMs;
        this.bankedMs = pStartTimeMs;
        this.mode = pMode;
        this.incrementMs = Math.max(0, pIncrementMs);
        this.delayMs = Math.max(0, pDelayMs);
        this.onRepaint = pOnRepaint;
        this.onExpired = pOnExpired;

        // started and stopped together with the clock, a timer that keeps running keeps the board alive
        timer = new Timer(100, e -> tick());
    }

    /**
     * Gives this clock the sections of a tournament time control.
     * <p>
     * Classical chess hands out time in stages: forty moves in ninety minutes, then thirty minutes
     * for the rest. The clock starts on the time of the first stage, which the game was built with,
     * and every later stage arrives when its predecessor's moves have all been played. An empty list
     * is the normal case of one time for the whole game. I take the stages after construction rather
     * than as another constructor argument, because most games have none and a constructor with
     * seven arguments is already long enough to misread.
     * <p>
     * Time complexity: O(s) for the s stages copied. Space complexity: O(s).
     *
     * @param pStages the stages in the order they are played, may be null or empty
     */
    public void setStages(List<ClockStage> pStages) {
        this.stages = pStages == null ? List.of() : List.copyOf(pStages);
        this.movesInStage = 0;
        this.currentStage = 0;
    }

    /**
     * Starts counting down this player's time.
     * <p>
     * A player's time runs from the moment it is their turn. I remember that moment from the
     * monotonic clock, mark the clock as running and start the refresh timer. Starting a clock that
     * already runs changes nothing, so its original start moment is kept.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    public void start() {
        // a running clock keeps its original start moment
        if (running) {
            return;
        }
        runningSinceNanos = System.nanoTime();
        // a simple delay clock waits out its whole delay again at the start of every turn
        delayLeftMs = mode == ClockMode.SIMPLE_DELAY ? delayMs : 0;
        running = true;
        // only a running clock needs display refreshes
        timer.start();
    }

    /**
     * Stops counting down and keeps the time that is left.
     * <p>
     * When a player finishes a move their time has to freeze exactly where it is. I work out the
     * remaining time from the monotonic clock, store it, mark the clock as stopped and stop the
     * refresh timer, so nothing keeps firing for a clock that doesn't run. Stopping a clock that
     * isn't running changes nothing.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    public void stop() {
        // nothing to settle on a stopped clock
        if (!running) {
            return;
        }
        // bank the time that was left at this moment
        long beforeMs = bankedMs;
        bankedMs = currentTimeMs();
        // a Bronstein clock gives back exactly what the move used, and never more than the delay
        if (mode == ClockMode.BRONSTEIN && START_TIME_MS > 0) {
            bankedMs += Math.min(beforeMs - bankedMs, delayMs);
        }
        timeMs = bankedMs;
        running = false;
        // a stopped timer no longer holds on to this clock and its board
        timer.stop();
    }

    /**
     * Stops the clock and puts the starting time back.
     * <p>
     * A restarted game needs both clocks as they were at the beginning. I stop the clock and its
     * refresh timer and reset the stored and displayed time to the starting time.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    public void reset() {
        running = false;
        timer.stop();
        timeMs = START_TIME_MS;
        bankedMs = START_TIME_MS;
        // a new game plays the control from its first stage again
        movesInStage = 0;
        currentStage = 0;
        // and it is nowhere near low on time yet
        lowTimeWarned = false;
    }

    /**
     * Adds time to this player's clock, for example the increment after a move.
     * <p>
     * Time controls such as 2+1 give a player extra seconds after every move they make. I add the
     * amount to the banked time, which works whether the clock is running or not, and refresh the
     * displayed value. Unlimited clocks and amounts of zero or less are ignored.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pExtraMs milliseconds to add, 0 or less does nothing
     */
    public void addTime(long pExtraMs) {
        // an unlimited clock has nothing to add to
        if (START_TIME_MS == 0 || pExtraMs <= 0) {
            return;
        }
        bankedMs += pExtraMs;
        timeMs = currentTimeMs();
        // time back above the mark means the next dip under it is worth warning about again
        if (timeMs >= LOW_TIME_MS) {
            lowTimeWarned = false;
        }
    }

    /**
     * Gives this player whatever their time control owes them for the move they just finished.
     * <p>
     * Only a Fischer clock pays after a move, and it pays the same increment whether the move took a
     * second or a minute. A Bronstein clock has already given back the time the move really used at
     * the moment it stopped, and sudden death and a simple delay owe nothing at all, so for those
     * this does nothing. Calling it on every move keeps the board from having to know which mode is
     * being played.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    public void onMoveFinished() {
        // Bronstein settles up when the clock stops, the other two modes owe nothing
        if (mode == ClockMode.FISCHER) {
            addTime(incrementMs);
        }
        // a tournament control also counts the move towards the end of the current stage
        movesInStage++;
        advanceStageIfReached();
    }

    /**
     * Moves on to the next stage of a tournament control once this one is played out.
     * <p>
     * A stage ends when the player has made the moves it covers, and the time of the stage that
     * follows arrives at that moment rather than at the start of the game. A control without stages,
     * and the last stage of one, have no boundary left to reach, so nothing happens there. The count
     * starts over, because the next stage counts its own moves.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    private void advanceStageIfReached() {
        // nothing to advance towards without stages, or once the last one is being played
        if (currentStage >= stages.size() || stages.get(currentStage).runsToTheEnd()) {
            return;
        }
        if (movesInStage < stages.get(currentStage).moves()) {
            return;
        }

        currentStage++;
        movesInStage = 0;
        // the new stage brings its own time with it
        if (currentStage < stages.size()) {
            addTime(stages.get(currentStage).timeMs());
        }
    }

    /**
     * Refreshes the display and detects when the time has run out.
     * <p>
     * The Swing timer calls this ten times a second. A stopped clock is ignored and an unlimited
     * clock only repaints. Otherwise I take the remaining time from the monotonic clock, so a tick
     * that arrives late still sees the right time, repaint, and report the flag fall once the time
     * reaches zero.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    private void tick() {
        if (!running) return;

        // an unlimited clock only needs the repaint
        if (START_TIME_MS == 0) {
            onRepaint.run();
            return;
        }

        timeMs = currentTimeMs();
        onRepaint.run();

        // the warning comes once, at the moment the time first drops under the low mark
        if (!lowTimeWarned && timeMs < LOW_TIME_MS) {
            lowTimeWarned = true;
            LowTimeSound.play();
        }

        if (timeMs == 0) {
            stop();
            onExpired.onExpired(isWhite);
        }
    }

    /**
     * Works out how much time is left right now.
     * <p>
     * Counting timer ticks lost time whenever the event thread was busy, because late ticks were
     * merged into one. I subtract the time that really passed since the last start, measured with
     * System.nanoTime, from the banked time. Stopped and unlimited clocks just return the banked
     * value.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return remaining time in milliseconds, never below 0
     */
    private long currentTimeMs() {
        // a stopped or unlimited clock doesn't lose time
        if (!running || START_TIME_MS == 0) {
            return bankedMs;
        }
        long elapsedMs = (System.nanoTime() - runningSinceNanos) / 1_000_000;
        // a simple delay clock counts nothing at all until this turn's delay is used up
        long countedMs = Math.max(0, elapsedMs - delayLeftMs);
        return Math.max(0, bankedMs - countedMs);
    }

    /**
     * Writes a remaining time the way the clock bar shows it.
     * <p>
     * Above ten seconds a player thinks in minutes and seconds, and below it they count in tenths,
     * so the display changes shape exactly there. An unlimited clock has no time to spend, so it
     * keeps showing plain zeros instead of counting tenths of a time that never moves. I keep the
     * rule out of the drawing itself, so it can be checked without a screen.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) for the text.
     *
     * @param pTimeMs      remaining time in milliseconds, 0 or more
     * @param pStartTimeMs the time this clock started from, 0 for an unlimited clock
     * @return the text shown on the clock bar, never null
     */
    public static String formatTime(long pTimeMs, long pStartTimeMs) {
        long totalSec = pTimeMs / 1000;
        // below ten seconds every tenth matters, and an unlimited clock never gets there
        if (pStartTimeMs > 0 && pTimeMs < TENTHS_BELOW_MS) {
            return String.format("%d.%d", totalSec, pTimeMs % 1000 / 100);
        }
        return String.format("%02d:%02d", totalSec / 60, totalSec % 60);
    }

    /**
     * Paints this player's clock bar.
     * <p>
     * Each player needs to see their remaining time, whose turn it is and which colour they play. I
     * fill the bar, mark a running clock with a green edge, draw a thin separator towards the board,
     * a colour swatch, the player label and the remaining time, which turns red below 30 seconds. The
     * text uses the logical sans serif font, which every platform has, unlike Arial.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the two font objects.
     *
     * @param pG2d     graphics context of the board panel, never null
     * @param pYOffset top edge of the clock bar in panel pixels
     * @param pWidth   width of the bar in pixels, greater than 0
     * @param pHeight  height of the bar in pixels, greater than 0
     */
    public void draw(Graphics2D pG2d, int pYOffset, int pWidth, int pHeight) {
        int pad = pHeight / 6;

        //Background
        pG2d.setColor(running ? new Color(45, 45, 48) : new Color(28, 28, 30));
        pG2d.fillRect(0, pYOffset, pWidth, pHeight);

        //Green left border on the active clock
        if (running) {
            pG2d.setColor(ACTIVE_BANNER_COLOR);
            pG2d.fillRect(0, pYOffset, 4, pHeight);
        }

        //Thin separator between clock and board edge
        pG2d.setColor(SEPERATOR_COLOR);
        pG2d.fillRect(0, running ? pYOffset + pHeight - 1 : pYOffset, pWidth, 1);

        //Player colour swatch (small filled square)
        int swatchSize = pHeight / 4;
        int swatchX = pad + 4; // clear of the green active border
        int swatchY = pYOffset + (pHeight - swatchSize) / 2;

        pG2d.setColor(isWhite ? PLAYER_WHITE_COLOR : PLAYER_BLACK_COLOR);
        pG2d.fillRect(swatchX, swatchY, swatchSize, swatchSize);
        pG2d.setColor(CLOCK_COLOR);
        pG2d.drawRect(swatchX, swatchY, swatchSize, swatchSize);

        //Player label, logical fonts exist on every platform
        pG2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, pHeight / 5));
        pG2d.setColor(running ? Color.WHITE : PLAYER_NAME_COLOR);

        FontMetrics fmLabel = pG2d.getFontMetrics();
        String label = isWhite ? "WHITE" : "BLACK";
        int labelX = swatchX + swatchSize + pad / 2;
        int labelY = pYOffset + (pHeight + fmLabel.getAscent() - fmLabel.getDescent()) / 2;
        pG2d.drawString(label, labelX, labelY);

        //Time display
        String timeText = formatTime(timeMs, START_TIME_MS);

        Color timeColor;
        if (!running) timeColor = CLOCK_COLOR;
        else if (timeMs < LOW_TIME_MS) timeColor = ALARM_COLOR; // red under 30 s
        else timeColor = Color.WHITE;

        pG2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, pHeight / 2));
        pG2d.setColor(timeColor);

        FontMetrics fmTime = pG2d.getFontMetrics();
        int timeX = pWidth - pad - fmTime.stringWidth(timeText);
        int timeY = pYOffset + (pHeight + fmTime.getAscent() - fmTime.getDescent()) / 2;
        pG2d.drawString(timeText, timeX, timeY);
    }

    //GETTER
    public boolean isRunning() {
        return running;
    }

    /**
     * Tells whether the refresh timer of this clock is currently active.
     * <p>
     * A timer that keeps firing holds on to the board through its repaint callback, so tests need to
     * see whether it still runs. I report the state of the Swing timer.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true if the refresh timer is running
     */
    public boolean isTicking() {
        return timer.isRunning();
    }

    /**
     * Tells whether this clock has already warned its player about the time.
     * <p>
     * The warning itself is a sound, which a test cannot listen for, so the clock records that it
     * gave one. That also makes the rule visible: one warning per dip under the mark, and another
     * one only after time has been added back above it.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true once the warning has been given for the current dip under the low mark
     */
    public boolean isLowTimeWarned() {
        return lowTimeWarned;
    }

    /**
     * Returns the time this player has left.
     * <p>
     * Tests, the result logic and later features need the exact remaining time, not the value that
     * was last painted. I compute it from the monotonic clock at the moment of the call.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return remaining time in milliseconds, 0 for an unlimited clock or when the time is up
     */
    public long getTimeMs() {
        return currentTimeMs();
    }
}
