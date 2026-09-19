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

import engine.core.GameResult;
import engine.core.GameSession;
import engine.core.Termination;
import engine.model.GameConfig;
import engine.model.GameRecord;
import engine.persistence.PgnManager;
import ui.board.Board;
import ui.board.EndScreen;
import ui.board.MoveLogPanel;
import ui.i18n.Messages;
import ui.menu.MainMenu;
import ui.menu.PastGamesPanel;
import ui.theme.Theme;
import ui.theme.UiComponents;

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
     * Otherwise I build the window on the event thread, sized to fit the usable part of the screen,
     * and show the menu. If building the window
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
                // never larger than the usable part of the screen, small laptop screens included
                Rectangle usableArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                frame.setSize(fitToScreen(new Dimension(1400, 1000), usableArea));
                frame.setMinimumSize(fitToScreen(new Dimension(1200, 900), usableArea));
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

    /**
     * Shrinks a window size so it fits into the usable screen area.
     * <p>
     * A fixed 1400 by 1000 window with a 1200 by 900 minimum is taller than many laptop screens, so
     * part of the board and the bottom clock ended up below the screen edge. I keep the preferred
     * width and height where they fit and cut each one down to the screen area where they don't.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPreferred size the window would like to have, never null
     * @param pScreen    usable screen area without task bars, never null
     * @return a size that is no larger than the screen area in either direction, never null
     * @throws NullPointerException if one of the arguments is null
     */
    public static Dimension fitToScreen(Dimension pPreferred, Rectangle pScreen) {
        return new Dimension(Math.min(pPreferred.width, pScreen.width), Math.min(pPreferred.height, pScreen.height));
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
     * up together. I fall back to an unlimited game without a configuration, build the board with
     * squares that fit the screen and the move log panel, and register what happens when the game
     * ends: the game is saved, a warning appears when that failed, and the end screen leads back to
     * the menu.
     * <p>
     * Time complexity: O(p) for the starting pieces. Space complexity: O(p) for the new board.
     *
     * @param pConfig names, times and increment of the new game; null starts an unlimited game
     */
    public static void startGame(GameConfig pConfig) {
        // no configuration means a casual game without clocks
        final GameConfig cfg = pConfig == null ? GameConfig.unlimited() : pConfig;

        SwingUtilities.invokeLater(() -> {
            // squares small enough for the whole game screen to fit on this screen
            Rectangle usableArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            Board board = new Board(cfg, Board.tileSizeFor(usableArea.width, usableArea.height));
            MoveLogPanel logPanel = new MoveLogPanel(board.getPreferredSize().height);
            GameSession session = board.getSession();
            session.setMoveLogView(logPanel);
            // clicking a move in the log takes the game back to it, or forward again
            logPanel.setPlySelectedListener(session::goToPly);

            session.setEndListener((pResult, pTermination) -> {
                // the session owns the moves and the result, the names and the clock come from the config,
                // and the reason it ended is what the PGN Termination tag gets written from. The board
                // always starts a game from the usual position, so there is no starting FEN to record.
                GameRecord record = new GameRecord(cfg, pResult.pgnToken(), pTermination,
                        session.getMoveLog(), session.getFenHistory(), null);
                // a game that couldn't be written must not disappear without a word
                boolean saved = PgnManager.save(record);
                // the engine reports a result and a reason, the sentence the players read is built here
                String displayMessage = endMessage(cfg, pResult, pTermination);

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
            gameContainer.add(actionBar(board), BorderLayout.SOUTH);

            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setBackground(new Color(28, 28, 30));
            wrapper.add(gameContainer, new GridBagConstraints());

            frame.setContentPane(wrapper);
            frame.revalidate();
            frame.repaint();
        });
    }

    /**
     * Builds the row of actions under the board.
     * <p>
     * Players need a way to take a move back and to play it again, and both only make sense while
     * there is something to take back or replay. I build the two buttons, hand the clicks to the
     * session, which asks the opponent in a timed game, and let the board tell me whenever the game
     * changed so the buttons can be greyed out exactly when they would do nothing.
     * <p>
     * Players also step away from a game, and stopping the clock should not mean ending it. The
     * pause button pauses and resumes the board and says which of the two it will do next, so a
     * player always reads the action rather than the state. Everything it needs is on the board
     * itself, which stops the clocks and refuses moves while it is paused.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the panel and its buttons.
     *
     * @param pBoard the board of the running game, never null
     * @return the action row, never null
     * @throws NullPointerException if pBoard is null
     */
    private static JPanel actionBar(Board pBoard) {
        GameSession session = pBoard.getSession();

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        bar.setBackground(Theme.PANEL_BG);

        Font buttonFont = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        JButton takeBack = UiComponents.button(Messages.get("game.takeBack"), buttonFont, Theme.BUTTON_SECONDARY);
        takeBack.setName("takeBack");
        JButton replay = UiComponents.button(Messages.get("game.replayMove"), buttonFont, Theme.BUTTON_SECONDARY);
        replay.setName("replayMove");
        JButton pause = UiComponents.button(Messages.get("game.pause"), buttonFont, Theme.BUTTON_SECONDARY);
        pause.setName("pause");

        // the session decides whether the move really comes back, since a timed game asks the opponent
        takeBack.addActionListener(e -> session.requestTakeback());
        replay.addActionListener(e -> session.redo());
        pause.addActionListener(e -> {
            pBoard.setPaused(!pBoard.isPaused());
            // the button names what pressing it will do next, not what the game is doing now
            pause.setText(Messages.get(pBoard.isPaused() ? "game.resume" : "game.pause"));
        });

        // a button that would do nothing says so by being grey
        Runnable refresh = () -> {
            takeBack.setEnabled(session.canUndo());
            replay.setEnabled(session.canRedo());
        };
        pBoard.setGameChangedListener(refresh);
        refresh.run();

        bar.add(takeBack);
        bar.add(replay);
        bar.add(pause);
        return bar;
    }

    /**
     * Turns a result and the reason for it into the sentence the end screen shows.
     * <p>
     * The rules engine reports that a game ended and why, but it knows nothing about the players or
     * the language they read, so the wording belongs here. I look up the winner's name for the three
     * reasons that have one and build a sentence for every reason a game can end with. Because the
     * reason is an enum, adding a new one makes this switch fail to compile instead of silently
     * showing an empty end screen.
     * <p>
     * Time complexity: O(1). Space complexity: O(n) for the returned sentence.
     *
     * @param pConfig      names of both players, never null
     * @param pResult      how the game ended, never null and never ONGOING
     * @param pTermination why the game ended, never null
     * @return the sentence shown on the end screen, never null
     * @throws NullPointerException if any argument is null
     */
    private static String endMessage(GameConfig pConfig, GameResult pResult, Termination pTermination) {
        // only mate, a resignation and a flag fall have a winner to name
        String winner = pResult == GameResult.WHITE_WINS ? pConfig.whiteName() : pConfig.blackName();
        // the three reasons with a winner put the name into the sentence, because where the name
        // belongs in a sentence is not the same in every language
        return switch (pTermination) {
            case CHECKMATE -> Messages.format("end.checkmate", winner);
            case RESIGNATION -> Messages.format("end.resignation", winner);
            case TIME_OUT -> Messages.format("end.timeOut", winner);
            case STALEMATE -> Messages.get("end.stalemate");
            case INSUFFICIENT_MATERIAL -> Messages.get("end.insufficientMaterial");
            case TIME_OUT_WITHOUT_MATING_MATERIAL -> Messages.get("end.timeOutNoMaterial");
            case FIFTY_MOVE_RULE -> Messages.get("end.fiftyMoveRule");
            case SEVENTY_FIVE_MOVE_RULE -> Messages.get("end.seventyFiveMoveRule");
            case THREEFOLD_REPETITION -> Messages.get("end.threefoldRepetition");
            case FIVEFOLD_REPETITION -> Messages.get("end.fivefoldRepetition");
            case DRAW_AGREED -> Messages.get("end.drawAgreed");
        };
    }

    public static void showPastGames() {
        SwingUtilities.invokeLater(() -> {
            frame.setContentPane(new PastGamesPanel());
            frame.revalidate();
            frame.repaint();
        });
    }
}
