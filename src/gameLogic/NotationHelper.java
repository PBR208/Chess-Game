package gameLogic;

import pieces.*;

public class NotationHelper {

    public NotationHelper() {

    }

    public String toNotation(Move m, int fromCol, int fromRow) {

        Piece p = m.getPiece();

        // Castling
        // A king moving two squares is always a castle.
        if (p instanceof King && Math.abs(m.getNewCol() - fromCol) == 2) {
            return m.getNewCol() > fromCol ? "O-O" : "O-O-O";
        }

        // Piece identifier
        // Standard notation uses the first letter of the piece name, except:
        //   Knight -> N  (K is already taken by King)
        //   Pawn   -> "" on a push, or the from-file on a capture (exd5)
        boolean isCapture = m.getCapture() != null;

        String pieceChar;
        if (p instanceof Pawn) {
            pieceChar = isCapture ? file(fromCol) : "";
        } else if (p instanceof Knight) {
            pieceChar = "N";
        } else {
            pieceChar = p.getType().getDisplayName().substring(0, 1); // K, Q, R, B
        }

        // Capture marker
        String captureMarker = isCapture ? "x" : "";

        // Destination square
        String to = file(m.getNewCol()) + rank(m.getNewRow());

        // Promotion suffix
        String promo = m.getPromotionChoice() != null
                ? "=" + m.getPromotionChoice()
                : "";

        return pieceChar + captureMarker + to + promo;
    }

    //COORDINATE HELPER

    /**
     * Column 0–7 -> file letter a–h
     */
    private static String file(int col) {
        return String.valueOf((char) ('a' + col));
    }

    /**
     * Row 0–7 (top = 0) -> chess rank 8–1
     */
    private static int rank(int row) {
        return 8 - row;
    }
}

