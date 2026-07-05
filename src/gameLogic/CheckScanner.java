package gameLogic;

import gui.Board;
import pieces.King;
import pieces.Piece;

import java.util.List;

public class CheckScanner {

    private final Board b;

    public CheckScanner(Board b) {
        this.b = b;
    }

    public boolean isKingLeftInCheck(Move move) {

        Piece piece = move.getPiece();

        int oldCol = piece.getCol();
        int oldRow = piece.getRow();

        Piece captured = move.getCapture();

        piece.setCol(move.getNewCol());
        piece.setRow(move.getNewRow());

        if (captured != null) {
            b.removePiece(captured);
        }

        boolean kingInCheck = isKingInCheckRN(piece.isWhite());

        // undo move
        piece.setCol(oldCol);
        piece.setRow(oldRow);

        if (captured != null) {
            b.addPiece(captured);
        }

        return kingInCheck;
    }

    public boolean isKingInCheckRN(boolean isWhite) {

        Piece king = findKing(isWhite);

        if (king == null) return false;

        int kingCol = king.getCol();
        int kingRow = king.getRow();

        for (Piece p : b.getPieces()) {

            if (p.isWhite() == isWhite) continue;

            if (canPieceAttackSquare(p, kingCol, kingRow)) {
                return true;
            }
        }

        return false;
    }

    private boolean canPieceAttackSquare(Piece p, int col, int row) {

        return p.isValidMovement(col, row) && !p.isValidCollide(col, row);

    }

    private Piece findKing(boolean isWhite) {
        List<Piece> pieces = b.getPieces();
        for (Piece p : pieces) {
            if (isWhite == p.isWhite() && p instanceof King) {
                return p;
            }
        }

        return null;
    }


}