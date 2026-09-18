package engine.search;

/*
 * Purpose: Analyst answers the questions a player asks about a position rather than the one the game
 * asks. What should I play here, what is my opponent threatening, how bad was that move. The first is
 * an ordinary search; the second is the same search run after handing the turn over, so it reports
 * what the other side would do if I did nothing at all; the third compares the score before a move
 * with the score after it. All of it can be called off part way, because analysis runs while somebody
 * is reading the board and the answer stops being wanted the moment they move on.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.Fen;
import engine.core.MoveGen;
import engine.core.Moves;
import engine.core.Position;

public final class Analyst {

    // how much worse a position has to get before the move that did it counts as a blunder
    public static final int BLUNDER_CENTIPAWNS = 200;

    // and how much worse before it counts as a mistake, short of a blunder
    public static final int MISTAKE_CENTIPAWNS = 100;

    private final Searcher searcher = new Searcher();

    /**
     * Works out the best move in a position and what it is worth.
     * <p>
     * This is what a hint is: the move the search would play, reached by the same search the program
     * plays with, so the hint can never be advice the program itself would not take. The position is
     * copied first, because whoever asked for the hint is still looking at the board it came from.
     * <p>
     * Time complexity: O(b^d) worst case for branching factor b and depth d, cut down by alpha beta
     * and bounded by the limits. Space complexity: O(1) beyond the copy.
     *
     * @param pPosition position to look at, never null and unchanged when this returns
     * @param pLimits   how hard to look, never null
     * @return what the search found, never null
     * @throws NullPointerException if either argument is null
     */
    public Searcher.Result analyse(Position pPosition, Searcher.Limits pLimits) {
        return searcher.search(copyOf(pPosition), pLimits);
    }

    /**
     * Works out what the other side would do if you did nothing.
     * <p>
     * A threat is the move waiting for you, and the way to find it is to hand the turn over without
     * playing anything and ask what the other side likes best. The position that produces is not one
     * any game could reach, which is exactly why it answers the question: it is the board as it would
     * stand if you wasted your move.
     * <p>
     * Being in check has no threat worth showing, because everything except answering the check is
     * beside the point, and handing the turn over while in check would describe a board where your
     * king could simply be taken.
     * <p>
     * Time complexity: as for a search with these limits. Space complexity: O(1) beyond the copy.
     *
     * @param pPosition position to look at, never null and unchanged when this returns
     * @param pLimits   how hard to look, never null
     * @return the move the other side is threatening, or Moves.NONE when there is none to show
     * @throws NullPointerException if either argument is null
     */
    public int threatMove(Position pPosition, Searcher.Limits pLimits) {
        int us = pPosition.sideToMove();
        // answering a check is the only thing that matters, so there is no threat to report
        if (MoveGen.isInCheck(pPosition, us)) {
            return Moves.NONE;
        }

        Position copy = copyOf(pPosition);
        copy.setSideToMove(1 - us);
        // whatever could have been captured in passing is gone once a move is skipped
        copy.setEpSquare(Position.NO_EN_PASSANT);

        return searcher.search(copy, pLimits).bestMove;
    }

    /**
     * Stops whatever is being worked out right now.
     * <p>
     * Analysis runs while somebody is reading a board, and the moment they step to the next position
     * the answer they were waiting for is worth nothing. Stopping is what keeps a replay responsive
     * instead of queueing up a search for every position somebody passed through on the way.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    public void cancel() {
        searcher.stop();
    }

    /**
     * Tells how much a move cost the player who made it.
     * <p>
     * Both scores are read from the point of view of the side that moved, which is what makes them
     * comparable: the search reports every position from the point of view of whoever is to move, so
     * the score after a move belongs to the opponent and has to be turned round before it means
     * anything to the player who played it.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pBeforeForMover what the position was worth to the mover before the move
     * @param pAfterForMover  what it was worth to the mover afterwards
     * @return how many hundredths of a pawn the move gave away, negative when it gained
     */
    public static int costOf(int pBeforeForMover, int pAfterForMover) {
        return pBeforeForMover - pAfterForMover;
    }

    /**
     * Tells whether a move threw enough away to be called a blunder.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pBeforeForMover what the position was worth to the mover before the move
     * @param pAfterForMover  what it was worth to the mover afterwards
     * @return true if the move gave away at least a blunder's worth
     */
    public static boolean isBlunder(int pBeforeForMover, int pAfterForMover) {
        return costOf(pBeforeForMover, pAfterForMover) >= BLUNDER_CENTIPAWNS;
    }

    /**
     * Tells whether a move was a mistake, short of a blunder.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pBeforeForMover what the position was worth to the mover before the move
     * @param pAfterForMover  what it was worth to the mover afterwards
     * @return true if the move gave away a mistake's worth but less than a blunder's
     */
    public static boolean isMistake(int pBeforeForMover, int pAfterForMover) {
        int cost = costOf(pBeforeForMover, pAfterForMover);
        return cost >= MISTAKE_CENTIPAWNS && cost < BLUNDER_CENTIPAWNS;
    }

    /**
     * Takes a copy of a position that a search can change freely.
     * <p>
     * A position has no copy of its own, and the text it can be written as is the one honest way to
     * duplicate it here. The copy also starts with an empty undo stack, so analysis of a long game
     * never inherits how far that game has already gone.
     * <p>
     * Time complexity: O(64) to write and read the position. Space complexity: O(1) for the copy.
     *
     * @param pPosition position to copy, never null and unchanged
     * @return a position equal to it that nothing else is holding, never null
     */
    private static Position copyOf(Position pPosition) {
        return Fen.parse(Fen.write(pPosition));
    }
}
