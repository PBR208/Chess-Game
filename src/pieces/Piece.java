package pieces;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import gui.Board;

public class Piece {

    protected int col, row;
    protected int xPos, yPos;

    protected boolean isWhite;
    protected String name;
    protected int value;

    private boolean isFirstMove = true;

    BufferedImage img = null;

    {
        try {
            img = ImageIO.read(Piece.class.getResourceAsStream("/pieces.png"));
            if (img == null) throw new IOException("Resource not found");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to load image from classpath.");
        }
    }

    protected final int imgScale = img.getWidth() / 6;

    public Image front;
    public Board b;

    public Piece(Board b) {
        this.b = b;
    }

    public boolean isValidMovement(int col, int row) {
        return true;
    }

    public boolean isValidCollide(int col, int row) {
        return false;
    }

    public void paint(Graphics2D g2d) {

        g2d.drawImage(front, xPos, yPos, null);
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

    public String getName() {
        return name;
    }

    public boolean isFirstMove() {
        return isFirstMove;
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
}
