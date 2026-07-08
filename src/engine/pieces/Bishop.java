package engine.pieces;

import ui.board.Board;

public class Bishop extends Piece {
    public Bishop(Board b, int col, int row, boolean isWhite) {
        super(b, col, row, isWhite, PieceType.BISHOP, 2);
    }

    public boolean isValidMovement(int col, int row) {
        return Math.abs(col - this.col) == Math.abs(row - this.row);
    }

    public boolean isValidCollide(int col, int row) {

        int colDiff = col - this.col;
        int rowDiff = row - this.row;

        // Not a diagonal move
        if (Math.abs(colDiff) != Math.abs(rowDiff)) {
            return false;
        }

        int colStep = Integer.compare(col, this.col);
        int rowStep = Integer.compare(row, this.row);

        // Check all squares between start and destination
        for (int i = 1; i < Math.abs(colDiff); i++) {
            if (b.getPiece(
                    this.col + i * colStep,
                    this.row + i * rowStep) != null) {
                return true; // Collision found
            }
        }

        return false; // No collision
    }
}
