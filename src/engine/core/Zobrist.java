package engine.core;

/*
 * Purpose: Zobrist holds the random numbers that give every position a 64 bit key. A position key is
 * the exclusive or of one number per piece on its square, the castling rights, the en passant file
 * and the side to move, which means a move can update the key by xoring the few numbers it changes
 * instead of hashing the whole board. That is what makes repetition detection and, later, the
 * transposition table cheap. The numbers come from a fixed seed, so the same position always gets
 * the same key in every run and on every machine, which keeps saved games and tests reproducible.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public final class Zobrist {

    // one number per piece and square, the 768 numbers a position key is mostly made of
    private static final long[][] PIECE_SQUARE = new long[Pieces.COUNT][Bitboards.SQUARE_COUNT];
    // one number per combination of castling rights, indexed by the four bit mask itself
    private static final long[] CASTLING = new long[16];
    // one number per file a pawn could be captured on en passant
    private static final long[] EN_PASSANT_FILE = new long[8];
    // xored into the key while Black is to move
    private static final long SIDE_TO_MOVE;

    // any fixed value works, this one just has to stay the same forever
    private static final long SEED = 0x9E3779B97F4A7C15L;

    static {
        long state = SEED;
        for (int piece = 0; piece < Pieces.COUNT; piece++) {
            for (int square = 0; square < Bitboards.SQUARE_COUNT; square++) {
                state = nextRandom(state);
                PIECE_SQUARE[piece][square] = state;
            }
        }
        for (int rights = 0; rights < CASTLING.length; rights++) {
            state = nextRandom(state);
            CASTLING[rights] = state;
        }
        for (int file = 0; file < EN_PASSANT_FILE.length; file++) {
            state = nextRandom(state);
            EN_PASSANT_FILE[file] = state;
        }
        state = nextRandom(state);
        SIDE_TO_MOVE = state;
    }

    private Zobrist() {
    }

    /**
     * Produces the next number of the fixed random sequence.
     * <p>
     * The keys must be spread over the whole 64 bit range and must be identical in every run, so a
     * seeded generator is better here than anything that depends on the machine or the clock. This
     * is the xorshift64 generator, three shifts and three exclusive ors, which is more than good
     * enough for hash keys and needs no library.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pState current state of the generator, never zero
     * @return the next state, which is also the next random number
     */
    private static long nextRandom(long pState) {
        long state = pState;
        state ^= state << 13;
        state ^= state >>> 7;
        state ^= state << 17;
        return state;
    }

    /**
     * Returns the number of one piece standing on one square.
     * <p>
     * Putting a piece on a square and taking it off both xor this same number into the key, so a
     * move only has to touch the squares it actually changes.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPiece  piece code, Pieces.WHITE_PAWN up to Pieces.BLACK_KING
     * @param pSquare square number, 0 to 63
     * @return the random number of that piece on that square
     */
    public static long piece(int pPiece, int pSquare) {
        return PIECE_SQUARE[pPiece][pSquare];
    }

    /**
     * Returns the number of one set of castling rights.
     * <p>
     * Rights change rarely but they do belong to the position, because the same pieces with and
     * without the right to castle are different positions for the repetition rule. I index the table
     * with the four bit mask, so changing rights means xoring the old number out and the new one in.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pCastlingRights four bit mask of the remaining castling rights, 0 to 15
     * @return the random number of that combination
     */
    public static long castling(int pCastlingRights) {
        return CASTLING[pCastlingRights];
    }

    /**
     * Returns the number of an en passant file.
     * <p>
     * Only the file matters, because the rank of an en passant square follows from the side to move.
     * A position where a pawn can be captured en passant differs from the same position without that
     * option, which is why the file is part of the key at all.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pFile file of the en passant square, 0 for the a-file up to 7 for the h-file
     * @return the random number of that file
     */
    public static long enPassantFile(int pFile) {
        return EN_PASSANT_FILE[pFile];
    }

    /**
     * Returns the number that marks Black to move.
     * <p>
     * The same pieces on the same squares are two different positions depending on who moves next, so
     * one number is xored in while it is Black's turn and xored out again when it is White's.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the random number for the side to move
     */
    public static long sideToMove() {
        return SIDE_TO_MOVE;
    }
}
