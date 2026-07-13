package ui.board;

import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import java.awt.*;

public class EndScreen extends JDialog {

    public EndScreen(JFrame parent, String msg, int tileSize, Runnable onReturn) {
        super(parent, true);

        int gap = tileSize / 8;
        setLayout(new GridLayout(2, 1, gap, gap));
        setUndecorated(true);
        getContentPane().setBackground(Theme.PANEL_BG);

        JLabel txt = new JLabel(msg, SwingConstants.CENTER);
        txt.setFont(new Font("Arial", Font.BOLD, tileSize / 3));
        txt.setForeground(Theme.FG);

        JButton returnButton = UiComponents.button("Return to Menu", new Font("Arial", Font.PLAIN, tileSize / 6), Theme.ACCENT);
        returnButton.addActionListener(e -> {
            dispose();
            onReturn.run();
        });

        add(txt);
        add(returnButton);

        setSize(tileSize * 4, (int) (tileSize * 2.5));
        setLocationRelativeTo(parent);
    }
}