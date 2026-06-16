package pieces;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import main.Board;

public class Piece {

    protected int col, row;
    protected int xPos, yPos;

    protected boolean isWhite;
    protected String name;
    protected int value;

    private boolean isFirstmove = true;

    BufferedImage img = null;
    {
        try {

            img = ImageIO.read(ClassLoader.getSystemResourceAsStream("pieces.png"));
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    protected int imgScale = img.getWidth()/6;

    public Image front;
    public Board b;

    public Piece(Board b){
        this.b = b;
    }

    public boolean isValidMovement(int col, int row){return true;}
    public boolean isValidCollide(int col, int row){return false;}

    public void paint(Graphics2D g2d){

        g2d.drawImage(front, xPos, yPos, null);
    }

    // GETTER

    public int getCol() {
        return col;
    }

    public int getRow() {
        return row;
    }

    public int getxPos() {
        return xPos;
    }

    public int getyPos() {
        return yPos;
    }

    public boolean isWhite() {
        return isWhite;
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }

    public boolean isFirstmove() {
        return isFirstmove;
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

    public void setWhite(boolean white) {
        isWhite = white;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public void setFirstmove(boolean firstmove) {
        isFirstmove = firstmove;
    }
}
