package pieces;

import main.Board;

import java.awt.image.BufferedImage;

public class Knight extends Piece{
    public Knight(Board b, int col, int row, boolean isWhite) {
        super(b);
        this.col = col;
        this.row = row;
        this.xPos = col * b.tileSize;
        this.yPos = row * b.tileSize;

        this.isWhite = isWhite;
        this.name = "Knight";

        this.front = img.getSubimage(3 * imgScale, isWhite ? 0 : imgScale, imgScale, imgScale).getScaledInstance(b.tileSize, b.tileSize, BufferedImage.SCALE_SMOOTH);
    }
}
