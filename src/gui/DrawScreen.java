package gui;

import javax.swing.*;
import java.awt.*;

public class DrawScreen extends JDialog {

    public DrawScreen(JFrame parent) {
        super(parent, true);

        setLayout(new GridLayout(2, 1, 10, 10));
        setUndecorated(true);

        JLabel txt = new JLabel("The game concluded in a draw", SwingConstants.CENTER);
        txt.setFont(new Font("Ariel", Font.BOLD, 20));

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
