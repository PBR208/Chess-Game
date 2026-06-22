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

    JFrame parent;

    public FiftyRuleDraw(JFrame parent, boolean isForced) {
        super(parent, true);

        String msg;
        JButton button1;
        JButton button2 = null;

        setUndecorated(true);
        setLayout(new GridLayout(3, 1, 10, 10));

        if (isForced) {
            msg = "The game has ended in a draw under the 75-move rule. No captures or pawn moves occurred in the last 75 moves";
            button1 = new JButton("Restart");

            button1.addActionListener(e -> {
                dispose();
            });

        } else {
            msg = "A draw may be claimed because 50 moves have been played without a capture or pawn move.";
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
        txt.setFont(new Font("Ariel", Font.BOLD, 15));


        add(txt);
        add(button1);
        add(button2);

        setSize(300, 200);
        setLocationRelativeTo(parent);
    }

    public DrawResult getResult() {
        return result;
    }
}
