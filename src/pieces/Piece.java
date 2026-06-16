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
}
