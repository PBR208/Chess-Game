package engine.pieces;

import ui.board.Board;

public class Knight extends Piece {
    public Knight(Board b, int col, int row, boolean isWhite) {
        super(b, col, row, isWhite, PieceType.KNIGHT, 3);
    }

    public boolean isValidMovement(int col, int row) {
        return Math.abs(col - this.col) * Math.abs(row - this.row) == 2;
    }
}
