package pieces;

import main.Board;

import java.awt.image.BufferedImage;

public class Bishop extends Piece{
    public Bishop(Board b, int col, int row, boolean isWhite) {
        super(b);
        this.col = col;
        this.row = row;
        this.xPos = col * b.tileSize;
        this.yPos = row * b.tileSize;

        this.isWhite = isWhite;
        this.name = "Bishop";

        this.front = img.getSubimage(2 * imgScale, isWhite ? 0 : imgScale, imgScale, imgScale).getScaledInstance(b.tileSize, b.tileSize, BufferedImage.SCALE_SMOOTH);
    }
}
