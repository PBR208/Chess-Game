package pieces;

import main.Board;

import java.awt.image.BufferedImage;

public class Pawn extends Piece{
    public Pawn(Board b, int col, int row, boolean isWhite) {
        super(b);
        this.col = col;
        this.row = row;
        this.xPos = col * b.tileSize;
        this.yPos = row * b.tileSize;

        this.isWhite = isWhite;
        this.name = "Pawn";

        this.front = img.getSubimage(5 * imgScale, isWhite ? 0 : imgScale, imgScale, imgScale).getScaledInstance(b.tileSize, b.tileSize, BufferedImage.SCALE_SMOOTH);
    }
}
