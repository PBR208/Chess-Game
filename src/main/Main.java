package main;

import gui.Board;

import javax.swing.*;
import java.awt.*;

public class Main {

    static void main(String[] args) {
        JFrame frame = new JFrame("Chess");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridBagLayout());
        frame.getContentPane().setBackground(Color.BLACK);
        frame.setMinimumSize(new Dimension(1000, 1000));
        frame.setLocationRelativeTo(null);

        Board board = new Board();

        frame.add(board);
        frame.setVisible(true);
    }
}
