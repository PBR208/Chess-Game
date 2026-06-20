package pieces;

import main.Board;

import java.awt.image.BufferedImage;

public class Bishop extends Piece {
    public Bishop(Board b, int col, int row, boolean isWhite) {
        super(b);
        this.col = col;
        this.row = row;
        this.xPos = col * b.getTileSize();
        this.yPos = row * b.getTileSize();

        this.isWhite = isWhite;
        this.name = "Bishop";

        this.front = img.getSubimage(2 * imgScale, isWhite ? 0 : imgScale, imgScale, imgScale).getScaledInstance(b.getTileSize(), b.getTileSize(), BufferedImage.SCALE_SMOOTH);
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
