package engine.core;

/*
 * Purpose: San writes a move the way players and PGN files do, as standard algebraic notation. That
 * looks simple and is not: the piece letter is left out for pawns, a pawn capture names the file it
 * came from, a piece that shares its target square with an identical piece has to be told apart by
 * file, rank or both, and a move that gives check or mate carries a marker that can only be known by
 * playing the move first. All of that needs the position around the move, so this works on the core
 * position and its move generator rather than on a move object that carries no context.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public final class San {

    private San() {
    }

    /**
     * Writes a legal move in standard algebraic notation.
     * <p>
     * Saved games have to be readable by other chess programs, which expect SAN and not a pair of
     * squares. Castling becomes O-O or O-O-O. Every other move is built from the piece letter, which
     * a pawn does not have, the origin file when a pawn captures, the part that tells identical
     * pieces apart, a cross for a capture, the target square and the promotion piece. Finally I play
     * the move to see whether it gives check and whether the opponent still has a reply, which is
     * what decides between no marker, a plus and a hash. The position is unchanged when this returns.
     * <p>
     * Time complexity: O(m) for the m legal moves of the position, generated once for the
     * disambiguation and once more after the move for the check marker.
     * Space complexity: O(m) for the two move lists.
     *
     * @param pPosition position the move is played in, never null and unchanged afterwards
     * @param pMove     a legal move of that position, packed by the Moves class
     * @return the move in standard algebraic notation, never null
     */
    public static String of(Position pPosition, int pMove) {
        int from = Moves.from(pMove);
        int to = Moves.to(pMove);
        int piece = pPosition.pieceAt(from);
        int type = Pieces.typeOf(piece);

        String text;
        // a king moving two files is castling, and the side follows from the direction
        if (Moves.isCastling(pMove)) {
            text = to > from ? "O-O" : "O-O-O";
        } else {
            // an en passant capture lands on an empty square but is still a capture
            boolean capture = pPosition.pieceAt(to) != Pieces.NONE || Moves.isEnPassant(pMove);
            StringBuilder move = new StringBuilder();

            if (type == Pieces.PAWN) {
                // a capturing pawn is named by the file it came from, a pushing pawn not at all
                if (capture) {
                    move.append(fileOf(from));
                }
            } else {
                move.append(Pieces.fenCharOf(Pieces.make(Pieces.WHITE, type)));
                move.append(disambiguation(pPosition, pMove, piece, from, to));
            }

            if (capture) {
                move.append('x');
            }
            move.append(Bitboards.nameOf(to));

            if (Moves.isPromotion(pMove)) {
                move.append('=').append(Pieces.fenCharOf(Pieces.make(Pieces.WHITE, Moves.promotionType(pMove))));
            }
            text = move.toString();
        }

        return text + checkSuffix(pPosition, pMove);
    }

    /**
     * Works out how a moving piece is told apart from identical pieces reaching the same square.
     * <p>
     * SAN only names the origin when another piece of the same kind could also make the move, for
     * example Nbd2 when both knights reach d2. I look through the legal moves for another piece of
     * the same type and colour that ends on the same square, and then name the file when no rival
     * shares it, the rank when no rival shares that, and both when neither is unique on its own.
     * <p>
     * Time complexity: O(m) for the m legal moves of the position. Space complexity: O(m) for the
     * move list.
     *
     * @param pPosition position the move is played in, never null
     * @param pMove     the move being written, packed by the Moves class
     * @param pPiece    piece code of the moving piece
     * @param pFrom     square the move starts on, 0 to 63
     * @param pTo       square the move ends on, 0 to 63
     * @return "", a file letter, a rank digit, or both; never null
     */
    private static String disambiguation(Position pPosition, int pMove, int pPiece, int pFrom, int pTo) {
        int[] moves = new int[MoveGen.MAX_MOVES];
        int count = MoveGen.generateLegal(pPosition, moves, 0);

        boolean rivalExists = false;
        boolean fileShared = false;
        boolean rankShared = false;

        for (int index = 0; index < count; index++) {
            int candidate = moves[index];
            int candidateFrom = Moves.from(candidate);
            // the move itself and moves to another square are no rivals
            if (candidateFrom == pFrom || Moves.to(candidate) != pTo) {
                continue;
            }
            // only an identical piece needs telling apart, a different one is named by its letter
            if (pPosition.pieceAt(candidateFrom) != pPiece) {
                continue;
            }
            rivalExists = true;
            fileShared |= Bitboards.fileOf(candidateFrom) == Bitboards.fileOf(pFrom);
            rankShared |= Bitboards.rankOf(candidateFrom) == Bitboards.rankOf(pFrom);
        }

        // without a rival the plain letter is already unique
        if (!rivalExists) {
            return "";
        }
        if (!fileShared) {
            return fileOf(pFrom);
        }
        if (!rankShared) {
            return rankOf(pFrom);
        }
        return fileOf(pFrom) + rankOf(pFrom);
    }

    /**
     * Returns the marker a move earns for giving check or mate.
     * <p>
     * Whether a move gives check can only be seen after it is played, so I play it, ask whether the
     * side to move is in check and whether it has any legal reply left, and take the move back again.
     * No reply while in check is mate and gets a hash, a check with a reply gets a plus, anything
     * else gets nothing.
     * <p>
     * Time complexity: O(m) for the m legal replies, which are generated to see whether any exists.
     * Space complexity: O(m) for the move list.
     *
     * @param pPosition position the move is played in, never null and unchanged afterwards
     * @param pMove     the move being written, packed by the Moves class
     * @return "#", "+" or "", never null
     */
    private static String checkSuffix(Position pPosition, int pMove) {
        pPosition.makeMove(pMove);
        boolean inCheck = MoveGen.isInCheck(pPosition, pPosition.sideToMove());
        int[] replies = new int[MoveGen.MAX_MOVES];
        boolean hasReply = MoveGen.generateLegal(pPosition, replies, 0) > 0;
        pPosition.unmakeMove(pMove);

        // mate wins over a plain check
        if (inCheck && !hasReply) {
            return "#";
        }
        return inCheck ? "+" : "";
    }

    /**
     * Returns the file of a square as the letter SAN uses.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare square number, 0 to 63
     * @return the file as a letter from a to h, never null
     */
    private static String fileOf(int pSquare) {
        return String.valueOf((char) ('a' + Bitboards.fileOf(pSquare)));
    }

    /**
     * Returns the rank of a square as the digit SAN uses.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare square number, 0 to 63
     * @return the rank as a digit from 1 to 8, never null
     */
    private static String rankOf(int pSquare) {
        return String.valueOf((char) ('1' + Bitboards.rankOf(pSquare)));
    }
}
