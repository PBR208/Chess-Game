package pieces;

import main.Board;

import java.awt.image.BufferedImage;

public class Queen extends Piece{
    public Queen(Board b, int col, int row, boolean isWhite) {
        super(b);
        this.col = col;
        this.row = row;
        this.xPos = col * b.getTileSize();
        this.yPos = row * b.getTileSize();

        this.isWhite = isWhite;
        this.name = "Queen";

        this.front = img.getSubimage(1 * imgScale, isWhite ? 0 : imgScale, imgScale, imgScale).getScaledInstance(b.getTileSize(), b.getTileSize(), BufferedImage.SCALE_SMOOTH);
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
