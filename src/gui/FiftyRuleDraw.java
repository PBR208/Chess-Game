package gui;

import javax.swing.*;
import java.awt.*;

public class FiftyRuleDraw extends JDialog {

    public enum DrawResult {
        ACCEPTED,
        DECLINED,
        NONE
    }

    private DrawResult result = DrawResult.NONE;

    public FiftyRuleDraw(JFrame parent, int tileSize, boolean isForced) {
        super(parent, true);

        int gap = tileSize / 8;
        int rows = isForced ? 2 : 3;
        setLayout(new GridLayout(rows, 1, gap, gap));
        setUndecorated(true);

        String msg;
        JButton button1;
        JButton button2 = null;

        Font labelFont = new Font("Arial", Font.BOLD, tileSize / 6);
        Font buttonFont = new Font("Arial", Font.PLAIN, tileSize / 6);

        if (isForced) {
            msg = "<html><center>The game has ended in a draw under the 75-move rule.<br>"
                    + "No captures or pawn moves occurred in the last 75 moves.</center></html>";
            button1 = new JButton("Restart");
            button1.addActionListener(e -> dispose());
        } else {
            msg = "<html><center>A draw may be claimed — 50 moves have been played"
                    + " without a capture or pawn move.</center></html>";
            button1 = new JButton("Claim Draw");
            button2 = new JButton("Decline");

            button1.addActionListener(e -> {
                result = DrawResult.ACCEPTED;
                dispose();
            });
            button2.addActionListener(e -> {
                result = DrawResult.DECLINED;
                dispose();
            });
        }

        JLabel txt = new JLabel(msg, SwingConstants.CENTER);
        txt.setFont(labelFont);
        button1.setFont(buttonFont);

        add(txt);
        add(button1);
        if (button2 != null) {
            button2.setFont(buttonFont);
            add(button2);
        }

        setSize(tileSize * 4, (int) (tileSize * 2.5));
        setLocationRelativeTo(parent);
    }

    public DrawResult getResult() {
        return result;
    }
}