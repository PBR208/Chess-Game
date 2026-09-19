package test;

/*
 * Purpose: GameTest is the hand-written test runner for the whole project. I keep it free of
 * external frameworks so the repository stays dependency-free and anyone with a JDK can run it.
 * It covers the rules engine, persistence, notation and every Swing screen in one place. Engine
 * and panel tests run in any JVM, while tests that open real dialogs or frames need a display,
 * so the same suite works on a desktop and on a headless build machine.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.*;
import engine.imports.*;
import engine.model.*;
import engine.persistence.*;
import engine.pieces.*;
import ui.board.*;
import ui.i18n.*;
import ui.menu.*;
import ui.theme.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone GUI + action test runner - no dependencies required.
 * <p>
 * Run via IntelliJ: right-click GameTest -> Run 'GameTest.main()'
 * Run via terminal: java -cp "out;src" test.GameTest (use ':' instead of ';' on macOS/Linux)
 * On a headless JVM the engine and panel tests still run and window tests are skipped.
 * <p>
 * Each test() call registers a named check. Results are printed to the console
 * and a summary is shown at the end. A failing assertion does NOT stop the
 * remaining tests from running.
 * <p>
 * All modal dialogs (EndScreen, FiftyRuleDraw, PromoteGUI) schedule a button
 * click on the EDT via a short Timer before calling setVisible(true). The
 * Timer fires inside the modal's secondary event loop, dismisses the dialog,
 * and lets setVisible() return so the result can be checked.
 * <p>
 * Non-modal panels (MainMenu, NewGamePanel, PastGamesPanel, ReplayPanel) are
 * tested structurally and via direct doClick() calls, since they aren't
 * blocking. Navigation that depends on Main's static frame (Main.showMenu(),
 * Main.startGame()) is intentionally NOT exercised here - those two methods
 * are trivial pass-throughs and testing them would require booting the real
 * application frame. MainMenu's "New Game" button is the one exception: its
 * handler uses SwingUtilities.getWindowAncestor(this) rather than Main's
 * static field, so it's fully testable in isolation.
 * <p>
 * PREREQUISITE: ui.board.PieceSprites exposes the sprite sheet accessors that
 * the replay viewer and the promotion dialog cut their own images from:
 * public static BufferedImage getSheet()
 * public static int getSheetScale()
 */
public class GameTest {

    // -- Constants ---------------------------------------------------------

    private static final int TILE_SIZE = 85;
    private static final int BOARD_HEIGHT = TILE_SIZE * 8;
    private static final int CLICK_DELAY = 200;

    // -- Mini test framework -----------------------------------------------

    private static final List<String> passed = new ArrayList<>();
    private static final List<String> failed = new ArrayList<>();
    private static final List<String> skipped = new ArrayList<>();

    // windows (dialogs, frames) can only be created when the JVM has a display
    private static final boolean HAS_DISPLAY = !GraphicsEnvironment.isHeadless();

    // a single test may not block the run for longer than this
    private static final long TEST_TIMEOUT_MS = 15_000;

    // daemon thread that rescues tests stuck behind a modal dialog
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "test-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Runs one named test and records whether it passed or failed.
     * <p>
     * Every check in this file goes through here so results are counted the same way. I arm a
     * watchdog before running the body. If the body is still running when the time budget is used
     * up, the watchdog closes every visible dialog on the event dispatch thread, which releases a
     * test waiting on a modal nobody clicked, and the test is reported as a timeout instead of
     * hanging the whole run. Failures are unwrapped first, so an assertion thrown inside
     * invokeAndWait shows its real message rather than null.
     * <p>
     * Time complexity: O(1) plus the cost of the test body.
     * Space complexity: O(1) per call, plus one list entry for the result.
     *
     * @param pName human readable test name printed in the report, never null
     * @param pBody test body to execute, never null
     */
    private static void test(String pName, TestBody pBody) {
        // set once the watchdog had to close dialogs for this test
        AtomicBoolean timedOut = new AtomicBoolean(false);
        ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
            timedOut.set(true);
            // closing the dialog lets a blocked invokeAndWait return
            SwingUtilities.invokeLater(GameTest::disposeOpenDialogs);
        }, TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        try {
            pBody.run();
            // a body that only finished because its dialog was force closed still failed
            if (timedOut.get()) {
                throw new AssertionError("timed out after " + TEST_TIMEOUT_MS + " ms, open dialogs were closed");
            }
            passed.add(pName);
            System.out.println("  PASS  " + pName);
        } catch (Throwable t) {
            // report the real reason, not the InvocationTargetException wrapper
            String reason = describeFailure(t);
            failed.add(pName + " -> " + reason);
            System.out.println("  FAIL  " + pName);
            System.out.println("        " + reason);
        } finally {
            // the test is over, so the watchdog must not fire later
            watchdog.cancel(false);
        }
    }

    /**
     * Closes every visible dialog so a test blocked behind a modal can continue.
     * <p>
     * The watchdog calls this on the event dispatch thread when a test runs out of time. I go
     * through all windows the JVM knows about and dispose each visible Dialog, while frames stay
     * open so the shared host frame survives for the remaining tests.
     * <p>
     * Time complexity: O(w) where w is the number of windows.
     * Space complexity: O(w) for the array returned by Window.getWindows().
     */
    private static void disposeOpenDialogs() {
        for (Window window : Window.getWindows()) {
            // only dialogs block the test thread, frames stay untouched
            if (window instanceof Dialog && window.isVisible()) window.dispose();
        }
    }

    /**
     * Turns a test failure into a readable one-line reason for the report.
     * <p>
     * Tests running on the event dispatch thread throw through invokeAndWait, which wraps the real
     * error in an InvocationTargetException whose own message is null. I unwrap those wrappers,
     * then use the message of a failed check as is and the full exception text for anything else,
     * so unexpected exceptions still show their type.
     * <p>
     * Time complexity: O(k) where k is the length of the wrapper chain. Space complexity: O(1).
     *
     * @param pError error thrown by a test body, never null
     * @return a description of what went wrong, never null
     */
    private static String describeFailure(Throwable pError) {
        Throwable cause = pError;
        // peel off the reflection wrappers added by invokeAndWait
        while (cause instanceof InvocationTargetException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        // check messages are already written for humans
        if (cause instanceof AssertionError && cause.getMessage() != null) return cause.getMessage();
        // anything else keeps its type so the cause is obvious
        return cause.toString();
    }

    /**
     * Registers a test that needs a real window, such as a modal dialog or a JFrame.
     * <p>
     * I use this instead of {@link #test} for anything that constructs a java.awt.Window, because
     * doing that on a headless JVM throws HeadlessException and would show up as a false failure.
     * With a display the test runs exactly like a normal one. Without a display I record it as
     * skipped and print a SKIP line, so the summary still shows that it exists.
     * <p>
     * Time complexity: O(1), plus the cost of the test body when it runs.
     * Space complexity: O(1) per call, one list entry when the test is skipped.
     *
     * @param pName human readable test name printed in the report, never null
     * @param pBody test body to execute when a display is available, never null
     */
    private static void guiTest(String pName, TestBody pBody) {
        // no display means no windows, so skip instead of failing
        if (!HAS_DISPLAY) {
            skipped.add(pName);
            System.out.println("  SKIP  " + pName + " (needs a display)");
            return;
        }
        // with a display it behaves like any other test
        test(pName, pBody);
    }

    @FunctionalInterface
    interface TestBody {
        void run() throws Exception;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkEqual(Object expected, Object actual, String message) {
        if (!expected.equals(actual))
            throw new AssertionError(message + " - expected: " + expected + ", got: " + actual);
    }

    private static void checkNotNull(Object obj, String message) {
        if (obj == null) throw new AssertionError(message);
    }

    private static void checkTrue(boolean condition, String message) {
        check(condition, message);
    }

    // -- Swing helpers -----------------------------------------------------

    /**
     * Schedules doClick() on the first visible JButton whose text equals label. Used for MODAL dialogs.
     */
    private static void scheduleClick(String label) {
        Timer t = new Timer(CLICK_DELAY, e -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isVisible() && clickButton(d, label)) break;
            }
        });
        t.setRepeats(false);
        t.start();
    }

    /**
     * Recursively clicks the first button whose text or component name matches the label.
     * <p>
     * Modal dialog tests use this from a timer to press a button while the dialog blocks the test
     * thread. It walks the component tree depth-first, clicks the first AbstractButton that
     * matches and stops there. Matching the component name as well covers icon-only buttons such
     * as the promotion pieces, which have no text at all.
     * <p>
     * Time complexity: O(n) where n is the number of components in the tree.
     * Space complexity: O(d) for the recursion, where d is the nesting depth of the tree.
     *
     * @param pContainer container whose component tree is searched, never null
     * @param pLabel     button text or component name to match, never null
     * @return true if a matching button was found and clicked, false otherwise
     */
    private static boolean clickButton(Container pContainer, String pLabel) {
        for (Component comp : pContainer.getComponents()) {
            // press the first button that matches by text or name
            if (comp instanceof AbstractButton btn && matchesLabel(btn, pLabel)) {
                btn.doClick();
                return true;
            }
            // otherwise keep looking inside nested containers
            if (comp instanceof Container sub && clickButton(sub, pLabel)) return true;
        }
        return false;
    }

    /**
     * Recursively checks whether an AbstractButton with matching text exists.
     */
    private static boolean hasButton(Container c, String label) {
        return findButton(c, label) != null;
    }

    /**
     * Recursively finds the first button whose text or component name matches the label.
     * <p>
     * Structural tests use this to check that a screen offers a certain action. It walks the
     * component tree depth-first and returns the first AbstractButton that matches, looking at
     * the component name as well so icon-only buttons can be found too.
     * <p>
     * Time complexity: O(n) where n is the number of components in the tree.
     * Space complexity: O(d) for the recursion, where d is the nesting depth of the tree.
     *
     * @param pContainer container whose component tree is searched, never null
     * @param pLabel     button text or component name to match, never null
     * @return the first matching button, or null when there is none
     */
    private static AbstractButton findButton(Container pContainer, String pLabel) {
        for (Component comp : pContainer.getComponents()) {
            // the first match by text or name wins
            if (comp instanceof AbstractButton btn && matchesLabel(btn, pLabel)) return btn;
            // descend into nested panels
            if (comp instanceof Container sub) {
                AbstractButton found = findButton(sub, pLabel);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Tells whether a button is identified by the given label.
     * <p>
     * Buttons with text are matched by their text, and icon-only buttons by the component name the
     * UI code gives them. It simply compares the label against both values.
     * <p>
     * Time complexity: O(k) where k is the label length. Space complexity: O(1).
     *
     * @param pButton button to inspect, never null
     * @param pLabel  expected text or component name, never null
     * @return true if either the text or the component name equals the label
     */
    private static boolean matchesLabel(AbstractButton pButton, String pLabel) {
        // text for normal buttons, component name for icon-only ones
        return pLabel.equals(pButton.getText()) || pLabel.equals(pButton.getName());
    }

    /**
     * Returns the first JLabel found in a container (depth-first).
     */
    private static JLabel findLabel(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JLabel lbl) return lbl;
            if (comp instanceof Container sub) {
                JLabel found = findLabel(sub);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Returns every JLabel found in a container (depth-first).
     */
    private static List<JLabel> findAllLabels(Container c) {
        List<JLabel> result = new ArrayList<>();
        collectLabels(c, result);
        return result;
    }

    private static void collectLabels(Container c, List<JLabel> out) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JLabel lbl) out.add(lbl);
            if (comp instanceof Container sub) collectLabels(sub, out);
        }
    }

    /**
     * Returns the first JTextArea found in a container (depth-first).
     */
    private static JTextArea findTextArea(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JTextArea ta) return ta;
            if (comp instanceof Container sub) {
                JTextArea found = findTextArea(sub);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Returns the first JTextField found in a container (depth-first).
     */
    private static JTextField findTextField(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JTextField tf) return tf;
            if (comp instanceof Container sub) {
                JTextField found = findTextField(sub);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Returns every JTextField found in a container (depth-first), in tree order.
     */
    private static List<JTextField> findAllTextFields(Container c) {
        List<JTextField> result = new ArrayList<>();
        collectTextFields(c, result);
        return result;
    }

    private static void collectTextFields(Container c, List<JTextField> out) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JTextField tf) out.add(tf);
            if (comp instanceof Container sub) collectTextFields(sub, out);
        }
    }

    /**
     * Returns the first JList found in a container (depth-first).
     */
    @SuppressWarnings("unchecked")
    private static JList<String> findList(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JList<?> l) return (JList<String>) l;
            if (comp instanceof Container sub) {
                JList<String> found = findList(sub);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Counts how many components of the given class exist in the tree.
     */
    private static int countComponents(Container c, Class<?> type) {
        int count = 0;
        for (Component comp : c.getComponents()) {
            if (type.isInstance(comp)) count++;
            if (comp instanceof Container sub) count += countComponents(sub, type);
        }
        return count;
    }

    // -- Entry point -------------------------------------------------------

    /**
     * Runs every registered test and reports the result.
     * <p>
     * This is the single entry point I use for local runs and build scripts. It points saved games
     * at a temporary folder unless chess.gamesDir is already set, creates the host frame the
     * dialog tests attach to, executes each test group in order, prints a summary and exits with
     * status 1 when anything failed. It has to be public because the Java 17 launcher only
     * accepts a public static main method.
     * <p>
     * Time complexity: O(t) where t is the number of registered tests, not counting the work done
     * inside each test body. Space complexity: O(t) for the passed and failed result lists.
     *
     * @param pArgs command line arguments, currently unused; may be empty but never null
     * @throws Exception if the temporary games folder or the host frame cannot be created
     */
    public static void main(String[] pArgs) throws Exception {
        // keep test games out of the real library unless a folder was given explicitly
        if (System.getProperty(PgnManager.GAMES_DIR_PROPERTY) == null) {
            System.setProperty(PgnManager.GAMES_DIR_PROPERTY,
                    Files.createTempDirectory("chess-game-tests").toString());
        }

        // engine and panel tests still run headless, only window tests get skipped
        if (!HAS_DISPLAY) {
            System.out.println("No display available, skipping tests that open windows.");
        }

        JFrame[] frameHolder = {null};
        // the host frame for dialog tests can only exist with a display
        if (HAS_DISPLAY) {
            SwingUtilities.invokeAndWait(() -> {
                frameHolder[0] = new JFrame("GameTest host");
                frameHolder[0].setVisible(true);
            });
        }
        JFrame frame = frameHolder[0];

        // =================================================================
        System.out.println("\n-- GameConfig --------------------------------------------------");
        // =================================================================

        test("GameConfig: stores all fields as given", () -> {
            GameConfig cfg = new GameConfig("Alice", "Bob", 300_000, 300_000, "Blitz 5+0");
            checkEqual("Alice", cfg.whiteName(), "whiteName");
            checkEqual("Bob", cfg.blackName(), "blackName");
            checkEqual(300_000L, cfg.whiteTimeMs(), "whiteTimeMs");
            checkEqual(300_000L, cfg.blackTimeMs(), "blackTimeMs");
            checkEqual("Blitz 5+0", cfg.timeLabel(), "timeLabel");
        });

        test("GameConfig: blank names default to White/Black", () -> {
            GameConfig cfg = new GameConfig("  ", "", 0, 0, "Unlimited");
            checkEqual("White", cfg.whiteName(), "whiteName default");
            checkEqual("Black", cfg.blackName(), "blackName default");
        });

        test("GameConfig: names are trimmed", () -> {
            GameConfig cfg = new GameConfig("  Alice  ", " Bob ", 0, 0, "Unlimited");
            checkEqual("Alice", cfg.whiteName(), "trimmed whiteName");
            checkEqual("Bob", cfg.blackName(), "trimmed blackName");
        });

        test("GameConfig: unlimited() factory has zero time", () -> {
            GameConfig cfg = GameConfig.unlimited();
            checkEqual(0L, cfg.whiteTimeMs(), "whiteTimeMs");
            checkEqual(0L, cfg.blackTimeMs(), "blackTimeMs");
            checkEqual("Unlimited", cfg.timeLabel(), "timeLabel");
        });

        test("GameConfig: increment defaults to zero and is kept when given", () -> {
            checkEqual(0L, new GameConfig("A", "B", 60_000, 60_000, "Bullet 1+0").incrementMs(),
                    "a configuration without increment must add nothing");
            checkEqual(1_000L, new GameConfig("A", "B", 120_000, 120_000, "Bullet 2+1", 1_000).incrementMs(),
                    "the given increment must be kept");
        });

        // =================================================================
        System.out.println("\n-- GameRecord --------------------------------------------------");
        // =================================================================

        test("GameRecord: built from GameConfig captures fields", () -> {
            GameConfig cfg = new GameConfig("Alice", "Bob", 300_000, 300_000, "Blitz 5+0");
            GameRecord r = new GameRecord(cfg, "1-0",
                    List.of("e4", "e5"), List.of("fen1", "fen2"));
            checkEqual("Alice", r.whiteName, "whiteName");
            checkEqual("Bob", r.blackName, "blackName");
            checkEqual("1-0", r.result, "result");
            checkEqual(2, r.moves.size(), "moves size");
            checkEqual(2, r.fenHistory.size(), "fenHistory size");
            checkNotNull(r.date, "date should be auto-populated");
        });

        test("GameRecord: loaded-from-file constructor preserves given date", () -> {
            GameRecord r = new GameRecord("Alice", "Bob", "0-1",
                    "2026.01.15", "Rapid 10+0", List.of("d4"), List.of("fen1"));
            checkEqual("2026.01.15", r.date, "date");
            checkEqual("Rapid 10+0", r.timeControl, "timeControl");
        });

        test("GameRecord: move/fen lists are immutable copies", () -> {
            List<String> moves = new ArrayList<>(List.of("e4"));
            GameRecord r = new GameRecord("A", "B", "1-0", "2026.01.01", "Blitz", moves, List.of());
            moves.add("e5"); // mutate original after construction
            checkEqual(1, r.moves.size(), "GameRecord.moves must not reflect later mutation");
        });

        test("GameRecord: getDisplayTitle formats correctly", () -> {
            GameRecord r = new GameRecord("Alice", "Bob", "1-0",
                    "2026.07.03", "Blitz 5+0", List.of(), List.of());
            String title = r.getDisplayTitle();
            check(title.contains("Alice"), "title must contain white name");
            check(title.contains("Bob"), "title must contain black name");
            check(title.contains("1-0"), "title must contain result");
            check(title.contains("Blitz 5+0"), "title must contain time control");
            // the separator has to be a real em dash rather than the text of its escape, which is
            // what a doubled backslash in the source would silently turn it into
            check(title.contains(String.valueOf((char) 0x2014)), "the parts must be separated by an em dash");
        });

        // =================================================================
        System.out.println("\n-- FenLoader ----------------------------------------------------");
        // =================================================================

        test("FenLoader: parses starting position correctly", () -> {
            String startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
            char[][] grid = FenLoader.parse(startFen);
            checkEqual('r', grid[0][0], "black rook at a8");
            checkEqual('R', grid[7][0], "white rook at a1");
            checkEqual('k', grid[0][4], "black king at e8");
            checkEqual('K', grid[7][4], "white king at e1");
            checkEqual('p', grid[1][3], "black pawn at d7");
            checkEqual('P', grid[6][3], "white pawn at d2");
        });

        test("FenLoader: empty squares parsed as null-char", () -> {
            char[][] grid = FenLoader.parse("8/8/8/8/8/8/8/8 w - - 0 1");
            checkEqual('\0', grid[3][3], "empty square should be '\\0'");
        });

        test("FenLoader: mixed digit-and-piece rank parses correctly", () -> {
            // rank: 4 empties, White King, 3 empties
            char[][] grid = FenLoader.parse("8/8/8/8/4K3/8/8/8 w - - 0 1");
            checkEqual('K', grid[4][4], "King should be at col 4 on this rank");
            checkEqual('\0', grid[4][0], "col 0 should be empty");
            checkEqual('\0', grid[4][7], "col 7 should be empty");
        });

        test("FenLoader: isWhiteTurn reads active colour field", () -> {
            check(FenLoader.isWhiteTurn("8/8/8/8/8/8/8/8 w - - 0 1"), "'w' should mean White's turn");
            check(!FenLoader.isWhiteTurn("8/8/8/8/8/8/8/8 b - - 0 1"), "'b' should mean Black's turn");
        });

        // =================================================================
        System.out.println("\n-- PgnManager ---------------------------------------------------");
        // =================================================================

        test("PgnManager: save then loadAll round-trips a game", () -> {
            String uniqueWhite = "TestWhite" + System.nanoTime();
            GameRecord original = new GameRecord(uniqueWhite, "TestBlack", "1-0",
                    "2026.01.01", "Blitz 5+0",
                    List.of("e4", "e5", "Nf3", "Nc6"),
                    List.of("fen-after-e4", "fen-after-e5", "fen-after-Nf3", "fen-after-Nc6"));

            PgnManager.save(original);
            List<GameRecord> all = PgnManager.loadAll();

            GameRecord found = all.stream()
                    .filter(r -> r.whiteName.equals(uniqueWhite))
                    .findFirst().orElse(null);

            checkNotNull(found, "saved game must be findable via loadAll()");
            checkEqual("TestBlack", found.blackName, "blackName round-trip");
            checkEqual("1-0", found.result, "result round-trip");
            checkEqual(4, found.moves.size(), "move count round-trip");
            checkEqual("Nc6", found.moves.get(3), "last move round-trip");
            checkEqual(4, found.fenHistory.size(), "fen count round-trip");

            cleanupSavedGame(uniqueWhite);
        });

        test("PgnManager: loadAll returns newest-first ordering", () -> {
            List<GameRecord> all = PgnManager.loadAll();
            // Not asserting exact order details beyond "no exception and a list is returned" -
            // ordering depends on filesystem state, which this test does not control globally.
            checkNotNull(all, "loadAll must never return null");
        });

        test("PgnManager: player names with quotes, backslashes and brackets survive a save and load", () -> {
            String white = "Magnus \"The Hammer\" \\ " + System.nanoTime();
            String black = "Bob [Blitz]";
            PgnManager.save(new GameRecord(white, black, "1-0", "2026.01.01", "Blitz 5+0",
                    List.of("e4", "e5", "Nf3"), List.of("fen1", "fen2", "fen3")));

            GameRecord found = PgnManager.loadAll().stream()
                    .filter(r -> r.whiteName.equals(white))
                    .findFirst().orElse(null);

            checkNotNull(found, "a game with quotes in a player name must still show up in the library");
            checkEqual(black, found.blackName, "brackets in a player name must survive");
            checkEqual(List.of("e4", "e5", "Nf3"), found.moves, "the moves must not get mixed up with the tags");
            cleanupSavedGame(white);
        });

        test("PgnManager: saved games start with the seven tag roster in order", () -> {
            String white = "RosterWhite" + System.nanoTime();
            PgnManager.save(new GameRecord(white, "RosterBlack", "0-1", "2026.01.02", "Rapid 10+0",
                    List.of("d4"), List.of("fen1")));

            File[] files = PgnManager.getGamesDirectory().toFile().listFiles((dir, name) -> name.contains(white));
            checkNotNull(files, "the games folder must exist after saving");
            checkEqual(1, files.length, "exactly one file must be saved for the game");
            List<String> tagNames = Files.readAllLines(files[0].toPath()).stream()
                    .filter(line -> line.startsWith("["))
                    .map(line -> line.substring(1, line.indexOf(' ')))
                    .toList();
            checkEqual(List.of("Event", "Site", "Date", "Round", "White", "Black", "Result"), tagNames.subList(0, 7),
                    "the seven tag roster must come first and in order");
            cleanupSavedGame(white);
        });

        test("PgnManager: the default games folder follows each operating system", () -> {
            String home = System.getProperty("user.home");
            // an absolute path on whatever OS runs the test
            String absolute = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "data").toString();

            checkEqual(java.nio.file.Paths.get(absolute, "ChessGame", "games"),
                    PgnManager.defaultGamesDirectory("Windows 11", home, absolute, null),
                    "Windows must use APPDATA");
            checkEqual(java.nio.file.Paths.get(home, "AppData", "Roaming", "ChessGame", "games"),
                    PgnManager.defaultGamesDirectory("Windows 10", home, " ", null),
                    "Windows without APPDATA must use AppData/Roaming in the home folder");
            checkEqual(java.nio.file.Paths.get(home, "Library", "Application Support", "ChessGame", "games"),
                    PgnManager.defaultGamesDirectory("Mac OS X", home, null, null),
                    "macOS must use Application Support");
            checkEqual(java.nio.file.Paths.get(absolute, "chess-game", "games"),
                    PgnManager.defaultGamesDirectory("Linux", home, null, absolute),
                    "Linux must use XDG_DATA_HOME");
            checkEqual(java.nio.file.Paths.get(home, ".local", "share", "chess-game", "games"),
                    PgnManager.defaultGamesDirectory("Linux", home, null, "relative/data"),
                    "a relative XDG_DATA_HOME must be ignored");
        });

        test("PgnManager: old games are copied to the new folder once and never overwritten", () -> {
            java.nio.file.Path legacy = Files.createTempDirectory("chess-legacy-games");
            Files.writeString(legacy.resolve("old.pgn"), "[White \"A\"]");

            java.nio.file.Path target = Files.createTempDirectory("chess-new-library").resolve("games");
            checkEqual(1, PgnManager.migrateLegacyGames(legacy, target), "the old game must be copied");
            check(Files.exists(target.resolve("old.pgn")), "the copied game must be in the new folder");

            // a later start must not bring back a game the player deleted
            Files.delete(target.resolve("old.pgn"));
            checkEqual(0, PgnManager.migrateLegacyGames(legacy, target), "the copy must only happen once");
            check(!Files.exists(target.resolve("old.pgn")), "a deleted game must stay deleted");
            check(Files.exists(legacy.resolve("old.pgn")), "the old folder must stay untouched");

            // a game that already exists in the new folder keeps its content
            java.nio.file.Path existing = Files.createTempDirectory("chess-existing-library");
            Files.writeString(existing.resolve("old.pgn"), "mine");
            checkEqual(0, PgnManager.migrateLegacyGames(legacy, existing), "an existing game must not be copied over");
            checkEqual("mine", Files.readString(existing.resolve("old.pgn")), "an existing game must not be overwritten");
        });

        test("PgnManager: save reports when the games folder can't be used", () -> {
            String previous = System.getProperty(PgnManager.GAMES_DIR_PROPERTY);
            java.nio.file.Path blocker = Files.createTempFile("chess-not-a-folder", ".txt");
            try {
                // a regular file where the folder should be makes saving impossible
                System.setProperty(PgnManager.GAMES_DIR_PROPERTY, blocker.toString());
                boolean saved = PgnManager.save(new GameRecord("A", "B", "1-0", "2026.01.01", "Blitz 5+0",
                        List.of("e4"), List.of("fen1")));
                check(!saved, "saving into a file instead of a folder must report a failure");
            } finally {
                // later tests keep using the temporary games folder
                if (previous != null) {
                    System.setProperty(PgnManager.GAMES_DIR_PROPERTY, previous);
                }
                Files.deleteIfExists(blocker);
            }
        });

        test("PgnManager: the library pairs every game with the file it came from", () -> {
            String white = "LibraryWhite" + System.nanoTime();
            PgnManager.save(new GameRecord(white, "LibraryBlack", "1-0", "2026.01.01", "Blitz 5+0",
                    List.of("e4"), List.of("fen1")));

            List<PgnManager.SavedGame> library = PgnManager.loadLibrary();
            PgnManager.SavedGame mine = library.stream()
                    .filter(g -> g.record.whiteName.equals(white)).findFirst().orElse(null);
            checkNotNull(mine, "the saved game must appear in the library");
            check(Files.exists(mine.file), "the library must name a file that is really there");
            // both views have to stay in step, a file that fails to parse must drop out of each
            checkEqual(PgnManager.loadAll().size(), library.size(),
                    "the record list and the library must hold the same games");
            cleanupSavedGame(white);
        });

        test("PgnManager: a game nobody renamed is listed by its players", () -> {
            String white = "UnnamedWhite" + System.nanoTime();
            PgnManager.save(new GameRecord(white, "UnnamedBlack", "1-0", "2026.01.01", "Blitz 5+0",
                    List.of("e4"), List.of("fen1")));

            PgnManager.SavedGame mine = PgnManager.loadLibrary().stream()
                    .filter(g -> g.record.whiteName.equals(white)).findFirst().orElse(null);
            checkNotNull(mine, "the saved game must appear in the library");
            check(mine.name.isEmpty(), "a game carrying the default event must count as unnamed, got: " + mine.name);
            checkEqual(mine.record.getDisplayTitle(), mine.title(),
                    "an unnamed game must be listed by who played it");
            cleanupSavedGame(white);
        });

        test("PgnManager: renaming a game survives quotes and shows up in the library", () -> {
            String white = "RenameWhite" + System.nanoTime();
            PgnManager.save(new GameRecord(white, "RenameBlack", "1-0", "2026.01.01", "Blitz 5+0",
                    List.of("e4"), List.of("fen1")));

            PgnManager.SavedGame mine = PgnManager.loadLibrary().stream()
                    .filter(g -> g.record.whiteName.equals(white)).findFirst().orElse(null);
            checkNotNull(mine, "the saved game must appear in the library");

            // a name with a quote and a backslash is exactly what broke tag values before
            String name = "My best \"win\" \\ ever";
            check(PgnManager.rename(mine.file, name), "renaming must report success");

            PgnManager.SavedGame renamed = PgnManager.loadLibrary().stream()
                    .filter(g -> g.record.whiteName.equals(white)).findFirst().orElse(null);
            checkNotNull(renamed, "the renamed game must still be in the library");
            checkEqual(name, renamed.name, "the name must come back exactly as it was typed");
            check(renamed.title().contains(name), "the library must list the game under its name");
            check(renamed.title().contains(white), "and must still show who played it");
            checkEqual(mine.record.moves.size(), renamed.record.moves.size(),
                    "renaming must not touch the moves");

            // an empty name puts the game back to being listed by its players
            check(PgnManager.rename(renamed.file, "  "), "clearing a name must report success");
            PgnManager.SavedGame cleared = PgnManager.loadLibrary().stream()
                    .filter(g -> g.record.whiteName.equals(white)).findFirst().orElse(null);
            checkNotNull(cleared, "the game must survive losing its name");
            check(cleared.name.isEmpty(), "a blank name must make the game unnamed again, got: " + cleared.name);
            cleanupSavedGame(white);
        });

        test("PgnManager: deleting a game removes it from the library and the disk", () -> {
            String white = "DeleteWhite" + System.nanoTime();
            PgnManager.save(new GameRecord(white, "DeleteBlack", "1-0", "2026.01.01", "Blitz 5+0",
                    List.of("e4"), List.of("fen1")));

            PgnManager.SavedGame mine = PgnManager.loadLibrary().stream()
                    .filter(g -> g.record.whiteName.equals(white)).findFirst().orElse(null);
            checkNotNull(mine, "the saved game must appear in the library");

            check(PgnManager.delete(mine.file), "deleting must report that a game went");
            check(!Files.exists(mine.file), "the file must really be gone");
            check(PgnManager.loadLibrary().stream().noneMatch(g -> g.record.whiteName.equals(white)),
                    "the deleted game must not be listed any more");
            // deleting the same entry twice is harmless, but it must not claim to have deleted it
            check(!PgnManager.delete(mine.file), "deleting a game that is already gone must report nothing");
        });

        test("PgnManager: every game of a shared file is listed and marked as sharing it", () -> {
            String white = "SharedWhite" + System.nanoTime();
            java.nio.file.Path dir = PgnManager.getGamesDirectory();
            Files.createDirectories(dir);
            // two games in one file, the way a tournament download arrives
            Files.writeString(dir.resolve("2026.01.01_" + white + ".pgn"),
                    "[Event \"Club Night\"]\n[White \"" + white + "\"]\n[Black \"First\"]\n[Result \"1-0\"]\n\n1. e4 1-0\n\n"
                            + "[Event \"Club Night\"]\n[White \"" + white + "\"]\n[Black \"Second\"]\n[Result \"0-1\"]\n\n1. d4 0-1\n");

            List<PgnManager.SavedGame> mine = PgnManager.loadLibrary().stream()
                    .filter(g -> g.record.whiteName.equals(white)).toList();
            checkEqual(2, mine.size(), "both games of the file must be listed");
            for (PgnManager.SavedGame game : mine) {
                check(game.sharesFile, "a game from a file of two must know it shares the file");
                check(game.name.isEmpty(), "and is listed by its players, got: " + game.name);
            }
            cleanupSavedGame(white);

            String alone = "AloneWhite" + System.nanoTime();
            PgnManager.save(new GameRecord(alone, "AloneBlack", "1-0", "2026.01.01", "Blitz 5+0",
                    List.of("e4"), List.of("fen1")));
            PgnManager.SavedGame single = PgnManager.loadLibrary().stream()
                    .filter(g -> g.record.whiteName.equals(alone)).findFirst().orElse(null);
            checkNotNull(single, "the saved game must appear in the library");
            check(!single.sharesFile, "a game saved here has a file of its own");
            cleanupSavedGame(alone);
        });

        // =================================================================
        System.out.println();
        System.out.println("-- PGN import and export ----------------------------------------");
        // =================================================================

        test("PgnWriter: the roster comes first, then the tags a saved game needs", () -> {
            GameRecord record = new GameRecord("Alice", "Bob", "1-0", "2026.01.02", "Blitz 5+0",
                    "300+5", GameRecord.TERMINATION_TIME_FORFEIT, null,
                    List.of("e4", "e5"), List.of());

            List<String> lines = PgnWriter.write(record).lines().toList();
            List<String> tagNames = lines.stream()
                    .filter(line -> line.startsWith("["))
                    .map(line -> line.substring(1, line.indexOf(' ')))
                    .toList();

            checkEqual(List.of("Event", "Site", "Date", "Round", "White", "Black", "Result"),
                    tagNames.subList(0, 7), "the seven tag roster must come first and in order");
            check(tagNames.contains("TimeControl"), "a saved game must say what it was played at");
            check(tagNames.contains("Termination"), "a finished game must say why it ended");
            check(lines.contains("[TimeControl " + (char) 34 + "300+5" + (char) 34 + "]"),
                    "the time control must be written the way PGN spells it");
        });

        test("PgnWriter: the moves wrap at eighty columns and carry no position comments", () -> {
            List<String> manyMoves = new ArrayList<>();
            // a game long enough that the movetext cannot fit on one line
            for (int ply = 0; ply < 60; ply++) {
                manyMoves.add("Nf3");
            }
            GameRecord record = new GameRecord("Alice", "Bob", "1/2-1/2", "2026.01.02", "Blitz 5+0",
                    "300+0", GameRecord.TERMINATION_NORMAL, null, manyMoves, List.of());

            String pgn = PgnWriter.write(record);
            for (String line : pgn.lines().toList()) {
                check(line.length() <= 80, "no line may pass eighty columns, got " + line.length() + ": " + line);
            }
            check(!pgn.contains("{"), "the position after every move no longer belongs in the file");
            check(pgn.contains("1. Nf3"), "the first move must carry its number");
            check(pgn.trim().endsWith("1/2-1/2"), "the movetext must end with the result");
        });

        test("PgnWriter: a game that was set up says so and keeps its own numbering", () -> {
            GameRecord record = new GameRecord("Alice", "Bob", "*", "2026.01.02", "Unlimited",
                    "-", null, "4k3/8/8/8/8/8/8/4K3 b - - 0 12",
                    List.of("Ke7"), List.of());

            String pgn = PgnWriter.write(record);
            check(pgn.contains("[SetUp " + (char) 34 + "1" + (char) 34 + "]"),
                    "a set up game must be marked as one");
            check(pgn.contains("[FEN "), "a set up game must carry the position it started from");
            check(pgn.contains("12... Ke7"),
                    "a game that starts with Black must number from its own move, got: " + pgn);
        });

        test("PgnReader: comments, side lines, glyphs and move numbers are not moves", () -> {
            String pgn = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]

                    1. e4 {the usual start} e5 ; and a line comment
                    2. Nf3 (2. Nc3 Nf6) 2... Nc6 $1 *
                    """;

            GameRecord record = PgnReader.read(pgn);
            checkNotNull(record, "the game must be readable");
            checkEqual(List.of("e4", "e5", "Nf3", "Nc6"), record.moves,
                    "only the moves that were played belong in the game");
            checkEqual("*", record.result, "the result token must be picked up");
        });

        test("PgnReader: every game of a file with several games comes back", () -> {
            String pgn = """
                    [White "First White"]
                    [Black "First Black"]
                    [Result "1-0"]

                    1. e4 e5 2. Nf3 1-0

                    [White "Second White"]
                    [Black "Second Black"]
                    [Result "0-1"]

                    1. d4 d5 0-1
                    """;

            List<GameRecord> games = PgnReader.readAll(pgn);
            checkEqual(2, games.size(), "both games in the file must be read");
            checkEqual("First White", games.get(0).whiteName, "the first game keeps its players");
            checkEqual("Second Black", games.get(1).blackName, "the second game keeps its players");
            checkEqual(List.of("d4", "d5"), games.get(1).moves, "the games must not run into each other");
        });

        test("PgnReader: castling with zeros and annotation marks become real moves", () -> {
            String pgn = """
                    [White "A"]
                    [Black "B"]
                    [Result "*"]

                    1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. 0-0!? *
                    """;

            GameRecord record = PgnReader.read(pgn);
            checkNotNull(record, "the game must be readable");
            checkEqual(List.of("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "O-O"), record.moves,
                    "castling written with zeros is still castling, and the marks say nothing about the move");
            checkEqual(record.moves.size(), record.fenHistory.size(),
                    "the positions are rebuilt from the moves, one per move");
            // the last position has to be a position, which proves the replay really ran
            Fen.parse(record.fenHistory.get(record.fenHistory.size() - 1));
        });

        test("PgnReader: a game saved in the old format keeps its positions", () -> {
            // the writer used to put the position after every move into a comment
            String pgn = """
                    [White "Old"]
                    [Black "Format"]
                    [Result "1-0"]

                    1. Zz9 {rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1} 1-0
                    """;

            GameRecord record = PgnReader.read(pgn);
            checkNotNull(record, "a game from an older version must still open");
            checkEqual(1, record.fenHistory.size(),
                    "a move that cannot be replayed falls back on the position in its comment");
            check(record.fenHistory.get(0).startsWith("rnbqkbnr"),
                    "the position must be the one the file recorded");
        });

        test("PgnWriter and PgnReader: a game survives being written and read back", () -> {
            // the two characters a tag value has to protect, built from their codes so this test
            // cannot be broken by an escape being doubled somewhere along the way
            String tricky = "Magnus " + (char) 34 + "The Hammer" + (char) 34 + " " + (char) 92;
            GameRecord original = new GameRecord(tricky, "Bob [Blitz]", "1-0", "2026.01.02",
                    "Blitz 5+0", "300+5", GameRecord.TERMINATION_NORMAL, null,
                    List.of("e4", "e5", "Nf3"), List.of());

            GameRecord reread = PgnReader.read(PgnWriter.write(original));

            checkNotNull(reread, "a game I wrote myself must be readable again");
            checkEqual(tricky, reread.whiteName, "quotes and backslashes in a name must survive");
            checkEqual("Bob [Blitz]", reread.blackName, "brackets in a name must survive");
            checkEqual("1-0", reread.result, "the result must survive");
            checkEqual("300+5", reread.pgnTimeControl, "the time control must survive");
            checkEqual(GameRecord.TERMINATION_NORMAL, reread.termination, "the reason must survive");
            checkEqual(List.of("e4", "e5", "Nf3"), reread.moves, "the moves must survive");
        });

        test("GameRecord: the PGN termination and time control follow the standard", () -> {
            checkEqual(GameRecord.TERMINATION_TIME_FORFEIT, GameRecord.pgnTermination(Termination.TIME_OUT),
                    "a flag fall is a forfeit on time");
            checkEqual(GameRecord.TERMINATION_TIME_FORFEIT,
                    GameRecord.pgnTermination(Termination.TIME_OUT_WITHOUT_MATING_MATERIAL),
                    "a flag fall without mating material is still a forfeit on time");
            checkEqual(GameRecord.TERMINATION_NORMAL, GameRecord.pgnTermination(Termination.CHECKMATE),
                    "a mate ends the game by the rules");
            checkEqual(GameRecord.TERMINATION_NORMAL, GameRecord.pgnTermination(Termination.THREEFOLD_REPETITION),
                    "PGN has no tag value per drawing rule");
            check(GameRecord.pgnTermination(null) == null, "a game still running claims no reason");

            checkEqual("300+5", GameRecord.pgnTimeControl(
                            new GameConfig("A", "B", 300_000, 300_000, "Blitz 5+5", 5_000)),
                    "the tag counts in seconds");
            checkEqual(GameRecord.NO_TIME_CONTROL, GameRecord.pgnTimeControl(GameConfig.unlimited()),
                    "a game without a clock has no time control at all");
        });

        // =================================================================
        System.out.println("\n-- BoardState ---------------------------------------------------");
        // =================================================================
        // BoardState holds the position data that used to live directly on
        // Board (pieces list + grid + en passant tile). None of these tests
        // need a visible window - only Piece construction needs a Board
        // reference at all (for tile size / sprite slicing).

        test("BoardState: getPiece reflects the starting position", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    checkNotNull(state.getPiece(4, 7), "white king must be at e1");
                    check(state.getPiece(4, 7).isWhite(), "piece at e1 must be white");
                    checkNotNull(state.getPiece(4, 0), "black king must be at e8");
                    check(!state.getPiece(4, 0).isWhite(), "piece at e8 must be black");
                    check(state.getPiece(4, 4) == null, "e4 must be empty at game start");
                }));

        test("BoardState: getPieces returns an unmodifiable view", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    List<Piece> pieces = state.getPieces();
                    boolean threw = false;
                    try {
                        pieces.clear();
                    } catch (UnsupportedOperationException e) {
                        threw = true;
                    }
                    check(threw, "BoardState.getPieces() must return an unmodifiable list");
                }));

        test("BoardState: removePiece clears both the list and the grid cell", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    Piece pawn = state.getPiece(0, 6);
                    state.removePiece(pawn);
                    check(state.getPiece(0, 6) == null, "grid cell must be cleared after removePiece");
                    check(!state.getPieces().contains(pawn), "piece list must not contain the removed piece");
                }));

        test("BoardState: addPiece places a piece into both the list and the grid", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    Piece extraQueen = new Queen(state, 4, 4, true);
                    state.addPiece(extraQueen);
                    checkEqual(extraQueen, state.getPiece(4, 4), "grid must reflect the newly added piece");
                    check(state.getPieces().contains(extraQueen), "piece list must contain the newly added piece");
                }));

        test("BoardState: moveOnGrid vacates the old square and occupies the new one", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    Piece pawn = state.getPiece(0, 6);
                    pawn.setRow(4); // pretend it already moved logically to a4
                    state.moveOnGrid(pawn, 0, 6);
                    check(state.getPiece(0, 6) == null, "old square must be vacated");
                    checkEqual(pawn, state.getPiece(0, 4), "new square must hold the piece");
                }));

        test("BoardState: getTileNum/getEnPassantTile round-trip", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    state.setEnPassantTile(state.getTileNum(3, 2));
                    checkEqual(state.getTileNum(3, 2), state.getEnPassantTile(), "round trip through getTileNum");
                }));

        test("BoardState: setPieces replaces the entire position at once", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();

                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece loneKing = new King(state, 4, 4, true);
                    custom.add(loneKing);
                    state.setPieces(custom);

                    checkEqual(1, state.getPieces().size(), "only the pieces passed to setPieces must remain");
                    checkEqual(loneKing, state.getPiece(4, 4), "the lone king must be findable at its square");
                    check(state.getPiece(4, 7) == null, "old positions must be cleared by setPieces");
                }));

        // =================================================================
        System.out.println("\n-- Move & CheckScanner ------------------------------------------");
        // =================================================================

        test("Move: capture resolves directly from BoardState when the destination is occupied", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    Piece whitePawn = state.getPiece(4, 6);
                    Piece blackPawn = state.getPiece(3, 1);
                    Move m = new Move(state, whitePawn, 3, 1); // hypothetical capture, legality not checked here
                    checkEqual(blackPawn, m.getCapture(), "Move must resolve capture from BoardState at construction");
                }));

        test("Move: destination square with no piece has a null capture", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    Piece whitePawn = state.getPiece(4, 6);
                    Move m = new Move(state, whitePawn, 4, 4);
                    check(m.getCapture() == null, "empty destination square must mean no capture");
                }));

        test("CheckScanner: neither king is in check at game start", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    CheckScanner cs = new CheckScanner(state);
                    check(!cs.isKingInCheckRN(true), "White king must not be in check at game start");
                    check(!cs.isKingInCheckRN(false), "Black king must not be in check at game start");
                }));

        test("CheckScanner: detects check from an unobstructed rook", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();

                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(state, 4, 7, true);
                    Piece blackRook = new Rook(state, 4, 0, false);
                    custom.add(whiteKing);
                    custom.add(blackRook);
                    state.setPieces(custom);

                    CheckScanner cs = new CheckScanner(state);
                    check(cs.isKingInCheckRN(true), "White king on an open file facing a rook must be in check");
                }));

        test("CheckScanner: isKingLeftInCheck rejects a king move into an attacked square", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();

                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(state, 0, 7, true);  // a1
                    Piece blackRook = new Rook(state, 3, 7, false); // d1, attacks the whole 1st rank
                    custom.add(whiteKing);
                    custom.add(blackRook);
                    state.setPieces(custom);

                    CheckScanner cs = new CheckScanner(state);

                    Move intoCheck = new Move(state, whiteKing, 1, 7); // Ka1-b1, still on rank 1
                    check(cs.isKingLeftInCheck(intoCheck), "stepping onto the attacked rank must be flagged");

                    Move awayFromCheck = new Move(state, whiteKing, 0, 6); // Ka1-a2, off the rank
                    check(!cs.isKingLeftInCheck(awayFromCheck), "stepping off the attacked rank must be safe");
                }));

        test("CheckScanner: isKingLeftInCheck detects a discovered check from a third piece", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();

                    // White king on e1, White rook on e4 blocking a Black rook on e8.
                    // Sliding the White rook off the e-file must expose the king.
                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(state, 4, 7, true);   // e1
                    Piece whiteRook = new Rook(state, 4, 4, true);   // e4
                    Piece blackRook = new Rook(state, 4, 0, false);  // e8
                    custom.add(whiteKing);
                    custom.add(whiteRook);
                    custom.add(blackRook);
                    state.setPieces(custom);

                    CheckScanner cs = new CheckScanner(state);

                    Move slideOff = new Move(state, whiteRook, 3, 4); // Re4-d4, leaves the e-file
                    check(cs.isKingLeftInCheck(slideOff),
                            "moving the blocking rook off the file must expose the king to the rook on e8");
                }));

        // =================================================================
        System.out.println("\n-- NotationHelper -----------------------------------------------");
        // =================================================================

        test("NotationHelper: simple pawn push has no piece letter or capture marker", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    Move m = new Move(state, state.getPiece(4, 6), 4, 4);
                    checkEqual("e4", new NotationHelper().toNotation(m, 4, 6), "pawn push e2-e4 must be notated 'e4'");
                }));

        test("NotationHelper: pawn capture is notated with the origin file", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    Move m = new Move(state, state.getPiece(4, 6), 3, 1); // hypothetical capture on d7
                    checkEqual("exd7", new NotationHelper().toNotation(m, 4, 6),
                            "pawn capture must be notated with the origin file, e.g. 'exd7'");
                }));

        test("NotationHelper: knight move uses 'N' (K is reserved for King)", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    Move m = new Move(state, state.getPiece(1, 7), 2, 5); // Nc3
                    checkEqual("Nc3", new NotationHelper().toNotation(m, 1, 7), "knight move must be notated with 'N'");
                }));

        test("NotationHelper: castling is notated O-O / O-O-O", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    Piece king = state.getPiece(4, 7);
                    NotationHelper nh = new NotationHelper();

                    Move kingside = new Move(state, king, 6, 7);
                    checkEqual("O-O", nh.toNotation(kingside, 4, 7), "kingside castle notation");

                    Move queenside = new Move(state, king, 2, 7);
                    checkEqual("O-O-O", nh.toNotation(queenside, 4, 7), "queenside castle notation");
                }));

        // =================================================================
        System.out.println("\n-- FenGenerator -------------------------------------------------");
        // =================================================================

        test("FenGenerator: starting position matches the standard FEN placement field", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    String fen = new FenGenerator(state).generate(true, 0, 1);
                    check(fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"),
                            "placement field must match the standard starting position, got: " + fen);
                }));

        test("FenGenerator: active colour field reflects isWhiteTurn", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    FenGenerator fg = new FenGenerator(state);
                    check(fg.generate(true, 0, 1).contains(" w "), "white to move must produce ' w '");
                    check(fg.generate(false, 0, 1).contains(" b "), "black to move must produce ' b '");
                }));

        test("FenGenerator: starting position has all four castling rights", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    String fen = new FenGenerator(state).generate(true, 0, 1);
                    checkEqual("KQkq", fen.split(" ")[2], "all four castling rights must be present at game start");
                }));

        test("FenGenerator: a moved king removes both of that side's castling rights", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

                    state.removePiece(state.getPiece(5, 7)); // clear f1 so the king can step there
                    Move kingStep = new Move(state, state.getPiece(4, 7), 5, 7);
                    check(gc.isValidMove(kingStep), "Ke1-f1 must be a legal move on an otherwise-untouched board");
                    gc.makeMove(kingStep);

                    String castling = new FenGenerator(state).generate(false, 0, 1).split(" ")[2];
                    check(!castling.contains("K") && !castling.contains("Q"),
                            "White must lose both castling rights once the king has moved, got: " + castling);
                    check(castling.contains("k") && castling.contains("q"),
                            "Black's castling rights must be unaffected, got: " + castling);
                }));

        test("FenGenerator: en passant target square appears after a double pawn push", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

                    gc.makeMove(new Move(state, state.getPiece(4, 6), 4, 4)); // e2-e4

                    String epField = new FenGenerator(state).generate(false, 0, 1).split(" ")[3];
                    checkEqual("e3", epField, "en passant target square after e2-e4 must be e3");
                }));

        // =================================================================
        System.out.println("\n-- MoveHistory --------------------------------------------------");
        // =================================================================

        test("MoveHistory: record adds one entry to both moveLog and fenHistory", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    MoveHistory history = new MoveHistory(state);
                    Move m = new Move(state, state.getPiece(4, 6), 4, 4);

                    history.record(m, 4, 6, false, 1, 1);

                    checkEqual(1, history.getMoveLog().size(), "moveLog must contain exactly one entry");
                    checkEqual("e4", history.getMoveLog().get(0), "notation must be recorded correctly");
                    checkEqual(1, history.getFenHistory().size(), "fenHistory must contain exactly one entry");
                }));

        test("MoveHistory: clear empties both lists", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    MoveHistory history = new MoveHistory(state);
                    history.record(new Move(state, state.getPiece(4, 6), 4, 4), 4, 6, false, 1, 1);
                    history.clear();
                    checkEqual(0, history.getMoveLog().size(), "moveLog must be empty after clear()");
                    checkEqual(0, history.getFenHistory().size(), "fenHistory must be empty after clear()");
                }));

        test("MoveHistory: getMoveLog/getFenHistory return unmodifiable views", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    MoveHistory history = new MoveHistory(state);
                    boolean threwOnMoveLog = false, threwOnFenHistory = false;
                    try {
                        history.getMoveLog().add("hack");
                    } catch (UnsupportedOperationException e) {
                        threwOnMoveLog = true;
                    }
                    try {
                        history.getFenHistory().add("hack");
                    } catch (UnsupportedOperationException e) {
                        threwOnFenHistory = true;
                    }
                    check(threwOnMoveLog, "getMoveLog() must not allow external mutation");
                    check(threwOnFenHistory, "getFenHistory() must not allow external mutation");
                }));

        test("MoveHistory: listener receives updates on record() and clear()", () ->
                SwingUtilities.invokeAndWait(() -> {
                    BoardState state = newBoardState();
                    MoveHistory history = new MoveHistory(state);

                    int[] updateCount = {0};
                    boolean[] clearedFlag = {false};
                    history.setListener(new MoveHistory.Listener() {
                        public void onUpdate(List<String> moveLog, String currentFen) {
                            updateCount[0]++;
                        }

                        public void onClear() {
                            clearedFlag[0] = true;
                        }
                    });

                    history.record(new Move(state, state.getPiece(4, 6), 4, 4), 4, 6, false, 1, 1);
                    history.clear();

                    checkEqual(1, updateCount[0], "listener must be notified exactly once per record()");
                    check(clearedFlag[0], "listener must be notified on clear()");
                }));

        // =================================================================
        System.out.println("\n-- PieceType ----------------------------------------------------");
        // =================================================================

        test("PieceType: has exactly six values with correct display names", () -> {
            checkEqual(6, PieceType.values().length, "there must be exactly six piece types");
            checkEqual("King", PieceType.KING.getDisplayName(), "King display name");
            checkEqual("Queen", PieceType.QUEEN.getDisplayName(), "Queen display name");
            checkEqual("Rook", PieceType.ROOK.getDisplayName(), "Rook display name");
            checkEqual("Bishop", PieceType.BISHOP.getDisplayName(), "Bishop display name");
            checkEqual("Knight", PieceType.KNIGHT.getDisplayName(), "Knight display name");
            checkEqual("Pawn", PieceType.PAWN.getDisplayName(), "Pawn display name");
        });

        test("PieceSprites: sprite sheet loads from the classpath with six piece columns", () -> {
            BufferedImage sheet = PieceSprites.loadSpriteSheet(PieceSprites.SPRITE_SHEET_PATH);
            checkNotNull(sheet, "the bundled sprite sheet must load");
            checkEqual(0, sheet.getWidth() % 6, "the sheet width must split into six piece columns");
            check(sheet.getHeight() >= sheet.getWidth() / 6 * 2, "the sheet must hold a white and a black row");
        });

        test("PieceSprites: a missing sprite sheet fails with a message naming the resource", () -> {
            String missing = "/resources/does-not-exist.png";
            try {
                PieceSprites.loadSpriteSheet(missing);
            } catch (IllegalStateException e) {
                check(e.getMessage().contains(missing), "the message must name the missing resource, got: " + e.getMessage());
                return;
            }
            check(false, "loading a missing sprite sheet must throw IllegalStateException");
        });

        test("PieceSprites: every sprite is scaled to the board's square size", () -> {
            PieceSprites sprites = new PieceSprites(64);
            BufferedImage knight = sprites.spriteFor(Pieces.KNIGHT, true);
            checkEqual(64, knight.getWidth(), "a sprite must be as wide as one square");
            checkEqual(64, knight.getHeight(), "a sprite must be as high as one square");
        });

        test("PieceSprites: the same piece and colour comes back from the cache", () -> {
            PieceSprites sprites = new PieceSprites(40);
            BufferedImage first = sprites.spriteFor(Pieces.QUEEN, false);
            BufferedImage second = sprites.spriteFor(Pieces.QUEEN, false);
            check(first == second, "a repaint must reuse the scaled sprite instead of scaling it again");
            check(first != sprites.spriteFor(Pieces.QUEEN, true), "black and white must not share a sprite");
        });

        test("PieceSprites: every piece type has its own column in the sheet", () -> {
            java.util.Set<Integer> columns = new java.util.HashSet<>();
            // the sprites speak the engine's piece codes, so the types are counted the way it counts them
            for (int type = 0; type < Pieces.TYPE_COUNT; type++) {
                int column = PieceSprites.spriteColumn(type);
                check(column >= 0 && column < 6, "the column of type " + type + " must lie in the sheet, got " + column);
                check(columns.add(column), "two piece types must not share the sprite column " + column);
            }
            checkEqual(6, columns.size(), "all six piece types must have a column");
        });

        test("PieceSprites: a square size of zero or less is refused", () -> {
            try {
                new PieceSprites(0);
                throw new AssertionError("a sprite source without pixels must not be built");
            } catch (IllegalArgumentException expected) {
                // the size is checked before anything is scaled
            }
        });

        // =================================================================
        System.out.println("\n-- ChessClock ---------------------------------------------------");
        // =================================================================

        test("ChessClock: starts stopped with configured time", () -> {
            ChessClock clock = new ChessClock(true, 60_000, () -> {
            }, (w) -> {
            });
            checkEqual(60_000L, clock.getTimeMs(), "initial timeMs");
            check(!clock.isRunning(), "clock should not be running until start() is called");
        });

        test("ChessClock: start()/stop() toggles running state", () -> {
            ChessClock clock = new ChessClock(true, 60_000, () -> {
            }, (w) -> {
            });
            clock.start();
            check(clock.isRunning(), "should be running after start()");
            clock.stop();
            check(!clock.isRunning(), "should not be running after stop()");
        });

        test("ChessClock: reset() restores start time and stops", () -> {
            ChessClock clock = new ChessClock(true, 60_000, () -> {
            }, (w) -> {
            });
            clock.start();
            clock.reset();
            checkEqual(60_000L, clock.getTimeMs(), "timeMs after reset");
            check(!clock.isRunning(), "should not be running after reset()");
        });

        test("ChessClock: draw() does not throw for a normal clock", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ChessClock clock = new ChessClock(true, 60_000, () -> {
                    }, (w) -> {
                    });
                    BufferedImage img = new BufferedImage(400, 100, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = img.createGraphics();
                    clock.draw(g2d, 0, 400, 100); // must not throw
                    g2d.dispose();
                }));

        test("ChessClock: draw() does not throw for unlimited (0ms) clock", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ChessClock clock = new ChessClock(false, 0, () -> {
                    }, (w) -> {
                    });
                    BufferedImage img = new BufferedImage(400, 100, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = img.createGraphics();
                    clock.draw(g2d, 0, 400, 100); // must not throw, shows "--:--"
                    g2d.dispose();
                }));

        test("ChessClock: unlimited clock never fires onExpired", () -> {
            CountDownLatch expired = new CountDownLatch(1);
            ChessClock clock = new ChessClock(true, 0, () -> {
            }, (w) -> expired.countDown());
            clock.start();
            boolean firedTooEarly = expired.await(400, TimeUnit.MILLISECONDS);
            clock.stop();
            check(!firedTooEarly, "unlimited (0ms) clock must never expire");
        });

        test("ChessClock: running clock counts down and fires onExpired at zero", () -> {
            CountDownLatch expired = new CountDownLatch(1);
            boolean[] expiredWhite = {false};
            ChessClock clock = new ChessClock(true, 150, () -> {
            }, (w) -> {
                expiredWhite[0] = w;
                expired.countDown();
            });
            clock.start();
            boolean firedInTime = expired.await(2, TimeUnit.SECONDS);
            check(firedInTime, "onExpired must fire once timeMs reaches 0");
            check(expiredWhite[0], "expired flag should report isWhite = true");
            check(!clock.isRunning(), "clock must stop itself after expiring");
        });

        test("ChessClock: keeps counting while the event thread is busy", () -> {
            ChessClock clock = new ChessClock(true, 60_000, () -> {
            }, (w) -> {
            });
            clock.start();
            // block the event dispatch thread the clock timer runs on
            SwingUtilities.invokeAndWait(() -> {
                try {
                    Thread.sleep(1_500);
                } catch (InterruptedException ignored) {
                }
            });
            long used = 60_000 - clock.getTimeMs();
            clock.stop();
            check(used >= 1_400, "a busy event thread must not hand out free time, only " + used + " ms were counted");
        });

        test("ChessClock: addTime adds an increment, but not to an unlimited clock", () -> {
            ChessClock timed = new ChessClock(true, 60_000, () -> {
            }, (w) -> {
            });
            timed.addTime(2_000);
            checkEqual(62_000L, timed.getTimeMs(), "a timed clock must gain the added time");

            ChessClock unlimited = new ChessClock(false, 0, () -> {
            }, (w) -> {
            });
            unlimited.addTime(2_000);
            checkEqual(0L, unlimited.getTimeMs(), "an unlimited clock must stay unlimited");
        });

        test("ChessClock: the refresh timer only runs while the clock runs", () -> {
            ChessClock clock = new ChessClock(true, 60_000, () -> {
            }, (w) -> {
            });
            check(!clock.isTicking(), "a new clock must not start its timer before the clock starts");
            clock.start();
            check(clock.isTicking(), "a running clock needs its timer");
            clock.stop();
            check(!clock.isTicking(), "a stopped clock must stop its timer");
        });

        test("ChessClock: a Fischer clock pays its increment after a move", () -> {
            ChessClock clock = new ChessClock(true, 60_000, ClockMode.FISCHER, 2_000, 0, () -> {
            }, w -> {
            });
            clock.onMoveFinished();
            checkEqual(62_000L, clock.getTimeMs(), "the increment is paid whether the move needed it or not");
        });

        test("ChessClock: sudden death and a delay clock pay no increment", () -> {
            ChessClock sudden = new ChessClock(true, 60_000, ClockMode.SUDDEN_DEATH, 0, 0, () -> {
            }, w -> {
            });
            sudden.onMoveFinished();
            checkEqual(60_000L, sudden.getTimeMs(), "sudden death gives a player nothing but their own time");

            ChessClock delayed = new ChessClock(true, 60_000, ClockMode.SIMPLE_DELAY, 0, 3_000, () -> {
            }, w -> {
            });
            delayed.onMoveFinished();
            checkEqual(60_000L, delayed.getTimeMs(), "a delay is not an increment and is never paid out");
        });

        test("ChessClock: a simple delay charges nothing until the delay is used up", () -> {
            ChessClock clock = new ChessClock(true, 10_000, ClockMode.SIMPLE_DELAY, 0, 5_000, () -> {
            }, w -> {
            });
            clock.start();
            Thread.sleep(200);
            clock.stop();
            // the whole think fitted inside the delay, so it cost nothing at all
            checkEqual(10_000L, clock.getTimeMs(), "time spent inside the delay must not be charged");
        });

        test("ChessClock: a simple delay charges only what goes past it", () -> {
            ChessClock clock = new ChessClock(true, 10_000, ClockMode.SIMPLE_DELAY, 0, 100, () -> {
            }, w -> {
            });
            clock.start();
            Thread.sleep(400);
            clock.stop();

            long left = clock.getTimeMs();
            check(left < 10_000, "thinking past the delay has to cost time, got " + left);
            check(left >= 9_000, "but only what went past it, got " + left);
        });

        test("ChessClock: Bronstein gives back what the move used, up to the delay", () -> {
            ChessClock generous = new ChessClock(true, 10_000, ClockMode.BRONSTEIN, 0, 5_000, () -> {
            }, w -> {
            });
            generous.start();
            Thread.sleep(200);
            generous.stop();
            check(generous.getTimeMs() >= 9_990,
                    "a move well inside the delay costs nothing once it is given back, got " + generous.getTimeMs());

            ChessClock capped = new ChessClock(true, 10_000, ClockMode.BRONSTEIN, 0, 100, () -> {
            }, w -> {
            });
            capped.start();
            Thread.sleep(500);
            capped.stop();
            check(capped.getTimeMs() <= 9_700,
                    "a move past the delay gets only the delay back, got " + capped.getTimeMs());
        });

        test("ChessClock: the display counts in tenths below ten seconds", () -> {
            checkEqual("01:05", ChessClock.formatTime(65_000, 600_000), "minutes and seconds above ten seconds");
            checkEqual("00:10", ChessClock.formatTime(10_000, 600_000), "ten seconds still reads as a clock");
            checkEqual("9.4", ChessClock.formatTime(9_400, 600_000), "below ten seconds every tenth shows");
            checkEqual("0.0", ChessClock.formatTime(0, 600_000), "a fallen flag shows no time left");
            checkEqual("00:00", ChessClock.formatTime(0, 0), "an unlimited clock never counts tenths");
        });

        test("ClockStage: a tournament control is read the way players write it", () -> {
            List<ClockStage> classical = ClockStage.parse("40/90, 30");
            checkEqual(2, classical.size(), "forty moves in ninety minutes, then thirty minutes");
            checkEqual(40, classical.get(0).moves(), "the first stage covers forty moves");
            checkEqual(5_400_000L, classical.get(0).timeMs(), "ninety minutes, in milliseconds");
            check(classical.get(1).runsToTheEnd(), "the last stage has no move count of its own");
            checkEqual(1_800_000L, classical.get(1).timeMs(), "thirty minutes for the rest of the game");

            checkEqual(1, ClockStage.parse("30").size(), "a control without a slash is a single stage");
            check(ClockStage.parse("").isEmpty(), "no text means no stages");
            check(ClockStage.parse(null).isEmpty(), "and neither does nothing at all");
            check(ClockStage.parse("40/ninety").isEmpty(), "a control nobody can read gives no stages at all");
        });

        test("ChessClock: a staged control hands out its time when the stage is played out", () -> {
            ChessClock clock = new ChessClock(true, 60_000, ClockMode.SUDDEN_DEATH, 0, 0, () -> {
            }, w -> {
            });
            // two moves at a minute, then a minute and a half for whatever is left
            clock.setStages(List.of(new ClockStage(2, 60_000),
                    new ClockStage(ClockStage.UNTIL_THE_END, 30_000)));

            clock.onMoveFinished();
            checkEqual(60_000L, clock.getTimeMs(), "a move inside the stage brings nothing with it");

            clock.onMoveFinished();
            checkEqual(90_000L, clock.getTimeMs(), "playing the stage out brings the next stage's time");

            clock.onMoveFinished();
            checkEqual(90_000L, clock.getTimeMs(), "the last stage runs to the end and brings no more");
        });

        test("LowTimeSound: a warning never throws, with or without a sound card", () -> {
            // a build server has no sound card, and a warning must never take a game down with it
            checkEqual(!GraphicsEnvironment.isHeadless(), LowTimeSound.isAvailable(),
                    "a machine without a screen is treated as one without sound");
            LowTimeSound.play();
        });

        test("ChessClock: the low time warning comes once, and again after time is added", () -> {
            ChessClock clock = new ChessClock(true, 2_000, () -> {
            }, w -> {
            });
            check(!clock.isLowTimeWarned(), "a fresh clock has nothing to warn about yet");

            clock.start();
            Thread.sleep(250);
            check(clock.isLowTimeWarned(), "a clock under the low mark warns its player");
            clock.stop();

            clock.addTime(120_000);
            check(!clock.isLowTimeWarned(), "time back above the mark earns another warning later on");
        });

        test("Board: a board whose clocks are stopped can be garbage collected", () -> {
            java.lang.ref.WeakReference<Board> ref = boardWithStoppedClocks();
            // give the collector a few chances, a live timer would keep the board reachable forever
            for (int i = 0; i < 20 && ref.get() != null; i++) {
                System.gc();
                Thread.sleep(50);
            }
            check(ref.get() == null, "no clock timer may keep a board alive after its clocks are stopped");
        });

        test("Board: the player who just moved gets the increment", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(new GameConfig("Alice", "Bob", 120_000, 120_000, "Bullet 2+1", 1_000));
                    GameSession session = board.getSession();
                    session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));

                    long whiteLeft = board.getRemainingTimeMs(true);
                    check(whiteLeft > 120_000, "White's clock must include the one second increment, got " + whiteLeft + " ms");
                    check(board.isClockRunning(false), "Black's clock must run after White's move");
                }));

        test("Board: squares shrink so the game screen fits a 1366 by 768 laptop", () -> {
            // 728 pixels are left once a 40 pixel task bar is gone
            int tile = Board.tileSizeFor(1366, 728);
            // eight rows and two clock bars plus the window frame
            check(tile * 10 + 60 <= 728, "the board and both clocks must fit the screen height, got " + tile + " px squares");
            check(tile < Board.DEFAULT_TILE_SIZE, "a small screen must get smaller squares than the default, got " + tile);
        });

        test("Board: squares stay at the default on large screens and at the minimum on tiny ones", () -> {
            checkEqual(Board.DEFAULT_TILE_SIZE, Board.tileSizeFor(2560, 1400), "a large screen keeps the default squares");
            checkEqual(Board.MIN_TILE_SIZE, Board.tileSizeFor(320, 240), "a tiny screen never goes below the minimum");
        });

        test("Board: a board with smaller squares measures itself and its pieces in them", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited(), 60);
                    checkEqual(60, board.getTileSize(), "the board must use the given square size");
                    // eight squares wide, eight rows and two clock bars high
                    checkEqual(new Dimension(480, 600), board.getPreferredSize(), "the panel size must follow the squares");
                    checkEqual(60, board.getSprites().spriteFor(Pieces.KNIGHT, true).getWidth(),
                            "the piece sprites must be scaled to the smaller squares");
                }));

        test("Board: a square size below the minimum is refused", () -> {
            try {
                new Board(GameConfig.unlimited(), Board.MIN_TILE_SIZE - 1);
                throw new AssertionError("a board with squares below the minimum must not be built");
            } catch (IllegalArgumentException expected) {
                // the size is checked before anything else is created
            }
        });

        test("Main: the actions beside the board are live exactly when they would do something", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    GameSession session = board.getSession();
                    // the board installs the real claim dialog, which a test must never open
                    session.setDrawArbiter(null);

                    JPanel bar = app.Main.actionBar(board);
                    AbstractButton resign = findButton(bar, "resign");
                    AbstractButton offer = findButton(bar, "offerDraw");
                    AbstractButton claim = findButton(bar, "claimDraw");
                    checkNotNull(resign, "the row must offer resigning");
                    checkNotNull(offer, "and offering a draw");
                    checkNotNull(claim, "and claiming one");

                    check(resign.isEnabled(), "a running game can be resigned");
                    check(offer.isEnabled(), "and a draw can be offered in it");
                    check(!claim.isEnabled(), "but nothing is claimable in the starting position");

                    // both knights out and back twice brings the starting position back a third time
                    String[][] shuffle = {{"g1", "f3"}, {"g8", "f6"}, {"f3", "g1"}, {"f6", "g8"}};
                    for (int round = 0; round < 2; round++) {
                        for (String[] step : shuffle) {
                            session.play(session.moveFor(Bitboards.squareOf(step[0]), Bitboards.squareOf(step[1])));
                        }
                    }
                    check(claim.isEnabled(), "a threefold repetition makes the claim live");

                    session.resign(Pieces.WHITE);
                    check(!resign.isEnabled(), "a finished game cannot be resigned");
                    check(!offer.isEnabled(), "nor drawn by agreement");
                    check(!claim.isEnabled(), "nor claimed");
                }));

        test("Main: taking back and replaying a move are live exactly when there is one", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    GameSession session = board.getSession();

                    JPanel bar = app.Main.actionBar(board);
                    AbstractButton takeBack = findButton(bar, "takeBack");
                    AbstractButton replay = findButton(bar, "replayMove");
                    checkNotNull(takeBack, "the row must offer taking a move back");
                    checkNotNull(replay, "and playing it again");
                    check(!takeBack.isEnabled(), "there is nothing to take back before the first move");
                    check(!replay.isEnabled(), "and nothing to play again");

                    session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
                    check(takeBack.isEnabled(), "a played move can be taken back");

                    session.undo();
                    check(!takeBack.isEnabled(), "the only move is gone again");
                    check(replay.isEnabled(), "so it can be played again");

                    session.redo();
                    check(takeBack.isEnabled(), "a replayed move can be taken back once more");
                    check(!replay.isEnabled(), "and there is nothing left to play again");
                }));

        test("Board: pausing stops both clocks and resuming starts the one to move", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(new GameConfig("Alice", "Bob", 120_000, 120_000, "Bullet 2+1", 1_000));
                    check(board.areClocksRunning(), "a new game runs White's clock");

                    board.setPaused(true);
                    check(board.isPaused(), "the board has to know it is paused");
                    check(!board.areClocksRunning(), "a paused game stops both clocks");

                    board.setPaused(true);
                    check(board.isPaused(), "pausing an already paused game changes nothing");

                    board.setPaused(false);
                    check(!board.isPaused(), "the game runs again");
                    check(board.isClockRunning(true), "and the clock of the player to move carries on");
                }));

        test("Board: a paused board takes no moves", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    Input input = new Input(board, board.getSession());
                    board.setPaused(true);

                    // the middle of e2, which holds a pawn in the starting position
                    input.mousePressed(new java.awt.event.MouseEvent(board, java.awt.event.MouseEvent.MOUSE_PRESSED,
                            System.currentTimeMillis(), 0, 382, 637, 1, false));
                    check(board.getSelectedSquare() < 0, "nothing may be picked up while the game is paused");
                }));

        test("Main: the window size is cut down to the usable screen area", () -> {
            Rectangle laptop = new Rectangle(0, 0, 1366, 728);
            checkEqual(new Dimension(1366, 728), app.Main.fitToScreen(new Dimension(1400, 1000), laptop),
                    "a window larger than the screen must shrink to it");
            checkEqual(new Dimension(1200, 728), app.Main.fitToScreen(new Dimension(1200, 900), laptop),
                    "a width that fits must stay while the height shrinks");
            checkEqual(new Dimension(1400, 1000), app.Main.fitToScreen(new Dimension(1400, 1000), new Rectangle(0, 0, 2560, 1400)),
                    "a window that fits must keep its size");
        });

        test("Input: a press on the bottom clock bar is ignored", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    Input input = new Input(board, board.getSession());
                    // below the first rank, on White's clock bar
                    input.mousePressed(new java.awt.event.MouseEvent(board, java.awt.event.MouseEvent.MOUSE_PRESSED,
                            System.currentTimeMillis(), 0, 40, 805, 1, false));
                    check(board.getSelectedSquare() < 0, "nothing may be selected from the clock bar");
                }));

        test("Input: a press on the top clock bar doesn't pick up a piece", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    Input input = new Input(board, board.getSession());
                    // above the eighth rank, on Black's clock bar, right over the a8 rook
                    input.mousePressed(new java.awt.event.MouseEvent(board, java.awt.event.MouseEvent.MOUSE_PRESSED,
                            System.currentTimeMillis(), 0, 40, 40, 1, false));
                    check(board.getSelectedSquare() < 0, "the a8 rook must not be picked up from the clock bar");
                }));

        test("Input: releasing a piece left of the board cancels the move", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    GameSession session = board.getSession();
                    Input input = new Input(board, session);

                    // pick up the b1 knight in the middle of its square
                    input.mousePressed(new java.awt.event.MouseEvent(board, java.awt.event.MouseEvent.MOUSE_PRESSED,
                            System.currentTimeMillis(), 0, 125, 720, 1, false));
                    // let go left of the board, level with a3
                    input.mouseReleased(new java.awt.event.MouseEvent(board, java.awt.event.MouseEvent.MOUSE_RELEASED,
                            System.currentTimeMillis(), 0, -50, 550, 1, false));

                    checkEqual(Pieces.WHITE_KNIGHT, session.position().pieceAt(Bitboards.squareOf("b1")),
                            "the knight must stay on b1");
                    check(session.getMoveLog().isEmpty(), "no move may be played");
                }));

        test("Main: a headless start explains the problem and exits with code 2", () -> {
            String javaExe = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toString();
            File output = Files.createTempFile("chess-headless-start", ".log").toFile();
            // start the real entry point in its own JVM, the way a server or a container would
            Process process = new ProcessBuilder(javaExe, "-Djava.awt.headless=true",
                    "-cp", System.getProperty("java.class.path"), "app.Main")
                    .redirectErrorStream(true)
                    .redirectOutput(output)
                    .start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            String text = Files.readString(output.toPath());
            Files.deleteIfExists(output.toPath());

            check(finished, "a headless start must end on its own");
            // 2 is the exit code Main uses for a missing display
            checkEqual(2, process.exitValue(), "a headless start must report a failure, output: " + text);
            check(text.contains("graphical display"), "the output must explain that a display is missing, got: " + text);
        });

        // =================================================================
        System.out.println("\n-- EndScreen ----------------------------------------------------");
        // =================================================================

        guiTest("EndScreen: size scales with tileSize", () ->
                SwingUtilities.invokeAndWait(() -> {
                    EndScreen d = new EndScreen(frame, "White wins", TILE_SIZE, () -> {
                    });
                    checkEqual(TILE_SIZE * 4, d.getWidth(), "width");
                    checkEqual((int) (TILE_SIZE * 2.5), d.getHeight(), "height");
                    d.dispose();
                }));

        guiTest("EndScreen: label displays passed message", () ->
                SwingUtilities.invokeAndWait(() -> {
                    EndScreen d = new EndScreen(frame, "Black wins", TILE_SIZE, () -> {
                    });
                    JLabel lbl = findLabel(d.getContentPane());
                    checkNotNull(lbl, "EndScreen must contain a JLabel");
                    checkEqual("Black wins", lbl.getText(), "label text");
                    d.dispose();
                }));

        guiTest("EndScreen: contains 'Return to Menu' button", () ->
                SwingUtilities.invokeAndWait(() -> {
                    EndScreen d = new EndScreen(frame, "Stalemate - Draw", TILE_SIZE, () -> {
                    });
                    check(hasButton(d, "Return to Menu"), "EndScreen must have a Return to Menu button");
                    d.dispose();
                }));

        guiTest("EndScreen: clicking button closes dialog", () -> {
            scheduleClick("Return to Menu");
            boolean[] visible = {true};
            SwingUtilities.invokeAndWait(() -> {
                EndScreen d = new EndScreen(frame, "White wins", TILE_SIZE, () -> {
                });
                d.setVisible(true); // blocks until the button is clicked
                visible[0] = d.isVisible();
            });
            check(!visible[0], "Dialog should be closed after clicking Return to Menu");
        });

        guiTest("EndScreen: clicking button invokes onReturn callback", () -> {
            boolean[] callbackFired = {false};
            scheduleClick("Return to Menu");
            SwingUtilities.invokeAndWait(() -> {
                EndScreen d = new EndScreen(frame, "White wins", TILE_SIZE, () -> callbackFired[0] = true);
                d.setVisible(true);
            });
            check(callbackFired[0], "onReturn callback must fire when the button is clicked");
        });

        guiTest("EndScreen: onReturn is NOT called if dialog is disposed programmatically", () -> {
            boolean[] callbackFired = {false};
            SwingUtilities.invokeAndWait(() -> {
                EndScreen d = new EndScreen(frame, "White wins", TILE_SIZE, () -> callbackFired[0] = true);
                d.dispose(); // closing without clicking must not trigger navigation
            });
            check(!callbackFired[0], "onReturn must only fire from the button click, not from dispose()");
        });

        guiTest("EndScreen: closing the window also invokes onReturn", () -> {
            boolean[] callbackFired = {false};
            SwingUtilities.invokeAndWait(() -> {
                EndScreen d = new EndScreen(frame, "White wins", TILE_SIZE, () -> callbackFired[0] = true);
                // same event the window system sends for Alt+F4
                d.dispatchEvent(new java.awt.event.WindowEvent(d, java.awt.event.WindowEvent.WINDOW_CLOSING));
            });
            check(callbackFired[0], "closing the end screen window must return to the menu as well");
        });

        // =================================================================
        System.out.println("\n-- FiftyRuleDraw (optional claim) ------------------------------");
        // =================================================================

        guiTest("FiftyRuleDraw: size scales with tileSize", () ->
                SwingUtilities.invokeAndWait(() -> {
                    FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, false);
                    checkEqual(TILE_SIZE * 4, d.getWidth(), "width");
                    checkEqual((int) (TILE_SIZE * 2.5), d.getHeight(), "height");
                    d.dispose();
                }));

        guiTest("FiftyRuleDraw: optional claim has correct buttons", () ->
                SwingUtilities.invokeAndWait(() -> {
                    FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, false);
                    check(hasButton(d, "Claim Draw"), "Must have 'Claim Draw'");
                    check(hasButton(d, "Decline"), "Must have 'Decline'");
                    check(!hasButton(d, "OK"), "Must NOT have 'OK'");
                    d.dispose();
                }));

        guiTest("FiftyRuleDraw: Claim Draw returns ACCEPTED", () -> {
            scheduleClick("Claim Draw");
            FiftyRuleDraw.DrawResult[] result = {null};
            SwingUtilities.invokeAndWait(() -> {
                FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, false);
                d.setVisible(true);
                result[0] = d.getResult();
            });
            checkEqual(FiftyRuleDraw.DrawResult.ACCEPTED, result[0], "result");
        });

        guiTest("FiftyRuleDraw: Decline returns DECLINED", () -> {
            scheduleClick("Decline");
            FiftyRuleDraw.DrawResult[] result = {null};
            SwingUtilities.invokeAndWait(() -> {
                FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, false);
                d.setVisible(true);
                result[0] = d.getResult();
            });
            checkEqual(FiftyRuleDraw.DrawResult.DECLINED, result[0], "result");
        });

        // =================================================================
        System.out.println("\n-- FiftyRuleDraw (forced draw) ---------------------------------");
        // =================================================================

        guiTest("FiftyRuleDraw: forced draw has correct buttons", () ->
                SwingUtilities.invokeAndWait(() -> {
                    FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, true);
                    check(hasButton(d, "OK"), "Must have 'OK'");
                    check(!hasButton(d, "Claim Draw"), "Must NOT have 'Claim Draw'");
                    check(!hasButton(d, "Decline"), "Must NOT have 'Decline'");
                    d.dispose();
                }));

        guiTest("FiftyRuleDraw: forced draw OK closes dialog", () -> {
            scheduleClick("OK");
            boolean[] visible = {true};
            SwingUtilities.invokeAndWait(() -> {
                FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, true);
                d.setVisible(true);
                visible[0] = d.isVisible();
            });
            check(!visible[0], "Forced-draw dialog should close after clicking OK");
        });

        // =================================================================
        System.out.println("\n-- PromoteGUI ---------------------------------------------------");
        // =================================================================

        guiTest("PromoteGUI: size scales with tileSize", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PromoteGUI d = new PromoteGUI(frame, TILE_SIZE);
                    checkEqual(TILE_SIZE * 4, d.getWidth(), "width");
                    checkEqual(TILE_SIZE, d.getHeight(), "height");
                    d.dispose();
                }));

        guiTest("PromoteGUI: all four buttons present", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PromoteGUI d = new PromoteGUI(frame, TILE_SIZE);
                    check(hasButton(d, "Queen"), "Must have 'Queen'");
                    check(hasButton(d, "Rook"), "Must have 'Rook'");
                    check(hasButton(d, "Bishop"), "Must have 'Bishop'");
                    check(hasButton(d, "Knight"), "Must have 'Knight'");
                    d.dispose();
                }));

        guiTest("PromoteGUI: the buttons keep their names and are described in the chosen language", () ->
                SwingUtilities.invokeAndWait(() -> {
                    java.util.Locale previous = Messages.getLocale();
                    try {
                        Messages.setLocale(java.util.Locale.GERMAN);
                        PromoteGUI dialog = new PromoteGUI(frame, TILE_SIZE);

                        // the name is how a test finds an icon with no text, so it stays English
                        AbstractButton queen = findButton(dialog, "Queen");
                        checkNotNull(queen, "the button must still be found by its English name");
                        // what a player reads, and what a screen reader says, follows the language
                        checkEqual("Dame", queen.getToolTipText(), "a German player reads the German name");
                        checkEqual("Dame", queen.getAccessibleContext().getAccessibleName(),
                                "and a screen reader announces the same");

                        dialog.dispose();
                    } finally {
                        // the rest of the suite reads English
                        Messages.setLocale(previous);
                    }
                }));

        guiTest("PromoteGUI: Queen -> Choice.QUEEN", () -> {
            scheduleClick("Queen");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog());
            checkEqual(PromoteGUI.Choice.QUEEN, choice[0], "choice");
        });

        guiTest("PromoteGUI: Rook -> Choice.ROOK", () -> {
            scheduleClick("Rook");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog());
            checkEqual(PromoteGUI.Choice.ROOK, choice[0], "choice");
        });

        guiTest("PromoteGUI: Bishop -> Choice.BISHOP", () -> {
            scheduleClick("Bishop");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog());
            checkEqual(PromoteGUI.Choice.BISHOP, choice[0], "choice");
        });

        guiTest("PromoteGUI: Knight -> Choice.KNIGHT", () -> {
            scheduleClick("Knight");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog());
            checkEqual(PromoteGUI.Choice.KNIGHT, choice[0], "choice");
        });

        guiTest("PromoteGUI: Black's dialog shows black pieces", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PromoteGUI white = new PromoteGUI(frame, TILE_SIZE, true);
                    PromoteGUI black = new PromoteGUI(frame, TILE_SIZE, false);
                    int[] whitePixels = iconPixels(findButton(white, "Queen"));
                    int[] blackPixels = iconPixels(findButton(black, "Queen"));
                    white.dispose();
                    black.dispose();
                    check(!java.util.Arrays.equals(whitePixels, blackPixels), "Black's queen icon must differ from White's");
                }));

        guiTest("PromoteGUI: closing the window doesn't skip the promotion", () -> {
            boolean[] stillOpen = {false};
            // first try to close the dialog through the window system, like Alt+F4 does
            Timer closer = new Timer(20, e -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof PromoteGUI d && d.isVisible()) {
                        d.dispatchEvent(new java.awt.event.WindowEvent(d, java.awt.event.WindowEvent.WINDOW_CLOSING));
                        stillOpen[0] = d.isVisible();
                        ((Timer) e.getSource()).stop();
                    }
                }
            });
            closer.start();
            // then pick a piece the normal way
            scheduleClick("Rook");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog());
            closer.stop();
            check(stillOpen[0], "closing the window must leave the promotion dialog open");
            checkEqual(PromoteGUI.Choice.ROOK, choice[0], "the piece picked afterwards must count");
        });

        guiTest("PromoteGUI: a dialog closed without a choice falls back to a queen", () -> {
            // close the dialog from code, the way a parent window or the watchdog would
            Timer disposer = new Timer(20, e -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof PromoteGUI d && d.isVisible()) {
                        d.dispose();
                        ((Timer) e.getSource()).stop();
                    }
                }
            });
            disposer.start();
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog());
            disposer.stop();
            checkEqual(PromoteGUI.Choice.QUEEN, choice[0], "a dialog closed without a click must still produce a queen");
        });

        // =================================================================
        System.out.println("\n-- SwingPromotionChooser & SwingDrawOfferResolver ---------------");
        // =================================================================
        // These wrap PromoteGUI/FiftyRuleDraw and translate their results into
        // the engine-side PromotionChooser/DrawOfferResolver contract. The
        // dialog behavior itself is already covered above - these tests only
        // check the translation.

        guiTest("SwingPromotionChooser: Queen selection maps to the engine's queen", () -> {
            scheduleClick("Queen");
            int[] result = {Pieces.NONE};
            SwingUtilities.invokeAndWait(() -> {
                JFrame testFrame = new JFrame();
                Board board = new Board(GameConfig.unlimited());
                testFrame.setContentPane(board);
                testFrame.pack();
                result[0] = new SwingPromotionChooser(board).pick(true);
                testFrame.dispose();
            });
            checkEqual(Pieces.QUEEN, result[0], "clicking Queen must resolve to the queen the engine counts with");
        });

        guiTest("SwingPromotionChooser: Knight selection maps to the engine's knight", () -> {
            scheduleClick("Knight");
            int[] result = {Pieces.NONE};
            SwingUtilities.invokeAndWait(() -> {
                JFrame testFrame = new JFrame();
                Board board = new Board(GameConfig.unlimited());
                testFrame.setContentPane(board);
                testFrame.pack();
                result[0] = new SwingPromotionChooser(board).pick(false);
                testFrame.dispose();
            });
            checkEqual(Pieces.KNIGHT, result[0], "clicking Knight must resolve to the knight the engine counts with");
        });

        guiTest("SwingDrawOfferResolver: Claim Draw resolves the fifty move claim to true", () -> {
            scheduleClick("Claim Draw");
            boolean[] result = {false};
            SwingUtilities.invokeAndWait(() -> {
                JFrame testFrame = new JFrame();
                Board board = new Board(GameConfig.unlimited());
                testFrame.setContentPane(board);
                testFrame.pack();
                result[0] = new SwingDrawOfferResolver(board).offerFiftyMoveDraw();
                testFrame.dispose();
            });
            check(result[0], "clicking Claim Draw must resolve the fifty move claim to true");
        });

        guiTest("SwingDrawOfferResolver: Decline resolves the fifty move claim to false", () -> {
            scheduleClick("Decline");
            boolean[] result = {true};
            SwingUtilities.invokeAndWait(() -> {
                JFrame testFrame = new JFrame();
                Board board = new Board(GameConfig.unlimited());
                testFrame.setContentPane(board);
                testFrame.pack();
                result[0] = new SwingDrawOfferResolver(board).offerFiftyMoveDraw();
                testFrame.dispose();
            });
            check(!result[0], "clicking Decline must resolve the fifty move claim to false");
        });

        guiTest("SwingDrawOfferResolver: notifyForcedDraw shows and dismisses the forced-draw dialog", () -> {
            scheduleClick("OK");
            SwingUtilities.invokeAndWait(() -> {
                JFrame testFrame = new JFrame();
                Board board = new Board(GameConfig.unlimited());
                testFrame.setContentPane(board);
                testFrame.pack();
                new SwingDrawOfferResolver(board).notifyForcedDraw(); // must not throw
                testFrame.dispose();
            });
        });

        guiTest("SwingDrawOfferResolver: repetition claim explains the repetition and resolves to true", () -> {
            String[] dialogText = {null};
            // read the message while the dialog is open, before the scheduled click closes it
            Timer peek = new Timer(20, e -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof JDialog d && d.isVisible() && findLabel(d.getContentPane()) != null) {
                        dialogText[0] = findLabel(d.getContentPane()).getText();
                        ((Timer) e.getSource()).stop();
                    }
                }
            });
            peek.start();
            scheduleClick("Claim Draw");
            boolean[] result = {false};
            SwingUtilities.invokeAndWait(() -> {
                JFrame testFrame = new JFrame();
                Board board = new Board(GameConfig.unlimited());
                testFrame.setContentPane(board);
                testFrame.pack();
                result[0] = new SwingDrawOfferResolver(board).offerRepetitionDraw();
                testFrame.dispose();
            });
            peek.stop();
            check(result[0], "clicking Claim Draw must resolve offerRepetitionDraw() to true");
            checkNotNull(dialogText[0], "the claim dialog must show a message");
            check(dialogText[0].contains("three times"), "the message must explain the repetition, got: " + dialogText[0]);
        });

        // =================================================================
        System.out.println("\n-- MoveLogPanel -------------------------------------------------");
        // =================================================================

        test("MoveLogPanel: preferred size matches board height", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    checkEqual(200, p.getPreferredSize().width, "preferred width");
                    checkEqual(BOARD_HEIGHT, p.getPreferredSize().height, "preferred height");
                }));

        test("MoveLogPanel: header label contains 'Move History'", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    JLabel lbl = findLabel(p);
                    checkNotNull(lbl, "MoveLogPanel must contain a JLabel header");
                    check(lbl.getText().contains("Move History"),
                            "Header label must contain 'Move History', got: " + lbl.getText());
                }));

        test("MoveLogPanel: contains a JTextArea", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    checkNotNull(findTextArea(p), "MoveLogPanel must contain a JTextArea");
                }));

        test("MoveLogPanel: update with empty log clears text", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    p.update(List.of(), "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                    JTextArea ta = findTextArea(p);
                    checkNotNull(ta, "Must contain a JTextArea");
                    checkEqual("", ta.getText(), "text area should be empty after update with []");
                }));

        test("MoveLogPanel: update renders full move pairs", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    p.update(List.of("e4", "e5", "Nf3", "Nc6"), "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                    String text = findTextArea(p).getText();
                    check(text.contains("1."), "Must contain move number '1.'");
                    check(text.contains("e4"), "Must contain white's first move 'e4'");
                    check(text.contains("e5"), "Must contain black's first move 'e5'");
                    check(text.contains("2."), "Must contain move number '2.'");
                    check(text.contains("Nf3"), "Must contain white's second move 'Nf3'");
                    check(text.contains("Nc6"), "Must contain black's second move 'Nc6'");
                }));

        test("MoveLogPanel: update shows '...' when black has not moved yet", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    p.update(List.of("e4", "e5", "Nf3"), "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                    String text = findTextArea(p).getText();
                    check(text.contains("Nf3"), "Must contain white's pending move 'Nf3'");
                    check(text.contains("..."), "Must show '...' for black's pending reply");
                }));

        test("MoveLogPanel: repeated update replaces content, no duplication", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    p.update(List.of("e4"), "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                    p.update(List.of("e4", "e5"), "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                    String text = findTextArea(p).getText();
                    int count = 0, idx = 0;
                    while ((idx = text.indexOf("1.", idx)) != -1) {
                        count++;
                        idx++;
                    }
                    checkEqual(1, count, "Move number '1.' must appear exactly once after two updates");
                }));

        test("MoveLogPanel: clear empties the text area", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    p.update(List.of("e4", "e5", "Nf3", "Nc6"), "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                    p.clear();
                    JTextArea ta = findTextArea(p);
                    checkEqual("", ta.getText(), "text area must be empty after clear()");
                }));

        // =================================================================
        System.out.println("\n-- ReplayPanel --------------------------------------------------");
        // =================================================================

        List<String> sampleMoves = List.of("e4", "e5", "Nf3");
        List<String> sampleFens = List.of(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2"
        );

        // three recorded positions plus the board before anybody moved makes four frames
        test("ReplayPanel: opens on the position before anybody moved", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleMoves, sampleFens);
                    JLabel lbl = findMoveLabel(p);
                    checkNotNull(lbl, "ReplayPanel must show a move-index label");
                    check(lbl.getText().contains("1/4"),
                            "the starting position is the first of four frames, got: " + lbl.getText());
                    check(lbl.getText().contains("Start position"),
                            "and it belongs to no move, got: " + lbl.getText());
                }));

        test("ReplayPanel: next button advances position", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleMoves, sampleFens);
                    AbstractButton next = findButton(p, "next");
                    checkNotNull(next, "Must have a next button");
                    next.doClick();
                    JLabel lbl = findMoveLabel(p);
                    check(lbl.getText().contains("2/4"), "Should be at position 2 of 4, got: " + lbl.getText());
                    check(lbl.getText().contains("(White)"),
                            "the second frame follows White's first move, got: " + lbl.getText());
                }));

        test("ReplayPanel: last button jumps to final position", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleMoves, sampleFens);
                    AbstractButton last = findButton(p, "last");
                    checkNotNull(last, "Must have a last button");
                    last.doClick();
                    JLabel lbl = findMoveLabel(p);
                    check(lbl.getText().contains("4/4"), "Should be at the final position, got: " + lbl.getText());
                }));

        test("ReplayPanel: next button does not overrun the list", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleMoves, sampleFens);
                    AbstractButton next = findButton(p, "next");
                    for (int i = 0; i < 10; i++) next.doClick(); // click far past the end
                    JLabel lbl = findMoveLabel(p);
                    check(lbl.getText().contains("4/4"), "Cursor must clamp at the last position, got: " + lbl.getText());
                }));

        test("ReplayPanel: first button returns to position 1", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleMoves, sampleFens);
                    findButton(p, "last").doClick(); // jump to end first
                    AbstractButton first = findButton(p, "first");
                    checkNotNull(first, "Must have a first button");
                    first.doClick();
                    JLabel lbl = findMoveLabel(p);
                    check(lbl.getText().contains("1/4"), "Should be back at position 1, got: " + lbl.getText());
                }));

        test("ReplayPanel: prev button does not underrun position 1", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleMoves, sampleFens);
                    AbstractButton prev = findButton(p, "previous");
                    for (int i = 0; i < 5; i++) prev.doClick(); // click before the start
                    JLabel lbl = findMoveLabel(p);
                    check(lbl.getText().contains("1/4"), "Cursor must clamp at the first position, got: " + lbl.getText());
                }));

        test("ReplayPanel: empty FEN list shows 'No moves' without throwing", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(List.of(), List.of());
                    JLabel lbl = findMoveLabel(p);
                    checkEqual("No moves", lbl.getText(), "label text for empty history");
                    // Painting an empty history must not throw
                    BufferedImage img = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
                    p.setSize(600, 600);
                    p.paint(img.createGraphics());
                }));

        test("ReplayPanel: turning the board round changes what is drawn", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleMoves, sampleFens);
                    // the board has to be painted on its own. Painting the whole panel without a
                    // window draws nothing at all, because the split panes leave the board no size,
                    // which is also why the older paint test could only check that nothing threw.
                    Component canvas = findByName(p, "replayBoard");
                    checkNotNull(canvas, "the replay must have a board to draw on");
                    canvas.setSize(480, 480);

                    // comparing the picture is what proves the board really turned, without the
                    // panel having to expose which way round it happens to be
                    BufferedImage before = new BufferedImage(480, 480, BufferedImage.TYPE_INT_ARGB);
                    canvas.paint(before.createGraphics());

                    AbstractButton flip = findButton(p, "flip");
                    checkNotNull(flip, "the replay must offer turning the board round");
                    flip.doClick();

                    BufferedImage after = new BufferedImage(480, 480, BufferedImage.TYPE_INT_ARGB);
                    canvas.paint(after.createGraphics());

                    boolean identical = java.util.Arrays.equals(
                            before.getRGB(0, 0, 480, 480, null, 0, 480),
                            after.getRGB(0, 0, 480, 480, null, 0, 480));
                    check(!identical, "the same position from the other side has to look different");
                }));

        test("ReplayPanel: the copy buttons are there and never throw", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleMoves, sampleFens);
                    AbstractButton copyFen = findButton(p, "copyFen");
                    AbstractButton copyMoves = findButton(p, "copyMoves");
                    checkNotNull(copyFen, "the position must be copyable");
                    checkNotNull(copyMoves, "and so must the moves");
                    // a machine with no clipboard has to stay quiet rather than throw out of a click
                    copyFen.doClick();
                    copyMoves.doClick();
                }));

        test("ReplayPanel: asking for a move shows the position after it", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleMoves, sampleFens);

                    // White's second move is the third half move, so its position is the last frame
                    p.showMove(2);
                    JLabel lbl = findMoveLabel(p);
                    check(lbl.getText().contains("4/4"),
                            "move three must show the position after it, got: " + lbl.getText());

                    p.showMove(0);
                    check(findMoveLabel(p).getText().contains("2/4"),
                            "White's first move must show the second frame, got: " + findMoveLabel(p).getText());

                    // a move this game never had leaves the replay where it was
                    p.showMove(99);
                    check(findMoveLabel(p).getText().contains("2/4"),
                            "a move that was never played must change nothing, got: " + findMoveLabel(p).getText());
                    p.showMove(-1);
                    check(findMoveLabel(p).getText().contains("2/4"),
                            "and neither must a move before the first, got: " + findMoveLabel(p).getText());
                }));

        guiTest("ReplayPanel: copying the position puts its FEN on the clipboard", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleMoves, sampleFens);
                    findButton(p, "copyFen").doClick();
                    try {
                        String copied = (String) Toolkit.getDefaultToolkit().getSystemClipboard()
                                .getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                        checkEqual(Fen.START_POSITION, copied,
                                "the replay opens on the starting position, so that is what gets copied");
                    } catch (Exception problem) {
                        check(false, "the clipboard could not be read back: " + problem);
                    }
                }));

        // =================================================================
        System.out.println("\n-- Theme & UiComponents -----------------------------------------");
        // =================================================================

        test("Theme: palette constants are all defined and visually distinct", () -> {
            checkNotNull(Theme.BG, "BG must be defined");
            checkNotNull(Theme.PANEL_BG, "PANEL_BG must be defined");
            checkNotNull(Theme.FG, "FG must be defined");
            checkNotNull(Theme.ACCENT, "ACCENT must be defined");
            checkNotNull(Theme.MUTED, "MUTED must be defined");
            checkNotNull(Theme.BUTTON_SECONDARY, "BUTTON_SECONDARY must be defined");
            check(!Theme.BG.equals(Theme.PANEL_BG), "BG and PANEL_BG must be visually distinct");
            check(!Theme.ACCENT.equals(Theme.BUTTON_SECONDARY), "ACCENT and BUTTON_SECONDARY must be visually distinct");
        });

        test("UiComponents: button() applies the shared flat, dark-theme look", () ->
                SwingUtilities.invokeAndWait(() -> {
                    JButton b = UiComponents.button("Test", new Font(Font.SANS_SERIF, Font.BOLD, 14), Theme.ACCENT);
                    checkEqual(Theme.ACCENT, b.getBackground(), "background must match the given color");
                    checkEqual(Theme.FG, b.getForeground(), "foreground must always be Theme.FG");
                    check(!b.isBorderPainted(), "border must not be painted");
                    check(!b.isFocusPainted(), "focus ring must not be painted");
                    checkEqual(Cursor.HAND_CURSOR, b.getCursor().getType(), "cursor must be the hand cursor");
                }));

        test("UiComponents: style() applies the same look to a JToggleButton", () ->
                SwingUtilities.invokeAndWait(() -> {
                    JToggleButton t = new JToggleButton("Preset");
                    UiComponents.style(t, new Font(Font.SANS_SERIF, Font.PLAIN, 12), Theme.BUTTON_SECONDARY);
                    checkEqual(Theme.BUTTON_SECONDARY, t.getBackground(), "background must apply to toggle buttons too");
                    check(!t.isBorderPainted(), "border must not be painted on a toggle button either");
                }));

        test("UiComponents: displayable keeps text the font can draw", () -> {
            Font font = new Font(Font.DIALOG, Font.PLAIN, 12);
            checkEqual("Move Log", UiComponents.displayable(font, "Move Log", "Log"), "plain letters must be kept");
        });

        test("UiComponents: displayable falls back to ASCII when a character is missing", () -> {
            Font font = new Font(Font.DIALOG, Font.PLAIN, 12);
            // U+FFFF is a noncharacter, so no font can draw it
            String missing = "Next " + (char) 0xFFFF;
            checkEqual("Next >", UiComponents.displayable(font, missing, "Next >"), "a missing glyph must switch to the ASCII text");
        });

        test("UiComponents: a button with an ASCII text shows it when the font lacks a symbol", () ->
                SwingUtilities.invokeAndWait(() -> {
                    String missing = "Replay " + (char) 0xFFFF;
                    JButton b = UiComponents.button(missing, "Replay >", new Font(Font.SANS_SERIF, Font.PLAIN, 13), Theme.BUTTON_SECONDARY);
                    checkEqual("Replay >", b.getText(), "the button must show the ASCII text");
                    checkEqual(Theme.BUTTON_SECONDARY, b.getBackground(), "the fallback button must keep the shared look");
                }));

        test("UiComponents: addHoverEffect brightens on enter and restores on exit", () ->
                SwingUtilities.invokeAndWait(() -> {
                    // plain button on purpose, UiComponents.button() already registers the hover effect
                    JButton b = new JButton("Hover");
                    b.setBackground(Theme.BUTTON_SECONDARY);
                    UiComponents.addHoverEffect(b);
                    Color original = b.getBackground();

                    // A real MouseEvent is required here - JButton's own look-and-feel
                    // listener is also registered and will NPE on a null event.
                    java.awt.event.MouseEvent enter = new java.awt.event.MouseEvent(
                            b, java.awt.event.MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 0, 0, 0, false);
                    for (java.awt.event.MouseListener l : b.getMouseListeners()) l.mouseEntered(enter);
                    check(!original.equals(b.getBackground()), "background must change on hover");

                    java.awt.event.MouseEvent exit = new java.awt.event.MouseEvent(
                            b, java.awt.event.MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, 0, 0, 0, false);
                    for (java.awt.event.MouseListener l : b.getMouseListeners()) l.mouseExited(exit);
                    checkEqual(original, b.getBackground(), "background must be restored after the mouse exits");
                }));

        // =================================================================
        System.out.println("\n-- MainMenu -----------------------------------------------------");
        // =================================================================

        test("MainMenu: shows title and both navigation buttons", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MainMenu menu = new MainMenu();
                    check(hasButton(menu, "New Game"), "Must have a 'New Game' button");
                    check(hasButton(menu, "Past Games"), "Must have a 'Past Games' button");
                    boolean hasTitle = findAllLabels(menu).stream()
                            .anyMatch(l -> "CHESS".equals(l.getText()));
                    check(hasTitle, "Must show the 'CHESS' title label");
                }));

        test("MainMenu: the menu is written in the language that was chosen", () ->
                SwingUtilities.invokeAndWait(() -> {
                    java.util.Locale previous = Messages.getLocale();
                    try {
                        Messages.setLocale(java.util.Locale.GERMAN);
                        MainMenu german = new MainMenu();
                        check(hasButton(german, "Neues Spiel"), "the German menu offers a new game in German");
                        boolean germanTitle = findAllLabels(german).stream()
                                .anyMatch(l -> "SCHACH".equals(l.getText()));
                        check(germanTitle, "and carries the German title");

                        Messages.setLocale(java.util.Locale.ENGLISH);
                        check(hasButton(new MainMenu(), "New Game"), "the English menu reads as it always did");
                    } finally {
                        // the rest of the suite reads English
                        Messages.setLocale(previous);
                    }
                }));

        test("MainMenu: every shipped language can be picked from the menu", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MainMenu menu = new MainMenu();
                    // the buttons keep their names whatever language they are written in
                    for (java.util.Locale supported : Messages.supportedLocales()) {
                        check(hasButton(menu, "language-" + supported.getLanguage()),
                                "the menu must offer " + supported.getDisplayLanguage());
                    }
                }));

        guiTest("MainMenu: New Game navigates to NewGamePanel via ancestor frame", () ->
                SwingUtilities.invokeAndWait(() -> {
                    JFrame testFrame = new JFrame();
                    MainMenu menu = new MainMenu();
                    testFrame.setContentPane(menu);
                    testFrame.pack();

                    AbstractButton newGameBtn = findButton(menu, "New Game");
                    checkNotNull(newGameBtn, "Must find the New Game button");
                    newGameBtn.doClick();

                    check(testFrame.getContentPane() instanceof NewGamePanel,
                            "Clicking 'New Game' must replace the content pane with NewGamePanel");
                    testFrame.dispose();
                }));

        // =================================================================
        System.out.println("\n-- NewGamePanel -------------------------------------------------");
        // =================================================================

        test("NewGamePanel: shows player name fields defaulting to White/Black", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    List<JTextField> fields = findAllTextFields(p);
                    // First two text fields are the name fields (custom min/sec follow)
                    check(fields.size() >= 2, "Must have at least 2 text fields for names");
                    checkEqual("White", fields.get(0).getText(), "white name field default");
                    checkEqual("Black", fields.get(1).getText(), "black name field default");
                }));

        test("NewGamePanel: custom time fields default to 10 min / 0 sec", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    List<JTextField> fields = findAllTextFields(p);
                    check(fields.size() >= 4, "Must have min/sec custom fields");
                    checkEqual("10", fields.get(2).getText(), "custom minutes default");
                    checkEqual("0", fields.get(3).getText(), "custom seconds default");
                }));

        test("NewGamePanel: all preset buttons are present", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    String[] expectedPresets = {
                            "Unlimited", "Bullet 1+0", "Bullet 2+1", "Blitz 3+0",
                            "Blitz 5+0", "Rapid 10+0", "Rapid 15+10", "Classical 30+0"
                    };
                    for (String preset : expectedPresets) {
                        check(hasButton(p, preset), "Must have preset button: " + preset);
                    }
                }));

        test("NewGamePanel: Rapid 10+0 is selected by default", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    AbstractButton rapidBtn = findButton(p, "Rapid 10+0");
                    checkNotNull(rapidBtn, "Rapid 10+0 button must exist");
                    check(rapidBtn.isSelected(), "Rapid 10+0 must be selected by default");
                }));

        test("NewGamePanel: selecting a different preset deselects the previous one", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    AbstractButton rapidBtn = findButton(p, "Rapid 10+0");
                    AbstractButton blitzBtn = findButton(p, "Blitz 5+0");
                    blitzBtn.doClick();
                    check(blitzBtn.isSelected(), "Blitz 5+0 must become selected after clicking");
                    check(!rapidBtn.isSelected(), "Rapid 10+0 must be deselected (ButtonGroup enforces exclusivity)");
                }));

        test("NewGamePanel: Back and Start buttons are present", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    check(hasButton(p, "back"), "Must have a Back button");
                    check(hasButton(p, "start"), "Must have a Start button");
                }));

        test("NewGamePanel: presets with an increment pass it on to the game", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    findButton(p, "Bullet 2+1").doClick();
                    checkEqual(1_000L, p.createConfig().incrementMs(), "Bullet 2+1 must add one second per move");
                    findButton(p, "Rapid 15+10").doClick();
                    checkEqual(10_000L, p.createConfig().incrementMs(), "Rapid 15+10 must add ten seconds per move");
                    findButton(p, "Blitz 5+0").doClick();
                    checkEqual(0L, p.createConfig().incrementMs(), "Blitz 5+0 has no increment");
                }));

        test("NewGamePanel: an untouched screen starts the preselected Rapid 10+0", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = new NewGamePanel().createConfig();
                    checkEqual("Rapid 10+0", cfg.timeLabel(), "the label must match the preselected preset");
                    checkEqual(600_000L, cfg.whiteTimeMs(), "White must get ten minutes");
                    checkEqual(600_000L, cfg.blackTimeMs(), "Black must get ten minutes");
                }));

        test("NewGamePanel: a custom time counts without pressing Enter", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    findButton(p, "Custom:").doClick();
                    List<JTextField> fields = findAllTextFields(p);
                    // the minute and second fields follow the two name fields
                    fields.get(2).setText("5");
                    fields.get(3).setText("30");

                    GameConfig cfg = p.createConfig();
                    checkNotNull(cfg, "a valid custom time must produce a configuration");
                    checkEqual(330_000L, cfg.whiteTimeMs(), "White must get five and a half minutes");
                    checkEqual(330_000L, cfg.blackTimeMs(), "Black must get five and a half minutes");
                    checkEqual("Custom 5:30", cfg.timeLabel(), "the label must show minutes and seconds");
                }));

        test("NewGamePanel: an invalid custom time is refused with a message", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    findButton(p, "Custom:").doClick();
                    findAllTextFields(p).get(2).setText("-3");

                    check(p.createConfig() == null, "a negative time must not produce a configuration");
                    boolean explained = findAllLabels(p).stream()
                            .anyMatch(l -> l.getText() != null && l.getText().contains("minutes"));
                    check(explained, "the reason must be shown on the screen");
                }));

        test("NewGamePanel: an untouched screen keeps the mode its time control implies", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    checkEqual(ClockMode.SUDDEN_DEATH, p.createConfig().clockMode(),
                            "Rapid 10+0 has no increment, so it is played as sudden death");

                    findButton(p, "Bullet 2+1").doClick();
                    checkEqual(ClockMode.FISCHER, p.createConfig().clockMode(),
                            "a preset with an increment is a Fischer clock");
                }));

        test("NewGamePanel: picking Bronstein trades the increment for a delay", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    // a preset that does have an increment, to prove the mode wins over it
                    findButton(p, "Rapid 15+10").doClick();
                    findButton(p, "Bronstein").doClick();
                    // the delay field sits behind the two names and the two custom time fields
                    findAllTextFields(p).get(4).setText("5");

                    GameConfig cfg = p.createConfig();
                    checkEqual(ClockMode.BRONSTEIN, cfg.clockMode(), "the mode the player picked must win");
                    checkEqual(5_000L, cfg.delayMs(), "the delay is read from the delay field");
                    checkEqual(0L, cfg.incrementMs(), "a Bronstein clock pays no increment");
                }));

        test("NewGamePanel: picking Delay reads the same field", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    findButton(p, "Delay").doClick();
                    findAllTextFields(p).get(4).setText("3");

                    GameConfig cfg = p.createConfig();
                    checkEqual(ClockMode.SIMPLE_DELAY, cfg.clockMode(), "the delay mode must reach the game");
                    checkEqual(3_000L, cfg.delayMs(), "with the seconds that were typed");
                }));

        test("NewGamePanel: Black can start with a time of their own", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    // the odds field is the last one on the screen
                    findAllTextFields(p).get(5).setText("3");

                    GameConfig cfg = p.createConfig();
                    checkEqual(600_000L, cfg.whiteTimeMs(), "White keeps the time of the preset");
                    checkEqual(180_000L, cfg.blackTimeMs(), "Black gets the time from the odds field");
                }));

        // =================================================================
        System.out.println("\n-- PastGamesPanel -----------------------------------------------");
        // =================================================================

        test("PastGamesPanel: constructs without throwing and shows a game list", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PastGamesPanel p = new PastGamesPanel();
                    JList<String> list = findList(p);
                    checkNotNull(list, "PastGamesPanel must contain a JList");
                }));

        test("PastGamesPanel: shows Move Log and Replay toggle buttons", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PastGamesPanel p = new PastGamesPanel();
                    check(hasButton(p, "Move Log"), "Must have a 'Move Log' toggle button");
                    check(hasButton(p, "replay"), "Must have a 'Replay' toggle button");
                }));

        test("PastGamesPanel: shows Back to Menu button", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PastGamesPanel p = new PastGamesPanel();
                    check(hasButton(p, "backToMenu"), "Must have a Back to Menu button");
                }));

        test("PastGamesPanel: a saved game appears in the list", () -> {
            String uniqueWhite = "PanelTestWhite" + System.nanoTime();
            GameRecord record = new GameRecord(uniqueWhite, "PanelTestBlack", "1-0",
                    "2026.01.01", "Blitz 5+0", List.of("e4", "e5"), List.of("fenA", "fenB"));
            PgnManager.save(record);

            SwingUtilities.invokeAndWait(() -> {
                PastGamesPanel p = new PastGamesPanel();
                JList<String> list = findList(p);
                checkNotNull(list, "Must find the game list");

                boolean found = false;
                for (int i = 0; i < list.getModel().getSize(); i++) {
                    if (list.getModel().getElementAt(i).contains(uniqueWhite)) {
                        found = true;
                        break;
                    }
                }
                check(found, "Saved game with white=" + uniqueWhite + " must appear in the list");
            });

            cleanupSavedGame(uniqueWhite);
        });

        test("PastGamesPanel: searching narrows the list down to the matching games", () -> {
            String white = "SearchWhite" + System.nanoTime();
            PgnManager.save(new GameRecord(white, "SearchBlack", "1-0", "2026.01.01", "Blitz 5+0",
                    List.of("e4"), List.of("fen1")));

            SwingUtilities.invokeAndWait(() -> {
                PastGamesPanel p = new PastGamesPanel();
                JList<String> list = findList(p);
                checkNotNull(list, "Must find the game list");
                Component field = findByName(p, "librarySearch");
                checkNotNull(field, "the library must have a search field");
                JTextField search = (JTextField) field;

                int everything = list.getModel().getSize();
                check(everything >= 1, "the library must list the game that was just saved");

                // typing filters straight away, there is nothing to confirm
                search.setText(white);
                checkEqual(1, list.getModel().getSize(), "only the searched game may be left");
                check(list.getModel().getElementAt(0).contains(white),
                        "the one left must be the searched game, got: " + list.getModel().getElementAt(0));

                search.setText("no game is ever called this");
                check(list.getModel().getElementAt(0).contains("No games match"),
                        "a search that finds nothing must say so, got: " + list.getModel().getElementAt(0));

                search.setText("");
                checkEqual(everything, list.getModel().getSize(),
                        "clearing the search must bring the whole library back");
            });

            cleanupSavedGame(white);
        });

        test("PastGamesPanel: renaming the selected game shows the new name in the list", () -> {
            String white = "PanelRenameWhite" + System.nanoTime();
            String name = "Sunday club final";
            PgnManager.save(new GameRecord(white, "PanelRenameBlack", "1-0", "2026.01.01", "Blitz 5+0",
                    List.of("e4"), List.of("fen1")));

            SwingUtilities.invokeAndWait(() -> {
                PastGamesPanel p = new PastGamesPanel();
                JTextField search = (JTextField) findByName(p, "librarySearch");
                search.setText(white);
                JList<String> list = findList(p);

                // answering in code instead of in a dialog, which a test run has nobody to click
                String[] asked = {null};
                p.setPrompts(new PastGamesPanel.LibraryPrompts() {
                    @Override
                    public boolean confirmDelete(String pTitle) {
                        check(false, "renaming must never ask about deleting");
                        return false;
                    }

                    @Override
                    public String askName(String pTitle, String pCurrentName) {
                        asked[0] = pCurrentName;
                        return name;
                    }

                    @Override
                    public void sayFailed(String pMessage) {
                        check(false, "nothing must fail here, got: " + pMessage);
                    }
                });

                check(!p.renameSelected(), "renaming with nothing selected must do nothing");
                list.setSelectedIndex(0);
                check(p.renameSelected(), "renaming must report success");
                checkEqual("", asked[0], "a game nobody named yet must offer an empty name");
                check(list.getModel().getElementAt(0).contains(name),
                        "the list must show the new name, got: " + list.getModel().getElementAt(0));
                check(list.getModel().getElementAt(0).contains(white),
                        "and must still show who played, got: " + list.getModel().getElementAt(0));
            });

            cleanupSavedGame(white);
        });

        test("PastGamesPanel: deleting asks first and then takes the game out of the library", () -> {
            String white = "PanelDeleteWhite" + System.nanoTime();
            PgnManager.save(new GameRecord(white, "PanelDeleteBlack", "1-0", "2026.01.01", "Blitz 5+0",
                    List.of("e4"), List.of("fen1")));

            SwingUtilities.invokeAndWait(() -> {
                PastGamesPanel p = new PastGamesPanel();
                JTextField search = (JTextField) findByName(p, "librarySearch");
                search.setText(white);
                JList<String> list = findList(p);
                list.setSelectedIndex(0);

                boolean[] answer = {false};
                p.setPrompts(new PastGamesPanel.LibraryPrompts() {
                    @Override
                    public boolean confirmDelete(String pTitle) {
                        check(pTitle.contains(white), "the question must name the game, got: " + pTitle);
                        return answer[0];
                    }

                    @Override
                    public String askName(String pTitle, String pCurrentName) {
                        check(false, "deleting must never ask for a name");
                        return null;
                    }

                    @Override
                    public void sayFailed(String pMessage) {
                        check(false, "nothing must fail here, got: " + pMessage);
                    }
                });

                // saying no has to leave the game exactly where it was
                check(!p.deleteSelected(), "a game must survive being declined");
                check(list.getModel().getElementAt(0).contains(white),
                        "the declined game must still be listed, got: " + list.getModel().getElementAt(0));

                answer[0] = true;
                check(p.deleteSelected(), "deleting must report success");
                for (int i = 0; i < list.getModel().getSize(); i++) {
                    check(!list.getModel().getElementAt(i).contains(white),
                            "the deleted game must be gone from the list, got: " + list.getModel().getElementAt(i));
                }
                check(PgnManager.loadLibrary().stream().noneMatch(g -> g.record.whiteName.equals(white)),
                        "the deleted game must be gone from the disk as well");
            });
        });

        test("PastGamesPanel: a game that shares its file is neither renamed nor deleted", () -> {
            String white = "PanelSharedWhite" + System.nanoTime();
            java.nio.file.Path file = PgnManager.getGamesDirectory().resolve("2026.01.01_" + white + ".pgn");
            Files.createDirectories(file.getParent());
            String pgn = "[White \"" + white + "\"]\n[Black \"First\"]\n[Result \"1-0\"]\n\n1. e4 1-0\n\n"
                    + "[White \"" + white + "\"]\n[Black \"Second\"]\n[Result \"0-1\"]\n\n1. d4 0-1\n";
            Files.writeString(file, pgn);

            SwingUtilities.invokeAndWait(() -> {
                PastGamesPanel p = new PastGamesPanel();
                JTextField search = (JTextField) findByName(p, "librarySearch");
                search.setText(white);
                JList<String> list = findList(p);
                list.setSelectedIndex(0);

                int[] refusals = {0};
                p.setPrompts(new PastGamesPanel.LibraryPrompts() {
                    @Override
                    public boolean confirmDelete(String pTitle) {
                        check(false, "a shared file must be refused before anybody is asked");
                        return true;
                    }

                    @Override
                    public String askName(String pTitle, String pCurrentName) {
                        check(false, "a shared file must be refused before anybody is asked");
                        return "Renamed";
                    }

                    @Override
                    public void sayFailed(String pMessage) {
                        refusals[0]++;
                    }
                });

                check(!p.renameSelected(), "renaming one game of a shared file must be refused");
                check(!p.deleteSelected(), "and so must deleting it");
                checkEqual(2, refusals[0], "the player must be told why, both times");
            });
            try {
                check(Files.readString(file).equals(pgn), "the shared file must be left exactly as it was");
            } finally {
                cleanupSavedGame(white);
            }
        });

        test("PastGamesPanel: selecting a game populates the move log", () -> {
            String uniqueWhite = "SelectTestWhite" + System.nanoTime();
            GameRecord record = new GameRecord(uniqueWhite, "SelectTestBlack", "0-1",
                    "2026.01.01", "Rapid 10+0", List.of("d4", "d5", "c4"), List.of("f1", "f2", "f3"));
            PgnManager.save(record);

            SwingUtilities.invokeAndWait(() -> {
                PastGamesPanel p = new PastGamesPanel();
                JList<String> list = findList(p);
                checkNotNull(list, "Must find the game list");

                int idx = -1;
                for (int i = 0; i < list.getModel().getSize(); i++) {
                    if (list.getModel().getElementAt(i).contains(uniqueWhite)) {
                        idx = i;
                        break;
                    }
                }
                check(idx >= 0, "Must locate the saved test game in the list");
                list.setSelectedIndex(idx);

                JTextArea log = findTextArea(p);
                checkNotNull(log, "Must find the move-log text area");
                check(log.getText().contains("d4"), "Move log must contain 'd4' after selection");
            });

            cleanupSavedGame(uniqueWhite);
        });

        // =================================================================
        System.out.println("\n-- Engine without a display -------------------------------------");
        // =================================================================

        test("StartPosition: builds the 32 pieces of a new game on their home squares", () -> {
            BoardState state = new BoardState();
            state.setPieces(StartPosition.create(state));

            checkEqual(32, state.getPieces().size(), "a new game starts with 32 pieces");
            checkEqual(PieceType.KING, state.getPiece(4, 7).getType(), "the white king stands on e1");
            checkEqual(PieceType.KING, state.getPiece(4, 0).getType(), "the black king stands on e8");
            check(state.getPiece(0, 6) instanceof Pawn, "a white pawn stands on a2");
            check(state.getPiece(0, 1) instanceof Pawn, "a black pawn stands on a7");
            check(state.getPiece(4, 4) == null, "the middle of the board starts empty");
        });

        test("GameController: plays a move without a board, a window or any Swing class", () -> {
            // counts what the rules ask of the screen, without being a screen
            int[] clockSwitches = {0};
            int[] repaints = {0};
            boolean[] handedTo = {true};
            GameView view = new GameView() {
                @Override
                public void switchClocks(boolean pWhiteToMove) {
                    clockSwitches[0]++;
                    handedTo[0] = pWhiteToMove;
                }

                @Override
                public void stopClocks() {
                }

                @Override
                public void resetClocks() {
                }

                @Override
                public void repaint() {
                    repaints[0]++;
                }
            };

            List<String> loggedMoves = new ArrayList<>();
            MoveLogView log = new MoveLogView() {
                @Override
                public void update(List<String> pMoveLog, String pCurrentFen) {
                    loggedMoves.clear();
                    loggedMoves.addAll(pMoveLog);
                }

                @Override
                public void clear() {
                    loggedMoves.clear();
                }
            };

            BoardState state = new BoardState();
            state.setPieces(StartPosition.create(state));
            GameController gc = new GameController(view, state, GameConfig.unlimited(),
                    w -> PieceType.QUEEN, noOpDrawResolver());
            gc.setMoveLogView(log);

            Move e2e4 = new Move(state, state.getPiece(4, 6), 4, 4);
            check(gc.isValidMove(e2e4), "e2-e4 must be legal in the starting position");
            gc.makeMove(e2e4);

            checkNotNull(state.getPiece(4, 4), "the pawn must stand on e4 after the move");
            check(state.getPiece(4, 6) == null, "e2 must be empty after the move");
            check(!gc.isTurnOfWhite(), "Black must be to move after White's first move");
            checkEqual(1, clockSwitches[0], "the rules must hand the clock over exactly once");
            check(!handedTo[0], "the clock must be handed to Black");
            check(repaints[0] >= 1, "the rules must ask for a repaint after the move");
            checkEqual(1, loggedMoves.size(), "the move log must receive exactly one move");
            checkEqual("e4", loggedMoves.get(0), "the logged move must read e4");
        });

        // =================================================================
        System.out.println("\n-- GameController: actions ------------------------------------");
        // =================================================================

        test("Board: a new game on the board starts with White to move", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    check(board.getSession().isWhiteToMove(), "White must move first");
                }));

        test("GameController: flagFall(true) reports Black wins on time (0-1)", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = new GameConfig("Alice", "Bob", 100, 100, "Bullet");
                    GameController gc = new GameController(noOpGameView(), newBoardState(), cfg,
                            w -> PieceType.QUEEN, noOpDrawResolver());
                    String[] captured = {null, null};

                    gc.setGameEndListener((record, message) -> {
                        captured[0] = record.result;
                        captured[1] = message;
                    });

                    gc.flagFall(true); // White's time expired
                    checkEqual("0-1", captured[0], "result when White flags");
                    check(captured[1].contains("Bob"), "message must name the winner (Bob)");
                    check(captured[1].contains("time"), "message must mention winning on time");
                }));

        test("GameController: flagFall(false) reports White wins on time (1-0)", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = new GameConfig("Alice", "Bob", 100, 100, "Bullet");
                    GameController gc = new GameController(noOpGameView(), newBoardState(), cfg,
                            w -> PieceType.QUEEN, noOpDrawResolver());
                    String[] captured = {null, null};

                    gc.setGameEndListener((record, message) -> {
                        captured[0] = record.result;
                        captured[1] = message;
                    });

                    gc.flagFall(false); // Black's time expired
                    checkEqual("1-0", captured[0], "result when Black flags");
                    check(captured[1].contains("Alice"), "message must name the winner (Alice)");
                }));

        test("GameController: flagFall stops both clocks", () -> {
            GameConfig cfg = new GameConfig("Alice", "Bob", 100, 100, "Bullet");
            // the rules only ask the view to stop the clocks, so counting that call is the check
            int[] stops = {0};
            GameView view = new GameView() {
                @Override
                public void switchClocks(boolean pWhiteToMove) {
                }

                @Override
                public void stopClocks() {
                    stops[0]++;
                }

                @Override
                public void resetClocks() {
                }

                @Override
                public void repaint() {
                }
            };

            GameController gc = new GameController(view, newBoardState(), cfg,
                    w -> PieceType.QUEEN, noOpDrawResolver());
            gc.setGameEndListener((record, message) -> {
            });
            gc.flagFall(true);
            checkEqual(1, stops[0], "a flag fall must stop the clocks exactly once");
        });

        test("GameController: endGame fires listener exactly once per call", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = new GameConfig("Alice", "Bob", 100, 100, "Bullet");
                    GameController gc = new GameController(noOpGameView(), newBoardState(), cfg,
                            w -> PieceType.QUEEN, noOpDrawResolver());
                    int[] callCount = {0};
                    gc.setGameEndListener((record, message) -> callCount[0]++);
                    gc.flagFall(true);
                    checkEqual(1, callCount[0], "listener must fire exactly once");
                }));

        test("GameController: a second end condition after the game is over is ignored", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = new GameConfig("Alice", "Bob", 100, 100, "Bullet");
                    GameController gc = new GameController(noOpGameView(), newBoardState(), cfg,
                            w -> PieceType.QUEEN, noOpDrawResolver());
                    List<String> results = new ArrayList<>();
                    gc.setGameEndListener((record, message) -> results.add(record.result));

                    gc.flagFall(true);  // White flags first
                    gc.flagFall(false); // a late second flag must not count
                    checkEqual(List.of("0-1"), results, "only the first result may be reported");
                }));

        test("Board: checkmate leaves both clocks stopped so no flag can fall later", () -> {
            GameConfig cfg = new GameConfig("Alice", "Bob", 400, 400, "Bullet");
            List<String> endReasons = new java.util.concurrent.CopyOnWriteArrayList<>();
            Board[] boardHolder = {null};

            SwingUtilities.invokeAndWait(() -> {
                Board board = new Board(cfg);
                GameSession session = board.getSession();
                session.setEndListener((pResult, pTermination) -> endReasons.add(pTermination.name()));

                // Fool's mate
                session.play(session.moveFor(Bitboards.squareOf("f2"), Bitboards.squareOf("f3")));
                session.play(session.moveFor(Bitboards.squareOf("e7"), Bitboards.squareOf("e5")));
                session.play(session.moveFor(Bitboards.squareOf("g2"), Bitboards.squareOf("g4")));
                session.play(session.moveFor(Bitboards.squareOf("d8"), Bitboards.squareOf("h4")));
                boardHolder[0] = board;
            });

            check(!boardHolder[0].areClocksRunning(), "no clock may run once the game is over");
            // wait longer than the whole clock, a restarted clock would have flagged by now
            Thread.sleep(1_000);
            checkEqual(1, endReasons.size(), "only the checkmate may end the game, got: " + endReasons);
            checkEqual(Termination.CHECKMATE.name(), endReasons.get(0), "the single result must be the checkmate");
        });

        test("GameController: no move is accepted after the game is over", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    gc.setGameEndListener((record, message) -> {
                    });
                    gc.flagFall(true); // the game ends on time

                    Piece pawn = state.getPiece(4, 6);
                    Move m = new Move(state, pawn, 4, 4); // e2-e4
                    check(!gc.isValidMove(m), "e2-e4 must be rejected once the game is over");
                    gc.makeMove(m); // a direct call must be ignored as well
                    checkEqual(6, pawn.getRow(), "the pawn must stay on e2");
                    check(gc.getMoveLog().isEmpty(), "no move may be recorded after the game ended");
                }));

        test("GameController: FEN full-move number grows after every Black move", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    GameRecord[] finished = {null};
                    gc.setGameEndListener((record, message) -> finished[0] = record);

                    gc.makeMove(new Move(state, state.getPiece(6, 7), 5, 5)); // 1. Nf3
                    gc.makeMove(new Move(state, state.getPiece(4, 1), 4, 3)); // 1... e5
                    gc.makeMove(new Move(state, state.getPiece(3, 6), 3, 4)); // 2. d4
                    gc.makeMove(new Move(state, state.getPiece(1, 0), 2, 2)); // 2... Nc6
                    gc.makeMove(new Move(state, state.getPiece(1, 7), 3, 6)); // 3. Nbd2
                    gc.flagFall(false); // ending the game hands over the recorded history

                    List<String> expected = List.of(
                            "rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1",
                            "rnbqkbnr/pppp1ppp/8/4p3/8/5N2/PPPPPPPP/RNBQKB1R w KQkq e6 0 2",
                            "rnbqkbnr/pppp1ppp/8/4p3/3P4/5N2/PPP1PPPP/RNBQKB1R b KQkq d3 0 2",
                            "r1bqkbnr/pppp1ppp/2n5/4p3/3P4/5N2/PPP1PPPP/RNBQKB1R w KQkq - 1 3",
                            "r1bqkbnr/pppp1ppp/2n5/4p3/3P4/5N2/PPPNPPPP/R1BQKB1R b KQkq - 2 3");
                    checkEqual(expected, finished[0].fenHistory, "every recorded FEN must carry the right counters");
                }));

        test("GameController: SAN marks a check with +", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

                    gc.makeMove(new Move(state, state.getPiece(4, 6), 4, 4)); // 1. e4
                    gc.makeMove(new Move(state, state.getPiece(5, 1), 5, 3)); // 1... f5
                    gc.makeMove(new Move(state, state.getPiece(3, 7), 7, 3)); // 2. Qh5+
                    checkEqual(List.of("e4", "f5", "Qh5+"), gc.getMoveLog(), "a checking move must end with +");
                }));

        test("GameController: SAN marks checkmate with #", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

                    gc.makeMove(new Move(state, state.getPiece(5, 6), 5, 5)); // 1. f3
                    gc.makeMove(new Move(state, state.getPiece(4, 1), 4, 3)); // 1... e5
                    gc.makeMove(new Move(state, state.getPiece(6, 6), 6, 4)); // 2. g4
                    gc.makeMove(new Move(state, state.getPiece(3, 0), 7, 4)); // 2... Qh4#
                    checkEqual(List.of("f3", "e5", "g4", "Qh4#"), gc.getMoveLog(), "the mating move must end with #");
                }));

        test("GameController: SAN adds the origin file when two knights can reach the square", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

                    gc.makeMove(new Move(state, state.getPiece(6, 7), 5, 5)); // 1. Nf3
                    gc.makeMove(new Move(state, state.getPiece(4, 1), 4, 3)); // 1... e5
                    gc.makeMove(new Move(state, state.getPiece(3, 6), 3, 4)); // 2. d4
                    gc.makeMove(new Move(state, state.getPiece(1, 0), 2, 2)); // 2... Nc6
                    gc.makeMove(new Move(state, state.getPiece(1, 7), 3, 6)); // 3. Nbd2, Nf3 could go to d2 too
                    checkEqual("Nbd2", gc.getMoveLog().get(4), "knights on different files are told apart by file");
                }));

        test("GameController: SAN adds the origin rank when the rivals share the file", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();

                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece lowerRook = new Rook(state, 0, 7, true); // a1
                    custom.add(lowerRook);
                    custom.add(new Rook(state, 0, 3, true));       // a5
                    custom.add(new King(state, 7, 7, true));       // h1
                    custom.add(new King(state, 7, 0, false));      // h8
                    state.setPieces(custom);

                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    gc.makeMove(new Move(state, lowerRook, 0, 5)); // Ra1-a3, the a5 rook could go there too
                    checkEqual("R1a3", gc.getMoveLog().get(0), "rooks on one file are told apart by rank");
                }));

        test("GameController: SAN adds file and rank when neither alone is unique", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();

                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece movingQueen = new Queen(state, 0, 7, true); // a1
                    custom.add(movingQueen);
                    custom.add(new Queen(state, 2, 7, true));  // c1, same rank
                    custom.add(new Queen(state, 0, 5, true));  // a3, same file
                    custom.add(new King(state, 7, 7, true));   // h1
                    custom.add(new King(state, 3, 0, false));  // d8
                    state.setPieces(custom);

                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    gc.makeMove(new Move(state, movingQueen, 1, 6)); // Qa1-b2, c1 and a3 could go there too
                    checkEqual("Qa1b2", gc.getMoveLog().get(0), "file and rank are both needed when each is shared");
                }));

        test("GameController: threefold repetition can be claimed", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    FakeDrawOfferResolver resolver = new FakeDrawOfferResolver(true);
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, resolver);
                    String[] ending = {null};
                    gc.setGameEndListener((record, message) -> ending[0] = record.result + " " + message);

                    // knights out and back twice bring the start position back for the third time
                    for (int round = 0; round < 2; round++) {
                        gc.makeMove(new Move(state, state.getPiece(6, 7), 5, 5)); // Ng1-f3
                        gc.makeMove(new Move(state, state.getPiece(6, 0), 5, 2)); // Ng8-f6
                        gc.makeMove(new Move(state, state.getPiece(5, 5), 6, 7)); // Nf3-g1
                        gc.makeMove(new Move(state, state.getPiece(5, 2), 6, 0)); // Nf6-g8
                    }

                    check(resolver.offerDrawCalled, "the third occurrence must offer a draw claim");
                    checkEqual(8, gc.getMoveLog().size(), "the claim must come right after the eighth half move");
                    checkNotNull(ending[0], "accepting the claim must end the game");
                    check(ending[0].startsWith("1/2-1/2") && ending[0].contains("Threefold"),
                            "the game must end as a threefold repetition draw, got: " + ending[0]);
                }));

        test("GameController: fivefold repetition ends the game automatically", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    FakeDrawOfferResolver resolver = new FakeDrawOfferResolver(false); // every claim is declined
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, resolver);
                    String[] ending = {null};
                    gc.setGameEndListener((record, message) -> ending[0] = record.result + " " + message);

                    // four rounds bring the start position back for the fifth time
                    for (int round = 0; round < 4; round++) {
                        gc.makeMove(new Move(state, state.getPiece(6, 7), 5, 5)); // Ng1-f3
                        gc.makeMove(new Move(state, state.getPiece(6, 0), 5, 2)); // Ng8-f6
                        gc.makeMove(new Move(state, state.getPiece(5, 5), 6, 7)); // Nf3-g1
                        gc.makeMove(new Move(state, state.getPiece(5, 2), 6, 0)); // Nf6-g8
                    }

                    checkEqual(16, gc.getMoveLog().size(), "all sixteen half moves must be played");
                    checkNotNull(ending[0], "the fifth occurrence must end the game without a claim");
                    check(ending[0].startsWith("1/2-1/2") && ending[0].contains("Fivefold"),
                            "the game must end as a fivefold repetition draw, got: " + ending[0]);
                }));

        test("GameController: an en passant square nobody can use does not hide a repetition", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    FakeDrawOfferResolver resolver = new FakeDrawOfferResolver(true);
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, resolver);
                    String[] ending = {null};
                    gc.setGameEndListener((record, message) -> ending[0] = record.result + " " + message);

                    // after 1.e4 the FEN lists e3, but no black pawn can take there
                    gc.makeMove(new Move(state, state.getPiece(4, 6), 4, 4)); // 1. e4
                    for (int round = 0; round < 2; round++) {
                        gc.makeMove(new Move(state, state.getPiece(6, 0), 5, 2)); // Ng8-f6
                        gc.makeMove(new Move(state, state.getPiece(6, 7), 5, 5)); // Ng1-f3
                        gc.makeMove(new Move(state, state.getPiece(5, 2), 6, 0)); // Nf6-g8
                        gc.makeMove(new Move(state, state.getPiece(5, 5), 6, 7)); // Nf3-g1
                    }

                    checkEqual(9, gc.getMoveLog().size(),
                            "the position after 1.e4 occurs for the third time after nine half moves");
                    checkNotNull(ending[0], "accepting the claim must end the game");
                    check(ending[0].contains("Threefold"), "the game must end by threefold repetition, got: " + ending[0]);
                }));

        test("GameController: king against king is an immediate draw", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(state, 4, 7, true);   // e1
                    custom.add(whiteKing);
                    custom.add(new King(state, 4, 0, false));         // e8
                    custom.add(new Knight(state, 3, 6, false));       // d2, the last piece besides the kings
                    state.setPieces(custom);

                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    String[] ending = {null};
                    gc.setGameEndListener((record, message) -> ending[0] = record.result + " " + message);

                    gc.makeMove(new Move(state, whiteKing, 3, 6)); // Kxd2 leaves two bare kings
                    checkNotNull(ending[0], "two bare kings must end the game");
                    check(ending[0].startsWith("1/2-1/2") && ending[0].contains("Insufficient material"),
                            "the game must end as a draw by insufficient material, got: " + ending[0]);
                }));

        test("GameController: king and knight against king is a draw", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKnight = new Knight(state, 1, 7, true); // b1
                    custom.add(new King(state, 4, 7, true));           // e1
                    custom.add(whiteKnight);
                    custom.add(new King(state, 4, 0, false));          // e8
                    state.setPieces(custom);

                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    String[] ending = {null};
                    gc.setGameEndListener((record, message) -> ending[0] = record.result + " " + message);

                    gc.makeMove(new Move(state, whiteKnight, 2, 5)); // Nc3
                    checkNotNull(ending[0], "a lone knight can never mate, so the game must end");
                    check(ending[0].startsWith("1/2-1/2") && ending[0].contains("Insufficient material"),
                            "the game must end as a draw by insufficient material, got: " + ending[0]);
                }));

        test("GameController: bishops on the same colour can't mate, so the game is drawn", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(state, 4, 7, true);   // e1
                    custom.add(whiteKing);
                    custom.add(new Bishop(state, 2, 7, true));        // c1, dark square
                    custom.add(new King(state, 4, 0, false));         // e8
                    custom.add(new Bishop(state, 5, 0, false));       // f8, dark square as well
                    state.setPieces(custom);

                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    String[] ending = {null};
                    gc.setGameEndListener((record, message) -> ending[0] = record.result + " " + message);

                    gc.makeMove(new Move(state, whiteKing, 4, 6)); // Ke2
                    checkNotNull(ending[0], "same coloured bishops can never mate, so the game must end");
                    check(ending[0].startsWith("1/2-1/2") && ending[0].contains("Insufficient material"),
                            "the game must end as a draw by insufficient material, got: " + ending[0]);
                }));

        test("GameController: bishops on opposite colours keep the game going", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(state, 4, 7, true);   // e1
                    custom.add(whiteKing);
                    custom.add(new Bishop(state, 2, 7, true));        // c1, dark square
                    custom.add(new King(state, 4, 0, false));         // e8
                    custom.add(new Bishop(state, 2, 0, false));       // c8, light square
                    state.setPieces(custom);

                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    String[] ending = {null};
                    gc.setGameEndListener((record, message) -> ending[0] = record.result + " " + message);

                    gc.makeMove(new Move(state, whiteKing, 4, 6)); // Ke2
                    check(ending[0] == null, "opposite coloured bishops can still mate, got: " + ending[0]);
                }));

        test("GameController: running out of time against a lone king is a draw", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    custom.add(new King(state, 4, 7, true));   // e1
                    custom.add(new Queen(state, 3, 7, true));  // d1
                    custom.add(new King(state, 4, 0, false));  // e8, Black has nothing else
                    state.setPieces(custom);

                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    String[] ending = {null};
                    gc.setGameEndListener((record, message) -> ending[0] = record.result + " " + message);

                    gc.flagFall(true); // White's clock runs out
                    checkNotNull(ending[0], "a flag fall always ends the game");
                    check(ending[0].startsWith("1/2-1/2"), "a lone king can't win on time, got: " + ending[0]);
                }));

        test("GameController: the 50-move claim is offered once to each player, not after every move", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    custom.add(new King(state, 0, 7, true));   // a1, start of the white king tour
                    custom.add(new King(state, 5, 0, false));  // f8, start of the black king tour
                    custom.add(new Pawn(state, 0, 4, true));   // a4, blocked by a5
                    custom.add(new Pawn(state, 0, 3, false));  // a5
                    state.setPieces(custom);

                    FakeDrawOfferResolver resolver = new FakeDrawOfferResolver(false); // both players decline
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, resolver);
                    shuffleKings(gc, state, 120);

                    checkEqual(2, resolver.offerDrawCount,
                            "White and Black must each be asked once, not after all 21 moves past the limit");
                }));

        // =================================================================
        System.out.println("\n-- GameController: rules engine --------------------------------");
        // =================================================================
        // GameController now depends on PromotionChooser/DrawOfferResolver
        // interfaces instead of creating PromoteGUI/FiftyRuleDraw directly, so
        // these scenarios are driven with fake, headless implementations -
        // no dialog-clicking Timer tricks needed for any of the tests below.
        // Each test builds its own position and its own GameController on a
        // view that draws nothing, so these rules run without a window at all
        // now that the board itself has moved on to the game session.

        test("GameController: makeMove executes a simple pawn push", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

                    Piece pawn = state.getPiece(4, 6); // e2
                    Move m = new Move(state, pawn, 4, 4); // e2-e4
                    check(gc.isValidMove(m), "e2-e4 must be a legal opening move");
                    gc.makeMove(m);

                    checkEqual(4, pawn.getCol(), "pawn column after move");
                    checkEqual(4, pawn.getRow(), "pawn row after move");
                    check(!gc.isTurnOfWhite(), "turn must pass to Black after White's move");
                    checkEqual(1, gc.getMoveLog().size(), "move log must record one move");
                    checkEqual("e4", gc.getMoveLog().get(0), "move must be recorded in algebraic notation");
                }));

        test("GameController: makeMove captures an enemy piece", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

                    gc.makeMove(new Move(state, state.getPiece(4, 6), 4, 4)); // e2-e4
                    gc.makeMove(new Move(state, state.getPiece(3, 1), 3, 3)); // d7-d5

                    Piece blackPawn = state.getPiece(3, 3);
                    Move capture = new Move(state, state.getPiece(4, 4), 3, 3); // exd5
                    check(gc.isValidMove(capture), "exd5 must be a legal capture");
                    checkEqual(blackPawn, capture.getCapture(), "capture must target the black pawn on d5");

                    gc.makeMove(capture);
                    check(!state.getPieces().contains(blackPawn), "captured pawn must be removed from the board");
                }));

        test("GameController: kingside castling moves both king and rook", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();

                    state.removePiece(state.getPiece(5, 7)); // clear f1 (bishop)
                    state.removePiece(state.getPiece(6, 7)); // clear g1 (knight)

                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    Piece king = state.getPiece(4, 7);
                    Piece rook = state.getPiece(7, 7);

                    Move castleMove = new Move(state, king, 6, 7);
                    check(gc.isValidMove(castleMove), "kingside castling must be legal with a clear path and no checks");
                    gc.makeMove(castleMove);

                    checkEqual(6, king.getCol(), "king must land on g1");
                    checkEqual(5, rook.getCol(), "rook must land on f1");
                    checkEqual("O-O", gc.getMoveLog().get(0), "castling must be recorded as O-O");
                }));

        test("GameController: en passant capture removes the passed pawn", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

                    gc.makeMove(new Move(state, state.getPiece(4, 6), 4, 4)); // e2-e4
                    gc.makeMove(new Move(state, state.getPiece(0, 1), 0, 2)); // a7-a6 (waiting move)
                    gc.makeMove(new Move(state, state.getPiece(4, 4), 4, 3)); // e4-e5
                    Piece blackPawn = state.getPiece(3, 1);
                    gc.makeMove(new Move(state, blackPawn, 3, 3)); // d7-d5, lands beside White's e5 pawn

                    Piece whitePawn = state.getPiece(4, 3);
                    Move enPassant = new Move(state, whitePawn, 3, 2); // exd6 en passant

                    // At construction, the destination square (d6) is empty, so Move
                    // resolves capture=null here - en passant capture is only attached
                    // once GameController.movePawn() commits the move. This reflects
                    // the current (unmodified) two-step capture resolution, not a bug
                    // introduced by this test.
                    check(enPassant.getCapture() == null,
                            "before commit, a Move to an empty square reports no capture yet");

                    check(gc.isValidMove(enPassant), "en passant capture must be legal immediately after the double step");
                    gc.makeMove(enPassant);

                    checkEqual(blackPawn, enPassant.getCapture(),
                            "after commit, the en-passant capture must be attached to the Move");
                    check(!state.getPieces().contains(blackPawn), "the passed pawn must be captured");
                    check(state.getPiece(3, 3) == null, "the passed pawn's original square must be empty");
                }));

        test("GameController: en passant that captures the checking pawn is legal, not checkmate", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();

                    // FEN 5r1k/8/2p5/3pP3/p3K3/P7/8/2br4 w - d6 0 2, Black just played d7-d5 with check
                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whitePawn = new Pawn(state, 4, 3, true);     // e5
                    Piece checkingPawn = new Pawn(state, 3, 3, false); // d5
                    custom.add(new King(state, 4, 4, true));           // e4
                    custom.add(whitePawn);
                    custom.add(new Pawn(state, 0, 5, true));           // a3, blocked by a4
                    custom.add(checkingPawn);
                    custom.add(new King(state, 7, 0, false));          // h8
                    custom.add(new Rook(state, 5, 0, false));          // f8 covers the f-file
                    custom.add(new Rook(state, 3, 7, false));          // d1 covers the d-file
                    custom.add(new Bishop(state, 2, 7, false));        // c1 covers e3
                    custom.add(new Pawn(state, 2, 2, false));          // c6 guards d5
                    custom.add(new Pawn(state, 0, 4, false));          // a4
                    for (Piece p : custom) if (p instanceof Pawn) p.setFirstMove(false);
                    state.setPieces(custom);
                    state.setEnPassantTile(state.getTileNum(3, 2));    // d6

                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    String[] endMessage = {null};
                    gc.setGameEndListener((record, msg) -> endMessage[0] = msg);

                    check(!gc.isCheckmate(true), "exd6 en passant removes the checking pawn, so White is not mated");
                    Move enPassant = new Move(state, whitePawn, 3, 2); // exd6
                    check(gc.isValidMove(enPassant), "exd6 en passant must be legal while in check from d5");

                    gc.makeMove(enPassant);
                    check(!state.getPieces().contains(checkingPawn), "the checking pawn must be captured");
                    check(!new CheckScanner(state).isKingInCheckRN(true), "White must be out of check after exd6");
                    check(endMessage[0] == null, "the game must go on after exd6");
                }));

        test("GameController: en passant that exposes the own king along the rank is illegal", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();

                    // FEN 4k3/8/8/KPp4r/8/7P/8/8 w - c6 0 2, Black just played c7-c5
                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whitePawn = new Pawn(state, 1, 3, true); // b5
                    custom.add(new King(state, 0, 3, true));        // a5
                    custom.add(whitePawn);
                    custom.add(new Pawn(state, 7, 5, true));        // h3
                    custom.add(new Pawn(state, 2, 3, false));       // c5
                    custom.add(new Rook(state, 7, 3, false));       // h5, same rank as the king
                    custom.add(new King(state, 4, 0, false));       // e8
                    for (Piece p : custom) if (p instanceof Pawn) p.setFirstMove(false);
                    state.setPieces(custom);
                    state.setEnPassantTile(state.getTileNum(2, 2)); // c6

                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    Move enPassant = new Move(state, whitePawn, 2, 2); // bxc6
                    check(!gc.isValidMove(enPassant),
                            "bxc6 en passant clears both pawns from the 5th rank and must be illegal");
                    check(state.getPiece(2, 3) != null, "the check simulation must put the c5 pawn back");
                }));

        test("GameController: pawn promotion asks the PromotionChooser and replaces the piece", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();

                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(state, 4, 7, true);
                    Piece blackKing = new King(state, 4, 0, false);
                    Piece whitePawn = new Pawn(state, 0, 1, true); // one step from promoting on a8
                    custom.add(whiteKing);
                    custom.add(blackKing);
                    custom.add(whitePawn);
                    state.setPieces(custom);

                    boolean[] askedWhite = {false};
                    GameController gc = new GameController(noOpGameView(), state, cfg,
                            white -> {
                                askedWhite[0] = white;
                                return PieceType.KNIGHT;
                            }, noOpDrawResolver());

                    Move promo = new Move(state, whitePawn, 0, 0);
                    check(gc.isValidMove(promo), "the promoting push must be a legal move");
                    gc.makeMove(promo);

                    check(askedWhite[0], "PromotionChooser must be asked with isWhite = true");
                    Piece onA8 = state.getPiece(0, 0);
                    checkNotNull(onA8, "a piece must occupy a8 after promotion");
                    check(onA8 instanceof Knight, "the promoted piece must be a Knight, matching the chooser's answer");
                    check(!state.getPieces().contains(whitePawn), "the original pawn must be removed from the board");
                    checkEqual("a8=N", gc.getMoveLog().get(0), "promotion must be recorded with the '=N' suffix");
                }));

        test("GameController: detects checkmate and fires the end-of-game listener", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();

                    // Ladder-mate final move: Rb1-b8#. Rook A already covers rank 7,
                    // Rook B slides onto rank 8 and the Black king has no escape square.
                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(state, 0, 7, true);   // a1
                    Piece blackKing = new King(state, 7, 0, false); // h8
                    Piece rookA = new Rook(state, 0, 1, true);      // a7
                    Piece rookB = new Rook(state, 1, 7, true);      // b1
                    custom.add(whiteKing);
                    custom.add(blackKing);
                    custom.add(rookA);
                    custom.add(rookB);
                    state.setPieces(custom);

                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    String[] result = {null};
                    String[] message = {null};
                    gc.setGameEndListener((record, msg) -> {
                        result[0] = record.result;
                        message[0] = msg;
                    });

                    Move mate = new Move(state, rookB, 1, 0); // Rb1-b8#
                    check(gc.isValidMove(mate), "Rb1-b8 must be a legal move");
                    gc.makeMove(mate);

                    checkEqual("1-0", result[0], "White delivering checkmate must record a 1-0 result");
                    check(message[0].contains("checkmate"), "end-of-game message must mention checkmate");
                    check(gc.isCheckmate(false), "Black must now be in checkmate");
                }));

        test("GameController: detects stalemate (no legal moves, king not in check)", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();

                    // Textbook queen stalemate final move: Qg5-g6.
                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(state, 5, 1, true);   // f7
                    Piece blackKing = new King(state, 7, 0, false); // h8
                    Piece whiteQueen = new Queen(state, 6, 3, true); // g5
                    custom.add(whiteKing);
                    custom.add(blackKing);
                    custom.add(whiteQueen);
                    state.setPieces(custom);

                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    String[] result = {null};
                    gc.setGameEndListener((record, msg) -> result[0] = record.result);

                    Move stalemateMove = new Move(state, whiteQueen, 6, 2); // Qg5-g6
                    check(gc.isValidMove(stalemateMove), "Qg5-g6 must be a legal move");
                    gc.makeMove(stalemateMove);

                    checkEqual("1/2-1/2", result[0], "stalemate must be recorded as a 1/2-1/2 draw");
                    check(!gc.isCheckmate(false), "Black king must not be in check");
                    check(gc.isStalemate(false), "Black must have no legal moves");
                }));

        test("GameController: mate and stalemate checks don't depend on whose turn it is", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

                    // White is to move, but both sides have plenty of moves at the start
                    check(!gc.isStalemate(false), "Black can move in the starting position, so it isn't stalemated");
                    check(!gc.isCheckmate(false), "Black isn't mated in the starting position");
                    check(!gc.isStalemate(true), "White can move in the starting position");
                    check(!gc.isCheckmate(true), "White isn't mated in the starting position");
                }));

        test("GameController: a stalemated side is recognised even when it isn't its turn", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    custom.add(new King(state, 5, 1, true));    // f7
                    custom.add(new Queen(state, 6, 2, true));   // g6
                    custom.add(new King(state, 7, 0, false));   // h8, no legal move but not in check
                    state.setPieces(custom);

                    // a fresh controller has White to move
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    check(gc.isStalemate(false), "Black has no legal move and isn't in check");
                    check(!gc.isStalemate(true), "White still has moves");
                }));

        test("GameController: 50-move rule offers a draw at half-move 100; accepting ends the game", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    custom.add(new King(state, 0, 7, true));  // a1, start of the white king tour
                    custom.add(new King(state, 5, 0, false)); // f8, start of the black king tour
                    // two pawns blocking each other, so the material never counts as insufficient
                    custom.add(new Pawn(state, 0, 4, true));  // a4
                    custom.add(new Pawn(state, 0, 3, false)); // a5
                    state.setPieces(custom);

                    FakeDrawOfferResolver resolver = new FakeDrawOfferResolver(true);
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, resolver);
                    String[] result = {null};
                    gc.setGameEndListener((record, msg) -> result[0] = record.result);

                    shuffleKings(gc, state, 100);

                    check(resolver.offerDrawCalled, "the 50-move draw must be offered at half-move 100");
                    check(!resolver.forcedDrawNotified, "the 75-move forced draw must NOT fire yet");
                    checkEqual("1/2-1/2", result[0], "accepting the offer must end the game as a draw");
                }));

        test("GameController: 50-move rule offer can be declined, letting the game continue", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    custom.add(new King(state, 0, 7, true));  // a1, start of the white king tour
                    custom.add(new King(state, 5, 0, false)); // f8, start of the black king tour
                    // two pawns blocking each other, so the material never counts as insufficient
                    custom.add(new Pawn(state, 0, 4, true));  // a4
                    custom.add(new Pawn(state, 0, 3, false)); // a5
                    state.setPieces(custom);

                    FakeDrawOfferResolver resolver = new FakeDrawOfferResolver(false);
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, resolver);
                    String[] result = {null};
                    gc.setGameEndListener((record, msg) -> result[0] = record.result);

                    shuffleKings(gc, state, 100);

                    check(resolver.offerDrawCalled, "the 50-move draw must still be offered at half-move 100");
                    check(result[0] == null, "declining the offer must NOT end the game");
                }));

        test("GameController: 75-move rule forces a draw even if declined all along", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    custom.add(new King(state, 0, 7, true));  // a1, start of the white king tour
                    custom.add(new King(state, 5, 0, false)); // f8, start of the black king tour
                    // two pawns blocking each other, so the material never counts as insufficient
                    custom.add(new Pawn(state, 0, 4, true));  // a4
                    custom.add(new Pawn(state, 0, 3, false)); // a5
                    state.setPieces(custom);

                    FakeDrawOfferResolver resolver = new FakeDrawOfferResolver(false); // always decline
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, resolver);
                    String[] result = {null};
                    String[] message = {null};
                    gc.setGameEndListener((record, msg) -> {
                        result[0] = record.result;
                        message[0] = msg;
                    });

                    shuffleKings(gc, state, 150);

                    check(resolver.forcedDrawNotified, "the 75-move rule must fire regardless of prior declines");
                    checkEqual("1/2-1/2", result[0], "the 75-move rule must end the game as a draw");
                    check(message[0].contains("75-move"), "message must mention the 75-move rule");
                }));

        test("GameController: getMoveLog returns an unmodifiable view", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    BoardState state = newBoardState();
                    GameController gc = new GameController(noOpGameView(), state, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    gc.makeMove(new Move(state, state.getPiece(4, 6), 4, 4));

                    boolean threw = false;
                    try {
                        gc.getMoveLog().add("hack");
                    } catch (UnsupportedOperationException e) {
                        threw = true;
                    }
                    check(threw, "getMoveLog() must not allow external mutation of the recorded move history");
                }));

        // =================================================================
        System.out.println("\n-- Bitboard core ------------------------------------------------");
        // =================================================================

        test("Bitboards: square numbering runs from a1 to h8", () -> {
            checkEqual(0, Bitboards.squareOf("a1"), "a1 must be square 0");
            checkEqual(63, Bitboards.squareOf("h8"), "h8 must be square 63");
            checkEqual(28, Bitboards.squareOf("e4"), "e4 must be square 28");
            checkEqual("e4", Bitboards.nameOf(28), "square 28 must be named e4");
            checkEqual(4, Bitboards.fileOf(Bitboards.squareOf("e4")), "e4 stands on the e-file");
            checkEqual(3, Bitboards.rankOf(Bitboards.squareOf("e4")), "e4 stands on the fourth rank");
        });

        test("Bitboards: walking a set returns its squares from low to high", () -> {
            long set = Bitboards.bit(Bitboards.squareOf("a1")) | Bitboards.bit(Bitboards.squareOf("e4"))
                    | Bitboards.bit(Bitboards.squareOf("h8"));
            checkEqual(3, Bitboards.count(set), "the set holds three squares");
            checkEqual(Bitboards.squareOf("a1"), Bitboards.lowestSquare(set), "a1 is the lowest square");
            set = Bitboards.clearLowestSquare(set);
            checkEqual(Bitboards.squareOf("e4"), Bitboards.lowestSquare(set), "e4 follows a1");
            set = Bitboards.clearLowestSquare(set);
            checkEqual(Bitboards.squareOf("h8"), Bitboards.lowestSquare(set), "h8 is the last square");
            checkEqual(0, Bitboards.count(Bitboards.clearLowestSquare(set)), "the set is empty afterwards");
        });

        test("Moves: a packed move keeps its squares, flag and promotion piece", () -> {
            int quiet = Moves.encode(Bitboards.squareOf("e2"), Bitboards.squareOf("e4"));
            checkEqual(Bitboards.squareOf("e2"), Moves.from(quiet), "the move starts on e2");
            checkEqual(Bitboards.squareOf("e4"), Moves.to(quiet), "the move ends on e4");
            checkEqual(Moves.FLAG_NORMAL, Moves.flag(quiet), "a pawn push is an ordinary move");
            checkEqual("e2e4", Moves.toUci(quiet), "UCI spells the move as e2e4");

            int promotion = Moves.encodePromotion(Bitboards.squareOf("e7"), Bitboards.squareOf("e8"), Moves.PROMOTION_QUEEN);
            check(Moves.isPromotion(promotion), "the move must be marked as a promotion");
            checkEqual(Pieces.QUEEN, Moves.promotionType(promotion), "the pawn becomes a queen");
            checkEqual("e7e8q", Moves.toUci(promotion), "UCI spells a promotion with the piece letter");

            int castling = Moves.encodeCastling(Bitboards.squareOf("e1"), Bitboards.squareOf("g1"));
            check(Moves.isCastling(castling), "the move must be marked as castling");
            checkEqual("e1g1", Moves.toUci(castling), "UCI spells castling as the king move");

            int enPassant = Moves.encodeEnPassant(Bitboards.squareOf("e5"), Bitboards.squareOf("d6"));
            check(Moves.isEnPassant(enPassant), "the move must be marked as en passant");
        });

        test("Position: the starting position has the right pieces, rights and counters", () -> {
            Position position = Position.startPosition();

            checkEqual(32, Bitboards.count(position.occupancy()), "a new game has 32 pieces");
            checkEqual(16, Bitboards.count(position.occupancy(Pieces.WHITE)), "White owns 16 of them");
            checkEqual(8, Bitboards.count(position.pieces(Pieces.WHITE_PAWN)), "White has eight pawns");
            checkEqual(Pieces.WHITE_KING, position.pieceAt(Bitboards.squareOf("e1")), "the white king stands on e1");
            checkEqual(Pieces.BLACK_QUEEN, position.pieceAt(Bitboards.squareOf("d8")), "the black queen stands on d8");
            checkEqual(Pieces.NONE, position.pieceAt(Bitboards.squareOf("e4")), "the middle of the board is empty");
            checkEqual(Pieces.WHITE, position.sideToMove(), "White moves first");
            checkEqual(Position.ALL_CASTLING_RIGHTS, position.castlingRights(), "both sides may still castle both ways");
            checkEqual(Position.NO_EN_PASSANT, position.epSquare(), "nothing can be captured en passant yet");
            checkEqual(0, position.halfmoveClock(), "the fifty move counter starts at zero");
            checkEqual(1, position.fullmoveNumber(), "the game starts at move one");
            checkEqual(position.computeKey(), position.key(), "the key must match a full recount");
        });

        test("Position: making and taking back a move restores the position exactly", () -> {
            Position position = Position.startPosition();
            String before = position.toString();
            long keyBefore = position.key();

            int e2e4 = Moves.encode(Bitboards.squareOf("e2"), Bitboards.squareOf("e4"));
            position.makeMove(e2e4);
            check(!position.toString().equals(before), "the board must change when a move is played");
            checkEqual(Pieces.WHITE_PAWN, position.pieceAt(Bitboards.squareOf("e4")), "the pawn stands on e4");
            checkEqual(Pieces.NONE, position.pieceAt(Bitboards.squareOf("e2")), "e2 is empty now");
            checkEqual(Pieces.BLACK, position.sideToMove(), "Black is to move");
            checkEqual(1, position.ply(), "one move can be taken back");

            position.unmakeMove(e2e4);
            checkEqual(before, position.toString(), "the board must look exactly as before");
            checkEqual(keyBefore, position.key(), "the key must be the one from before the move");
            checkEqual(Pieces.WHITE, position.sideToMove(), "White is to move again");
            checkEqual(1, position.fullmoveNumber(), "the move number must be back at one");
            checkEqual(0, position.ply(), "nothing is left on the undo stack");
        });

        test("Position: the key stays correct over a sequence of moves", () -> {
            Position position = Position.startPosition();
            int[] line = {
                    Moves.encode(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")),
                    Moves.encode(Bitboards.squareOf("e7"), Bitboards.squareOf("e5")),
                    Moves.encode(Bitboards.squareOf("g1"), Bitboards.squareOf("f3")),
                    Moves.encode(Bitboards.squareOf("b8"), Bitboards.squareOf("c6")),
            };
            for (int move : line) {
                position.makeMove(move);
                checkEqual(position.computeKey(), position.key(),
                        "the incremental key must match a full recount after " + Moves.toUci(move));
            }
            for (int i = line.length - 1; i >= 0; i--) {
                position.unmakeMove(line[i]);
                checkEqual(position.computeKey(), position.key(),
                        "the key must stay correct while taking back " + Moves.toUci(line[i]));
            }
            checkEqual(Position.startPosition().key(), position.key(), "the start position must return");
        });

        test("Position: a double push only offers en passant when a pawn can take", () -> {
            // no black pawn stands next to e4, so there is nothing to capture
            Position quiet = Position.startPosition();
            quiet.makeMove(Moves.encode(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            checkEqual(Position.NO_EN_PASSANT, quiet.epSquare(), "an unusable en passant square must not be recorded");

            // black pawn on d4, so White's e2-e4 really can be answered by d4xe3
            Position offered = Position.empty();
            offered.put(Pieces.WHITE_KING, Bitboards.squareOf("e1"));
            offered.put(Pieces.BLACK_KING, Bitboards.squareOf("e8"));
            offered.put(Pieces.WHITE_PAWN, Bitboards.squareOf("e2"));
            offered.put(Pieces.BLACK_PAWN, Bitboards.squareOf("d4"));
            offered.makeMove(Moves.encode(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            checkEqual(Bitboards.squareOf("e3"), offered.epSquare(), "the skipped square must be e3");
            checkEqual(offered.computeKey(), offered.key(), "the en passant file belongs to the key");
        });

        test("Position: an en passant capture removes the passed pawn and puts it back", () -> {
            Position position = Position.empty();
            position.put(Pieces.WHITE_KING, Bitboards.squareOf("e1"));
            position.put(Pieces.BLACK_KING, Bitboards.squareOf("e8"));
            position.put(Pieces.WHITE_PAWN, Bitboards.squareOf("e5"));
            position.put(Pieces.BLACK_PAWN, Bitboards.squareOf("d7"));
            position.setSideToMove(Pieces.BLACK);

            position.makeMove(Moves.encode(Bitboards.squareOf("d7"), Bitboards.squareOf("d5")));
            checkEqual(Bitboards.squareOf("d6"), position.epSquare(), "the black pawn may be taken on d6");

            int capture = Moves.encodeEnPassant(Bitboards.squareOf("e5"), Bitboards.squareOf("d6"));
            position.makeMove(capture);
            checkEqual(Pieces.NONE, position.pieceAt(Bitboards.squareOf("d5")), "the captured pawn must leave the board");
            checkEqual(Pieces.WHITE_PAWN, position.pieceAt(Bitboards.squareOf("d6")), "the capturing pawn stands on d6");

            position.unmakeMove(capture);
            checkEqual(Pieces.BLACK_PAWN, position.pieceAt(Bitboards.squareOf("d5")), "the captured pawn must come back");
            checkEqual(Pieces.WHITE_PAWN, position.pieceAt(Bitboards.squareOf("e5")), "the capturing pawn returns to e5");
            checkEqual(position.computeKey(), position.key(), "the key must be correct again");
        });

        test("Position: promotion replaces the pawn and unmake brings it back", () -> {
            Position position = Position.empty();
            position.put(Pieces.WHITE_KING, Bitboards.squareOf("e1"));
            position.put(Pieces.BLACK_KING, Bitboards.squareOf("e8"));
            position.put(Pieces.WHITE_PAWN, Bitboards.squareOf("a7"));

            int promotion = Moves.encodePromotion(Bitboards.squareOf("a7"), Bitboards.squareOf("a8"), Moves.PROMOTION_KNIGHT);
            position.makeMove(promotion);
            checkEqual(Pieces.WHITE_KNIGHT, position.pieceAt(Bitboards.squareOf("a8")), "an underpromotion must give a knight");
            checkEqual(Pieces.NONE, position.pieceAt(Bitboards.squareOf("a7")), "the pawn has left a7");

            position.unmakeMove(promotion);
            checkEqual(Pieces.WHITE_PAWN, position.pieceAt(Bitboards.squareOf("a7")), "the pawn must stand on a7 again");
            checkEqual(Pieces.NONE, position.pieceAt(Bitboards.squareOf("a8")), "the knight must be gone");
            checkEqual(position.computeKey(), position.key(), "the key must be correct again");
        });

        test("Position: castling moves the rook as well and can be taken back", () -> {
            Position position = Position.empty();
            position.put(Pieces.WHITE_KING, Bitboards.squareOf("e1"));
            position.put(Pieces.WHITE_ROOK, Bitboards.squareOf("h1"));
            position.put(Pieces.BLACK_KING, Bitboards.squareOf("e8"));
            position.setCastlingRights(Position.WHITE_KINGSIDE);

            int castling = Moves.encodeCastling(Bitboards.squareOf("e1"), Bitboards.squareOf("g1"));
            position.makeMove(castling);
            checkEqual(Pieces.WHITE_KING, position.pieceAt(Bitboards.squareOf("g1")), "the king stands on g1");
            checkEqual(Pieces.WHITE_ROOK, position.pieceAt(Bitboards.squareOf("f1")), "the rook jumped to f1");
            checkEqual(0, position.castlingRights(), "a king that castled may not castle again");

            position.unmakeMove(castling);
            checkEqual(Pieces.WHITE_KING, position.pieceAt(Bitboards.squareOf("e1")), "the king returns to e1");
            checkEqual(Pieces.WHITE_ROOK, position.pieceAt(Bitboards.squareOf("h1")), "the rook returns to h1");
            checkEqual(Position.WHITE_KINGSIDE, position.castlingRights(), "the right comes back with the move");
            checkEqual(position.computeKey(), position.key(), "the key must be correct again");
        });

        test("Position: moving or losing a rook takes the matching castling right away", () -> {
            Position position = Position.startPosition();
            position.makeMove(Moves.encode(Bitboards.squareOf("a2"), Bitboards.squareOf("a4")));
            position.makeMove(Moves.encode(Bitboards.squareOf("a7"), Bitboards.squareOf("a5")));
            position.makeMove(Moves.encode(Bitboards.squareOf("a1"), Bitboards.squareOf("a3")));

            check((position.castlingRights() & Position.WHITE_QUEENSIDE) == 0,
                    "a rook that left a1 loses White's queenside right");
            check((position.castlingRights() & Position.WHITE_KINGSIDE) != 0,
                    "the kingside right is untouched");
            check((position.castlingRights() & Position.BLACK_QUEENSIDE) != 0,
                    "Black keeps both rights");
        });

        test("Position: the move counters follow the rules", () -> {
            Position position = Position.startPosition();

            position.makeMove(Moves.encode(Bitboards.squareOf("g1"), Bitboards.squareOf("f3")));
            checkEqual(1, position.halfmoveClock(), "a knight move raises the fifty move counter");
            checkEqual(1, position.fullmoveNumber(), "the move number only grows after Black moved");

            position.makeMove(Moves.encode(Bitboards.squareOf("g8"), Bitboards.squareOf("f6")));
            checkEqual(2, position.halfmoveClock(), "the counter keeps growing");
            checkEqual(2, position.fullmoveNumber(), "the second move begins after Black's reply");

            position.makeMove(Moves.encode(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            checkEqual(0, position.halfmoveClock(), "a pawn move starts the fifty move count over");
        });

        test("Position: a capture removes the piece and unmake puts it back", () -> {
            Position position = Position.empty();
            position.put(Pieces.WHITE_KING, Bitboards.squareOf("e1"));
            position.put(Pieces.BLACK_KING, Bitboards.squareOf("e8"));
            position.put(Pieces.WHITE_ROOK, Bitboards.squareOf("a1"));
            position.put(Pieces.BLACK_ROOK, Bitboards.squareOf("a8"));
            position.setHalfmoveClock(7);

            int capture = Moves.encode(Bitboards.squareOf("a1"), Bitboards.squareOf("a8"));
            position.makeMove(capture);
            checkEqual(Pieces.WHITE_ROOK, position.pieceAt(Bitboards.squareOf("a8")), "the white rook took on a8");
            checkEqual(0, position.halfmoveClock(), "a capture starts the fifty move count over");
            checkEqual(3, Bitboards.count(position.occupancy()), "only three pieces are left");

            position.unmakeMove(capture);
            checkEqual(Pieces.BLACK_ROOK, position.pieceAt(Bitboards.squareOf("a8")), "the black rook must come back");
            checkEqual(7, position.halfmoveClock(), "the fifty move counter comes back too");
            checkEqual(position.computeKey(), position.key(), "the key must be correct again");
        });

        // =================================================================
        System.out.println("\n-- Move generation: attacks -------------------------------------");
        // =================================================================

        test("MoveGen: a knight reaches two squares from a corner and eight from the centre", () -> {
            long fromA1 = MoveGen.knightAttacks(Bitboards.squareOf("a1"));
            checkEqual(2, Bitboards.count(fromA1), "a knight on a1 has two squares");
            check(Bitboards.contains(fromA1, Bitboards.squareOf("b3")), "a1 reaches b3");
            check(Bitboards.contains(fromA1, Bitboards.squareOf("c2")), "a1 reaches c2");

            checkEqual(8, Bitboards.count(MoveGen.knightAttacks(Bitboards.squareOf("d4"))),
                    "a knight in the centre has eight squares");
            checkEqual(2, Bitboards.count(MoveGen.knightAttacks(Bitboards.squareOf("h8"))),
                    "a knight on h8 has two squares");
        });

        test("MoveGen: a king reaches three squares from a corner and eight from the centre", () -> {
            long fromA1 = MoveGen.kingAttacks(Bitboards.squareOf("a1"));
            checkEqual(3, Bitboards.count(fromA1), "a king on a1 has three squares");
            check(Bitboards.contains(fromA1, Bitboards.squareOf("b2")), "a1 reaches b2");
            check(!Bitboards.contains(fromA1, Bitboards.squareOf("h1")), "a king must not wrap around the board");
            checkEqual(8, Bitboards.count(MoveGen.kingAttacks(Bitboards.squareOf("e4"))),
                    "a king in the centre has eight squares");
        });

        test("MoveGen: pawns attack diagonally forward and never wrap around the board", () -> {
            long white = MoveGen.pawnAttacks(Pieces.WHITE, Bitboards.squareOf("e4"));
            checkEqual(2, Bitboards.count(white), "a pawn on e4 attacks two squares");
            check(Bitboards.contains(white, Bitboards.squareOf("d5")), "e4 attacks d5");
            check(Bitboards.contains(white, Bitboards.squareOf("f5")), "e4 attacks f5");

            long edge = MoveGen.pawnAttacks(Pieces.WHITE, Bitboards.squareOf("a2"));
            checkEqual(1, Bitboards.count(edge), "a pawn on the a-file attacks one square");
            check(Bitboards.contains(edge, Bitboards.squareOf("b3")), "a2 attacks b3");

            long black = MoveGen.pawnAttacks(Pieces.BLACK, Bitboards.squareOf("h7"));
            checkEqual(1, Bitboards.count(black), "a black pawn on the h-file attacks one square");
            check(Bitboards.contains(black, Bitboards.squareOf("g6")), "h7 attacks g6");
        });

        test("MoveGen: a rook stops at the first piece and still attacks it", () -> {
            long occupancy = Bitboards.bit(Bitboards.squareOf("a1"))
                    | Bitboards.bit(Bitboards.squareOf("a4"))
                    | Bitboards.bit(Bitboards.squareOf("d1"));
            long attacks = MoveGen.rookAttacks(Bitboards.squareOf("a1"), occupancy);

            checkEqual(6, Bitboards.count(attacks), "the rook reaches six squares");
            check(Bitboards.contains(attacks, Bitboards.squareOf("a4")), "the blocking square can be captured");
            check(!Bitboards.contains(attacks, Bitboards.squareOf("a5")), "nothing behind the blocker is attacked");
            check(Bitboards.contains(attacks, Bitboards.squareOf("d1")), "the rook attacks along the rank as well");
            check(!Bitboards.contains(attacks, Bitboards.squareOf("e1")), "the rank stops at the blocker too");
        });

        test("MoveGen: a bishop stops on both diagonals", () -> {
            long occupancy = Bitboards.bit(Bitboards.squareOf("c1"))
                    | Bitboards.bit(Bitboards.squareOf("a3"))
                    | Bitboards.bit(Bitboards.squareOf("e3"));
            long attacks = MoveGen.bishopAttacks(Bitboards.squareOf("c1"), occupancy);

            checkEqual(4, Bitboards.count(attacks), "the bishop reaches four squares");
            check(Bitboards.contains(attacks, Bitboards.squareOf("b2")), "b2 lies on the way to a3");
            check(Bitboards.contains(attacks, Bitboards.squareOf("a3")), "the blocking square can be captured");
            check(Bitboards.contains(attacks, Bitboards.squareOf("e3")), "the other diagonal stops on e3");
            check(!Bitboards.contains(attacks, Bitboards.squareOf("f4")), "nothing behind the blocker is attacked");
        });

        test("MoveGen: a queen attacks everything a rook and a bishop would", () -> {
            long occupancy = Bitboards.bit(Bitboards.squareOf("d4"));
            int square = Bitboards.squareOf("d4");
            checkEqual(MoveGen.rookAttacks(square, occupancy) | MoveGen.bishopAttacks(square, occupancy),
                    MoveGen.queenAttacks(square, occupancy), "a queen is a rook and a bishop together");
            checkEqual(27, Bitboards.count(MoveGen.queenAttacks(square, occupancy)),
                    "a queen on d4 covers 27 squares on an empty board");
        });

        test("MoveGen: attacked squares are found in the starting position", () -> {
            Position position = Position.startPosition();

            check(MoveGen.isSquareAttacked(position, Bitboards.squareOf("e3"), Pieces.WHITE),
                    "the d2 and f2 pawns cover e3");
            check(MoveGen.isSquareAttacked(position, Bitboards.squareOf("a3"), Pieces.WHITE),
                    "the knight on b1 covers a3");
            check(!MoveGen.isSquareAttacked(position, Bitboards.squareOf("e6"), Pieces.WHITE),
                    "White reaches nothing on the sixth rank yet");
            check(MoveGen.isSquareAttacked(position, Bitboards.squareOf("e6"), Pieces.BLACK),
                    "the d7 and f7 pawns cover e6");
            check(!MoveGen.isInCheck(position, Pieces.WHITE), "nobody is in check at the start");
            check(!MoveGen.isInCheck(position, Pieces.BLACK), "nobody is in check at the start");
        });

        test("MoveGen: a rook on an open file gives check", () -> {
            Position position = Position.empty();
            position.put(Pieces.WHITE_KING, Bitboards.squareOf("e1"));
            position.put(Pieces.BLACK_KING, Bitboards.squareOf("a8"));
            position.put(Pieces.BLACK_ROOK, Bitboards.squareOf("e8"));

            check(MoveGen.isInCheck(position, Pieces.WHITE), "the rook on e8 checks the king on e1");
            check(!MoveGen.isInCheck(position, Pieces.BLACK), "Black is not in check");

            // a pawn in between takes the check away
            position.put(Pieces.WHITE_PAWN, Bitboards.squareOf("e4"));
            check(!MoveGen.isInCheck(position, Pieces.WHITE), "a piece in between blocks the check");
        });

        // =================================================================
        System.out.println("\n-- Move generation: legal moves and perft ------------------------");
        // =================================================================

        test("MoveGen: the starting position has twenty legal moves", () -> {
            int[] moves = new int[MoveGen.MAX_MOVES];
            checkEqual(20, MoveGen.generateLegal(Position.startPosition(), moves, 0),
                    "sixteen pawn moves and four knight moves");
        });

        test("MoveGen: a pinned piece may not step off the pin", () -> {
            // the knight on e2 shields the king on e1 from the rook on e8
            Position position = PerftTest.fromFen("4r2k/8/8/8/8/8/4N3/4K3 w - - 0 1");
            int[] moves = new int[MoveGen.MAX_MOVES];
            int count = MoveGen.generateLegal(position, moves, 0);

            for (int index = 0; index < count; index++) {
                check(Moves.from(moves[index]) != Bitboards.squareOf("e2"),
                        "the pinned knight must not move, got " + Moves.toUci(moves[index]));
            }
            checkEqual(4, count, "only the king may move, to d1, d2, f1 and f2");
        });

        test("MoveGen: a king may not castle across an attacked square", () -> {
            int[] moves = new int[MoveGen.MAX_MOVES];

            // the rook on f8 covers f1, the square the king would cross
            Position crossing = PerftTest.fromFen("5rk1/8/8/8/8/8/8/4K2R w K - 0 1");
            int count = MoveGen.generateLegal(crossing, moves, 0);
            for (int index = 0; index < count; index++) {
                check(!Moves.isCastling(moves[index]), "castling across an attacked square must not be offered");
            }

            // with nothing covering the way the same castling is fine
            Position allowed = PerftTest.fromFen("6k1/8/8/8/8/8/8/4K2R w K - 0 1");
            count = MoveGen.generateLegal(allowed, moves, 0);
            boolean castles = false;
            for (int index = 0; index < count; index++) {
                castles |= Moves.isCastling(moves[index]);
            }
            check(castles, "castling must be offered when nothing attacks the way");
        });

        test("MoveGen: an en passant capture is generated after a double push", () -> {
            Position position = PerftTest.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
            int[] moves = new int[MoveGen.MAX_MOVES];
            int count = MoveGen.generateLegal(position, moves, 0);

            boolean found = false;
            for (int index = 0; index < count; index++) {
                found |= Moves.isEnPassant(moves[index]) && "e5d6".equals(Moves.toUci(moves[index]));
            }
            check(found, "exd6 en passant must be among the legal moves");
        });

        test("Perft: the starting position matches its known node counts", () -> {
            Perft perft = new Perft();
            Position position = Position.startPosition();
            long keyBefore = position.key();

            checkEqual(20L, perft.count(position, 1), "twenty positions after one move");
            checkEqual(400L, perft.count(position, 2), "four hundred after two");
            checkEqual(8902L, perft.count(position, 3), "8902 after three");
            checkEqual(197281L, perft.count(position, 4), "197281 after four");
            checkEqual(keyBefore, position.key(), "counting must leave the position exactly as it was");
        });

        test("Perft: the four other standard positions match as well", () -> {
            Perft perft = new Perft();
            checkEqual(97862L, perft.count(PerftTest.fromFen(
                    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"), 3),
                    "kiwipete at depth three");
            checkEqual(43238L, perft.count(PerftTest.fromFen(
                    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"), 4),
                    "position three at depth four");
            checkEqual(9467L, perft.count(PerftTest.fromFen(
                    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"), 3),
                    "position four at depth three");
            checkEqual(62379L, perft.count(PerftTest.fromFen(
                    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 0 1"), 3),
                    "position five at depth three");
        });

        // =================================================================
        System.out.println("\n-- FEN codec ----------------------------------------------------");
        // =================================================================

        test("Fen: reading and writing a position gives the same text back", () -> {
            String[] fens = {
                    Fen.START_POSITION,
                    "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                    "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
                    "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
                    "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 0 1",
                    "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1",
            };
            for (String fen : fens) {
                checkEqual(fen, Fen.write(Fen.parse(fen)), "the codec must round trip this position");
            }
        });

        test("Fen: the starting position is written exactly as the standard spells it", () -> {
            checkEqual(Fen.START_POSITION, Fen.write(Position.startPosition()),
                    "the position the engine builds must write as the standard start FEN");
            checkEqual(Fen.write(Position.startPosition()), Fen.write(Fen.parse(Fen.START_POSITION)),
                    "reading the start FEN must give the same position the engine builds");
        });

        test("Fen: counters and castling rights survive the round trip", () -> {
            String fen = "r3k2r/8/8/8/8/8/8/R3K2R b Kq - 7 23";
            Position position = Fen.parse(fen);

            checkEqual(7, position.halfmoveClock(), "the half move clock is read");
            checkEqual(23, position.fullmoveNumber(), "the full move number is read");
            checkEqual(Position.WHITE_KINGSIDE | Position.BLACK_QUEENSIDE, position.castlingRights(),
                    "only the rights the FEN lists may survive");
            checkEqual(fen, Fen.write(position), "the text must come back unchanged");
        });

        test("Fen: an en passant square nobody can use is dropped", () -> {
            Position position = Fen.parse("4k3/8/8/3p4/8/8/8/4K3 w - d6 0 1");
            checkEqual(Position.NO_EN_PASSANT, position.epSquare(), "no white pawn can capture on d6");
            checkEqual("4k3/8/8/3p4/8/8/8/4K3 w - - 0 1", Fen.write(position),
                    "an unusable en passant square is written as a dash");
        });

        test("Fen: positions that cannot occur are refused with a reason", () -> {
            String[][] cases = {
                    {"8/8/8/8/8/8/8/8 w - - 0 1", "king"},
                    {"4k3/8/8/8/8/8/8/4K2K w - - 0 1", "king"},
                    {"4k2P/8/8/8/8/8/8/4K3 w - - 0 1", "pawn"},
                    {"4k3/8/8/8/8/8/8/4K3 w K - 0 1", "rook on h1"},
                    {"4k3/8/8/3pP3/8/8/8/4K3 w - d3 0 1", "rank 6"},
                    {"4k3/8/8/8/4R3/8/8/4K3 w - - 0 1", "not to move"},
                    {"4k3/8/8/8/8/8/8/4K4 w - - 0 1", "squares"},
                    {"4k3/8/8/8/8/8/8/4K3 w", "fields"},
            };
            for (String[] testCase : cases) {
                try {
                    Fen.parse(testCase[0]);
                    throw new AssertionError("this FEN must be refused: " + testCase[0]);
                } catch (IllegalArgumentException expected) {
                    check(expected.getMessage().toLowerCase().contains(testCase[1]),
                            "the message for " + testCase[0] + " should mention " + testCase[1]
                                    + ", got: " + expected.getMessage());
                }
            }
        });

        // =================================================================
        System.out.println("\n-- Algebraic notation from the core ------------------------------");
        // =================================================================

        test("San: plain moves get the piece letter, pawns get none", () -> {
            Position start = Position.startPosition();
            checkEqual("e4", San.of(start, Moves.encode(Bitboards.squareOf("e2"), Bitboards.squareOf("e4"))),
                    "a pawn push is written as the square alone");
            checkEqual("Nf3", San.of(start, Moves.encode(Bitboards.squareOf("g1"), Bitboards.squareOf("f3"))),
                    "a knight is written with an N");
            checkEqual(Fen.START_POSITION, Fen.write(start), "writing notation must not change the position");
        });

        test("San: a capturing pawn is named by the file it came from", () -> {
            Position position = Fen.parse("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2");
            checkEqual("exd5", San.of(position, Moves.encode(Bitboards.squareOf("e4"), Bitboards.squareOf("d5"))),
                    "a pawn capture names its own file");
        });

        test("San: identical pieces are told apart by file, or by rank when the file is shared", () -> {
            Position knights = Fen.parse("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1");
            checkEqual("Nbd2", San.of(knights, Moves.encode(Bitboards.squareOf("b1"), Bitboards.squareOf("d2"))),
                    "two knights reaching d2 are told apart by their file");

            Position rooks = Fen.parse("4k3/8/8/8/8/R7/8/R3K3 w - - 0 1");
            checkEqual("R1a2", San.of(rooks, Moves.encode(Bitboards.squareOf("a1"), Bitboards.squareOf("a2"))),
                    "two rooks on one file are told apart by their rank");
        });

        test("San: a promotion names the piece the pawn becomes", () -> {
            Position position = Fen.parse("8/P7/8/8/7k/8/8/4K3 w - - 0 1");
            checkEqual("a8=Q", San.of(position, Moves.encodePromotion(
                            Bitboards.squareOf("a7"), Bitboards.squareOf("a8"), Moves.PROMOTION_QUEEN)),
                    "a promotion to a queen");
            checkEqual("a8=N", San.of(position, Moves.encodePromotion(
                            Bitboards.squareOf("a7"), Bitboards.squareOf("a8"), Moves.PROMOTION_KNIGHT)),
                    "an underpromotion to a knight");
        });

        test("San: castling is written with the letter O", () -> {
            Position position = Fen.parse("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1");
            checkEqual("O-O", San.of(position, Moves.encodeCastling(
                    Bitboards.squareOf("e1"), Bitboards.squareOf("g1"))), "kingside castling");
            checkEqual("O-O-O", San.of(position, Moves.encodeCastling(
                    Bitboards.squareOf("e1"), Bitboards.squareOf("c1"))), "queenside castling");
        });

        test("San: check and mate are marked", () -> {
            Position check = Fen.parse("4k3/8/8/8/8/8/8/4KR2 w - - 0 1");
            checkEqual("Rf8+", San.of(check, Moves.encode(Bitboards.squareOf("f1"), Bitboards.squareOf("f8"))),
                    "a rook giving check gets a plus");

            Position mate = Fen.parse("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1");
            checkEqual("Ra8#", San.of(mate, Moves.encode(Bitboards.squareOf("a1"), Bitboards.squareOf("a8"))),
                    "a mate on the back rank gets a hash");
        });

        test("San: an en passant capture reads like any other pawn capture", () -> {
            Position position = Fen.parse("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
            checkEqual("exd6", San.of(position, Moves.encodeEnPassant(
                    Bitboards.squareOf("e5"), Bitboards.squareOf("d6"))), "en passant is written as exd6");
        });

        // =================================================================
        System.out.println("\n-- Game session on the core -------------------------------------");
        // =================================================================

        test("GameSession: a new game starts from the standard position with nothing played", () -> {
            GameSession session = new GameSession();
            checkEqual(GameResult.ONGOING, session.result(), "a new game is still running");
            check(session.termination() == null, "a running game has no reason to have ended");
            check(session.isWhiteToMove(), "White moves first");
            checkEqual(0, session.getMoveLog().size(), "no move has been played yet");
            checkEqual(Fen.START_POSITION, Fen.write(session.position()), "the board is the standard one");
        });

        test("GameSession: playing a move records it, hands the clock over and asks for a repaint", () -> {
            int[] switches = {0};
            int[] repaints = {0};
            boolean[] handedTo = {true};
            GameSession session = new GameSession();
            session.setView(new GameSession.View() {
                @Override
                public void switchClocks(boolean pWhiteToMove) {
                    switches[0]++;
                    handedTo[0] = pWhiteToMove;
                }

                @Override
                public void stopClocks() {
                }

                @Override
                public void resetClocks() {
                }

                @Override
                public void repaint() {
                    repaints[0]++;
                }
            });

            check(session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4"))),
                    "e2e4 must be accepted in the starting position");
            checkEqual(1, session.getMoveLog().size(), "the move is written down");
            checkEqual("e4", session.getMoveLog().get(0), "and written in algebraic notation");
            checkEqual(1, session.getFenHistory().size(), "the position after the move is recorded");
            checkEqual(1, switches[0], "the clock changes hands exactly once");
            check(!handedTo[0], "the clock goes to Black");
            check(repaints[0] >= 1, "the board is asked to repaint");
            check(!session.isWhiteToMove(), "Black is to move now");
        });

        test("GameSession: illegal moves and moves after the end are refused", () -> {
            GameSession session = new GameSession();
            check(!session.play(Moves.encode(Bitboards.squareOf("e2"), Bitboards.squareOf("e5"))),
                    "a pawn cannot jump three squares");
            checkEqual(Moves.NONE, session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e5")),
                    "no legal move connects those squares");
            checkEqual(0, session.getMoveLog().size(), "a refused move is not written down");

            session.resign(Pieces.WHITE);
            check(!session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4"))),
                    "a finished game accepts no more moves");
        });

        test("GameSession: the fastest mate ends the game with Black winning", () -> {
            GameResult[] reported = {null};
            Termination[] reason = {null};
            int[] stops = {0};
            GameSession session = new GameSession();
            session.setEndListener((pResult, pTermination) -> {
                reported[0] = pResult;
                reason[0] = pTermination;
            });
            session.setView(new GameSession.View() {
                @Override
                public void switchClocks(boolean pWhiteToMove) {
                }

                @Override
                public void stopClocks() {
                    stops[0]++;
                }

                @Override
                public void resetClocks() {
                }

                @Override
                public void repaint() {
                }
            });

            // 1. f3 e5 2. g4 Qh4 mate
            session.play(session.moveFor(Bitboards.squareOf("f2"), Bitboards.squareOf("f3")));
            session.play(session.moveFor(Bitboards.squareOf("e7"), Bitboards.squareOf("e5")));
            session.play(session.moveFor(Bitboards.squareOf("g2"), Bitboards.squareOf("g4")));
            session.play(session.moveFor(Bitboards.squareOf("d8"), Bitboards.squareOf("h4")));

            checkEqual(GameResult.BLACK_WINS, session.result(), "Black wins the fastest mate");
            checkEqual(Termination.CHECKMATE, session.termination(), "and the reason is mate");
            checkEqual("0-1", session.result().pgnToken(), "the PGN token matches the result");
            checkEqual("Qh4#", session.getMoveLog().get(3), "the mating move is marked with a hash");
            checkEqual(1, stops[0], "the clocks are stopped exactly once");
        });

        test("GameSession: a move that leaves the opponent without a reply is stalemate", () -> {
            GameSession session = new GameSession(Fen.parse("7k/5Q2/8/6K1/8/8/8/8 w - - 0 1"));
            session.play(session.moveFor(Bitboards.squareOf("g5"), Bitboards.squareOf("g6")));

            checkEqual(GameResult.DRAW, session.result(), "stalemate is a draw");
            checkEqual(Termination.STALEMATE, session.termination(), "and says so");
        });

        test("GameSession: a capture that leaves two bare kings draws at once", () -> {
            GameSession session = new GameSession(Fen.parse("4k3/8/8/8/8/8/4n3/4K3 w - - 0 1"));
            session.play(session.moveFor(Bitboards.squareOf("e1"), Bitboards.squareOf("e2")));

            checkEqual(GameResult.DRAW, session.result(), "two kings can never mate");
            checkEqual(Termination.INSUFFICIENT_MATERIAL, session.termination(), "and the reason says why");
        });

        test("GameSession: a flag fall is a win, unless the other side cannot mate", () -> {
            GameSession winning = new GameSession(Fen.parse("4k3/8/8/8/8/8/8/3QK3 b - - 0 1"));
            winning.flagFall(Pieces.BLACK);
            checkEqual(GameResult.WHITE_WINS, winning.result(), "a queen can still mate, so White wins on time");
            checkEqual(Termination.TIME_OUT, winning.termination(), "the reason is the clock");

            GameSession drawn = new GameSession(Fen.parse("4k3/8/8/8/8/8/8/4K3 b - - 0 1"));
            drawn.flagFall(Pieces.BLACK);
            checkEqual(GameResult.DRAW, drawn.result(), "a lone king does not win on time");
            checkEqual(Termination.TIME_OUT_WITHOUT_MATING_MATERIAL, drawn.termination(), "FIDE rule 6.9");
        });

        test("GameSession: the seventy five move rule draws without anybody claiming", () -> {
            boolean[] toldAboutForcedDraw = {false};
            GameSession session = new GameSession(Fen.parse("4k3/8/8/8/8/8/8/R3K3 w - - 149 80"));
            session.setDrawArbiter(new GameSession.DrawArbiter() {
                @Override
                public boolean offerFiftyMoveDraw() {
                    return false;
                }

                @Override
                public boolean offerRepetitionDraw() {
                    return false;
                }

                @Override
                public void notifyForcedDraw() {
                    toldAboutForcedDraw[0] = true;
                }
            });

            session.play(session.moveFor(Bitboards.squareOf("a1"), Bitboards.squareOf("a2")));
            checkEqual(GameResult.DRAW, session.result(), "the game draws itself after 75 moves");
            checkEqual(Termination.SEVENTY_FIVE_MOVE_RULE, session.termination(), "and says which rule");
            check(toldAboutForcedDraw[0], "the players are told about a draw they cannot refuse");
        });

        test("GameSession: the fifty move claim is offered once per player", () -> {
            int[] offers = {0};
            GameSession session = new GameSession(Fen.parse("4k3/8/8/8/8/8/8/R3K3 w - - 99 60"));
            session.setDrawArbiter(new GameSession.DrawArbiter() {
                @Override
                public boolean offerFiftyMoveDraw() {
                    offers[0]++;
                    return false;
                }

                @Override
                public boolean offerRepetitionDraw() {
                    return false;
                }

                @Override
                public void notifyForcedDraw() {
                }
            });

            session.play(session.moveFor(Bitboards.squareOf("a1"), Bitboards.squareOf("a2")));
            checkEqual(1, offers[0], "the player to move is asked once");
            session.play(session.moveFor(Bitboards.squareOf("e8"), Bitboards.squareOf("d8")));
            checkEqual(2, offers[0], "the other player is asked as well");
            session.play(session.moveFor(Bitboards.squareOf("a2"), Bitboards.squareOf("a3")));
            checkEqual(2, offers[0], "a player who declined is not asked again");
            checkEqual(GameResult.ONGOING, session.result(), "a declined claim leaves the game running");
        });

        test("GameSession: the fifty move claim is asked after the clock has changed hands", () -> {
            boolean[] clockHandedOverFirst = {false};
            int[] switches = {0};
            GameSession session = new GameSession(Fen.parse("4k3/8/8/8/8/8/8/R3K3 w - - 99 60"));
            session.setView(new GameSession.View() {
                @Override
                public void switchClocks(boolean pWhiteToMove) {
                    switches[0]++;
                }

                @Override
                public void stopClocks() {
                }

                @Override
                public void resetClocks() {
                }

                @Override
                public void repaint() {
                }
            });
            session.setDrawArbiter(new GameSession.DrawArbiter() {
                @Override
                public boolean offerFiftyMoveDraw() {
                    // the player who has to decide must be the one whose clock is running by now
                    clockHandedOverFirst[0] = switches[0] == 1;
                    return false;
                }

                @Override
                public boolean offerRepetitionDraw() {
                    return false;
                }

                @Override
                public void notifyForcedDraw() {
                }
            });

            session.play(session.moveFor(Bitboards.squareOf("a1"), Bitboards.squareOf("a2")));
            check(clockHandedOverFirst[0],
                    "the claim must be raised after the clock went over, never on the mover's time");
        });

        test("GameSession: a repeated position can be claimed as a draw", () -> {
            GameSession session = new GameSession();
            session.setDrawArbiter(new GameSession.DrawArbiter() {
                @Override
                public boolean offerFiftyMoveDraw() {
                    return false;
                }

                @Override
                public boolean offerRepetitionDraw() {
                    return true;
                }

                @Override
                public void notifyForcedDraw() {
                }
            });

            // both knights walk out and back twice, which brings the starting position back
            String[][] shuffle = {
                    {"g1", "f3"}, {"g8", "f6"}, {"f3", "g1"}, {"f6", "g8"},
                    {"g1", "f3"}, {"g8", "f6"}, {"f3", "g1"}, {"f6", "g8"},
            };
            for (String[] step : shuffle) {
                if (session.result().isFinished()) {
                    break;
                }
                session.play(session.moveFor(Bitboards.squareOf(step[0]), Bitboards.squareOf(step[1])));
            }

            checkEqual(GameResult.DRAW, session.result(), "the third occurrence may be claimed");
            checkEqual(Termination.THREEFOLD_REPETITION, session.termination(), "and says which rule");
        });

        test("GameSession: a promotion asks which piece the pawn becomes", () -> {
            GameSession session = new GameSession(Fen.parse("8/P7/8/8/7k/8/8/4K3 w - - 0 1"));
            session.setPromotionPicker(white -> Pieces.KNIGHT);

            int move = session.moveFor(Bitboards.squareOf("a7"), Bitboards.squareOf("a8"));
            check(Moves.isPromotion(move), "the move must be a promotion");
            checkEqual(Pieces.KNIGHT, Moves.promotionType(move), "the picker asked for a knight");
            session.play(move);
            checkEqual("a8=N", session.getMoveLog().get(0), "an underpromotion is written with its piece");
        });

        test("GameSession: the highlighted squares come from the legal moves of one piece", () -> {
            GameSession session = new GameSession();
            int[] targets = new int[MoveGen.MAX_MOVES];

            checkEqual(2, session.targetsFrom(Bitboards.squareOf("e2"), targets), "a pawn has two moves at the start");
            checkEqual(2, session.targetsFrom(Bitboards.squareOf("g1"), targets), "the knight has two squares");
            checkEqual(0, session.targetsFrom(Bitboards.squareOf("e1"), targets), "the king is hemmed in");
            checkEqual(0, session.targetsFrom(Bitboards.squareOf("e7"), targets), "a black pawn may not move on White's turn");
        });

        test("GameSession: restarting clears the board, the record and the result", () -> {
            GameSession session = new GameSession();
            session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            session.resign(Pieces.BLACK);
            checkEqual(GameResult.WHITE_WINS, session.result(), "a resignation ends the game");

            session.restart();
            checkEqual(GameResult.ONGOING, session.result(), "a restarted game runs again");
            check(session.termination() == null, "and has no reason to have ended");
            checkEqual(0, session.getMoveLog().size(), "the record starts empty");
            checkEqual(Fen.START_POSITION, Fen.write(session.position()), "the pieces are back where they started");
            check(session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4"))),
                    "and moves are accepted again");
        });

        // =================================================================
        System.out.println();
        System.out.println("-- Resigning, offering and claiming -----------------------------");
        // =================================================================

        test("GameSession: a draw can be claimed once the position has come back three times", () -> {
            GameSession session = new GameSession();
            // both knights out and back twice brings the starting position back for the third time
            String[][] shuffle = {{"g1", "f3"}, {"g8", "f6"}, {"f3", "g1"}, {"f6", "g8"}};
            for (int round = 0; round < 2; round++) {
                for (String[] step : shuffle) {
                    session.play(session.moveFor(Bitboards.squareOf(step[0]), Bitboards.squareOf(step[1])));
                }
            }

            check(session.isRepetitionClaimable(), "the third occurrence may be claimed");
            check(session.isDrawClaimable(), "so a draw is claimable at all");
            check(session.claimDraw(), "and claiming it ends the game");
            checkEqual(GameResult.DRAW, session.result(), "a claimed repetition is a draw");
            checkEqual(Termination.THREEFOLD_REPETITION, session.termination(), "and says which rule drew it");
        });

        test("GameSession: the fifty move rule can be claimed once fifty moves have passed", () -> {
            GameSession session = new GameSession(Fen.parse("4k3/8/8/8/8/8/8/R3K3 w - - 99 60"));
            check(!session.isFiftyMoveClaimable(), "ninety nine half moves are not yet fifty moves");

            session.play(session.moveFor(Bitboards.squareOf("a1"), Bitboards.squareOf("a2")));
            check(session.isFiftyMoveClaimable(), "the hundredth half move makes it claimable");

            check(session.claimDraw(), "claiming it ends the game");
            checkEqual(GameResult.DRAW, session.result(), "the fifty move rule draws");
            checkEqual(Termination.FIFTY_MOVE_RULE, session.termination(), "and names itself as the reason");
        });

        test("GameSession: a claim nobody is entitled to changes nothing", () -> {
            GameSession session = new GameSession();
            check(!session.isDrawClaimable(), "nothing is claimable in the starting position");
            check(!session.claimDraw(), "so claiming does nothing");
            checkEqual(GameResult.ONGOING, session.result(), "and the game carries on");
        });

        test("GameSession: an offered draw ends the game only when the opponent accepts", () -> {
            GameSession session = new GameSession();
            session.setDrawOfferArbiter(pWhiteOffers -> false);
            check(!session.offerDraw(), "a refused offer draws nothing");
            checkEqual(GameResult.ONGOING, session.result(), "and leaves the game running");

            boolean[] whiteOffered = {false};
            session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            session.setDrawOfferArbiter(pWhiteOffers -> {
                whiteOffered[0] = pWhiteOffers;
                return true;
            });

            check(session.offerDraw(), "an accepted offer ends the game");
            check(!whiteOffered[0], "Black is to move after e4, so Black is the one offering");
            checkEqual(GameResult.DRAW, session.result(), "an agreed draw is a draw");
            checkEqual(Termination.DRAW_AGREED, session.termination(), "and says it was agreed");
        });

        test("GameSession: the screen is told whenever the game changes", () -> {
            int[] changes = {0};
            GameSession session = new GameSession();
            session.setStateListener(() -> changes[0]++);

            session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            check(changes[0] >= 1, "a played move changes the game");

            int afterMove = changes[0];
            session.resign(Pieces.WHITE);
            check(changes[0] > afterMove, "and so does the end of it");
        });

        // =================================================================
        System.out.println();
        System.out.println("-- Languages ----------------------------------------------------");
        // =================================================================

        test("Messages: every text exists in both languages", () -> {
            java.util.Properties english = loadBundle("/resources/messages.properties");
            java.util.Properties german = loadBundle("/resources/messages_de.properties");

            check(!english.isEmpty(), "the English bundle must not be empty");
            // a key only one language has is a line that silently reads in the wrong language
            for (String key : english.stringPropertyNames()) {
                check(german.containsKey(key), "the German bundle is missing " + key);
            }
            for (String key : german.stringPropertyNames()) {
                check(english.containsKey(key), "the English bundle is missing " + key);
            }
        });

        test("Messages: a text comes back in the language that was chosen", () -> {
            java.util.Locale previous = Messages.getLocale();
            try {
                Messages.setLocale(java.util.Locale.ENGLISH);
                checkEqual("New Game", Messages.get("menu.newGame"), "English comes from the base bundle");

                Messages.setLocale(java.util.Locale.GERMAN);
                checkEqual("Neues Spiel", Messages.get("menu.newGame"), "German comes from its own bundle");

                checkEqual("menu.nothingHasThisKey", Messages.get("menu.nothingHasThisKey"),
                        "a key nobody wrote a text for shows itself rather than taking a screen down");
            } finally {
                // the rest of the suite reads English
                Messages.setLocale(previous);
            }
        });

        test("Messages: a sentence with a name in it is filled in for each language", () -> {
            java.util.Locale previous = Messages.getLocale();
            try {
                Messages.setLocale(java.util.Locale.ENGLISH);
                checkEqual("Alice wins by checkmate!", Messages.format("end.checkmate", "Alice"),
                        "the name goes into the English sentence");

                Messages.setLocale(java.util.Locale.GERMAN);
                checkEqual("Alice gewinnt durch Schachmatt!", Messages.format("end.checkmate", "Alice"),
                        "and into the German one, where the rest of the sentence differs");
            } finally {
                Messages.setLocale(previous);
            }
        });

        test("Messages: the game ships the languages the menu offers", () -> {
            check(Messages.supportedLocales().contains(java.util.Locale.ENGLISH), "English is shipped");
            check(Messages.supportedLocales().contains(java.util.Locale.GERMAN), "German is shipped");
            for (java.util.Locale supported : Messages.supportedLocales()) {
                Messages.setLocale(supported);
                checkEqual(true, !Messages.get("menu.newGame").equals("menu.newGame"),
                        "every offered language must actually have text, missing: " + supported);
            }
            Messages.setLocale(java.util.Locale.ENGLISH);
        });

        // =================================================================
        System.out.println();
        System.out.println("-- Undo, redo and takebacks -------------------------------------");
        // =================================================================

        test("GameSession: taking a move back restores the position exactly", () -> {
            GameSession session = new GameSession();
            String before = Fen.write(session.position());

            session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            check(session.undo(), "a played move can be taken back");

            checkEqual(before, Fen.write(session.position()),
                    "the pieces, the rights, the en passant square and both counters must all come back");
            checkEqual(0, session.getMoveLog().size(), "the record loses the move as well");
            checkEqual(0, session.getFenHistory().size(), "and the position it produced");
            check(session.isWhiteToMove(), "White is to move again");
            check(!session.canUndo(), "there is nothing left to take back");
            check(session.canRedo(), "but the move is waiting to be played again");
        });

        test("GameSession: taking back a rook move gives the castling right back", () -> {
            GameSession session = new GameSession(Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"));
            String before = Fen.write(session.position());

            session.play(session.moveFor(Bitboards.squareOf("h1"), Bitboards.squareOf("g1")));
            check(!Fen.write(session.position()).contains("KQkq"),
                    "moving the rook costs White the kingside right");

            check(session.undo(), "the rook move can be taken back");
            checkEqual(before, Fen.write(session.position()),
                    "a right the move gave away has to come back with it");
        });

        test("GameSession: a move played after an undo throws the redo branch away", () -> {
            GameSession session = new GameSession();
            session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            session.undo();
            check(session.canRedo(), "the move is waiting to be played again");

            session.play(session.moveFor(Bitboards.squareOf("d2"), Bitboards.squareOf("d4")));
            check(!session.canRedo(), "playing something else abandons what was taken back");
            checkEqual(List.of("d4"), session.getMoveLog(), "the game follows the move that was really played");
        });

        test("GameSession: redo replays the moves in order and keeps the rest of the branch", () -> {
            GameSession session = new GameSession();
            session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            session.play(session.moveFor(Bitboards.squareOf("e7"), Bitboards.squareOf("e5")));
            session.undo();
            session.undo();
            checkEqual(0, session.getMoveLog().size(), "both moves are taken back");

            check(session.redo(), "the first move comes back");
            checkEqual(List.of("e4"), session.getMoveLog(), "and it is the one that was played first");
            check(session.canRedo(), "the second move is still waiting");

            check(session.redo(), "which comes back as well");
            checkEqual(List.of("e4", "e5"), session.getMoveLog(), "the game stands where it stood");
            check(!session.canRedo(), "and nothing is left to replay");
        });

        test("GameSession: taking back the mating move lets the game go on", () -> {
            GameSession session = new GameSession();
            // 1. f3 e5 2. g4 Qh4 mate
            session.play(session.moveFor(Bitboards.squareOf("f2"), Bitboards.squareOf("f3")));
            session.play(session.moveFor(Bitboards.squareOf("e7"), Bitboards.squareOf("e5")));
            session.play(session.moveFor(Bitboards.squareOf("g2"), Bitboards.squareOf("g4")));
            session.play(session.moveFor(Bitboards.squareOf("d8"), Bitboards.squareOf("h4")));
            checkEqual(GameResult.BLACK_WINS, session.result(), "the game is over");

            check(session.undo(), "the mating move can be taken back");
            checkEqual(GameResult.ONGOING, session.result(), "which puts the game back to running");
            check(session.termination() == null, "with no reason left for it having ended");
            check(session.play(session.moveFor(Bitboards.squareOf("d8"), Bitboards.squareOf("h4"))),
                    "and the mate can be played all over again");
        });

        test("GameSession: taking back an irreversible move brings the repetition counts back", () -> {
            int[] repetitionOffers = {0};
            GameSession session = new GameSession();
            session.setDrawArbiter(new GameSession.DrawArbiter() {
                @Override
                public boolean offerFiftyMoveDraw() {
                    return false;
                }

                @Override
                public boolean offerRepetitionDraw() {
                    repetitionOffers[0]++;
                    return false;
                }

                @Override
                public void notifyForcedDraw() {
                }
            });

            // both knights out and back brings the starting position back for the second time
            String[][] shuffle = {{"g1", "f3"}, {"g8", "f6"}, {"f3", "g1"}, {"f6", "g8"}};
            for (String[] step : shuffle) {
                session.play(session.moveFor(Bitboards.squareOf(step[0]), Bitboards.squareOf(step[1])));
            }

            // a pawn move makes every earlier position unreachable and clears the counts
            session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            check(session.undo(), "taking the pawn move back has to bring those counts back");

            for (String[] step : shuffle) {
                session.play(session.moveFor(Bitboards.squareOf(step[0]), Bitboards.squareOf(step[1])));
            }
            checkEqual(1, repetitionOffers[0],
                    "the third occurrence must still be noticed after the counts were rebuilt");
        });

        test("GameSession: clicking a move jumps the game to it and back again", () -> {
            GameSession session = new GameSession();
            session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            session.play(session.moveFor(Bitboards.squareOf("e7"), Bitboards.squareOf("e5")));
            session.play(session.moveFor(Bitboards.squareOf("g1"), Bitboards.squareOf("f3")));

            check(session.goToPly(1), "the game can go back to just after the first move");
            checkEqual(List.of("e4"), session.getMoveLog(), "only the first move is played there");

            check(session.goToPly(3), "and forward to the end again");
            checkEqual(3, session.getMoveLog().size(), "all three moves are back");
            check(!session.goToPly(9), "a ply the game never reached is refused");
        });

        test("GameSession: a refused takeback leaves the game exactly as it was", () -> {
            GameSession session = new GameSession();
            session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            String afterMove = Fen.write(session.position());

            session.setTakebackArbiter(pWhiteAsks -> false);
            check(!session.requestTakeback(), "a refused request takes nothing back");
            checkEqual(afterMove, Fen.write(session.position()), "the position stays where it was");
            checkEqual(1, session.getMoveLog().size(), "and so does the record");

            boolean[] asked = {false};
            session.setTakebackArbiter(pWhiteAsks -> {
                asked[0] = pWhiteAsks;
                return true;
            });
            check(session.requestTakeback(), "an agreed request takes the move back");
            check(asked[0], "White played the last move, so White is the one asking");
            checkEqual(0, session.getMoveLog().size(), "and the move is gone");
        });

        test("GameSession: the clocks are recorded after a move and restored when it is taken back", () -> {
            List<String> calls = new ArrayList<>();
            GameSession session = new GameSession();
            session.setView(new GameSession.View() {
                @Override
                public void switchClocks(boolean pWhiteToMove) {
                }

                @Override
                public void stopClocks() {
                }

                @Override
                public void resetClocks() {
                }

                @Override
                public void repaint() {
                }

                @Override
                public void recordClocks(int pPly) {
                    calls.add("record " + pPly);
                }

                @Override
                public void restoreClocks(int pPly, boolean pWhiteToMove) {
                    calls.add("restore " + pPly);
                }
            });

            session.play(session.moveFor(Bitboards.squareOf("e2"), Bitboards.squareOf("e4")));
            session.undo();
            checkEqual(List.of("record 1", "restore 0"), calls,
                    "a move records the clocks at its ply, and taking it back restores the ply before it");
        });

        test("ChessClock: a restored time replaces whatever the clock was showing", () -> {
            ChessClock clock = new ChessClock(true, 60_000, () -> {
            }, w -> {
            });
            clock.setTimeMs(12_345);
            checkEqual(12_345L, clock.getTimeMs(), "the clock shows the time it was given back");
            clock.setTimeMs(-5);
            checkEqual(0L, clock.getTimeMs(), "a time below zero means no time left");

            ChessClock unlimited = new ChessClock(true, 0, () -> {
            }, w -> {
            });
            unlimited.setTimeMs(5_000);
            checkEqual(0L, unlimited.getTimeMs(), "an unlimited clock has no time to restore");
        });

        test("ChessClock: a restored reading takes a move back out of its stage as well", () -> {
            ChessClock clock = new ChessClock(true, 60_000, ClockMode.SUDDEN_DEATH, 0, 0, () -> {
            }, w -> {
            });
            // two moves at a minute, then half a minute more for whatever is left
            clock.setStages(List.of(new ClockStage(2, 60_000),
                    new ClockStage(ClockStage.UNTIL_THE_END, 30_000)));

            clock.onMoveFinished();
            ChessClock.Reading afterFirstMove = clock.reading();
            clock.onMoveFinished();
            checkEqual(90_000L, clock.getTimeMs(), "the second move plays the stage out");

            // taking the second move back, then playing it again, must hand the stage's time out once
            clock.restore(afterFirstMove);
            checkEqual(60_000L, clock.getTimeMs(), "the time goes back to before the second move");
            clock.onMoveFinished();
            checkEqual(90_000L, clock.getTimeMs(), "the move played again finishes the stage exactly once");
            clock.onMoveFinished();
            checkEqual(90_000L, clock.getTimeMs(), "and the last stage brings nothing more");
        });

        // -- Summary ------------------------------------------------------
        // the host frame is null on a headless run
        if (frame != null) SwingUtilities.invokeAndWait(frame::dispose);

        System.out.println("\n================================================================");
        System.out.printf("  %d passed, %d failed, %d skipped  (total: %d)%n",
                passed.size(), failed.size(), skipped.size(),
                passed.size() + failed.size() + skipped.size());
        if (!failed.isEmpty()) {
            System.out.println("\nFailed tests:");
            failed.forEach(f -> System.out.println("  x " + f));
        }
        System.out.println("================================================================\n");

        // always exit explicitly so scripts get a status code even while Swing threads are alive
        System.exit(failed.isEmpty() ? 0 : 1);
    }

    // -- Test-only helpers ------------------------------------------------

    /**
     * Finds the component with a given name anywhere below a container.
     * <p>
     * Some of what a screen draws is not a button or a label but a panel that paints itself, and
     * such a panel has no text to find it by. The ones worth checking carry a name, so this walks
     * the tree and returns the first component wearing the one that was asked for.
     * <p>
     * Time complexity: O(c) for the c components below the container.
     * Space complexity: O(d) for a tree of depth d.
     *
     * @param pRoot container to search below, never null
     * @param pName component name to look for, never null
     * @return the component with that name, or null when nothing below carries it
     */
    private static Component findByName(Container pRoot, String pName) {
        for (Component child : pRoot.getComponents()) {
            if (pName.equals(child.getName())) {
                return child;
            }
            // a named component can sit at any depth, inside panels and split panes
            if (child instanceof Container nested) {
                Component found = findByName(nested, pName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Reads one language bundle straight from the classpath, without any fallback.
     * <p>
     * The missing key test has to compare what each file really holds, and a bundle loaded the
     * normal way inherits every key from the English one, which would make the comparison pass
     * whatever is missing. So I read the file itself, as UTF-8, because that is what properties
     * files have been since Java 9 while the old stream based load still assumes ISO-8859-1 and
     * would quietly turn every German umlaut into nonsense.
     * <p>
     * Time complexity: O(n) in the size of the file. Space complexity: O(n) for the entries.
     *
     * @param pResource classpath path of the bundle, such as /resources/messages.properties
     * @return the entries of that file alone, never null
     * @throws Exception if the file is missing or cannot be read
     */
    private static java.util.Properties loadBundle(String pResource) throws Exception {
        java.util.Properties properties = new java.util.Properties();
        try (java.io.InputStream stream = GameTest.class.getResourceAsStream(pResource)) {
            checkNotNull(stream, "the bundle must be on the classpath: " + pResource);
            properties.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        }
        return properties;
    }

    /**
     * Reads the pixels of a button's icon so two icons can be compared.
     * <p>
     * The promotion test has to tell White's and Black's piece icons apart without looking at the
     * screen. I draw the icon image into a plain ARGB image and return all of its pixels row by row.
     * <p>
     * Time complexity: O(w * h) for an icon of width w and height h.
     * Space complexity: O(w * h) for the copy and the pixel array.
     *
     * @param pButton button that shows an ImageIcon, never null
     * @return the icon's pixels as ARGB values, never null
     * @throws ClassCastException if the button's icon is not an ImageIcon
     */
    private static int[] iconPixels(AbstractButton pButton) {
        Image image = ((ImageIcon) pButton.getIcon()).getImage();
        BufferedImage copy = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        // draw the icon into a plain image so its pixels can be compared
        Graphics2D g2d = copy.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return copy.getRGB(0, 0, copy.getWidth(), copy.getHeight(), null, 0, copy.getWidth());
    }

    /**
     * Creates a timed board on the event thread, stops its clocks and keeps only a weak reference.
     * <p>
     * The leak test must not hold the board itself, otherwise the test would keep it alive. I build
     * the board with a ten minute clock on the event thread, stop both clocks the way a finished game
     * does and hand back nothing but a weak reference.
     * <p>
     * Time complexity: O(p) for the p starting pieces. Space complexity: O(p) until the board is
     * collected.
     *
     * @return weak reference to the board, never null
     * @throws Exception if building the board on the event thread fails or is interrupted
     */
    private static java.lang.ref.WeakReference<Board> boardWithStoppedClocks() throws Exception {
        java.util.concurrent.atomic.AtomicReference<java.lang.ref.WeakReference<Board>> holder =
                new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Board board = new Board(new GameConfig("Alice", "Bob", 600_000, 600_000, "Rapid 10+0"));
            // a finished game stops both clocks
            board.stopClocks();
            holder.set(new java.lang.ref.WeakReference<>(board));
        });
        return holder.get();
    }

    /**
     * Builds a position with the 32 pieces of a new game standing on it.
     * <p>
     * The rules tests used to reach their position through a Swing board, which meant every one of
     * them built a window's worth of objects and could not run without the old board handing its
     * state out. The board now keeps its game in a GameSession instead, so the tests of the older
     * rules classes create the position they work on themselves.
     * <p>
     * Time complexity: O(p) for the 32 pieces placed. Space complexity: O(p) for the position.
     *
     * @return a position in the standard starting arrangement, never null
     */
    private static BoardState newBoardState() {
        BoardState state = new BoardState();
        state.setPieces(StartPosition.create(state));
        return state;
    }

    /**
     * A GameView that answers the rules without drawing anything.
     * <p>
     * The older rules engine asks its view to hand the clock over and to repaint. Tests that only
     * care about the rules have nothing to draw and no clocks to run, so they pass this instead of a
     * real board, which is what lets them run on a machine without a display.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return a view that ignores every call, never null
     */
    private static GameView noOpGameView() {
        return new GameView() {
            @Override
            public void switchClocks(boolean pWhiteToMove) {
            }

            @Override
            public void stopClocks() {
            }

            @Override
            public void resetClocks() {
            }

            @Override
            public void repaint() {
            }
        };
    }

    /**
     * A DrawOfferResolver that never offers/accepts anything - used by tests
     * that exercise move mechanics and don't care about the draw-offer path.
     */
    private static DrawOfferResolver noOpDrawResolver() {
        return new DrawOfferResolver() {
            public void notifyForcedDraw() {
            }

            public boolean offerDraw() {
                return false;
            }
        };
    }

    /**
     * A configurable, headless DrawOfferResolver test double. Tracks whether
     * each method was actually invoked, so tests can assert the 50/75-move
     * rules fired at the right half-move count without any dialog involved.
     */
    private static class FakeDrawOfferResolver implements DrawOfferResolver {
        private final boolean acceptOffer;
        boolean offerDrawCalled = false;
        int offerDrawCount = 0;
        boolean forcedDrawNotified = false;

        FakeDrawOfferResolver(boolean acceptOffer) {
            this.acceptOffer = acceptOffer;
        }

        @Override
        public void notifyForcedDraw() {
            forcedDrawNotified = true;
        }

        /**
         * Records that a draw claim was offered and answers with the configured choice.
         * <p>
         * Tests check both whether and how often a claim was offered. I set the flag, count the call
         * and return the answer the test picked when it created this fake.
         * <p>
         * Time complexity: O(1). Space complexity: O(1).
         *
         * @return true if this fake accepts every offer, false if it declines them
         */
        @Override
        public boolean offerDraw() {
            offerDrawCalled = true;
            // lets tests make sure a claim isn't offered over and over
            offerDrawCount++;
            return acceptOffer;
        }
    }

    // king tours of 9 and 10 squares, together they only repeat a position every 90 full moves
    private static final int[][] WHITE_KING_TOUR = {
            {0, 7}, {0, 6}, {0, 5}, {1, 5}, {2, 5}, {2, 6}, {1, 6}, {2, 7}, {1, 7}};         // a1 a2 a3 b3 c3 c2 b2 c1 b1
    private static final int[][] BLACK_KING_TOUR = {
            {5, 0}, {6, 0}, {7, 0}, {7, 1}, {7, 2}, {6, 2}, {5, 2}, {4, 2}, {4, 1}, {4, 0}}; // f8 g8 h8 h7 h6 g6 f6 e6 e7 e8

    /**
     * Walks both kings around their tours for a given number of half moves.
     * <p>
     * The 50 and 75 move rule tests need a long stretch of moves without a pawn move or a capture.
     * Shuffling the kings between two squares did that, but it repeats the same positions, which
     * correctly ends a game by repetition long before the move-count rules apply. I move the white
     * king around a nine square tour in the lower left corner and the black king around a ten
     * square tour in the upper right corner, alternating turns. Since 9 and 10 share no factor, no
     * position comes back within 90 full moves, and the kings never get close to each other.
     * <p>
     * Time complexity: O(h * p * s) for h half moves, because every move runs the end of game
     * search over p pieces and s squares. Space complexity: O(h) for the recorded move history.
     *
     * @param pGc        controller that plays the moves, never null
     * @param pState     board state with the kings on a1 and f8 and nothing else on their tours, never null
     * @param pHalfMoves number of half moves to play, from 0 up to 180
     */
    private static void shuffleKings(GameController pGc, BoardState pState, int pHalfMoves) {
        int whiteStep = 0;
        int blackStep = 0;
        for (int i = 0; i < pHalfMoves; i++) {
            // White moves on even half moves
            boolean whiteMoves = i % 2 == 0;
            int[][] tour = whiteMoves ? WHITE_KING_TOUR : BLACK_KING_TOUR;
            int step = whiteMoves ? whiteStep : blackStep;
            int[] from = tour[step % tour.length];
            int[] to = tour[(step + 1) % tour.length];
            // move the king from its current tour square to the next one
            Piece king = pState.getPiece(from[0], from[1]);
            pGc.makeMove(new Move(pState, king, to[0], to[1]));
            if (whiteMoves) {
                whiteStep++;
            } else {
                blackStep++;
            }
        }
    }

    /**
     * ReplayPanel's move-index label is the JLabel sitting in the SOUTH nav bar,
     * which is the second JLabel found overall (index 1) since the panel itself
     * has no other labels. Using text content ("position" or "No moves") to
     * disambiguate keeps this robust to minor layout reordering.
     */
    private static JLabel findMoveLabel(Container c) {
        for (JLabel l : findAllLabels(c)) {
            if (l.getText().contains("position") || l.getText().equals("No moves")) {
                return l;
            }
        }
        return null;
    }

    /**
     * Deletes the PGN files a test saved so repeated runs stay clean.
     * <p>
     * Persistence tests write real files, and leftovers would pile up between runs. I look in the
     * same games directory PgnManager writes to, pick every file whose name contains the sanitized
     * unique white player name and delete it. Problems are only printed as a warning, because a
     * failed cleanup should never fail a test that already passed.
     * <p>
     * Time complexity: O(f) where f is the number of files in the games directory.
     * Space complexity: O(f) for the directory listing.
     *
     * @param pUniqueWhiteName unique white player name the test saved its game under, never null
     */
    private static void cleanupSavedGame(String pUniqueWhiteName) {
        try {
            // same folder PgnManager saved into, so the property override is respected
            File gamesDir = PgnManager.getGamesDirectory().toFile();
            // saved file names contain the sanitized player name
            File[] matches = gamesDir.listFiles((dir, name) ->
                    name.contains(pUniqueWhiteName.replaceAll("[^a-zA-Z0-9_-]", "_")));
            if (matches != null) {
                for (File f : matches) Files.deleteIfExists(f.toPath());
            }
        } catch (Exception e) {
            // a cleanup problem must not turn a passing test red
            System.out.println("  (cleanup warning: " + e.getMessage() + ")");
        }
    }
}