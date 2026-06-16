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

    public void paint(Graphics2D g2d){

        g2d.drawImage(front, xPos, yPos, null);
    }

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
}
