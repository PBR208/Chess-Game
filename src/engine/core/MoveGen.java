package engine.core;

/*
 * Purpose: MoveGen answers the two questions a chess engine asks most often, which squares a piece
 * attacks and whether a square is under attack. Everything a king, a knight or a pawn can reach is
 * precomputed once into tables, because those moves never depend on the pieces around them. Sliding
 * pieces do depend on what blocks them, so their attacks are worked out with hyperbola quintessence,
 * which turns a ray lookup into a handful of arithmetic operations on the occupied squares instead
 * of a loop that walks square by square. The move generation itself builds on these attack sets.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public final class MoveGen {

    // squares a king reaches from each square
    private static final long[] KING_ATTACKS = new long[Bitboards.SQUARE_COUNT];
    // squares a knight jumps to from each square
    private static final long[] KNIGHT_ATTACKS = new long[Bitboards.SQUARE_COUNT];
    // squares a pawn of each colour captures on from each square
    private static final long[][] PAWN_ATTACKS = new long[2][Bitboards.SQUARE_COUNT];

    // the line through a square in each of the four sliding directions, without the square itself
    private static final long[] RANK_MASK = new long[Bitboards.SQUARE_COUNT];
    private static final long[] FILE_MASK = new long[Bitboards.SQUARE_COUNT];
    private static final long[] DIAGONAL_MASK = new long[Bitboards.SQUARE_COUNT];
    private static final long[] ANTI_DIAGONAL_MASK = new long[Bitboards.SQUARE_COUNT];

    // every square except the a-file, used to stop a westward shift from wrapping around
    private static final long NOT_FILE_A = ~Bitboards.FILE_A;
    // every square except the h-file, used to stop an eastward shift from wrapping around
    private static final long NOT_FILE_H = ~Bitboards.FILE_H;

    static {
        for (int square = 0; square < Bitboards.SQUARE_COUNT; square++) {
            long bit = Bitboards.bit(square);

            // a king steps east and west first, then the whole row moves one rank up and down
            long sideways = ((bit & NOT_FILE_H) << 1) | ((bit & NOT_FILE_A) >>> 1);
            long row = bit | sideways;
            KING_ATTACKS[square] = sideways | (row << 8) | (row >>> 8);

            // a knight moves one or two files sideways and then the rest of the way up or down
            long west1 = (bit & NOT_FILE_A) >>> 1;
            long west2 = (bit & ~(Bitboards.FILE_A | (Bitboards.FILE_A << 1))) >>> 2;
            long east1 = (bit & NOT_FILE_H) << 1;
            long east2 = (bit & ~(Bitboards.FILE_H | (Bitboards.FILE_H >>> 1))) << 2;
            long oneFile = west1 | east1;
            long twoFiles = west2 | east2;
            KNIGHT_ATTACKS[square] = (oneFile << 16) | (oneFile >>> 16) | (twoFiles << 8) | (twoFiles >>> 8);

            // a pawn captures diagonally forward, which is upwards for White and downwards for Black
            PAWN_ATTACKS[Pieces.WHITE][square] = ((bit & NOT_FILE_A) << 7) | ((bit & NOT_FILE_H) << 9);
            PAWN_ATTACKS[Pieces.BLACK][square] = ((bit & NOT_FILE_H) >>> 7) | ((bit & NOT_FILE_A) >>> 9);

            int file = Bitboards.fileOf(square);
            int rank = Bitboards.rankOf(square);
            RANK_MASK[square] = (Bitboards.RANK_1 << (8 * rank)) & ~bit;
            FILE_MASK[square] = (Bitboards.FILE_A << file) & ~bit;
            DIAGONAL_MASK[square] = buildRay(file, rank, 1, 1) | buildRay(file, rank, -1, -1);
            ANTI_DIAGONAL_MASK[square] = buildRay(file, rank, -1, 1) | buildRay(file, rank, 1, -1);
        }
    }

    private MoveGen() {
    }

    /**
     * Collects the squares of one ray leaving a square.
     * <p>
     * The diagonals a slider runs along are easier to build by walking them than to write down as
     * constants, and this runs once while the class is loaded. I step file by file and rank by rank
     * in the given direction until the ray leaves the board, collecting every square on the way. The
     * square the ray starts on is not part of the result.
     * <p>
     * Time complexity: O(8) for the longest possible ray. Space complexity: O(1).
     *
     * @param pFile     file the ray starts on, 0 to 7
     * @param pRank     rank the ray starts on, 0 to 7
     * @param pFileStep how far the ray moves sideways per step, -1, 0 or 1
     * @param pRankStep how far the ray moves up or down per step, -1, 0 or 1
     * @return the squares of the ray as a bitboard
     */
    private static long buildRay(int pFile, int pRank, int pFileStep, int pRankStep) {
        long ray = 0L;
        int file = pFile + pFileStep;
        int rank = pRank + pRankStep;
        // a ray ends at the edge of the board
        while (file >= 0 && file <= 7 && rank >= 0 && rank <= 7) {
            ray |= Bitboards.bit(Bitboards.square(file, rank));
            file += pFileStep;
            rank += pRankStep;
        }
        return ray;
    }

    /**
     * Returns the squares a king attacks from a square.
     * <p>
     * A king reaches its eight neighbours whatever else stands on the board, so the answer is a
     * table lookup. Whether one of those squares is safe to move to is a different question and not
     * decided here.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare square the king stands on, 0 to 63
     * @return the attacked squares as a bitboard
     */
    public static long kingAttacks(int pSquare) {
        return KING_ATTACKS[pSquare];
    }

    /**
     * Returns the squares a knight attacks from a square.
     * <p>
     * A knight jumps, so nothing can block it and its attacks depend on its square alone.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare square the knight stands on, 0 to 63
     * @return the attacked squares as a bitboard
     */
    public static long knightAttacks(int pSquare) {
        return KNIGHT_ATTACKS[pSquare];
    }

    /**
     * Returns the squares a pawn of one colour attacks from a square.
     * <p>
     * A pawn captures diagonally forward, which is towards rank eight for White and towards rank one
     * for Black, and it attacks those squares whether or not a piece stands there. The pawn's
     * forward push is not an attack and is not part of this.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pColour Pieces.WHITE or Pieces.BLACK
     * @param pSquare square the pawn stands on, 0 to 63
     * @return the attacked squares as a bitboard
     */
    public static long pawnAttacks(int pColour, int pSquare) {
        return PAWN_ATTACKS[pColour][pSquare];
    }

    /**
     * Returns the squares a bishop attacks from a square.
     * <p>
     * A bishop runs along both diagonals until something blocks it, and it attacks the blocking
     * square itself, since that is where a capture would happen. I work out both diagonals with the
     * same subtraction trick and put them together.
     * <p>
     * Time complexity: O(1), a fixed number of arithmetic operations. Space complexity: O(1).
     *
     * @param pSquare    square the bishop stands on, 0 to 63
     * @param pOccupancy every occupied square of the position
     * @return the attacked squares as a bitboard
     */
    public static long bishopAttacks(int pSquare, long pOccupancy) {
        return slidingAttacks(pSquare, pOccupancy, DIAGONAL_MASK[pSquare])
                | slidingAttacks(pSquare, pOccupancy, ANTI_DIAGONAL_MASK[pSquare]);
    }

    /**
     * Returns the squares a rook attacks from a square.
     * <p>
     * A rook runs along its rank and its file until something blocks it, and it attacks the blocking
     * square itself. I work out both lines with the same subtraction trick and put them together.
     * <p>
     * Time complexity: O(1), a fixed number of arithmetic operations. Space complexity: O(1).
     *
     * @param pSquare    square the rook stands on, 0 to 63
     * @param pOccupancy every occupied square of the position
     * @return the attacked squares as a bitboard
     */
    public static long rookAttacks(int pSquare, long pOccupancy) {
        return slidingAttacks(pSquare, pOccupancy, FILE_MASK[pSquare])
                | slidingAttacks(pSquare, pOccupancy, RANK_MASK[pSquare]);
    }

    /**
     * Returns the squares a queen attacks from a square.
     * <p>
     * A queen moves like a rook and like a bishop, so her attacks are both sets together.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare    square the queen stands on, 0 to 63
     * @param pOccupancy every occupied square of the position
     * @return the attacked squares as a bitboard
     */
    public static long queenAttacks(int pSquare, long pOccupancy) {
        return bishopAttacks(pSquare, pOccupancy) | rookAttacks(pSquare, pOccupancy);
    }

    /**
     * Works out how far a slider reaches along one line.
     * <p>
     * Walking a ray square by square is the obvious way and the slowest one, since a search asks for
     * this millions of times per second. This is the hyperbola quintessence trick instead: treating
     * the occupied squares of the line as a number and subtracting twice the slider flips exactly the
     * bits up to and including the first blocker, and doing the same on the reversed line covers the
     * other direction. The exclusive or of both, masked back to the line, is the attack set, and the
     * blocking squares stay in it because a capture ends there.
     * <p>
     * Time complexity: O(1), around ten arithmetic operations. Space complexity: O(1).
     *
     * @param pSquare    square the slider stands on, 0 to 63
     * @param pOccupancy every occupied square of the position
     * @param pLineMask  the line through the square, without the square itself
     * @return the attacked squares on that line
     */
    private static long slidingAttacks(int pSquare, long pOccupancy, long pLineMask) {
        long slider = Bitboards.bit(pSquare);
        // only the pieces on this line can block it
        long blockers = pOccupancy & pLineMask;
        long forward = blockers - 2 * slider;
        // the same subtraction on the mirrored line covers the other direction
        long backward = Long.reverse(Long.reverse(blockers) - 2 * Long.reverse(slider));
        return (forward ^ backward) & pLineMask;
    }

    /**
     * Tells whether one side attacks a square.
     * <p>
     * Check detection, castling and the legality of every king move come down to this question. I
     * ask it the other way around, which is what makes it fast: instead of generating all moves of
     * that side, I look from the square outwards and see whether the right kind of piece stands
     * where it would have to stand. For pawns that means looking along the capture directions of the
     * other colour, because a white pawn attacks this square exactly when it stands where a black
     * pawn from here could capture.
     * <p>
     * Time complexity: O(1), five table or slider lookups. Space complexity: O(1).
     *
     * @param pPosition position to look at, never null
     * @param pSquare   square that may be under attack, 0 to 63
     * @param pByColour side that may be attacking, Pieces.WHITE or Pieces.BLACK
     * @return true if that side attacks the square
     */
    public static boolean isSquareAttacked(Position pPosition, int pSquare, int pByColour) {
        // a pawn of the attacking colour has to stand where this square's own pawn attacks point
        if ((PAWN_ATTACKS[1 - pByColour][pSquare] & pPosition.pieces(Pieces.make(pByColour, Pieces.PAWN))) != 0L) {
            return true;
        }
        if ((KNIGHT_ATTACKS[pSquare] & pPosition.pieces(Pieces.make(pByColour, Pieces.KNIGHT))) != 0L) {
            return true;
        }
        if ((KING_ATTACKS[pSquare] & pPosition.pieces(Pieces.make(pByColour, Pieces.KING))) != 0L) {
            return true;
        }

        long occupancy = pPosition.occupancy();
        long queens = pPosition.pieces(Pieces.make(pByColour, Pieces.QUEEN));
        // a queen attacks like both sliders, so she is checked together with each of them
        if ((bishopAttacks(pSquare, occupancy)
                & (pPosition.pieces(Pieces.make(pByColour, Pieces.BISHOP)) | queens)) != 0L) {
            return true;
        }
        return (rookAttacks(pSquare, occupancy)
                & (pPosition.pieces(Pieces.make(pByColour, Pieces.ROOK)) | queens)) != 0L;
    }

    /**
     * Tells whether the king of one side is in check.
     * <p>
     * Almost every rule that ends a game starts here, and the legality filter of the generator uses
     * it after every candidate move. I look up where that king stands and ask whether the other side
     * attacks that square.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPosition position to look at, never null
     * @param pColour   side whose king is meant, Pieces.WHITE or Pieces.BLACK
     * @return true if that side's king is attacked
     */
    public static boolean isInCheck(Position pPosition, int pColour) {
        return isSquareAttacked(pPosition, pPosition.kingSquare(pColour), 1 - pColour);
    }
}
