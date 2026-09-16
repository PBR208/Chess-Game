package engine.core;

/*
 * Purpose: Bitboards holds the square numbering and the bit twiddling every other class of the new
 * engine core builds on. A position is stored as a set of 64 bit words, one per piece kind, where
 * bit n stands for square n, so testing, adding and removing pieces becomes a single machine
 * instruction instead of a loop over an object graph. I number squares the way chess engines
 * normally do, a1 is 0 and h8 is 63, which makes a pawn push north a shift by eight and keeps the
 * numbering compatible with the square names UCI expects later on.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public final class Bitboards {

    // number of squares on a chess board, and therefore bits in one bitboard
    public static final int SQUARE_COUNT = 64;

    // empty set of squares
    public static final long EMPTY = 0L;
    // every square at once
    public static final long ALL = -1L;

    // the a-file, bit a1 plus every eighth bit above it
    public static final long FILE_A = 0x0101010101010101L;
    public static final long FILE_H = FILE_A << 7;
    // the first rank, squares a1 to h1
    public static final long RANK_1 = 0x00000000000000FFL;
    public static final long RANK_2 = RANK_1 << 8;
    public static final long RANK_4 = RANK_1 << 24;
    public static final long RANK_5 = RANK_1 << 32;
    public static final long RANK_7 = RANK_1 << 48;
    public static final long RANK_8 = RANK_1 << 56;

    private Bitboards() {
    }

    /**
     * Returns the square number of a file and a rank.
     * <p>
     * Everything else in the core speaks square numbers, while people and the UCI protocol speak
     * files and ranks. Rank 0 is the first rank and file 0 is the a-file, so a1 is 0, h1 is 7 and
     * h8 is 63.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pFile file of the square, 0 for the a-file up to 7 for the h-file
     * @param pRank rank of the square, 0 for the first rank up to 7 for the eighth
     * @return the square number, 0 to 63
     */
    public static int square(int pFile, int pRank) {
        // eight squares per rank, files count upwards inside a rank
        return pRank * 8 + pFile;
    }

    /**
     * Returns the file of a square.
     * <p>
     * Pawn captures, file masks and the en passant key all need the file of a square. The lowest
     * three bits of a square number are its file, because a rank is exactly eight squares wide.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare square number, 0 to 63
     * @return the file, 0 for the a-file up to 7 for the h-file
     */
    public static int fileOf(int pSquare) {
        return pSquare & 7;
    }

    /**
     * Returns the rank of a square.
     * <p>
     * Promotion, double pushes and the starting squares of castling all depend on the rank. Shifting
     * a square number right by three divides it by the eight squares of a rank.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare square number, 0 to 63
     * @return the rank, 0 for the first rank up to 7 for the eighth
     */
    public static int rankOf(int pSquare) {
        return pSquare >>> 3;
    }

    /**
     * Returns a bitboard with only one square set.
     * <p>
     * Adding or removing a single piece means flipping exactly one bit, so this is the most used
     * helper of the whole core. I shift a single one bit into the position of the square.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare square number, 0 to 63
     * @return a bitboard holding only that square
     */
    public static long bit(int pSquare) {
        return 1L << pSquare;
    }

    /**
     * Tells whether a square is part of a set.
     * <p>
     * Move generation and the make and unmake code constantly ask whether a square is occupied by a
     * certain kind of piece. I mask the set with the single square and compare against zero.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pBitboard set of squares to look in
     * @param pSquare   square number, 0 to 63
     * @return true if the square is part of the set
     */
    public static boolean contains(long pBitboard, int pSquare) {
        return (pBitboard & bit(pSquare)) != 0L;
    }

    /**
     * Counts the squares in a set.
     * <p>
     * Material counting, mobility and the perft bulk counting later all boil down to counting bits.
     * Long.bitCount compiles to a single popcount instruction on every machine this runs on.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pBitboard set of squares
     * @return how many squares are in the set, 0 to 64
     */
    public static int count(long pBitboard) {
        return Long.bitCount(pBitboard);
    }

    /**
     * Returns the lowest square of a set.
     * <p>
     * Walking a set of squares means repeatedly taking its lowest square and clearing it, which is
     * how every move loop in the generator will work. The number of trailing zeros is exactly that
     * square. An empty set has no lowest square and reports 64, which no loop should ever use.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pBitboard set of squares, normally not empty
     * @return the lowest square number in the set, or 64 when the set is empty
     */
    public static int lowestSquare(long pBitboard) {
        return Long.numberOfTrailingZeros(pBitboard);
    }

    /**
     * Removes the lowest square from a set.
     * <p>
     * This is the second half of walking a set of squares. Subtracting one from a number flips its
     * lowest one bit to zero and sets everything below it, so the bitwise and of both drops exactly
     * that lowest bit.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pBitboard set of squares
     * @return the set without its lowest square
     */
    public static long clearLowestSquare(long pBitboard) {
        return pBitboard & (pBitboard - 1);
    }

    /**
     * Returns the name of a square.
     * <p>
     * Saved games, the UCI protocol and every test failure message name squares the way players do.
     * I turn the file into a letter from a to h and the rank into a digit from 1 to 8.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) for the two character name.
     *
     * @param pSquare square number, 0 to 63
     * @return the square name such as "e4", never null
     * @throws IllegalArgumentException if pSquare is outside 0 to 63
     */
    public static String nameOf(int pSquare) {
        // a name outside the board would silently produce nonsense characters
        if (pSquare < 0 || pSquare >= SQUARE_COUNT) {
            throw new IllegalArgumentException("square " + pSquare + " is outside the board");
        }
        return "" + (char) ('a' + fileOf(pSquare)) + (char) ('1' + rankOf(pSquare));
    }

    /**
     * Returns the square a name stands for.
     * <p>
     * FEN fields and UCI moves arrive as text, so the core has to read square names as well as write
     * them. I accept exactly two characters, a file letter from a to h and a rank digit from 1 to 8.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pName square name such as "e4", never null
     * @return the square number, 0 to 63
     * @throws IllegalArgumentException if the name is not a square of the board
     */
    public static int squareOf(String pName) {
        // anything else is not a square and must not be guessed at
        if (pName == null || pName.length() != 2) {
            throw new IllegalArgumentException("square name " + pName + " must have two characters");
        }
        int file = pName.charAt(0) - 'a';
        int rank = pName.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("square name " + pName + " is outside the board");
        }
        return square(file, rank);
    }

    /**
     * Draws a set of squares as eight lines of text.
     * <p>
     * A wrong bitboard is unreadable as a number, so every failing test and every debugging session
     * needs it as a board. I print rank eight at the top down to rank one, with a hash for a square
     * in the set and a dot for one outside it.
     * <p>
     * Time complexity: O(1), always 64 squares. Space complexity: O(1) for the fixed size text.
     *
     * @param pBitboard set of squares to draw
     * @return eight lines of eight characters each, never null
     */
    public static String toBoardString(long pBitboard) {
        StringBuilder text = new StringBuilder();
        // rank eight is printed first, the way a board is seen from White's side
        for (int rank = 7; rank >= 0; rank--) {
            for (int file = 0; file < 8; file++) {
                text.append(contains(pBitboard, square(file, rank)) ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }
}
