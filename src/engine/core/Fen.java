package engine.core;

/*
 * Purpose: Fen reads and writes positions in Forsyth Edwards notation, the text every chess tool
 * speaks. Reading is the part that has to be strict: a FEN can describe something that is not a
 * chess position at all, with two kings of one colour, a pawn on the last rank, castling rights for
 * a rook that is not there or a side that is already in check while the other one is to move, and
 * every one of those would break the engine much later and far away from the cause. So this refuses
 * the position instead and says exactly what is wrong with it.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public final class Fen {

    // the position every game starts from, written the way the standard spells it
    public static final String START_POSITION = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    // pawns may never stand on the first or the last rank
    private static final long PAWN_FORBIDDEN_RANKS = Bitboards.RANK_1 | Bitboards.RANK_8;

    private Fen() {
    }

    /**
     * Reads a position from a FEN string and refuses anything that is not a chess position.
     * <p>
     * A FEN arrives from a saved game, a chess GUI or a person typing it, so it cannot be trusted.
     * I read the piece placement rank by rank from rank eight down to rank one, then the side to
     * move, the castling rights, the en passant square and both counters, and then check everything
     * that has to hold for a position to be reachable at all: each rank holds exactly eight squares,
     * each side has exactly one king, no pawn stands on the first or last rank, every castling right
     * has its king and its rook still on their home squares, an en passant square lies on the right
     * rank with the pawn that just passed standing behind it, and the side that is not to move is
     * not in check, which would mean the last move was illegal. The counters have to make sense too.
     * An en passant square that no pawn of the side to move could actually use is dropped, because
     * the engine only keeps a square a capture can really happen on, which keeps the position key
     * comparable between a game that was played and the same position read from text.
     * <p>
     * Time complexity: O(64) for the placement and the checks. Space complexity: O(1) beyond the
     * position it returns.
     *
     * @param pFen a position in Forsyth Edwards notation, with four to six fields; never null
     * @return the position it describes, never null
     * @throws IllegalArgumentException if the text is not a legal chess position, with a message
     *                                  naming the field and the reason
     */
    public static Position parse(String pFen) {
        // a null or empty FEN is a caller mistake worth naming clearly
        if (pFen == null || pFen.isBlank()) {
            throw new IllegalArgumentException("a FEN must not be empty");
        }
        String[] fields = pFen.trim().split("\\s+");
        // placement, side, castling and en passant are required, both counters may be left out
        if (fields.length < 4 || fields.length > 6) {
            throw new IllegalArgumentException("a FEN needs four to six fields, got " + fields.length + ": " + pFen);
        }

        Position position = Position.empty();
        readPlacement(position, fields[0]);

        int sideToMove = readSideToMove(fields[1]);
        position.setSideToMove(sideToMove);
        position.setCastlingRights(readCastlingRights(position, fields[2]));
        readEnPassant(position, fields[3], sideToMove);

        int halfmoveClock = fields.length > 4 ? readCounter(fields[4], 0, "half move clock") : 0;
        int fullmoveNumber = fields.length > 5 ? readCounter(fields[5], 1, "full move number") : 1;
        position.setHalfmoveClock(halfmoveClock);
        position.setFullmoveNumber(fullmoveNumber);

        checkPosition(position, sideToMove);
        return position;
    }

    /**
     * Writes a position as a FEN string.
     * <p>
     * Saved games, the UCI protocol and every debugging output need the position as text. I walk the
     * board from rank eight down to rank one, counting empty squares between pieces the way the
     * standard does, and then add the side to move, the castling rights in the usual KQkq order, the
     * en passant square and both counters. Reading the result back gives the same position.
     * <p>
     * Time complexity: O(64) for the squares of the board. Space complexity: O(1), the text has a
     * bounded length.
     *
     * @param pPosition position to write, never null
     * @return the position in Forsyth Edwards notation, never null
     */
    public static String write(Position pPosition) {
        StringBuilder fen = new StringBuilder();

        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int piece = pPosition.pieceAt(Bitboards.square(file, rank));
                if (piece == Pieces.NONE) {
                    empty++;
                } else {
                    // a run of empty squares is written as its length
                    if (empty > 0) {
                        fen.append(empty);
                        empty = 0;
                    }
                    fen.append(Pieces.fenCharOf(piece));
                }
            }
            if (empty > 0) {
                fen.append(empty);
            }
            if (rank > 0) {
                fen.append('/');
            }
        }

        fen.append(pPosition.sideToMove() == Pieces.WHITE ? " w " : " b ");

        String castling = writeCastlingRights(pPosition.castlingRights());
        fen.append(castling).append(' ');

        int epSquare = pPosition.epSquare();
        fen.append(epSquare == Position.NO_EN_PASSANT ? "-" : Bitboards.nameOf(epSquare));

        fen.append(' ').append(pPosition.halfmoveClock());
        fen.append(' ').append(pPosition.fullmoveNumber());
        return fen.toString();
    }

    /**
     * Puts the pieces of the placement field onto the board.
     * <p>
     * The placement lists the ranks from eight down to one, separated by slashes, with a letter per
     * piece and a digit for a run of empty squares. I refuse a field that does not have eight ranks
     * or a rank that does not describe exactly eight squares, because everything after that would
     * silently sit on the wrong square.
     * <p>
     * Time complexity: O(64). Space complexity: O(1).
     *
     * @param pPosition empty position that receives the pieces, never null
     * @param pField    the placement field of the FEN, never null
     * @throws IllegalArgumentException if the placement is not eight ranks of eight squares
     */
    private static void readPlacement(Position pPosition, String pField) {
        String[] ranks = pField.split("/", -1);
        if (ranks.length != 8) {
            throw new IllegalArgumentException("the placement needs eight ranks, got " + ranks.length + ": " + pField);
        }
        for (int index = 0; index < 8; index++) {
            // the first rank of the text is rank eight, which is row seven here
            int rank = 7 - index;
            int file = 0;
            for (char symbol : ranks[index].toCharArray()) {
                if (symbol >= '1' && symbol <= '8') {
                    file += symbol - '0';
                } else {
                    // a letter outside the piece letters is reported by fromFenChar
                    if (file > 7) {
                        throw new IllegalArgumentException("rank " + (rank + 1) + " has more than eight squares: " + ranks[index]);
                    }
                    pPosition.put(Pieces.fromFenChar(symbol), Bitboards.square(file, rank));
                    file++;
                }
            }
            if (file != 8) {
                throw new IllegalArgumentException("rank " + (rank + 1) + " describes " + file
                        + " squares instead of eight: " + ranks[index]);
            }
        }
    }

    /**
     * Reads the side to move.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pField the side to move field, "w" or "b"; never null
     * @return Pieces.WHITE or Pieces.BLACK
     * @throws IllegalArgumentException if the field is neither w nor b
     */
    private static int readSideToMove(String pField) {
        if (pField.equals("w")) {
            return Pieces.WHITE;
        }
        if (pField.equals("b")) {
            return Pieces.BLACK;
        }
        throw new IllegalArgumentException("the side to move must be w or b, got: " + pField);
    }

    /**
     * Reads the castling rights and checks that the pieces behind them are still there.
     * <p>
     * A right only means something while its king and its rook stand on their home squares. The old
     * engine inferred rights from a first-move flag, so a position that was loaded rather than
     * played could claim a castling nobody could perform. I read the letters, refuse anything else
     * and then check each right against the board.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPosition position with its pieces already placed, never null
     * @param pField    the castling field, "-" or a combination of K, Q, k and q; never null
     * @return the rights as the four bit mask the position uses
     * @throws IllegalArgumentException if a letter is unknown or a right has no king or rook
     */
    private static int readCastlingRights(Position pPosition, String pField) {
        if (pField.equals("-")) {
            return 0;
        }
        int rights = 0;
        for (char symbol : pField.toCharArray()) {
            int right = switch (symbol) {
                case 'K' -> Position.WHITE_KINGSIDE;
                case 'Q' -> Position.WHITE_QUEENSIDE;
                case 'k' -> Position.BLACK_KINGSIDE;
                case 'q' -> Position.BLACK_QUEENSIDE;
                default -> throw new IllegalArgumentException(
                        "the castling field may only hold K, Q, k, q or a dash, got: " + pField);
            };
            // the same right twice is a malformed field rather than a harmless repetition
            if ((rights & right) != 0) {
                throw new IllegalArgumentException("the castling field repeats " + symbol + ": " + pField);
            }
            rights |= right;
        }

        checkCastlingRight(pPosition, rights, Position.WHITE_KINGSIDE, Pieces.WHITE, "e1", "h1", "K");
        checkCastlingRight(pPosition, rights, Position.WHITE_QUEENSIDE, Pieces.WHITE, "e1", "a1", "Q");
        checkCastlingRight(pPosition, rights, Position.BLACK_KINGSIDE, Pieces.BLACK, "e8", "h8", "k");
        checkCastlingRight(pPosition, rights, Position.BLACK_QUEENSIDE, Pieces.BLACK, "e8", "a8", "q");
        return rights;
    }

    /**
     * Checks that one castling right still has its king and its rook.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPosition   position with its pieces already placed, never null
     * @param pRights     the rights read so far
     * @param pRight      the single right to check
     * @param pColour     colour the right belongs to
     * @param pKingSquare name of the king's home square
     * @param pRookSquare name of the rook's corner
     * @param pLetter     the FEN letter of the right, for the error message
     * @throws IllegalArgumentException if the right is claimed without the king or the rook
     */
    private static void checkCastlingRight(Position pPosition, int pRights, int pRight, int pColour,
                                           String pKingSquare, String pRookSquare, String pLetter) {
        if ((pRights & pRight) == 0) {
            return;
        }
        if (pPosition.pieceAt(Bitboards.squareOf(pKingSquare)) != Pieces.make(pColour, Pieces.KING)) {
            throw new IllegalArgumentException("castling right " + pLetter + " needs a king on " + pKingSquare);
        }
        if (pPosition.pieceAt(Bitboards.squareOf(pRookSquare)) != Pieces.make(pColour, Pieces.ROOK)) {
            throw new IllegalArgumentException("castling right " + pLetter + " needs a rook on " + pRookSquare);
        }
    }

    /**
     * Reads the en passant square and checks that a double push really just happened.
     * <p>
     * The en passant square is where a capturing pawn would land, so it lies on the sixth rank while
     * White is to move and on the third while Black is. The pawn that passed has to stand behind it
     * and the square itself, as well as the one it came from, has to be empty. A square that passes
     * all that but that no pawn of the side to move actually attacks is dropped, because the engine
     * only keeps en passant squares a capture can happen on, and keeping an unusable one would give
     * two identical positions different keys.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPosition   position with its pieces already placed, never null
     * @param pField      the en passant field, "-" or a square name; never null
     * @param pSideToMove side to move, Pieces.WHITE or Pieces.BLACK
     * @throws IllegalArgumentException if the square is not a square, lies on the wrong rank or has
     *                                  no double pushed pawn behind it
     */
    private static void readEnPassant(Position pPosition, String pField, int pSideToMove) {
        if (pField.equals("-")) {
            return;
        }
        // squareOf reports anything that is not a square name
        int square = Bitboards.squareOf(pField);
        boolean whiteToMove = pSideToMove == Pieces.WHITE;
        int expectedRank = whiteToMove ? 5 : 2;
        if (Bitboards.rankOf(square) != expectedRank) {
            throw new IllegalArgumentException("an en passant square with " + (whiteToMove ? "White" : "Black")
                    + " to move must lie on rank " + (expectedRank + 1) + ", got: " + pField);
        }

        // the pawn that just passed stands one square beyond the skipped one
        int pawnSquare = whiteToMove ? square - 8 : square + 8;
        int cameFrom = whiteToMove ? square + 8 : square - 8;
        int passedPawn = Pieces.make(1 - pSideToMove, Pieces.PAWN);
        if (pPosition.pieceAt(pawnSquare) != passedPawn) {
            throw new IllegalArgumentException("an en passant square needs the pawn that passed on "
                    + Bitboards.nameOf(pawnSquare) + ": " + pField);
        }
        if (pPosition.pieceAt(square) != Pieces.NONE || pPosition.pieceAt(cameFrom) != Pieces.NONE) {
            throw new IllegalArgumentException("the squares an en passant pawn crossed must be empty: " + pField);
        }

        // only a square a pawn of the side to move really attacks is worth keeping
        long capturers = MoveGen.pawnAttacks(1 - pSideToMove, square)
                & pPosition.pieces(Pieces.make(pSideToMove, Pieces.PAWN));
        if (capturers != 0L) {
            pPosition.setEpSquare(square);
        }
    }

    /**
     * Reads one of the two move counters.
     * <p>
     * Time complexity: O(n) in the length of the field. Space complexity: O(1).
     *
     * @param pField   the counter field, never null
     * @param pMinimum smallest value the counter may have
     * @param pName    name of the counter for the error message, never null
     * @return the counter value
     * @throws IllegalArgumentException if the field is not a number or below the minimum
     */
    private static int readCounter(String pField, int pMinimum, String pName) {
        int value;
        try {
            value = Integer.parseInt(pField);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the " + pName + " must be a number, got: " + pField);
        }
        if (value < pMinimum) {
            throw new IllegalArgumentException("the " + pName + " must be at least " + pMinimum + ", got: " + value);
        }
        return value;
    }

    /**
     * Checks the things that make a placement a real chess position.
     * <p>
     * A FEN can describe a board that no game could ever reach. I check that each side has exactly
     * one king, that no pawn stands on the first or the last rank, where it would have had to
     * promote, and that the side which is not to move is not in check, because that would mean the
     * move just played left its own king attacked.
     * <p>
     * Time complexity: O(1), a few bitboard tests and one attack lookup. Space complexity: O(1).
     *
     * @param pPosition   the position that was read, never null
     * @param pSideToMove side to move, Pieces.WHITE or Pieces.BLACK
     * @throws IllegalArgumentException if the position could not occur in a game
     */
    private static void checkPosition(Position pPosition, int pSideToMove) {
        if (Bitboards.count(pPosition.pieces(Pieces.WHITE_KING)) != 1) {
            throw new IllegalArgumentException("White needs exactly one king");
        }
        if (Bitboards.count(pPosition.pieces(Pieces.BLACK_KING)) != 1) {
            throw new IllegalArgumentException("Black needs exactly one king");
        }

        long pawns = pPosition.pieces(Pieces.WHITE_PAWN) | pPosition.pieces(Pieces.BLACK_PAWN);
        if ((pawns & PAWN_FORBIDDEN_RANKS) != 0L) {
            throw new IllegalArgumentException("no pawn may stand on the first or the last rank");
        }

        // the side that just moved must not have left its own king in check
        if (MoveGen.isInCheck(pPosition, 1 - pSideToMove)) {
            throw new IllegalArgumentException("the side that is not to move must not be in check");
        }
    }

    /**
     * Writes the castling rights the way FEN spells them.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pCastlingRights the four bit mask of the remaining rights
     * @return the rights as KQkq in that order, or a dash when there are none
     */
    private static String writeCastlingRights(int pCastlingRights) {
        StringBuilder text = new StringBuilder();
        if ((pCastlingRights & Position.WHITE_KINGSIDE) != 0) {
            text.append('K');
        }
        if ((pCastlingRights & Position.WHITE_QUEENSIDE) != 0) {
            text.append('Q');
        }
        if ((pCastlingRights & Position.BLACK_KINGSIDE) != 0) {
            text.append('k');
        }
        if ((pCastlingRights & Position.BLACK_QUEENSIDE) != 0) {
            text.append('q');
        }
        // a position where nobody may castle writes a dash
        return text.length() == 0 ? "-" : text.toString();
    }
}
