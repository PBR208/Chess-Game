package engine.pieces;

import ui.board.Board;

public class Queen extends Piece {
    public Queen(Board b, int col, int row, boolean isWhite) {
        super(b, col, row, isWhite, PieceType.QUEEN, 1);
    }

    public boolean isValidMovement(int col, int row) {

        int colDiff = Math.abs(col - this.col);
        int rowDiff = Math.abs(row - this.row);

        // horizontal OR vertical OR diagonal
        return this.col == col
                || this.row == row
                || colDiff == rowDiff;
    }

    public boolean isValidCollide(int col, int row) {

        int colStep = Integer.compare(col, this.col);
        int rowStep = Integer.compare(row, this.row);

        int c = this.col + colStep;
        int r = this.row + rowStep;

        while (c != col || r != row) {

            if (b.getPiece(c, r) != null) {
                return true;
            }

            c += colStep;
            r += rowStep;
        }

        return false;
    }
}
