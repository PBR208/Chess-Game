package pieces;

import main.Board;

import java.awt.image.BufferedImage;

public class Rook extends Piece {
    public Rook(Board b, int col, int row, boolean isWhite) {
        super(b);
        this.col = col;
        this.row = row;
        this.xPos = col * b.getTileSize();
        this.yPos = row * b.getTileSize();

        this.isWhite = isWhite;
        this.name = "Rook";

        this.front = img.getSubimage(4 * imgScale, isWhite ? 0 : imgScale, imgScale, imgScale).getScaledInstance(b.getTileSize(), b.getTileSize(), BufferedImage.SCALE_SMOOTH);
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
