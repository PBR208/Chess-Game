package engine.model;

/*
 * Purpose: ClockStage is one section of a time control that hands out more time once a number of
 * moves has been played, which is how serious chess has been timed for a century: forty moves in
 * ninety minutes, then thirty minutes for whatever is left. A stage says how many moves it covers
 * and how much time arrives when it begins, and a whole time control is simply a list of them. The
 * last stage runs to the end of the game and so names no move count at all.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import java.util.ArrayList;
import java.util.List;

/**
 * @param moves  moves this stage covers before the next one begins, 0 for a stage that runs to the end
 * @param timeMs time added to the clock when this stage begins, in milliseconds
 */
public record ClockStage(int moves, long timeMs) {

    /** A stage that covers the rest of the game instead of a fixed number of moves. */
    public static final int UNTIL_THE_END = 0;

    /**
     * Keeps a stage to values a game could actually be played with.
     * <p>
     * A negative move count or a negative amount of time describes a stage nobody could play
     * through, and a time control is read from text that people type, so both are possible. I clamp
     * them rather than refusing, because the worst a zero does is hand out no time.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    public ClockStage {
        moves = Math.max(0, moves);
        timeMs = Math.max(0, timeMs);
    }

    /**
     * Tells whether this stage covers the rest of the game.
     * <p>
     * Every time control ends with a stage that has no move count, because at some point the game
     * simply has to finish. The clock needs to know which stage that is, so it stops looking for a
     * boundary that will never come.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true if no move count ends this stage
     */
    public boolean runsToTheEnd() {
        return moves == UNTIL_THE_END;
    }

    /**
     * Reads a time control written the way players write it.
     * <p>
     * Tournament times are written as sections separated by commas, where a section is either a
     * number of moves and the minutes they get, such as 40/90, or just the minutes for the rest of
     * the game, such as 30. So "40/90, 30" is forty moves in ninety minutes and then thirty minutes
     * to finish, which is the most common classical control there is. I read each section in turn
     * and treat one that names no move count as the last one, since nothing can follow the end of a
     * game. Text that makes no sense gives no stages at all, which leaves the clock on its plain
     * starting time rather than on a control nobody meant.
     * <p>
     * Time complexity: O(n) in the length of the text. Space complexity: O(s) for the s stages.
     *
     * @param pText the time control as text, may be null or empty
     * @return the stages in the order they are played, never null and empty when nothing was read
     */
    public static List<ClockStage> parse(String pText) {
        List<ClockStage> stages = new ArrayList<>();
        // no text at all is the normal case: a clock with one time and no stages
        if (pText == null || pText.isBlank()) {
            return stages;
        }

        for (String section : pText.split(",")) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int slash = trimmed.indexOf('/');
                if (slash < 0) {
                    // a section without a move count is the one that runs to the end
                    stages.add(new ClockStage(UNTIL_THE_END, minutesToMs(trimmed)));
                } else {
                    int moves = Integer.parseInt(trimmed.substring(0, slash).trim());
                    stages.add(new ClockStage(moves, minutesToMs(trimmed.substring(slash + 1))));
                }
            } catch (NumberFormatException e) {
                // one unreadable section makes the whole control guesswork, so none of it is used
                return new ArrayList<>();
            }
        }
        return stages;
    }

    /**
     * Turns a number of minutes written as text into milliseconds.
     * <p>
     * Time complexity: O(n) in the length of the text. Space complexity: O(1).
     *
     * @param pMinutes whole minutes as text, never null
     * @return the same time in milliseconds
     * @throws NumberFormatException if the text is not a whole number
     */
    private static long minutesToMs(String pMinutes) {
        return Long.parseLong(pMinutes.trim()) * 60_000L;
    }
}
