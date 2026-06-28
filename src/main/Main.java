package main;

import gui.Board;
import gui.MoveLogPanel;

import javax.swing.*;
import java.awt.*;

public class Main {

    public static void main(String[] args) {
        JFrame frame = new JFrame("Chess");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(new Color(28, 28, 30));
        frame.setLocationRelativeTo(null);

        Board board = new Board();
        MoveLogPanel logPanel = new MoveLogPanel(board.getPreferredSize().height);

        // Wire the panel to the game controller
        board.getGameController().setMoveLogPanel(logPanel);

        // Container for board + panel side by side
        JPanel gameContainer = new JPanel(new BorderLayout());
        gameContainer.setBackground(new Color(28, 28, 30));
        gameContainer.add(board, BorderLayout.CENTER);
        gameContainer.add(logPanel, BorderLayout.EAST);

        // Wrapper to center the game container in the window
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(new Color(28, 28, 30));
        wrapper.add(gameContainer, new GridBagConstraints());

        frame.add(wrapper);
        frame.setSize(1400, 1000);
        frame.setMinimumSize(new Dimension(1200, 900));
        frame.setVisible(true);
    }
}