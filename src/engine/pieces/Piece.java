package engine.pieces;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import ui.board.Board;

public class Piece {

    protected int col, row;
    protected int xPos, yPos;

    protected boolean isWhite;
    protected PieceType type;
    protected int value;

    private boolean isFirstMove = true;

    protected static final BufferedImage img;
    protected static final int imgScale;

    static {
        BufferedImage tmp = null;
        try {
            tmp = ImageIO.read(Piece.class.getResourceAsStream("/resources/pieces.png"));
            if (tmp == null) throw new IOException("Resource not found");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to load image from classpath.");
        }
        img = tmp;
        imgScale = (img != null) ? img.getWidth() / 6 : 0;
    }

    protected Image front;
    protected Board b;

    protected Piece(Board b, int col, int row, boolean isWhite, PieceType type, int spriteCol) {
        this.b = b;
        this.col = col;
        this.row = row;
        this.xPos = col * b.getTileSize();
        this.yPos = row * b.getTileSize();
        this.isWhite = isWhite;
        this.type = type;
        this.front = img.getSubimage(spriteCol * imgScale, isWhite ? 0 : imgScale, imgScale, imgScale)
                .getScaledInstance(b.getTileSize(), b.getTileSize(), BufferedImage.SCALE_SMOOTH);
    }

    // GETTER

    public int getCol() {
        return col;
    }

    public int getRow() {
        return row;
    }

    public boolean isWhite() {
        return isWhite;
    }

    public PieceType getType() {
        return type;
    }

    public boolean isFirstMove() {
        return isFirstMove;
    }

    public int getxPos() {
        return xPos;
    }

    public int getyPos() {
        return yPos;
    }

    public static BufferedImage getSpritesheet() {
        return img;
    }

    public static int getSpritesheetScale() {
        return imgScale;
    }

    // SETTER

    public void setCol(int col) {
        this.col = col;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public void setxPos(int xPos) {
        this.xPos = xPos;
    }

    public void setyPos(int yPos) {
        this.yPos = yPos;
    }

    public void setFirstMove(boolean firstMove) {
        isFirstMove = firstMove;
    }

    // HELPER

    public void moveTo(int col, int row) {
        this.col = col;
        this.row = row;
        this.xPos = col * b.getTileSize();
        this.yPos = row * b.getTileSize();
        this.isFirstMove = false;
    }

    public boolean isValidMovement(int col, int row) {
        return true;
    }

    public boolean isValidCollide(int col, int row) {
        return false;
    }

    public void paint(Graphics2D g2d, int x, int y) {
        g2d.drawImage(front, x, y, null);
    }
}
