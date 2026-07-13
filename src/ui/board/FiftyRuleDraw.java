package ui.board;

import ui.theme.Theme;
import ui.theme.UiComponents;

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
        getContentPane().setBackground(Theme.PANEL_BG);

        String msg;
        JButton button1;
        JButton button2 = null;

        Font labelFont = new Font("Arial", Font.BOLD, tileSize / 6);
        Font buttonFont = new Font("Arial", Font.PLAIN, tileSize / 6);

        if (isForced) {
            msg = "<html><center>The game has ended in a draw under the 75-move rule.<br>"
                    + "No captures or pawn moves occurred in the last 75 moves.</center></html>";
            button1 = UiComponents.button("Restart", buttonFont, Theme.BUTTON_SECONDARY);
            button1.addActionListener(e -> dispose());
        } else {
            msg = "<html><center>A draw may be claimed — 50 moves have been played"
                    + " without a capture or pawn move.</center></html>";
            button1 = UiComponents.button("Claim Draw", buttonFont, Theme.ACCENT);
            button2 = UiComponents.button("Decline", buttonFont, Theme.BUTTON_SECONDARY);

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
        txt.setForeground(Theme.FG);

        add(txt);
        add(button1);
        if (button2 != null) {
            add(button2);
        }

        setSize(tileSize * 4, (int) (tileSize * 2.5));
        setLocationRelativeTo(parent);
    }

    public DrawResult getResult() {
        return result;
    }
}