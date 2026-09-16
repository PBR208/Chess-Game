package test;

/*
 * Purpose: PerftTest is the gate that decides whether the move generator is right. It counts the
 * positions reachable from five standard test positions and compares them against the node counts
 * the chess programming community has agreed on for decades. Those positions were chosen because
 * each one breaks a generator in a different way, with en passant, pinned pieces, castling rights,
 * promotions and checks, so matching all of them leaves very little room for a rule to be wrong. I
 * keep the fast depths here for a normal run and the deep ones behind an argument, since the deep
 * gate counts hundreds of millions of positions and takes minutes.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.Bitboards;
import engine.core.Perft;
import engine.core.Pieces;
import engine.core.Position;

public final class PerftTest {

    // the five positions every engine is measured against, with the counts for depth 1 upwards
    private static final String START_POSITION =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String KIWIPETE =
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    private static final String POSITION_3 =
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";
    private static final String POSITION_4 =
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1";
    private static final String POSITION_5 =
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 0 1";

    private PerftTest() {
    }

    /**
     * Runs the perft gate and reports whether every count matched.
     * <p>
     * A normal run counts the depths that finish in seconds, which is what a build server should do
     * on every push. Passing "deep" as the first argument runs the full gate instead, the depths the
     * chess programming wiki lists as the reference, which counts hundreds of millions of positions
     * and takes minutes. Every line shows the position, the depth, the expected and the counted
     * number and how long it took, and the process ends with a non-zero status as soon as one count
     * is wrong.
     * <p>
     * Time complexity: O(n) for the n positions counted, which is the sum of the node counts.
     * Space complexity: O(1), the move lists are allocated once per Perft instance.
     *
     * @param pArgs "deep" to run the full gate, empty for the fast one; never null
     */
    public static void main(String[] pArgs) {
        boolean deep = pArgs.length > 0 && pArgs[0].equalsIgnoreCase("deep");
        System.out.println(deep ? "perft gate, full depths" : "perft gate, fast depths");

        boolean allMatched = true;
        if (deep) {
            allMatched &= check("start position", START_POSITION, 6, 119060324L);
            allMatched &= check("kiwipete", KIWIPETE, 5, 193690690L);
            allMatched &= check("position 3", POSITION_3, 7, 178633661L);
            allMatched &= check("position 4", POSITION_4, 5, 15833292L);
            allMatched &= check("position 5", POSITION_5, 5, 89941194L);
        } else {
            allMatched &= check("start position", START_POSITION, 5, 4865609L);
            allMatched &= check("kiwipete", KIWIPETE, 4, 4085603L);
            allMatched &= check("position 3", POSITION_3, 5, 674624L);
            allMatched &= check("position 4", POSITION_4, 4, 422333L);
            allMatched &= check("position 5", POSITION_5, 4, 2103487L);
        }

        System.out.println(allMatched ? "every count matched" : "at least one count was wrong");
        System.exit(allMatched ? 0 : 1);
    }

    /**
     * Counts one position to one depth and compares the result.
     * <p>
     * I read the position, count it, print what came out next to what was expected and how fast it
     * ran, and report whether the two agree. The speed is only printed for information, nothing
     * fails because of it.
     * <p>
     * Time complexity: O(n) for the n positions in the tree. Space complexity: O(1).
     *
     * @param pName     name of the position for the report, never null
     * @param pFen      the position in FEN, never null
     * @param pDepth    how many moves deep to count, 1 or more
     * @param pExpected the node count this position is known to have at that depth
     * @return true if the counted number matches the expected one
     */
    private static boolean check(String pName, String pFen, int pDepth, long pExpected) {
        Position position = fromFen(pFen);
        long start = System.nanoTime();
        long nodes = new Perft().count(position, pDepth);
        long millis = (System.nanoTime() - start) / 1_000_000L;

        boolean matched = nodes == pExpected;
        // a rate is only meaningful once the run took some measurable time
        String rate = millis > 0 ? (nodes / millis) + "k nodes/s" : "too fast to measure";
        System.out.printf("  %-15s depth %d  expected %,13d  got %,13d  %6d ms  %-20s %s%n",
                pName, pDepth, pExpected, nodes, millis, rate, matched ? "ok" : "WRONG");
        return matched;
    }

    /**
     * Reads a position from a FEN string.
     * <p>
     * The standard test positions are published as FEN, so the gate needs to read one. This is the
     * plain reader the tests need, it trusts its input and only understands what a correct FEN
     * contains: the piece placement from rank eight down to rank one, the side to move, the castling
     * rights, the en passant square and both counters, with the counters optional. The engine's own
     * FEN codec with full validation is a separate piece of work, this one exists so the generator
     * can be tested at all.
     * <p>
     * Time complexity: O(n) in the length of the FEN string. Space complexity: O(1) beyond the
     * position it builds.
     *
     * @param pFen a position in Forsyth Edwards notation, never null
     * @return the position it describes, never null
     * @throws IllegalArgumentException if the placement or a field cannot be read
     */
    public static Position fromFen(String pFen) {
        String[] fields = pFen.trim().split("\\s+");
        // placement and side to move are the two fields no FEN can do without
        if (fields.length < 2) {
            throw new IllegalArgumentException("a FEN needs at least a placement and a side to move: " + pFen);
        }

        Position position = Position.empty();
        String[] ranks = fields[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("a FEN placement needs eight ranks: " + fields[0]);
        }
        for (int index = 0; index < 8; index++) {
            // FEN writes rank eight first, while rank eight is row seven here
            int rank = 7 - index;
            int file = 0;
            for (char symbol : ranks[index].toCharArray()) {
                if (symbol >= '1' && symbol <= '8') {
                    // a digit stands for that many empty squares
                    file += symbol - '0';
                } else {
                    position.put(Pieces.fromFenChar(symbol), Bitboards.square(file, rank));
                    file++;
                }
            }
        }

        position.setSideToMove(fields[1].equals("w") ? Pieces.WHITE : Pieces.BLACK);

        if (fields.length > 2) {
            int rights = 0;
            if (fields[2].indexOf('K') >= 0) {
                rights |= Position.WHITE_KINGSIDE;
            }
            if (fields[2].indexOf('Q') >= 0) {
                rights |= Position.WHITE_QUEENSIDE;
            }
            if (fields[2].indexOf('k') >= 0) {
                rights |= Position.BLACK_KINGSIDE;
            }
            if (fields[2].indexOf('q') >= 0) {
                rights |= Position.BLACK_QUEENSIDE;
            }
            position.setCastlingRights(rights);
        }
        // a dash means no pawn can be captured en passant
        if (fields.length > 3 && !fields[3].equals("-")) {
            position.setEpSquare(Bitboards.squareOf(fields[3]));
        }
        if (fields.length > 4) {
            position.setHalfmoveClock(Integer.parseInt(fields[4]));
        }
        if (fields.length > 5) {
            position.setFullmoveNumber(Integer.parseInt(fields[5]));
        }
        return position;
    }
}
