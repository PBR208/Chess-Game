package engine.core;

/*
 * Purpose: GameResult says how a game stands, which is either still running or finished with one of
 * the three outcomes chess knows. The old engine tracked this with a boolean for "game over" plus a
 * result string that was built in several places, which is how a finished game could end twice and
 * report two different results. One value with the PGN token attached to it removes that whole class
 * of mistake, because a game can only be in one of these states and the token always matches.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public enum GameResult {

    /** The game is still being played and has no result yet. */
    ONGOING("*"),
    /** White won, by mate, resignation or on time. */
    WHITE_WINS("1-0"),
    /** Black won, by mate, resignation or on time. */
    BLACK_WINS("0-1"),
    /** Neither side won, by any of the drawing rules or by agreement. */
    DRAW("1/2-1/2");

    private final String pgnToken;

    GameResult(String pPgnToken) {
        this.pgnToken = pPgnToken;
    }

    /**
     * Returns the token PGN files use for this result.
     * <p>
     * Every saved game ends with this token and readers rely on it, so it belongs to the result
     * itself rather than to whoever writes the file. A game that is still running is written as a
     * star, which is what PGN uses for an unfinished game.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return "1-0", "0-1", "1/2-1/2" or "*", never null
     */
    public String pgnToken() {
        return pgnToken;
    }

    /**
     * Tells whether the game has finished.
     * <p>
     * The session refuses moves and stops the clocks once this is true, so it is asked before every
     * move and by the user interface on every repaint.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true for anything but ONGOING
     */
    public boolean isFinished() {
        return this != ONGOING;
    }

    /**
     * Returns the result a side wins with.
     * <p>
     * Mate, a flag fall and a resignation all end with one side winning, and each of those knows the
     * colour rather than the token.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pColour the winning side, Pieces.WHITE or Pieces.BLACK
     * @return WHITE_WINS or BLACK_WINS
     */
    public static GameResult wonBy(int pColour) {
        return pColour == Pieces.WHITE ? WHITE_WINS : BLACK_WINS;
    }
}
