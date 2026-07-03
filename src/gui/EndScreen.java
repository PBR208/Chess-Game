package gui;

import javax.swing.*;
import java.awt.*;

public class EndScreen extends JDialog {

    public EndScreen(JFrame parent, String msg, int tileSize, Runnable onReturn) {
        super(parent, true);

        int gap = tileSize / 8;
        setLayout(new GridLayout(2, 1, gap, gap));
        setUndecorated(true);

        JLabel txt = new JLabel(msg, SwingConstants.CENTER);
        txt.setFont(new Font("Arial", Font.BOLD, tileSize / 3)); // fixed: was "Ariel"

        JButton returnButton = new JButton("Return to Menu");
        returnButton.setFont(new Font("Arial", Font.PLAIN, tileSize / 6));
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