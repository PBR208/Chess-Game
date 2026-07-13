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
        int padding = tileSize / 8;
        setLayout(new BorderLayout(gap, gap));
        setUndecorated(true);
        getContentPane().setBackground(Theme.PANEL_BG);

        String msg;
        JButton button1;
        JButton button2 = null;

        Font labelFont = new Font(Font.DIALOG, Font.BOLD, Math.max(tileSize / 7, 12));
        Font buttonFont = new Font(Font.DIALOG, Font.PLAIN, Math.max(tileSize / 8, 11));

        if (isForced) {
            msg = "<html><center>The game has ended in a draw under<br>" +
                    "the 75-move rule. No captures or pawn<br>" +
                    "moves occurred in the last 75 moves.</center></html>";
            button1 = UiComponents.button("Restart", buttonFont, Theme.BUTTON_SECONDARY);
            button1.addActionListener(e -> dispose());
        } else {
            msg = "<html><center>A draw may be claimed. 50 moves have<br>" +
                    "been played without a capture or pawn move.</center></html>";
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

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBackground(Theme.PANEL_BG);
        textPanel.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));

        JLabel txt = new JLabel(msg, SwingConstants.CENTER);
        txt.setFont(labelFont);
        txt.setForeground(Theme.FG);
        textPanel.add(txt, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(1, button2 != null ? 2 : 1, gap, gap));
        buttonPanel.setBackground(Theme.PANEL_BG);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, padding, padding, padding));
        buttonPanel.add(button1);
        if (button2 != null) {
            buttonPanel.add(button2);
        }

        add(textPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(tileSize * 4, (int) (tileSize * 2.5));
        setLocationRelativeTo(parent);
    }

    public DrawResult getResult() {
        return result;
    }
}