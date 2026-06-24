package gui;

import javax.swing.*;
import java.awt.*;

public class EndScreen extends JDialog {

    public EndScreen(JFrame parent, String msg, int tileSize) {
        super(parent, true);

        int gap = tileSize / 8;
        setLayout(new GridLayout(2, 1, gap, gap));
        setUndecorated(true);

        JLabel txt = new JLabel(msg, SwingConstants.CENTER);
        txt.setFont(new Font("Ariel", Font.BOLD, tileSize / 3));

        JButton restartButton = new JButton("Restart");
        restartButton.setFont(new Font("Arial", Font.PLAIN, tileSize / 6));

        restartButton.addActionListener(
                e -> dispose()
        );

        add(txt);
        add(restartButton);

        setSize(tileSize * 4, (int) (tileSize * 2.5));
        setLocationRelativeTo(parent);
    }
}