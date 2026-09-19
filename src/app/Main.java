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

import engine.core.Fen;
import engine.core.GameResult;
import engine.core.GameSession;
import engine.core.Position;
import engine.core.Pieces;
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
import ui.menu.SetupPanel;
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
        startGame(pConfig, null);
    }

    /**
     * Opens the game screen for a new game that starts from a given position.
     * <p>
     * A game can begin from a position that was set up in the editor rather than from the standard
     * one. I read the FEN into a position, falling back to the standard one when no FEN is given, and
     * otherwise set the game up exactly as a normal one. The FEN is parsed here rather than trusted,
     * so an unusable one is refused before a window is built, which the editor prevents anyway by
     * only offering to start a position it could parse itself.
     * <p>
     * Time complexity: O(p) for the p pieces of the position. Space complexity: O(p) for the board.
     *
     * @param pConfig   names, times and increment of the new game; null starts an unlimited game
     * @param pStartFen position to begin from in Forsyth Edwards notation; null or blank starts from
     *                  the standard position
     * @throws IllegalArgumentException if pStartFen is not a legal chess position
     */
    public static void startGame(GameConfig pConfig, String pStartFen) {
        // no configuration means a casual game without clocks
        final GameConfig cfg = pConfig == null ? GameConfig.unlimited() : pConfig;
        // no FEN means the game begins where chess begins
        final Position start = pStartFen == null || pStartFen.isBlank()
                ? Position.startPosition()
                : Fen.parse(pStartFen);
        // a saved game names the position it began from, unless that was the usual one
        final String startFen = Fen.write(start).equals(Fen.START_POSITION) ? null : Fen.write(start);

        SwingUtilities.invokeLater(() -> {
            // squares small enough for the whole game screen to fit on this screen
            Rectangle usableArea = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            Board board = new Board(cfg, Board.tileSizeFor(usableArea.width, usableArea.height), start);
            MoveLogPanel logPanel = new MoveLogPanel(board.getPreferredSize().height);
            GameSession session = board.getSession();
            session.setMoveLogView(logPanel);
            // clicking a move in the log takes the game back to it, or forward again
            logPanel.setPlySelectedListener(session::goToPly);

            session.setEndListener((pResult, pTermination) -> {
                // the session owns the moves and the result, the names and the clock come from the config,
                // and the reason it ended is what the PGN Termination tag gets written from. A game set
                // up in the editor records its starting FEN, or its moves could not be read back.
                GameRecord record = new GameRecord(cfg, pResult.pgnToken(), pTermination,
                        session.getMoveLog(), session.getFenHistory(), startFen);
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
     * Builds the rows of actions under the board.
     * <p>
     * Players need a way to take a move back and to play it again, and both only make sense while
     * there is something to take back or replay. The session asks the opponent before a move comes
     * back in a timed game, so the button only hands the click over. Players also step away from a
     * game, and stopping the clock should not mean ending it. The pause button pauses and resumes the
     * board and says which of the two it will do next, so a player always reads the action rather
     * than the state. Everything it needs is on the board itself, which stops the clocks and refuses
     * moves while it is paused.
     * <p>
     * Games between people end by agreement or by resignation far more often than by mate, so the
     * second row resigns, offers a draw and claims one. Resigning asks once, because it is final and a
     * misclick would end the game. Offering a draw goes to the opponent, and claiming one goes to the
     * rules, which is why claiming is only live while a rule actually allows it. The session says
     * whenever the game changed, so every button is grey exactly when pressing it would do nothing.
     * I keep the two groups on rows of their own, so the longer German labels still fit beside a
     * small board.
     * <p>
     * It is public for the same reason fitToScreen is: the rule about when each action is live is
     * worth checking, and a test should be able to build the rows from a board without starting the
     * whole application around it.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the panels and their six buttons.
     *
     * @param pBoard the board of the running game, never null
     * @return the panel holding both rows of actions, never null
     * @throws NullPointerException if pBoard is null
     */
    public static JPanel actionBar(Board pBoard) {
        GameSession session = pBoard.getSession();

        JPanel bar = new JPanel(new GridLayout(2, 1));
        bar.setBackground(Theme.PANEL_BG);
        JPanel moveRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        moveRow.setBackground(Theme.PANEL_BG);
        JPanel endRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        endRow.setBackground(Theme.PANEL_BG);

        Font buttonFont = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        JButton takeBack = UiComponents.button(Messages.get("game.takeBack"), buttonFont, Theme.BUTTON_SECONDARY);
        takeBack.setName("takeBack");
        JButton replay = UiComponents.button(Messages.get("game.replayMove"), buttonFont, Theme.BUTTON_SECONDARY);
        replay.setName("replayMove");
        JButton pause = UiComponents.button(Messages.get("game.pause"), buttonFont, Theme.BUTTON_SECONDARY);
        pause.setName("pause");
        JButton resign = UiComponents.button(Messages.get("game.resign"), buttonFont, Theme.BUTTON_SECONDARY);
        resign.setName("resign");
        JButton offerDraw = UiComponents.button(Messages.get("game.offerDraw"), buttonFont, Theme.BUTTON_SECONDARY);
        offerDraw.setName("offerDraw");
        JButton claimDraw = UiComponents.button(Messages.get("game.claimDraw"), buttonFont, Theme.BUTTON_SECONDARY);
        claimDraw.setName("claimDraw");

        // the session decides whether the move really comes back, since a timed game asks the opponent
        takeBack.addActionListener(e -> session.requestTakeback());
        replay.addActionListener(e -> session.redo());
        pause.addActionListener(e -> {
            pBoard.setPaused(!pBoard.isPaused());
            // the button names what pressing it will do next, not what the game is doing now
            pause.setText(Messages.get(pBoard.isPaused() ? "game.resume" : "game.pause"));
        });
        resign.addActionListener(e -> {
            // giving up is final, so it is the one action worth asking about twice
            int answer = JOptionPane.showConfirmDialog(pBoard, Messages.get("game.resignQuestion"),
                    Messages.get("game.resign"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
                // the player to move is the one who gives up
                session.resign(session.isWhiteToMove() ? Pieces.WHITE : Pieces.BLACK);
            }
        });
        // the session asks the opponent, and the rules answer the claim
        offerDraw.addActionListener(e -> session.offerDraw());
        claimDraw.addActionListener(e -> session.claimDraw());

        // a button that would do nothing says so by being grey
        Runnable refresh = () -> {
            boolean running = !session.result().isFinished();
            takeBack.setEnabled(session.canUndo());
            replay.setEnabled(session.canRedo());
            resign.setEnabled(running);
            offerDraw.setEnabled(running);
            claimDraw.setEnabled(running && session.isDrawClaimable());
        };
        session.setStateListener(refresh);
        refresh.run();

        moveRow.add(takeBack);
        moveRow.add(replay);
        moveRow.add(pause);
        endRow.add(resign);
        endRow.add(offerDraw);
        endRow.add(claimDraw);
        bar.add(moveRow);
        bar.add(endRow);
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

    /**
     * Opens the position editor.
     * <p>
     * Setting a position up is a screen of its own, like the menu and the library, so the window
     * swaps it in the same way. The editor starts a game itself once the position can be played,
     * which is why nothing has to be handed back here.
     * <p>
     * Time complexity: O(64) for the board the editor opens on. Space complexity: O(1) beyond the
     * new screen.
     */
    public static void showSetup() {
        SwingUtilities.invokeLater(() -> {
            frame.setContentPane(new SetupPanel());
            frame.revalidate();
            frame.repaint();
        });
    }
}
