package engine.imports;

/*
 * Purpose: NotationHelper turns moves into standard algebraic notation, the move format that PGN
 * files and chess players use. It knows the piece letters, the castling symbols, capture and
 * promotion marks, how identical pieces are told apart and how check and checkmate are marked. I
 * keep it free of game state, so the caller hands in what only the rules engine can know, such as
 * which rival pieces could make the same move and whether the move gives check.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.pieces.*;

import java.util.List;

public class NotationHelper {

    public NotationHelper() {

    }

    /**
     * Converts a move into algebraic notation without disambiguation or check marker.
     * <p>
     * Some callers and tests only need the basic form of a move. I delegate to the full version
     * with an empty disambiguation and no check marker.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove    move that was just played, never null
     * @param pFromCol column the piece started on, 0 to 7
     * @param pFromRow row the piece started on, 0 to 7 where 0 is rank 8
     * @return the notation such as "Nf3", "exd5" or "O-O", never null
     * @throws NullPointerException if pMove or its piece is null
     */
    public String toNotation(Move pMove, int pFromCol, int pFromRow) {
        return toNotation(pMove, pFromCol, pFromRow, "", "");
    }

    /**
     * Converts a move into standard algebraic notation (SAN).
     * <p>
     * Saved games have to be readable by other chess programs, which expect SAN with
     * disambiguation and check markers. Castling becomes O-O or O-O-O. For every other move I build
     * the piece letter, or the origin file for a pawn capture, add the disambiguation for pieces,
     * the capture marker, the target square and the promotion piece. The check or mate marker is
     * appended in every case, castling included.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove           move that was just played, never null
     * @param pFromCol        column the piece started on, 0 to 7
     * @param pFromRow        row the piece started on, 0 to 7 where 0 is rank 8
     * @param pDisambiguation origin file, rank or both that tell identical pieces apart, "" when no
     *                        other piece could make the move; ignored for pawns; never null
     * @param pSuffix         "+" for check, "#" for checkmate, "" otherwise; never null
     * @return the SAN string such as "Nbd2", "exd6", "e8=Q+" or "O-O#", never null
     * @throws NullPointerException if pMove or its piece is null
     */
    public String toNotation(Move pMove, int pFromCol, int pFromRow, String pDisambiguation, String pSuffix) {

        Piece p = pMove.getPiece();

        // a king moving two squares is always castling
        if (p instanceof King && Math.abs(pMove.getNewCol() - pFromCol) == 2) {
            return (pMove.getNewCol() > pFromCol ? "O-O" : "O-O-O") + pSuffix;
        }

        boolean isCapture = pMove.getCapture() != null;

        // pieces use their letter plus disambiguation, pawns only show their file on a capture
        String pieceChar;
        if (p instanceof Pawn) {
            pieceChar = isCapture ? file(pFromCol) : "";
        } else if (p instanceof Knight) {
            // K is already taken by the king
            pieceChar = "N" + pDisambiguation;
        } else {
            pieceChar = p.getType().getDisplayName().substring(0, 1) + pDisambiguation;
        }

        String captureMarker = isCapture ? "x" : "";

        String to = file(pMove.getNewCol()) + rank(pMove.getNewRow());

        // the promotion piece follows an equals sign
        String promo = pMove.getPromotionChoice() != null
                ? "=" + pMove.getPromotionChoice()
                : "";

        return pieceChar + captureMarker + to + promo + pSuffix;
    }

    /**
     * Works out how a moving piece is told apart from identical pieces that reach the same square.
     * <p>
     * SAN only names the origin when another piece of the same type and colour could also make the
     * move. I return nothing when there is no such rival, the origin file when no rival stands on
     * that file, the origin rank when no rival stands on that rank, and both when neither alone is
     * unique.
     * <p>
     * Time complexity: O(r) for r rivals. Space complexity: O(1).
     *
     * @param pFromCol column of the moving piece before the move, 0 to 7
     * @param pFromRow row of the moving piece before the move, 0 to 7 where 0 is rank 8
     * @param pRivals  other pieces of the same type and colour that can legally reach the target
     *                 square, still on their squares before the move; never null, may be empty
     * @return "", a file letter, a rank digit or file and rank together, never null
     * @throws NullPointerException if pRivals or one of its entries is null
     */
    public String disambiguation(int pFromCol, int pFromRow, List<Piece> pRivals) {
        // without a rival the plain notation is already unique
        if (pRivals.isEmpty()) {
            return "";
        }

        boolean fileShared = false;
        boolean rankShared = false;
        for (Piece rival : pRivals) {
            fileShared |= rival.getCol() == pFromCol;
            rankShared |= rival.getRow() == pFromRow;
        }

        // the file is preferred, then the rank, then both together
        if (!fileShared) {
            return file(pFromCol);
        }
        if (!rankShared) {
            return String.valueOf(rank(pFromRow));
        }
        return file(pFromCol) + rank(pFromRow);
    }

    /**
     * Returns the SAN marker for a move that gives check or checkmate.
     * <p>
     * Readers of a game need to see at a glance which moves attacked the king. A checkmate is
     * marked with #, a plain check with + and every other move gets no marker.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pGivesCheck  true if the opponent's king is attacked after the move
     * @param pIsCheckmate true if that check leaves the opponent without a legal move
     * @return "#", "+" or "", never null
     */
    public String checkSuffix(boolean pGivesCheck, boolean pIsCheckmate) {
        // mate wins over a plain check
        if (pIsCheckmate) {
            return "#";
        }
        return pGivesCheck ? "+" : "";
    }

    //COORDINATE HELPER

    /**
     * Column 0-7 -> file letter a-h
     */
    private static String file(int col) {
        return String.valueOf((char) ('a' + col));
    }

    /**
     * Row 0-7 (top = 0) -> chess rank 8-1
     */
    private static int rank(int row) {
        return 8 - row;
    }
}

