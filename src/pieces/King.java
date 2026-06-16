package pieces;

import main.Board;

import java.awt.image.BufferedImage;

public class King extends Piece{
    public King(Board b, int col, int row, boolean isWhite) {
        super(b);
        this.col = col;
        this.row = row;
        this.xPos = col * b.getTileSize();
        this.yPos = row * b.getTileSize();

        this.isWhite = isWhite;
        this.name = "King";

        this.front = img.getSubimage(0 * imgScale, isWhite ? 0 : imgScale, imgScale, imgScale).getScaledInstance(b.getTileSize(), b.getTileSize(), BufferedImage.SCALE_SMOOTH);
    }

    public boolean isValidMovement(int col, int row){
        int colDiff = Math.abs(col - this.col);
        int rowDiff = Math.abs(row - this.row);

        return colDiff <= 1
                && rowDiff <= 1
                && (colDiff != 0 || rowDiff != 0);
    }
    public boolean isValidCollide(int col, int row){return false;}
}
