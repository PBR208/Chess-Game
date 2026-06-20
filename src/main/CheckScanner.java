package main;

import pieces.Piece;

import java.util.ArrayList;
import java.util.List;

public class CheckScanner {

    Board b;

    public CheckScanner(Board b){
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
            b.getPieces().add(captured);
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

    public Piece findKing(boolean isWhite){
        List <Piece> pieces = b.getPieces();
        for (Piece p : pieces){
            if (isWhite == p.isWhite() && p.getName().equals("King")){
                return p;
            }
        }
        return null;
    }

    public boolean isCheckmate(boolean teamColorWhite) {

        if (!isKingInCheckRN(teamColorWhite)) {
            return false;
        }

        return !hasLegalMoves(teamColorWhite);
    }


    public boolean isStalemate(boolean teamColorWhite) {

        if (isKingInCheckRN(teamColorWhite)) {
            return false;
        }

        return !hasLegalMoves(teamColorWhite);
    }

    private boolean hasLegalMoves(boolean teamColorWhite) {

        for (Piece p : new ArrayList<Piece>(b.getPieces())) {

            // only check this players pieces
            if (p.isWhite() != teamColorWhite) {
                continue;
            }

            // try every square on board
            for (int row = 0; row < 8; row++) {

                for (int col = 0; col < 8; col++) {


                    Move move = new Move(b, p, col, row);


                    if (b.isValidMove(move)) {
                        return true;
                    }

                }
            }
        }
        return false;
    }
}