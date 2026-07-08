package engine.pieces;

import ui.board.Board;

public class King extends Piece {
    public King(Board b, int col, int row, boolean isWhite) {
        super(b, col, row, isWhite, PieceType.KING, 0);
    }

    public boolean isValidMovement(int col, int row) {
        int colDiff = Math.abs(col - this.col);
        int rowDiff = Math.abs(row - this.row);

        return colDiff <= 1
                && rowDiff <= 1
                && (colDiff != 0 || rowDiff != 0)
                || canCastle(col, row);
    }

    public boolean isValidCollide(int col, int row) {
        return false;
    }

    private boolean canCastle(int col, int row) {

        // must stay on same row
        if (row != this.row) return false;
        if (!this.isFirstMove()) return false;

        int rookCol;
        int step;

        // kingside
        if (col == 6) {

            rookCol = 7;
            step = 1;

        }

        // queenside
        else if (col == 2) {

            rookCol = 0;
            step = -1;

        }

        // anything else is NOT castling
        else {
            return false;
        }

        Piece rook = b.getPiece(rookCol, row);

        if (!(rook instanceof Rook)) return false;

        if (!rook.isFirstMove()) return false;

        // check empty squares between king and rook
        for (int c = this.col + step; c != rookCol; c += step) {

            if (b.getPiece(c, row) != null) {
                return false;
            }
        }
        return true;
    }
}
