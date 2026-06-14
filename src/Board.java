import javax.swing.*;
import java.awt.*;

public class Board extends JPanel{

    public int tileSize = 85;

    private int rows = 8;
    private int cols = 8;

    public Board(){
        JFrame board = new JFrame();
        this.setPreferredSize(new Dimension(cols * tileSize, rows * tileSize));

    }

    public void paintComponent(Graphics g){
        Graphics2D g2d = (Graphics2D) g;

        for (int r = 0; r < rows; r++){
            for (int c = 0; c < cols; c++){
                g2d.setColor((c+r) % 2 == 0 ? Color.white : Color.black);
                g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
            }
        }
    }
}
