package ui.board;

import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import java.awt.*;

public class EndScreen extends JDialog {

    public EndScreen(JFrame parent, String msg, int tileSize, Runnable onReturn) {
        super(parent, true);

        int gap = tileSize / 8;
        int padding = tileSize / 4;
        setLayout(new BorderLayout(gap, gap));
        setUndecorated(true);
        getContentPane().setBackground(Theme.PANEL_BG);

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBackground(Theme.PANEL_BG);
        textPanel.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));

        JLabel txt = new JLabel(msg, SwingConstants.CENTER);
        txt.setFont(new Font(Font.DIALOG, Font.BOLD, Math.max(tileSize / 4, 14)));
        txt.setForeground(Theme.FG);

        textPanel.add(txt, BorderLayout.CENTER);

        JButton returnButton = UiComponents.button("Return to Menu", new Font(Font.DIALOG, Font.PLAIN, Math.max(tileSize / 8, 12)), Theme.ACCENT);
        returnButton.addActionListener(e -> {
            dispose();
            onReturn.run();
        });

        add(textPanel, BorderLayout.CENTER);
        add(returnButton, BorderLayout.SOUTH);

        setSize(tileSize * 4, (int) (tileSize * 2.5));
        setLocationRelativeTo(parent);
    }
}