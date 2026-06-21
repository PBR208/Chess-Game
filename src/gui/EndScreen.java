package gui;

import javax.swing.*;
import java.awt.*;

public class EndScreen extends JDialog {

    public EndScreen(JFrame parent, String msg) {
        super(parent, true);

        setLayout(new GridLayout(2, 1, 10, 10));
        setUndecorated(true);

        JLabel txt = new JLabel(msg, SwingConstants.CENTER);
        txt.setFont(new Font("Ariel", Font.BOLD, 30));

        JButton restartButton = new JButton("Restart");

        restartButton.addActionListener(
                e -> dispose()
        );

        add(txt);
        add(restartButton);

        setSize(300, 200);
        setLocationRelativeTo(parent);
    }
}