package engine.imports;

import engine.pieces.Piece;

import java.util.List;

public class CheckScanner {

    private final BoardState state;

    public CheckScanner(BoardState state) {
        this.state = state;
    }

    public boolean isKingLeftInCheck(Move move) {

        Piece piece = move.getPiece();

        int oldCol = piece.getCol();
        int oldRow = piece.getRow();
        int newCol = move.getNewCol();
        int newRow = move.getNewRow();

        Piece captured = move.getCapture();

        // Simulate the move
        piece.setCol(newCol);
        piece.setRow(newRow);

        if (captured != null) {
            state.removePiece(captured);
        }

        // Keep the grid in sync with the simulated position, since sliding
        // pieces (Bishop/Rook/Queen) resolve collisions via the grid, not
        // via the piece's own col/row fields.
        state.moveOnGrid(piece, oldCol, oldRow);

        boolean kingInCheck = isKingInCheckRN(piece.isWhite());

        // Revert the simulation
        piece.setCol(oldCol);
        piece.setRow(oldRow);
        state.moveOnGrid(piece, newCol, newRow);

        if (captured != null) {
            state.addPiece(captured);
        }

        return kingInCheck;
    }

    public boolean isKingInCheckRN(boolean isWhite) {

        Piece king = findKing(isWhite);

        if (king == null) return false;

        int kingCol = king.getCol();
        int kingRow = king.getRow();

        for (Piece p : state.getPieces()) {

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
        List<Piece> pieces = state.getPieces();
        for (Piece p : pieces) {
            if (isWhite == p.isWhite() && p.getType().getDisplayName().equals("King")) {
                return p;
            }
        }

        return null;
    }
}