package main;

import javax.swing.*;
import java.awt.*;

public class Main {

    private static JFrame frame;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Chess");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().setBackground(new Color(28, 28, 30));
            frame.setSize(1400, 1000);
            frame.setMinimumSize(new Dimension(1200, 900));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            //showMenu();
        });
    }
}