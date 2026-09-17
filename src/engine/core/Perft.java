package engine.core;

/*
 * Purpose: Perft counts how many positions can be reached from a position in a given number of
 * moves. That number is known exactly for a handful of standard test positions, which makes it the
 * one test that proves a move generator right: if a single rule is wrong anywhere, en passant, a
 * pinned piece, castling through check or an underpromotion, the count differs. I keep one move
 * list per depth, allocated once, so counting millions of positions allocates nothing at all.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public final class Perft {

    // deepest count this class supports, far beyond what any test needs
    public static final int MAX_DEPTH = 32;

    // one move list per depth, so a recursion level never overwrites the list above it
    private final int[][] moveLists = new int[MAX_DEPTH][MoveGen.MAX_MOVES];

    /**
     * Counts the positions reachable in a number of moves.
     * <p>
     * Every legal move is played, counted one level deeper and taken back again, so the position is
     * the same when this returns. At the last level I only count the moves instead of playing them,
     * which is the usual bulk counting and saves the most expensive part of the work. A depth of
     * zero is the position itself and counts as one.
     * <p>
     * Time complexity: O(n) for the n positions in the tree, each costing one move generation.
     * Space complexity: O(1), the move lists exist from the start and the recursion is bounded by
     * the depth.
     *
     * @param pPosition position to count from, never null and unchanged when this returns
     * @param pDepth    how many moves deep to count, 0 or more and below MAX_DEPTH
     * @return the number of positions reachable in exactly that many moves
     * @throws IllegalArgumentException if the depth is negative or not below MAX_DEPTH
     */
    public long count(Position pPosition, int pDepth) {
        // a depth outside the preallocated lists would overwrite memory that is not there
        if (pDepth < 0 || pDepth >= MAX_DEPTH) {
            throw new IllegalArgumentException("depth " + pDepth + " must be between 0 and " + (MAX_DEPTH - 1));
        }
        // the position itself is the only one reachable in no moves
        if (pDepth == 0) {
            return 1L;
        }

        int[] moves = moveLists[pDepth];
        int moveCount = MoveGen.generateLegal(pPosition, moves, 0);

        // at the last level the moves themselves are the answer, playing them would change nothing
        if (pDepth == 1) {
            return moveCount;
        }

        long nodes = 0L;
        for (int index = 0; index < moveCount; index++) {
            pPosition.makeMove(moves[index]);
            nodes += count(pPosition, pDepth - 1);
            pPosition.unmakeMove(moves[index]);
        }
        return nodes;
    }

    /**
     * Counts the positions after each single move and writes them down.
     * <p>
     * When a total is wrong, this is what finds the move it went wrong under: comparing per move
     * counts against a known good engine points straight at the rule that is broken, instead of
     * leaving a single wrong number to explain. The lines are sorted by nothing in particular, they
     * come in the order the generator produced them.
     * <p>
     * Time complexity: O(n) for the n positions in the tree. Space complexity: O(m) for the text of
     * m moves.
     *
     * @param pPosition position to count from, never null and unchanged when this returns
     * @param pDepth    how many moves deep to count, 1 or more and below MAX_DEPTH
     * @return one line per move, each with the move in UCI notation and its count, never null
     * @throws IllegalArgumentException if the depth is below one or not below MAX_DEPTH
     */
    public String divide(Position pPosition, int pDepth) {
        // dividing a single position among no moves makes no sense
        if (pDepth < 1 || pDepth >= MAX_DEPTH) {
            throw new IllegalArgumentException("depth " + pDepth + " must be between 1 and " + (MAX_DEPTH - 1));
        }
        int[] moves = new int[MoveGen.MAX_MOVES];
        int moveCount = MoveGen.generateLegal(pPosition, moves, 0);

        StringBuilder text = new StringBuilder();
        long total = 0L;
        for (int index = 0; index < moveCount; index++) {
            pPosition.makeMove(moves[index]);
            long nodes = count(pPosition, pDepth - 1);
            pPosition.unmakeMove(moves[index]);
            total += nodes;
            text.append(Moves.toUci(moves[index])).append(": ").append(nodes).append('\n');
        }
        text.append("total: ").append(total).append('\n');
        return text.toString();
    }
}
