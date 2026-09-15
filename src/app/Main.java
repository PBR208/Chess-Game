package app;

/*
 * Purpose: Main is the entry point of the chess application. It owns the single application window
 * and swaps the menu, the game screen and the Past Games library in and out of it. Because the whole
 * game is a Swing application, I check at startup that a graphical display is available, since a
 * headless Java runtime used to crash quietly on the event thread and still exit with code 0. Startup
 * problems now end the process with a clear message and a non-zero exit code that scripts can detect.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.model.GameConfig;
import engine.persistence.PgnManager;
import ui.board.Board;
import ui.board.EndScreen;
import ui.board.MoveLogPanel;
import ui.menu.MainMenu;
import ui.menu.PastGamesPanel;

import javax.swing.*;
import java.awt.*;

public class Main {

    // exit code when the application is started without a graphical display
    public static final int EXIT_NO_DISPLAY = 2;
    // exit code when building the window fails for any other reason
    public static final int EXIT_STARTUP_FAILED = 1;

    private static JFrame frame;

    /**
     * Starts the application window with the main menu.
     * <p>
     * This is what runs for java -jar and for the IDE launch. Without a graphical display no window
     * can open, so I print what went wrong and exit with code 2 before touching any window class.
     * Otherwise I build the window on the event thread and show the menu. If building the window
     * throws, I print the problem and exit with code 1, instead of leaving a process behind that
     * failed silently.
     * <p>
     * Time complexity: O(1) apart from building the menu. Space complexity: O(1).
     *
     * @param pArgs command line arguments, currently unused; may be empty but never null
     */
    public static void main(String[] pArgs) {
        // no display means no window, so explain it instead of crashing on the event thread
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Chess needs a graphical display, but this Java runtime is headless.");
            System.err.println("Start it from a desktop session and without -Djava.awt.headless=true.");
            System.exit(EXIT_NO_DISPLAY);
        }

        SwingUtilities.invokeLater(() -> {
            try {
                frame = new JFrame("Chess");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.getContentPane().setBackground(new Color(28, 28, 30));
                frame.setSize(1400, 1000);
                frame.setMinimumSize(new Dimension(1200, 900));
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);

                showMenu();
            } catch (RuntimeException | Error e) {
                // a window that can't be built must not leave a silent process behind
                System.err.println("Chess could not start: " + e);
                e.printStackTrace();
                System.exit(EXIT_STARTUP_FAILED);
            }
        });
    }

    public static void showMenu() {
        SwingUtilities.invokeLater(() -> {
            frame.setContentPane(new MainMenu());
            frame.revalidate();
            frame.repaint();
        });
    }

    /**
     * Opens the game screen for a new game.
     * <p>
     * After the New Game screen the board, the move log and the end of game handling have to be set
     * up together. I fall back to an unlimited game without a configuration, build the board and the
     * move log panel, and register what happens when the game ends: the game is saved, a warning
     * appears when that failed, and the end screen leads back to the menu.
     * <p>
     * Time complexity: O(p) for the starting pieces. Space complexity: O(p) for the new board.
     *
     * @param pConfig names, times and increment of the new game; null starts an unlimited game
     */
    public static void startGame(GameConfig pConfig) {
        // no configuration means a casual game without clocks
        final GameConfig cfg = pConfig == null ? GameConfig.unlimited() : pConfig;

        SwingUtilities.invokeLater(() -> {
            Board board = new Board(cfg);
            MoveLogPanel logPanel = new MoveLogPanel(board.getPreferredSize().height);
            board.getGameController().setMoveLogPanel(logPanel);

            board.getGameController().setGameEndListener((record, displayMessage) -> {
                // a game that couldn't be written must not disappear without a word
                boolean saved = PgnManager.save(record);

                SwingUtilities.invokeLater(() -> {
                    if (!saved) {
                        JOptionPane.showMessageDialog(frame,
                                "This game could not be saved to " + PgnManager.getGamesDirectory() + ".",
                                "Game not saved", JOptionPane.WARNING_MESSAGE);
                    }
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

    public static void showPastGames() {
        SwingUtilities.invokeLater(() -> {
            frame.setContentPane(new PastGamesPanel());
            frame.revalidate();
            frame.repaint();
        });
    }
}
