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

import javax.swing.*;
import java.awt.*;

public class ChessClock {

    private final long START_TIME_MS;
    private static final long LOW_TIME_MS = 30 * 1000L;       // red at < 30 s

    private final boolean isWhite;
    // remaining time as last shown on screen, refreshed on every tick
    private long timeMs;
    // remaining time at the moment the clock was last started or stopped
    private long bankedMs;
    // System.nanoTime() of the last start, only meaningful while running
    private long runningSinceNanos;
    private boolean running = false;

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
        this.isWhite = pIsWhite;
        this.START_TIME_MS = pStartTimeMs;
        this.timeMs = pStartTimeMs;
        this.bankedMs = pStartTimeMs;
        this.onRepaint = pOnRepaint;
        this.onExpired = pOnExpired;

        // started and stopped together with the clock, a timer that keeps running keeps the board alive
        timer = new Timer(100, e -> tick());
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
        bankedMs = currentTimeMs();
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
        return Math.max(0, bankedMs - elapsedMs);
    }

    public void draw(Graphics2D g2d, int yOffset, int width, int height) {
        int pad = height / 6;

        //Background
        g2d.setColor(running ? new Color(45, 45, 48) : new Color(28, 28, 30));
        g2d.fillRect(0, yOffset, width, height);

        //Green left border on the active clock
        if (running) {
            g2d.setColor(ACTIVE_BANNER_COLOR);
            g2d.fillRect(0, yOffset, 4, height);
        }

        //Thin separator between clock and board edge
        g2d.setColor(SEPERATOR_COLOR);
        g2d.fillRect(0, running ? yOffset + height - 1 : yOffset, width, 1);

        //Player colour swatch (small filled square)
        int swatchSize = height / 4;
        int swatchX = pad + 4; // clear of the green active border
        int swatchY = yOffset + (height - swatchSize) / 2;

        g2d.setColor(isWhite ? PLAYER_WHITE_COLOR : PLAYER_BLACK_COLOR);
        g2d.fillRect(swatchX, swatchY, swatchSize, swatchSize);
        g2d.setColor(CLOCK_COLOR);
        g2d.drawRect(swatchX, swatchY, swatchSize, swatchSize);

        //Player label
        g2d.setFont(new Font("Arial", Font.BOLD, height / 5));
        g2d.setColor(running ? Color.WHITE : PLAYER_NAME_COLOR);

        FontMetrics fmLabel = g2d.getFontMetrics();
        String label = isWhite ? "WHITE" : "BLACK";
        int labelX = swatchX + swatchSize + pad / 2;
        int labelY = yOffset + (height + fmLabel.getAscent() - fmLabel.getDescent()) / 2;
        g2d.drawString(label, labelX, labelY);

        //Time display
        long totalSec = timeMs / 1000;
        String timeText = String.format("%02d:%02d", totalSec / 60, totalSec % 60);

        Color timeColor;
        if (!running) timeColor = CLOCK_COLOR;
        else if (timeMs < LOW_TIME_MS) timeColor = ALARM_COLOR; // red under 30 s
        else timeColor = Color.WHITE;

        g2d.setFont(new Font("Arial", Font.BOLD, height / 2));
        g2d.setColor(timeColor);

        FontMetrics fmTime = g2d.getFontMetrics();
        int timeX = width - pad - fmTime.stringWidth(timeText);
        int timeY = yOffset + (height + fmTime.getAscent() - fmTime.getDescent()) / 2;
        g2d.drawString(timeText, timeX, timeY);
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
