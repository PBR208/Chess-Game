package main;

import gameLogic.GameConfig;
import gameLogic.PgnManager;
import gui.Board;
import gui.EndScreen;
import gui.MoveLogPanel;

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

            showMenu();
        });
    }

    public static void showMenu() {
        SwingUtilities.invokeLater(() -> {
            frame.setContentPane(new MainMenu());
            frame.revalidate();
            frame.repaint();
        });
    }

    public static void startGame(GameConfig config) {
        if (config == null) config = GameConfig.unlimited();
        final GameConfig cfg = config;

        SwingUtilities.invokeLater(() -> {
            Board board = new Board(cfg);
            MoveLogPanel logPanel = new MoveLogPanel(board.getPreferredSize().height);
            board.getGameController().setMoveLogPanel(logPanel);

            board.getGameController().setGameEndListener((record, displayMessage) -> {
                PgnManager.save(record);

                SwingUtilities.invokeLater(() -> {
                    EndScreen screen = new EndScreen(frame, displayMessage,
                            board.getTileSize(), Main::showMenu);
                    screen.setVisible(true);
                });
            });

            JPanel gameContainer = new JPanel(new BorderLayout());
            gameContainer.setBackground(new Color(28, 28, 30));
            gameContainer.add(board, BorderLayout.CENTER);
            gameContainer.add(logPanel, BorderLayout.EAST);

            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setBackground(new Color(28, 28, 30));
            wrapper.add(gameContainer, new GridBagConstraints());

            frame.setContentPane(wrapper);
            frame.revalidate();
            frame.repaint();
        });
    }
}