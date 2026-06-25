package test;

import gui.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone GUI test runner — no dependencies required.
 * <p>
 * Run via IntelliJ: right-click GameTest → Run 'GameTest.main()'
 * Run via terminal: java -ea -cp out test.GameTest
 * <p>
 * Each test() call registers a named check. Results are printed to the console
 * and a summary is shown at the end. A failing assertion does NOT stop the
 * remaining tests from running.
 * <p>
 * All dialogs are modal, so each test that opens one schedules a button click
 * on the EDT via a short Timer before calling setVisible(true). The Timer fires
 * inside the modal's secondary event loop, dismisses the dialog, and lets
 * setVisible() return so the result can be checked.
 */
public class GameTest {

    // ── Constants ─────────────────────────────────────────────────────────────

    /**
     * Matches Board.tileSize so every expected pixel value is derived the same way.
     */
    private static final int TILE_SIZE = 85;

    /**
     * ms to wait before auto-clicking a modal button.
     */
    private static final int CLICK_DELAY = 200;

    // ── Mini test framework ───────────────────────────────────────────────────

    private static final List<String> passed = new ArrayList<>();
    private static final List<String> failed = new ArrayList<>();
    private static String current = "";

    /**
     * Registers and runs one named test. Failures are caught and recorded.
     */
    private static void test(String name, TestBody body) {
        current = name;
        try {
            body.run();
            passed.add(name);
            System.out.println("  PASS  " + name);
        } catch (Throwable t) {
            failed.add(name + " → " + t.getMessage());
            System.out.println("  FAIL  " + name);
            System.out.println("        " + t.getMessage());
        }
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
            throw new AssertionError(message + " — expected: " + expected + ", got: " + actual);
    }

    private static void checkNotNull(Object obj, String message) {
        if (obj == null) throw new AssertionError(message);
    }

    // ── Swing helpers ─────────────────────────────────────────────────────────

    /**
     * Schedules doClick() on the first visible JButton whose text equals label.
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
     * Recursively clicks the first JButton with matching text. Returns true if found.
     */
    private static boolean clickButton(Container c, String label) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JButton btn && label.equals(btn.getText())) {
                btn.doClick();
                return true;
            }
            if (comp instanceof Container sub && clickButton(sub, label)) return true;
        }
        return false;
    }

    /**
     * Recursively checks whether a JButton with matching text exists.
     */
    private static boolean hasButton(Container c, String label) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JButton btn && label.equals(btn.getText())) return true;
            if (comp instanceof Container sub && hasButton(sub, label)) return true;
        }
        return false;
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

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("No display available — skipping all GUI tests.");
            return;
        }

        // Host frame that acts as the parent for all dialogs
        JFrame[] frameHolder = {null};
        SwingUtilities.invokeAndWait(() -> {
            frameHolder[0] = new JFrame("GameTest host");
            frameHolder[0].setVisible(true);
        });
        JFrame frame = frameHolder[0];

        System.out.println("\n── EndScreen ───────────────────────────────────────────────────");

        // Dialog dimensions must be 4× wide and 2.5× tall relative to tileSize
        test("EndScreen · size scales with tileSize", () ->
                SwingUtilities.invokeAndWait(() -> {
                    EndScreen d = new EndScreen(frame, "White wins", TILE_SIZE);
                    checkEqual(TILE_SIZE * 4, d.getWidth(), "width");
                    checkEqual((int) (TILE_SIZE * 2.5), d.getHeight(), "height");
                    d.dispose();
                }));

        // The label inside must show exactly the message passed to the constructor
        test("EndScreen · label displays passed message", () ->
                SwingUtilities.invokeAndWait(() -> {
                    EndScreen d = new EndScreen(frame, "Black wins", TILE_SIZE);
                    JLabel lbl = findLabel(d.getContentPane());
                    checkNotNull(lbl, "EndScreen must contain a JLabel");
                    checkEqual("Black wins", lbl.getText(), "label text");
                    d.dispose();
                }));

        // Must contain a Restart button
        test("EndScreen · contains Restart button", () ->
                SwingUtilities.invokeAndWait(() -> {
                    EndScreen d = new EndScreen(frame, "Stalemate - Draw", TILE_SIZE);
                    check(hasButton(d, "Restart"), "EndScreen must have a Restart button");
                    d.dispose();
                }));

        // Clicking Restart must dispose the dialog
        test("EndScreen · Restart closes dialog", () -> {
            scheduleClick("Restart");
            boolean[] visible = {true};
            SwingUtilities.invokeAndWait(() -> {
                EndScreen d = new EndScreen(frame, "White wins", TILE_SIZE);
                d.setVisible(true);         // blocks until Restart is clicked
                visible[0] = d.isVisible();
            });
            check(!visible[0], "Dialog should be closed after clicking Restart");
        });

        System.out.println("\n── FiftyRuleDraw (optional claim) ──────────────────────────────");

        // Same size contract as EndScreen
        test("FiftyRuleDraw · size scales with tileSize", () ->
                SwingUtilities.invokeAndWait(() -> {
                    FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, false);
                    checkEqual(TILE_SIZE * 4, d.getWidth(), "width");
                    checkEqual((int) (TILE_SIZE * 2.5), d.getHeight(), "height");
                    d.dispose();
                }));

        // Optional claim must have Claim Draw and Decline, but not Restart
        test("FiftyRuleDraw · optional claim has correct buttons", () ->
                SwingUtilities.invokeAndWait(() -> {
                    FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, false);
                    check(hasButton(d, "Claim Draw"), "Must have 'Claim Draw'");
                    check(hasButton(d, "Decline"), "Must have 'Decline'");
                    check(!hasButton(d, "Restart"), "Must NOT have 'Restart'");
                    d.dispose();
                }));

        // Clicking Claim Draw → result must be ACCEPTED
        test("FiftyRuleDraw · Claim Draw returns ACCEPTED", () -> {
            scheduleClick("Claim Draw");
            FiftyRuleDraw.DrawResult[] result = {null};
            SwingUtilities.invokeAndWait(() -> {
                FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, false);
                d.setVisible(true);         // blocks until a button is clicked
                result[0] = d.getResult();
            });
            checkEqual(FiftyRuleDraw.DrawResult.ACCEPTED, result[0], "result");
        });

        // Clicking Decline → result must be DECLINED
        test("FiftyRuleDraw · Decline returns DECLINED", () -> {
            scheduleClick("Decline");
            FiftyRuleDraw.DrawResult[] result = {null};
            SwingUtilities.invokeAndWait(() -> {
                FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, false);
                d.setVisible(true);
                result[0] = d.getResult();
            });
            checkEqual(FiftyRuleDraw.DrawResult.DECLINED, result[0], "result");
        });

        System.out.println("\n── FiftyRuleDraw (forced draw) ─────────────────────────────────");

        // Forced draw must have Restart only — no Claim Draw or Decline
        test("FiftyRuleDraw · forced draw has correct buttons", () ->
                SwingUtilities.invokeAndWait(() -> {
                    FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, true);
                    check(hasButton(d, "Restart"), "Must have 'Restart'");
                    check(!hasButton(d, "Claim Draw"), "Must NOT have 'Claim Draw'");
                    check(!hasButton(d, "Decline"), "Must NOT have 'Decline'");
                    d.dispose();
                }));

        // Clicking Restart on forced draw must close the dialog
        test("FiftyRuleDraw · forced draw Restart closes dialog", () -> {
            scheduleClick("Restart");
            boolean[] visible = {true};
            SwingUtilities.invokeAndWait(() -> {
                FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, true);
                d.setVisible(true);
                visible[0] = d.isVisible();
            });
            check(!visible[0], "Forced-draw dialog should close after clicking Restart");
        });

        System.out.println("\n── PromoteGUI ──────────────────────────────────────────────────");

        // Single row of 4 buttons → 4× wide, exactly 1 tile tall
        test("PromoteGUI · size scales with tileSize", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PromoteGUI d = new PromoteGUI(frame, TILE_SIZE);
                    checkEqual(TILE_SIZE * 4, d.getWidth(), "width");
                    checkEqual(TILE_SIZE, d.getHeight(), "height");
                    d.dispose();
                }));

        // All four piece options must be present
        test("PromoteGUI · all four buttons present", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PromoteGUI d = new PromoteGUI(frame, TILE_SIZE);
                    check(hasButton(d, "Queen"), "Must have 'Queen'");
                    check(hasButton(d, "Rook"), "Must have 'Rook'");
                    check(hasButton(d, "Bishop"), "Must have 'Bishop'");
                    check(hasButton(d, "Knight"), "Must have 'Knight'");
                    d.dispose();
                }));

        // Each button must return the matching Choice value
        test("PromoteGUI · Queen → Choice.QUEEN", () -> {
            scheduleClick("Queen");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> {
                choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog();
            });
            checkEqual(PromoteGUI.Choice.QUEEN, choice[0], "choice");
        });

        test("PromoteGUI · Rook → Choice.ROOK", () -> {
            scheduleClick("Rook");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> {
                choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog();
            });
            checkEqual(PromoteGUI.Choice.ROOK, choice[0], "choice");
        });

        test("PromoteGUI · Bishop → Choice.BISHOP", () -> {
            scheduleClick("Bishop");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> {
                choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog();
            });
            checkEqual(PromoteGUI.Choice.BISHOP, choice[0], "choice");
        });

        test("PromoteGUI · Knight → Choice.KNIGHT", () -> {
            scheduleClick("Knight");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> {
                choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog();
            });
            checkEqual(PromoteGUI.Choice.KNIGHT, choice[0], "choice");
        });

        // ── Summary ───────────────────────────────────────────────────────────
        SwingUtilities.invokeAndWait(() -> frame.dispose());

        System.out.println("\n════════════════════════════════════════════════════════════════");
        System.out.printf("  %d passed, %d failed  (total: %d)%n",
                passed.size(), failed.size(), passed.size() + failed.size());
        if (!failed.isEmpty()) {
            System.out.println("\nFailed tests:");
            failed.forEach(f -> System.out.println("  ✗ " + f));
        }
        System.out.println("════════════════════════════════════════════════════════════════\n");

        if (!failed.isEmpty()) System.exit(1);
    }
}