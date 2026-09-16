package engine.core;

/*
 * Purpose: MoveGen produces the moves of a position and answers which squares a side attacks.
 * Everything a king, a knight or a pawn can reach is precomputed once per square, because those
 * moves never depend on the pieces around them, while sliding pieces are worked out with hyperbola
 * quintessence, which turns a ray walk into a subtraction on the occupied squares of that line.
 * Moves are written into an array the caller owns, so generating them allocates nothing, and the
 * legal generator keeps only the moves that leave the own king safe, checked by playing each one.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

public final class MoveGen {

    // no chess position has more moves than this, the known maximum is 218
    public static final int MAX_MOVES = 256;

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

    // the rank a white pawn stands on after a single push from its start, and Black's equivalent
    private static final long RANK_3 = Bitboards.RANK_1 << 16;
    private static final long RANK_6 = Bitboards.RANK_1 << 40;

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
     * table lookup. Whether one of those squares is safe to move to is decided elsewhere.
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
     * A pawn captures diagonally forward, towards rank eight for White and towards rank one for
     * Black, whether or not a piece stands there. Its forward push is not an attack and not part of
     * this set.
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
     * square itself, since that is where a capture would happen.
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
     * square itself.
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
     * Almost every rule that ends a game starts here, and the legality filter uses it after every
     * candidate move. I look up where that king stands and ask whether the other side attacks that
     * square. A position without that king, which only happens in tests, counts as not in check.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPosition position to look at, never null
     * @param pColour   side whose king is meant, Pieces.WHITE or Pieces.BLACK
     * @return true if that side's king is attacked
     */
    public static boolean isInCheck(Position pPosition, int pColour) {
        int king = pPosition.kingSquare(pColour);
        // a board without that king cannot have it in check
        if (king >= Bitboards.SQUARE_COUNT) {
            return false;
        }
        return isSquareAttacked(pPosition, king, 1 - pColour);
    }

    /**
     * Writes every legal move of the side to move into an array.
     * <p>
     * This is what a search and the rules ask for, moves that may really be played. I generate the
     * moves that follow the movement rules first and then keep only those that do not leave the own
     * king in check, which I find out by playing each move on the position and taking it back again.
     * That is slower than tracking pins and checks directly, but it is exactly as correct, and perft
     * proves it against known node counts. The position is unchanged when this returns.
     * <p>
     * Time complexity: O(m) legality tests for m pseudo legal moves, each constant time.
     * Space complexity: O(1), the caller owns the array.
     *
     * @param pPosition position to generate for, never null
     * @param pMoves    array that receives the moves, at least pOffset plus MAX_MOVES long
     * @param pOffset   index the first move is written to, 0 or more
     * @return how many moves were written
     * @throws ArrayIndexOutOfBoundsException if the array is too short
     */
    public static int generateLegal(Position pPosition, int[] pMoves, int pOffset) {
        int pseudoLegal = generatePseudoLegal(pPosition, pMoves, pOffset);
        int us = pPosition.sideToMove();
        int kept = pOffset;

        for (int index = pOffset; index < pOffset + pseudoLegal; index++) {
            int move = pMoves[index];
            pPosition.makeMove(move);
            // a move that leaves the own king attacked was never legal
            boolean legal = !isInCheck(pPosition, us);
            pPosition.unmakeMove(move);
            if (legal) {
                pMoves[kept++] = move;
            }
        }
        return kept - pOffset;
    }

    /**
     * Writes every move that follows the movement rules into an array.
     * <p>
     * These are the moves a piece may make by its own rules, including captures, promotions, double
     * pushes, en passant and castling, but without asking whether the own king is left in check. I
     * take the piece kinds one after another, and for castling I check that the rook still stands in
     * its corner, that the squares in between are empty and that the king neither starts in check
     * nor crosses an attacked square, which the rights alone do not guarantee for a position that
     * was set up rather than played.
     * <p>
     * Time complexity: O(m) for the m moves written, each found with constant work.
     * Space complexity: O(1), the caller owns the array.
     *
     * @param pPosition position to generate for, never null
     * @param pMoves    array that receives the moves, at least pOffset plus MAX_MOVES long
     * @param pOffset   index the first move is written to, 0 or more
     * @return how many moves were written
     * @throws ArrayIndexOutOfBoundsException if the array is too short
     */
    public static int generatePseudoLegal(Position pPosition, int[] pMoves, int pOffset) {
        int index = pOffset;
        int us = pPosition.sideToMove();
        int them = 1 - us;
        long occupancy = pPosition.occupancy();
        long enemies = pPosition.occupancy(them);
        // a move may end anywhere that is not occupied by an own piece
        long targets = ~pPosition.occupancy(us);

        index = generatePawnMoves(pPosition, pMoves, index, us, occupancy, enemies);
        index = generateLeaperMoves(pPosition, pMoves, index, Pieces.make(us, Pieces.KNIGHT), targets);
        index = generateLeaperMoves(pPosition, pMoves, index, Pieces.make(us, Pieces.KING), targets);
        index = generateSliderMoves(pPosition, pMoves, index, Pieces.make(us, Pieces.BISHOP), targets, occupancy);
        index = generateSliderMoves(pPosition, pMoves, index, Pieces.make(us, Pieces.ROOK), targets, occupancy);
        index = generateSliderMoves(pPosition, pMoves, index, Pieces.make(us, Pieces.QUEEN), targets, occupancy);
        index = generateCastlingMoves(pPosition, pMoves, index, us, occupancy);

        return index - pOffset;
    }

    /**
     * Writes the moves of every pawn of one side.
     * <p>
     * Pawns carry four different moves and they are the only piece whose captures differ from its
     * steps, so they get their own pass. I push every pawn one square forward onto empty squares,
     * push those that started on their home rank a second square, turn every push and capture that
     * reaches the last rank into four promotions, add the diagonal captures of each pawn and finally
     * the en passant capture, which lands on an empty square and is recognised by the position's en
     * passant square.
     * <p>
     * Time complexity: O(m) for the m pawn moves written. Space complexity: O(1).
     *
     * @param pPosition  position to generate for, never null
     * @param pMoves     array that receives the moves
     * @param pIndex     index the first move is written to
     * @param pUs        side to move, Pieces.WHITE or Pieces.BLACK
     * @param pOccupancy every occupied square
     * @param pEnemies   every square the other side occupies
     * @return the index after the last move written
     */
    private static int generatePawnMoves(Position pPosition, int[] pMoves, int pIndex, int pUs,
                                         long pOccupancy, long pEnemies) {
        int index = pIndex;
        long pawns = pPosition.pieces(Pieces.make(pUs, Pieces.PAWN));
        boolean white = pUs == Pieces.WHITE;
        // how far one step forward moves a square number, and the rank a promotion happens on
        int step = white ? 8 : -8;
        long lastRank = white ? Bitboards.RANK_8 : Bitboards.RANK_1;
        long stepRank = white ? RANK_3 : RANK_6;

        long singlePushes = (white ? pawns << 8 : pawns >>> 8) & ~pOccupancy;
        // only a pawn that reached the third or sixth rank in one step came from its home rank
        long doublePushes = (white ? (singlePushes & stepRank) << 8 : (singlePushes & stepRank) >>> 8) & ~pOccupancy;

        long quietPushes = singlePushes & ~lastRank;
        while (quietPushes != 0L) {
            int to = Bitboards.lowestSquare(quietPushes);
            quietPushes = Bitboards.clearLowestSquare(quietPushes);
            pMoves[index++] = Moves.encode(to - step, to);
        }

        long promotionPushes = singlePushes & lastRank;
        while (promotionPushes != 0L) {
            int to = Bitboards.lowestSquare(promotionPushes);
            promotionPushes = Bitboards.clearLowestSquare(promotionPushes);
            index = addPromotions(pMoves, index, to - step, to);
        }

        while (doublePushes != 0L) {
            int to = Bitboards.lowestSquare(doublePushes);
            doublePushes = Bitboards.clearLowestSquare(doublePushes);
            pMoves[index++] = Moves.encode(to - 2 * step, to);
        }

        long remaining = pawns;
        while (remaining != 0L) {
            int from = Bitboards.lowestSquare(remaining);
            remaining = Bitboards.clearLowestSquare(remaining);
            long captures = PAWN_ATTACKS[pUs][from] & pEnemies;
            while (captures != 0L) {
                int to = Bitboards.lowestSquare(captures);
                captures = Bitboards.clearLowestSquare(captures);
                // a capture onto the last rank promotes just like a push does
                if ((Bitboards.bit(to) & lastRank) != 0L) {
                    index = addPromotions(pMoves, index, from, to);
                } else {
                    pMoves[index++] = Moves.encode(from, to);
                }
            }
        }

        int epSquare = pPosition.epSquare();
        if (epSquare != Position.NO_EN_PASSANT) {
            // a pawn that could be captured from the en passant square is one that may capture there
            long capturers = PAWN_ATTACKS[1 - pUs][epSquare] & pawns;
            while (capturers != 0L) {
                int from = Bitboards.lowestSquare(capturers);
                capturers = Bitboards.clearLowestSquare(capturers);
                pMoves[index++] = Moves.encodeEnPassant(from, epSquare);
            }
        }
        return index;
    }

    /**
     * Writes the four promotions of one pawn move.
     * <p>
     * A pawn reaching the last rank is four different moves, and underpromotions matter often enough
     * that leaving them out would make the move counts wrong. I write queen, rook, bishop and knight
     * in that order, since the queen is almost always the one a search wants first.
     * <p>
     * Time complexity: O(1), always four moves. Space complexity: O(1).
     *
     * @param pMoves array that receives the moves
     * @param pIndex index the first promotion is written to
     * @param pFrom  square the pawn starts on
     * @param pTo    square on the last rank
     * @return the index after the fourth promotion
     */
    private static int addPromotions(int[] pMoves, int pIndex, int pFrom, int pTo) {
        int index = pIndex;
        pMoves[index++] = Moves.encodePromotion(pFrom, pTo, Moves.PROMOTION_QUEEN);
        pMoves[index++] = Moves.encodePromotion(pFrom, pTo, Moves.PROMOTION_ROOK);
        pMoves[index++] = Moves.encodePromotion(pFrom, pTo, Moves.PROMOTION_BISHOP);
        pMoves[index++] = Moves.encodePromotion(pFrom, pTo, Moves.PROMOTION_KNIGHT);
        return index;
    }

    /**
     * Writes the moves of every knight or king of one side.
     * <p>
     * Both pieces reach a fixed set of squares that nothing can block, so their moves are the table
     * for their square without the squares their own side occupies.
     * <p>
     * Time complexity: O(m) for the m moves written. Space complexity: O(1).
     *
     * @param pPosition position to generate for, never null
     * @param pMoves    array that receives the moves
     * @param pIndex    index the first move is written to
     * @param pPiece    piece code of the knights or the king of the side to move
     * @param pTargets  every square the side to move may land on
     * @return the index after the last move written
     */
    private static int generateLeaperMoves(Position pPosition, int[] pMoves, int pIndex, int pPiece,
                                           long pTargets) {
        int index = pIndex;
        long pieces = pPosition.pieces(pPiece);
        boolean knight = Pieces.typeOf(pPiece) == Pieces.KNIGHT;
        while (pieces != 0L) {
            int from = Bitboards.lowestSquare(pieces);
            pieces = Bitboards.clearLowestSquare(pieces);
            long moves = (knight ? KNIGHT_ATTACKS[from] : KING_ATTACKS[from]) & pTargets;
            while (moves != 0L) {
                int to = Bitboards.lowestSquare(moves);
                moves = Bitboards.clearLowestSquare(moves);
                pMoves[index++] = Moves.encode(from, to);
            }
        }
        return index;
    }

    /**
     * Writes the moves of every slider of one kind.
     * <p>
     * Bishops, rooks and queens all reach as far as the occupied squares let them, so they share one
     * pass that differs only in which attack set is asked for.
     * <p>
     * Time complexity: O(m) for the m moves written. Space complexity: O(1).
     *
     * @param pPosition  position to generate for, never null
     * @param pMoves     array that receives the moves
     * @param pIndex     index the first move is written to
     * @param pPiece     piece code of the sliders of the side to move
     * @param pTargets   every square the side to move may land on
     * @param pOccupancy every occupied square, which is what blocks a slider
     * @return the index after the last move written
     */
    private static int generateSliderMoves(Position pPosition, int[] pMoves, int pIndex, int pPiece,
                                           long pTargets, long pOccupancy) {
        int index = pIndex;
        int type = Pieces.typeOf(pPiece);
        long pieces = pPosition.pieces(pPiece);
        while (pieces != 0L) {
            int from = Bitboards.lowestSquare(pieces);
            pieces = Bitboards.clearLowestSquare(pieces);
            long attacks = switch (type) {
                case Pieces.BISHOP -> bishopAttacks(from, pOccupancy);
                case Pieces.ROOK -> rookAttacks(from, pOccupancy);
                default -> queenAttacks(from, pOccupancy);
            };
            long moves = attacks & pTargets;
            while (moves != 0L) {
                int to = Bitboards.lowestSquare(moves);
                moves = Bitboards.clearLowestSquare(moves);
                pMoves[index++] = Moves.encode(from, to);
            }
        }
        return index;
    }

    /**
     * Writes the castling moves of the side to move.
     * <p>
     * Castling is the one move with conditions that go beyond the pieces involved. I check that the
     * right still exists, that the rook really stands in its corner, which a position set up from
     * FEN does not guarantee, that every square between king and rook is empty, and that the king
     * neither stands in check nor crosses a square the other side attacks. The square the king lands
     * on is checked as well, so an illegal castling never leaves this method.
     * <p>
     * Time complexity: O(1), at most six attack tests. Space complexity: O(1).
     *
     * @param pPosition  position to generate for, never null
     * @param pMoves     array that receives the moves
     * @param pIndex     index the first move is written to
     * @param pUs        side to move, Pieces.WHITE or Pieces.BLACK
     * @param pOccupancy every occupied square
     * @return the index after the last move written
     */
    private static int generateCastlingMoves(Position pPosition, int[] pMoves, int pIndex, int pUs, long pOccupancy) {
        int index = pIndex;
        boolean white = pUs == Pieces.WHITE;
        int kingFrom = white ? Bitboards.squareOf("e1") : Bitboards.squareOf("e8");
        // a king that is already in check may not castle at all
        if (pPosition.pieceAt(kingFrom) != Pieces.make(pUs, Pieces.KING)
                || isSquareAttacked(pPosition, kingFrom, 1 - pUs)) {
            return index;
        }

        int kingsideRight = white ? Position.WHITE_KINGSIDE : Position.BLACK_KINGSIDE;
        int queensideRight = white ? Position.WHITE_QUEENSIDE : Position.BLACK_QUEENSIDE;
        int rook = Pieces.make(pUs, Pieces.ROOK);

        if ((pPosition.castlingRights() & kingsideRight) != 0
                && pPosition.pieceAt(kingFrom + 3) == rook
                && (pOccupancy & (Bitboards.bit(kingFrom + 1) | Bitboards.bit(kingFrom + 2))) == 0L
                && !isSquareAttacked(pPosition, kingFrom + 1, 1 - pUs)
                && !isSquareAttacked(pPosition, kingFrom + 2, 1 - pUs)) {
            pMoves[index++] = Moves.encodeCastling(kingFrom, kingFrom + 2);
        }

        if ((pPosition.castlingRights() & queensideRight) != 0
                && pPosition.pieceAt(kingFrom - 4) == rook
                // the b-file square has to be empty as well, although the king never touches it
                && (pOccupancy & (Bitboards.bit(kingFrom - 1) | Bitboards.bit(kingFrom - 2)
                | Bitboards.bit(kingFrom - 3))) == 0L
                && !isSquareAttacked(pPosition, kingFrom - 1, 1 - pUs)
                && !isSquareAttacked(pPosition, kingFrom - 2, 1 - pUs)) {
            pMoves[index++] = Moves.encodeCastling(kingFrom, kingFrom - 2);
        }
        return index;
    }
}
