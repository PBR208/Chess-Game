package engine.core;

/*
 * Purpose: Termination says why a game ended, which is separate from who won it. Two games can both
 * end 1/2-1/2 for completely different reasons, and a player, a PGN file and the end screen all want
 * the reason rather than the token. The old engine carried this as an English sentence built at the
 * place the game ended, which meant the reason could not be tested, translated or written into a PGN
 * tag without parsing text back apart.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public enum Termination {

    /** The side to move is in check and has no legal move. */
    CHECKMATE,
    /** The side to move is not in check but has no legal move. */
    STALEMATE,
    /** Neither side has enough material left to ever deliver mate. */
    INSUFFICIENT_MATERIAL,
    /** A player claimed the draw after fifty moves without a capture or a pawn move. */
    FIFTY_MOVE_RULE,
    /** Seventy five moves passed without a capture or a pawn move, which draws without a claim. */
    SEVENTY_FIVE_MOVE_RULE,
    /** A player claimed the draw after the same position occurred three times. */
    THREEFOLD_REPETITION,
    /** The same position occurred five times, which draws without a claim. */
    FIVEFOLD_REPETITION,
    /** A player ran out of time while the opponent could still mate. */
    TIME_OUT,
    /** A player ran out of time but the opponent could never mate, so the game is drawn. */
    TIME_OUT_WITHOUT_MATING_MATERIAL,
    /** Both players agreed on a draw. */
    DRAW_AGREED,
    /** A player gave up the game. */
    RESIGNATION;

    /**
     * Tells whether this reason always ends the game as a draw.
     * <p>
     * Most reasons decide the result on their own, which lets the session check that a result and its
     * reason fit together instead of trusting the caller. Only mate, a flag fall and a resignation
     * have a winner, everything else here is drawn.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true if this reason can only ever produce a draw
     */
    public boolean isAlwaysDraw() {
        return this != CHECKMATE && this != TIME_OUT && this != RESIGNATION;
    }
}
