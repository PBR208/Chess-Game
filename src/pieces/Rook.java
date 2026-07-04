package pieces;

import gui.Board;

import java.awt.image.BufferedImage;

public class Rook extends Piece {
    public Rook(Board b, int col, int row, boolean isWhite) {
        super(b, col, row, isWhite, PieceType.ROOK, 4);
    }

    public boolean isValidMovement(int col, int row) {
        return col == this.col || row == this.row;
    }

    public boolean isValidCollide(int col, int row) {

        int colStep = Integer.compare(col, this.col);
        int rowStep = Integer.compare(row, this.row);

        int currentCol = this.col + colStep;
        int currentRow = this.row + rowStep;

        while (currentCol != col || currentRow != row) {
            if (b.getPiece(currentCol, currentRow) != null) {
                return true;
            }

            currentCol += colStep;
            currentRow += rowStep;
        }

        return false;
    }
}
