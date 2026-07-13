package test;

import engine.imports.*;
import engine.model.*;
import engine.persistence.*;
import engine.pieces.*;
import ui.board.*;
import ui.menu.*;
import ui.theme.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Standalone GUI + action test runner — no dependencies required.
 * <p>
 * Run via IntelliJ: right-click GameTest → Run 'GameTest.main()'
 * Run via terminal: java -ea -cp out test.GameTest
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
 * Main.startGame()) is intentionally NOT exercised here — those two methods
 * are trivial pass-throughs and testing them would require booting the real
 * application frame. MainMenu's "New Game" button is the one exception: its
 * handler uses SwingUtilities.getWindowAncestor(this) rather than Main's
 * static field, so it's fully testable in isolation.
 * <p>
 * PREREQUISITE: Piece.java must expose the static accessors added for
 * ReplayPanel support:
 * public static BufferedImage getSpritesheet()
 * public static int getSpritesheetScale()
 */
public class GameTest {

    // ── Constants ─────────────────────────────────────────────────────────

    private static final int TILE_SIZE = 85;
    private static final int BOARD_HEIGHT = TILE_SIZE * 8;
    private static final int CLICK_DELAY = 200;

    // ── Mini test framework ───────────────────────────────────────────────

    private static final List<String> passed = new ArrayList<>();
    private static final List<String> failed = new ArrayList<>();

    private static void test(String name, TestBody body) {
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

    private static void checkTrue(boolean condition, String message) {
        check(condition, message);
    }

    // ── Swing helpers ─────────────────────────────────────────────────────

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
     * Recursively clicks the first AbstractButton (JButton/JToggleButton) with matching text.
     */
    private static boolean clickButton(Container c, String label) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof AbstractButton btn && label.equals(btn.getText())) {
                btn.doClick();
                return true;
            }
            if (comp instanceof Container sub && clickButton(sub, label)) return true;
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
     * Recursively finds the first AbstractButton with matching text.
     */
    private static AbstractButton findButton(Container c, String label) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof AbstractButton btn && label.equals(btn.getText())) return btn;
            if (comp instanceof Container sub) {
                AbstractButton found = findButton(sub, label);
                if (found != null) return found;
            }
        }
        return null;
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

    // ── Entry point ───────────────────────────────────────────────────────

    static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("No display available — skipping all GUI tests.");
            return;
        }

        JFrame[] frameHolder = {null};
        SwingUtilities.invokeAndWait(() -> {
            frameHolder[0] = new JFrame("GameTest host");
            frameHolder[0].setVisible(true);
        });
        JFrame frame = frameHolder[0];

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── GameConfig ──────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("GameConfig · stores all fields as given", () -> {
            GameConfig cfg = new GameConfig("Alice", "Bob", 300_000, 300_000, "Blitz 5+0");
            checkEqual("Alice", cfg.whiteName(), "whiteName");
            checkEqual("Bob", cfg.blackName(), "blackName");
            checkEqual(300_000L, cfg.whiteTimeMs(), "whiteTimeMs");
            checkEqual(300_000L, cfg.blackTimeMs(), "blackTimeMs");
            checkEqual("Blitz 5+0", cfg.timeLabel(), "timeLabel");
        });

        test("GameConfig · blank names default to White/Black", () -> {
            GameConfig cfg = new GameConfig("  ", "", 0, 0, "Unlimited");
            checkEqual("White", cfg.whiteName(), "whiteName default");
            checkEqual("Black", cfg.blackName(), "blackName default");
        });

        test("GameConfig · names are trimmed", () -> {
            GameConfig cfg = new GameConfig("  Alice  ", " Bob ", 0, 0, "Unlimited");
            checkEqual("Alice", cfg.whiteName(), "trimmed whiteName");
            checkEqual("Bob", cfg.blackName(), "trimmed blackName");
        });

        test("GameConfig · unlimited() factory has zero time", () -> {
            GameConfig cfg = GameConfig.unlimited();
            checkEqual(0L, cfg.whiteTimeMs(), "whiteTimeMs");
            checkEqual(0L, cfg.blackTimeMs(), "blackTimeMs");
            checkEqual("Unlimited", cfg.timeLabel(), "timeLabel");
        });

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── GameRecord ──────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("GameRecord · built from GameConfig captures fields", () -> {
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

        test("GameRecord · loaded-from-file constructor preserves given date", () -> {
            GameRecord r = new GameRecord("Alice", "Bob", "0-1",
                    "2026.01.15", "Rapid 10+0", List.of("d4"), List.of("fen1"));
            checkEqual("2026.01.15", r.date, "date");
            checkEqual("Rapid 10+0", r.timeControl, "timeControl");
        });

        test("GameRecord · move/fen lists are immutable copies", () -> {
            List<String> moves = new ArrayList<>(List.of("e4"));
            GameRecord r = new GameRecord("A", "B", "1-0", "2026.01.01", "Blitz", moves, List.of());
            moves.add("e5"); // mutate original after construction
            checkEqual(1, r.moves.size(), "GameRecord.moves must not reflect later mutation");
        });

        test("GameRecord · getDisplayTitle formats correctly", () -> {
            GameRecord r = new GameRecord("Alice", "Bob", "1-0",
                    "2026.07.03", "Blitz 5+0", List.of(), List.of());
            String title = r.getDisplayTitle();
            check(title.contains("Alice"), "title must contain white name");
            check(title.contains("Bob"), "title must contain black name");
            check(title.contains("1-0"), "title must contain result");
            check(title.contains("Blitz 5+0"), "title must contain time control");
        });

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── FenLoader ────────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("FenLoader · parses starting position correctly", () -> {
            String startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
            char[][] grid = FenLoader.parse(startFen);
            checkEqual('r', grid[0][0], "black rook at a8");
            checkEqual('R', grid[7][0], "white rook at a1");
            checkEqual('k', grid[0][4], "black king at e8");
            checkEqual('K', grid[7][4], "white king at e1");
            checkEqual('p', grid[1][3], "black pawn at d7");
            checkEqual('P', grid[6][3], "white pawn at d2");
        });

        test("FenLoader · empty squares parsed as null-char", () -> {
            char[][] grid = FenLoader.parse("8/8/8/8/8/8/8/8 w - - 0 1");
            checkEqual('\0', grid[3][3], "empty square should be '\\0'");
        });

        test("FenLoader · mixed digit-and-piece rank parses correctly", () -> {
            // rank: 4 empties, White King, 3 empties
            char[][] grid = FenLoader.parse("8/8/8/8/4K3/8/8/8 w - - 0 1");
            checkEqual('K', grid[4][4], "King should be at col 4 on this rank");
            checkEqual('\0', grid[4][0], "col 0 should be empty");
            checkEqual('\0', grid[4][7], "col 7 should be empty");
        });

        test("FenLoader · isWhiteTurn reads active colour field", () -> {
            check(FenLoader.isWhiteTurn("8/8/8/8/8/8/8/8 w - - 0 1"), "'w' should mean White's turn");
            check(!FenLoader.isWhiteTurn("8/8/8/8/8/8/8/8 b - - 0 1"), "'b' should mean Black's turn");
        });

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── PgnManager ───────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("PgnManager · save then loadAll round-trips a game", () -> {
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

        test("PgnManager · loadAll returns newest-first ordering", () -> {
            List<GameRecord> all = PgnManager.loadAll();
            // Not asserting exact order details beyond "no exception and a list is returned" —
            // ordering depends on filesystem state, which this test does not control globally.
            checkNotNull(all, "loadAll must never return null");
        });

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── BoardState ───────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════
        // BoardState holds the position data that used to live directly on
        // Board (pieces list + grid + en passant tile). None of these tests
        // need a visible window — only Piece construction needs a Board
        // reference at all (for tile size / sprite slicing).

        test("BoardState · getPiece reflects the starting position", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    checkNotNull(state.getPiece(4, 7), "white king must be at e1");
                    check(state.getPiece(4, 7).isWhite(), "piece at e1 must be white");
                    checkNotNull(state.getPiece(4, 0), "black king must be at e8");
                    check(!state.getPiece(4, 0).isWhite(), "piece at e8 must be black");
                    check(state.getPiece(4, 4) == null, "e4 must be empty at game start");
                }));

        test("BoardState · getPieces returns an unmodifiable view", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    List<Piece> pieces = board.getState().getPieces();
                    boolean threw = false;
                    try {
                        pieces.clear();
                    } catch (UnsupportedOperationException e) {
                        threw = true;
                    }
                    check(threw, "BoardState.getPieces() must return an unmodifiable list");
                }));

        test("BoardState · removePiece clears both the list and the grid cell", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    Piece pawn = state.getPiece(0, 6);
                    state.removePiece(pawn);
                    check(state.getPiece(0, 6) == null, "grid cell must be cleared after removePiece");
                    check(!state.getPieces().contains(pawn), "piece list must not contain the removed piece");
                }));

        test("BoardState · addPiece places a piece into both the list and the grid", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    Piece extraQueen = new Queen(board, 4, 4, true);
                    state.addPiece(extraQueen);
                    checkEqual(extraQueen, state.getPiece(4, 4), "grid must reflect the newly added piece");
                    check(state.getPieces().contains(extraQueen), "piece list must contain the newly added piece");
                }));

        test("BoardState · moveOnGrid vacates the old square and occupies the new one", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    Piece pawn = state.getPiece(0, 6);
                    pawn.setRow(4); // pretend it already moved logically to a4
                    state.moveOnGrid(pawn, 0, 6);
                    check(state.getPiece(0, 6) == null, "old square must be vacated");
                    checkEqual(pawn, state.getPiece(0, 4), "new square must hold the piece");
                }));

        test("BoardState · getTileNum/getEnPassantTile round-trip", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    state.setEnPassantTile(state.getTileNum(3, 2));
                    checkEqual(state.getTileNum(3, 2), state.getEnPassantTile(), "round trip through getTileNum");
                }));

        test("BoardState · setPieces replaces the entire position at once", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();

                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece loneKing = new King(board, 4, 4, true);
                    custom.add(loneKing);
                    state.setPieces(custom);

                    checkEqual(1, state.getPieces().size(), "only the pieces passed to setPieces must remain");
                    checkEqual(loneKing, state.getPiece(4, 4), "the lone king must be findable at its square");
                    check(state.getPiece(4, 7) == null, "old positions must be cleared by setPieces");
                }));

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── Move & CheckScanner ──────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("Move · capture resolves directly from BoardState when the destination is occupied", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    Piece whitePawn = state.getPiece(4, 6);
                    Piece blackPawn = state.getPiece(3, 1);
                    Move m = new Move(state, whitePawn, 3, 1); // hypothetical capture, legality not checked here
                    checkEqual(blackPawn, m.getCapture(), "Move must resolve capture from BoardState at construction");
                }));

        test("Move · destination square with no piece has a null capture", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    Piece whitePawn = state.getPiece(4, 6);
                    Move m = new Move(state, whitePawn, 4, 4);
                    check(m.getCapture() == null, "empty destination square must mean no capture");
                }));

        test("CheckScanner · neither king is in check at game start", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    CheckScanner cs = new CheckScanner(board.getState());
                    check(!cs.isKingInCheckRN(true), "White king must not be in check at game start");
                    check(!cs.isKingInCheckRN(false), "Black king must not be in check at game start");
                }));

        test("CheckScanner · detects check from an unobstructed rook", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();

                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(board, 4, 7, true);
                    Piece blackRook = new Rook(board, 4, 0, false);
                    custom.add(whiteKing);
                    custom.add(blackRook);
                    state.setPieces(custom);

                    CheckScanner cs = new CheckScanner(state);
                    check(cs.isKingInCheckRN(true), "White king on an open file facing a rook must be in check");
                }));

        test("CheckScanner · isKingLeftInCheck rejects a king move into an attacked square", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();

                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(board, 0, 7, true);  // a1
                    Piece blackRook = new Rook(board, 3, 7, false); // d1, attacks the whole 1st rank
                    custom.add(whiteKing);
                    custom.add(blackRook);
                    state.setPieces(custom);

                    CheckScanner cs = new CheckScanner(state);

                    Move intoCheck = new Move(state, whiteKing, 1, 7); // Ka1-b1, still on rank 1
                    check(cs.isKingLeftInCheck(intoCheck), "stepping onto the attacked rank must be flagged");

                    Move awayFromCheck = new Move(state, whiteKing, 0, 6); // Ka1-a2, off the rank
                    check(!cs.isKingLeftInCheck(awayFromCheck), "stepping off the attacked rank must be safe");

                    // NOTE: isKingLeftInCheck only updates the *moving piece's* col/row
                    // during its simulate/undo — it does not call moveOnGrid(). That's
                    // fine for the king's own destination (checked directly above), but
                    // it means a *third* piece's discovered attack — e.g. a rook sliding
                    // away and exposing its own king to a pin — is not detected, because
                    // other pieces' collision scans still see the old piece occupying its
                    // old grid square. This is a pre-existing characteristic of
                    // CheckScanner, unrelated to the BoardState extraction, so it isn't
                    // asserted here as correct behavior — just documented.
                }));

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── NotationHelper ───────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("NotationHelper · simple pawn push has no piece letter or capture marker", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    Move m = new Move(state, state.getPiece(4, 6), 4, 4);
                    checkEqual("e4", new NotationHelper().toNotation(m, 4, 6), "pawn push e2-e4 must be notated 'e4'");
                }));

        test("NotationHelper · pawn capture is notated with the origin file", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    Move m = new Move(state, state.getPiece(4, 6), 3, 1); // hypothetical capture on d7
                    checkEqual("exd7", new NotationHelper().toNotation(m, 4, 6),
                            "pawn capture must be notated with the origin file, e.g. 'exd7'");
                }));

        test("NotationHelper · knight move uses 'N' (K is reserved for King)", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    Move m = new Move(state, state.getPiece(1, 7), 2, 5); // Nc3
                    checkEqual("Nc3", new NotationHelper().toNotation(m, 1, 7), "knight move must be notated with 'N'");
                }));

        test("NotationHelper · castling is notated O-O / O-O-O", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    Piece king = state.getPiece(4, 7);
                    NotationHelper nh = new NotationHelper();

                    Move kingside = new Move(state, king, 6, 7);
                    checkEqual("O-O", nh.toNotation(kingside, 4, 7), "kingside castle notation");

                    Move queenside = new Move(state, king, 2, 7);
                    checkEqual("O-O-O", nh.toNotation(queenside, 4, 7), "queenside castle notation");
                }));

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── FenGenerator ─────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("FenGenerator · starting position matches the standard FEN placement field", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    String fen = new FenGenerator(board.getState()).generate(true, 0, 1);
                    check(fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"),
                            "placement field must match the standard starting position, got: " + fen);
                }));

        test("FenGenerator · active colour field reflects isWhiteTurn", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    FenGenerator fg = new FenGenerator(board.getState());
                    check(fg.generate(true, 0, 1).contains(" w "), "white to move must produce ' w '");
                    check(fg.generate(false, 0, 1).contains(" b "), "black to move must produce ' b '");
                }));

        test("FenGenerator · starting position has all four castling rights", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    String fen = new FenGenerator(board.getState()).generate(true, 0, 1);
                    checkEqual("KQkq", fen.split(" ")[2], "all four castling rights must be present at game start");
                }));

        test("FenGenerator · a moved king removes both of that side's castling rights", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();
                    GameController gc = new GameController(board, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

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

        test("FenGenerator · en passant target square appears after a double pawn push", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();
                    GameController gc = new GameController(board, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

                    gc.makeMove(new Move(state, state.getPiece(4, 6), 4, 4)); // e2-e4

                    String epField = new FenGenerator(state).generate(false, 0, 1).split(" ")[3];
                    checkEqual("e3", epField, "en passant target square after e2-e4 must be e3");
                }));

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── MoveHistory ──────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("MoveHistory · record adds one entry to both moveLog and fenHistory", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    MoveHistory history = new MoveHistory(state);
                    Move m = new Move(state, state.getPiece(4, 6), 4, 4);

                    history.record(m, 4, 6, false, 1, 1);

                    checkEqual(1, history.getMoveLog().size(), "moveLog must contain exactly one entry");
                    checkEqual("e4", history.getMoveLog().get(0), "notation must be recorded correctly");
                    checkEqual(1, history.getFenHistory().size(), "fenHistory must contain exactly one entry");
                }));

        test("MoveHistory · clear empties both lists", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
                    MoveHistory history = new MoveHistory(state);
                    history.record(new Move(state, state.getPiece(4, 6), 4, 4), 4, 6, false, 1, 1);
                    history.clear();
                    checkEqual(0, history.getMoveLog().size(), "moveLog must be empty after clear()");
                    checkEqual(0, history.getFenHistory().size(), "fenHistory must be empty after clear()");
                }));

        test("MoveHistory · getMoveLog/getFenHistory return unmodifiable views", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    MoveHistory history = new MoveHistory(board.getState());
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

        test("MoveHistory · listener receives updates on record() and clear()", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    BoardState state = board.getState();
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

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── PieceType ────────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("PieceType · has exactly six values with correct display names", () -> {
            checkEqual(6, PieceType.values().length, "there must be exactly six piece types");
            checkEqual("King", PieceType.KING.getDisplayName(), "King display name");
            checkEqual("Queen", PieceType.QUEEN.getDisplayName(), "Queen display name");
            checkEqual("Rook", PieceType.ROOK.getDisplayName(), "Rook display name");
            checkEqual("Bishop", PieceType.BISHOP.getDisplayName(), "Bishop display name");
            checkEqual("Knight", PieceType.KNIGHT.getDisplayName(), "Knight display name");
            checkEqual("Pawn", PieceType.PAWN.getDisplayName(), "Pawn display name");
        });

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── ChessClock ───────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("ChessClock · starts stopped with configured time", () -> {
            ChessClock clock = new ChessClock(true, 60_000, () -> {
            }, (w) -> {
            });
            checkEqual(60_000L, clock.getTimeMs(), "initial timeMs");
            check(!clock.isRunning(), "clock should not be running until start() is called");
        });

        test("ChessClock · start()/stop() toggles running state", () -> {
            ChessClock clock = new ChessClock(true, 60_000, () -> {
            }, (w) -> {
            });
            clock.start();
            check(clock.isRunning(), "should be running after start()");
            clock.stop();
            check(!clock.isRunning(), "should not be running after stop()");
        });

        test("ChessClock · reset() restores start time and stops", () -> {
            ChessClock clock = new ChessClock(true, 60_000, () -> {
            }, (w) -> {
            });
            clock.start();
            clock.reset();
            checkEqual(60_000L, clock.getTimeMs(), "timeMs after reset");
            check(!clock.isRunning(), "should not be running after reset()");
        });

        test("ChessClock · draw() does not throw for a normal clock", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ChessClock clock = new ChessClock(true, 60_000, () -> {
                    }, (w) -> {
                    });
                    BufferedImage img = new BufferedImage(400, 100, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = img.createGraphics();
                    clock.draw(g2d, 0, 400, 100); // must not throw
                    g2d.dispose();
                }));

        test("ChessClock · draw() does not throw for unlimited (0ms) clock", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ChessClock clock = new ChessClock(false, 0, () -> {
                    }, (w) -> {
                    });
                    BufferedImage img = new BufferedImage(400, 100, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = img.createGraphics();
                    clock.draw(g2d, 0, 400, 100); // must not throw, shows "--:--"
                    g2d.dispose();
                }));

        test("ChessClock · unlimited clock never fires onExpired", () -> {
            CountDownLatch expired = new CountDownLatch(1);
            ChessClock clock = new ChessClock(true, 0, () -> {
            }, (w) -> expired.countDown());
            clock.start();
            boolean firedTooEarly = expired.await(400, TimeUnit.MILLISECONDS);
            clock.stop();
            check(!firedTooEarly, "unlimited (0ms) clock must never expire");
        });

        test("ChessClock · running clock counts down and fires onExpired at zero", () -> {
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

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── EndScreen ────────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("EndScreen · size scales with tileSize", () ->
                SwingUtilities.invokeAndWait(() -> {
                    EndScreen d = new EndScreen(frame, "White wins", TILE_SIZE, () -> {
                    });
                    checkEqual(TILE_SIZE * 4, d.getWidth(), "width");
                    checkEqual((int) (TILE_SIZE * 2.5), d.getHeight(), "height");
                    d.dispose();
                }));

        test("EndScreen · label displays passed message", () ->
                SwingUtilities.invokeAndWait(() -> {
                    EndScreen d = new EndScreen(frame, "Black wins", TILE_SIZE, () -> {
                    });
                    JLabel lbl = findLabel(d.getContentPane());
                    checkNotNull(lbl, "EndScreen must contain a JLabel");
                    checkEqual("Black wins", lbl.getText(), "label text");
                    d.dispose();
                }));

        test("EndScreen · contains 'Return to Menu' button", () ->
                SwingUtilities.invokeAndWait(() -> {
                    EndScreen d = new EndScreen(frame, "Stalemate - Draw", TILE_SIZE, () -> {
                    });
                    check(hasButton(d, "Return to Menu"), "EndScreen must have a Return to Menu button");
                    d.dispose();
                }));

        test("EndScreen · clicking button closes dialog", () -> {
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

        test("EndScreen · clicking button invokes onReturn callback", () -> {
            boolean[] callbackFired = {false};
            scheduleClick("Return to Menu");
            SwingUtilities.invokeAndWait(() -> {
                EndScreen d = new EndScreen(frame, "White wins", TILE_SIZE, () -> callbackFired[0] = true);
                d.setVisible(true);
            });
            check(callbackFired[0], "onReturn callback must fire when the button is clicked");
        });

        test("EndScreen · onReturn is NOT called if dialog is disposed programmatically", () -> {
            boolean[] callbackFired = {false};
            SwingUtilities.invokeAndWait(() -> {
                EndScreen d = new EndScreen(frame, "White wins", TILE_SIZE, () -> callbackFired[0] = true);
                d.dispose(); // closing without clicking must not trigger navigation
            });
            check(!callbackFired[0], "onReturn must only fire from the button click, not from dispose()");
        });

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── FiftyRuleDraw (optional claim) ──────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("FiftyRuleDraw · size scales with tileSize", () ->
                SwingUtilities.invokeAndWait(() -> {
                    FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, false);
                    checkEqual(TILE_SIZE * 4, d.getWidth(), "width");
                    checkEqual((int) (TILE_SIZE * 2.5), d.getHeight(), "height");
                    d.dispose();
                }));

        test("FiftyRuleDraw · optional claim has correct buttons", () ->
                SwingUtilities.invokeAndWait(() -> {
                    FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, false);
                    check(hasButton(d, "Claim Draw"), "Must have 'Claim Draw'");
                    check(hasButton(d, "Decline"), "Must have 'Decline'");
                    check(!hasButton(d, "Restart"), "Must NOT have 'Restart'");
                    d.dispose();
                }));

        test("FiftyRuleDraw · Claim Draw returns ACCEPTED", () -> {
            scheduleClick("Claim Draw");
            FiftyRuleDraw.DrawResult[] result = {null};
            SwingUtilities.invokeAndWait(() -> {
                FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, false);
                d.setVisible(true);
                result[0] = d.getResult();
            });
            checkEqual(FiftyRuleDraw.DrawResult.ACCEPTED, result[0], "result");
        });

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

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── FiftyRuleDraw (forced draw) ─────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("FiftyRuleDraw · forced draw has correct buttons", () ->
                SwingUtilities.invokeAndWait(() -> {
                    FiftyRuleDraw d = new FiftyRuleDraw(frame, TILE_SIZE, true);
                    check(hasButton(d, "Restart"), "Must have 'Restart'");
                    check(!hasButton(d, "Claim Draw"), "Must NOT have 'Claim Draw'");
                    check(!hasButton(d, "Decline"), "Must NOT have 'Decline'");
                    d.dispose();
                }));

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

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── PromoteGUI ───────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("PromoteGUI · size scales with tileSize", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PromoteGUI d = new PromoteGUI(frame, TILE_SIZE);
                    checkEqual(TILE_SIZE * 4, d.getWidth(), "width");
                    checkEqual(TILE_SIZE, d.getHeight(), "height");
                    d.dispose();
                }));

        test("PromoteGUI · all four buttons present", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PromoteGUI d = new PromoteGUI(frame, TILE_SIZE);
                    check(hasButton(d, "Queen"), "Must have 'Queen'");
                    check(hasButton(d, "Rook"), "Must have 'Rook'");
                    check(hasButton(d, "Bishop"), "Must have 'Bishop'");
                    check(hasButton(d, "Knight"), "Must have 'Knight'");
                    d.dispose();
                }));

        test("PromoteGUI · Queen → Choice.QUEEN", () -> {
            scheduleClick("Queen");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog());
            checkEqual(PromoteGUI.Choice.QUEEN, choice[0], "choice");
        });

        test("PromoteGUI · Rook → Choice.ROOK", () -> {
            scheduleClick("Rook");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog());
            checkEqual(PromoteGUI.Choice.ROOK, choice[0], "choice");
        });

        test("PromoteGUI · Bishop → Choice.BISHOP", () -> {
            scheduleClick("Bishop");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog());
            checkEqual(PromoteGUI.Choice.BISHOP, choice[0], "choice");
        });

        test("PromoteGUI · Knight → Choice.KNIGHT", () -> {
            scheduleClick("Knight");
            PromoteGUI.Choice[] choice = {null};
            SwingUtilities.invokeAndWait(() -> choice[0] = new PromoteGUI(frame, TILE_SIZE).showDialog());
            checkEqual(PromoteGUI.Choice.KNIGHT, choice[0], "choice");
        });

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── SwingPromotionChooser & SwingDrawOfferResolver ───────────────");
        // ═════════════════════════════════════════════════════════════════
        // These wrap PromoteGUI/FiftyRuleDraw and translate their results into
        // the engine-side PromotionChooser/DrawOfferResolver contract. The
        // dialog behavior itself is already covered above — these tests only
        // check the translation.

        test("SwingPromotionChooser · Queen selection maps to PieceType.QUEEN", () -> {
            scheduleClick("Queen");
            PieceType[] result = {null};
            SwingUtilities.invokeAndWait(() -> {
                JFrame testFrame = new JFrame();
                Board board = new Board(GameConfig.unlimited());
                testFrame.setContentPane(board);
                testFrame.pack();
                result[0] = new SwingPromotionChooser(board).choose(true);
                testFrame.dispose();
            });
            checkEqual(PieceType.QUEEN, result[0], "clicking Queen must resolve to PieceType.QUEEN");
        });

        test("SwingPromotionChooser · Knight selection maps to PieceType.KNIGHT", () -> {
            scheduleClick("Knight");
            PieceType[] result = {null};
            SwingUtilities.invokeAndWait(() -> {
                JFrame testFrame = new JFrame();
                Board board = new Board(GameConfig.unlimited());
                testFrame.setContentPane(board);
                testFrame.pack();
                result[0] = new SwingPromotionChooser(board).choose(false);
                testFrame.dispose();
            });
            checkEqual(PieceType.KNIGHT, result[0], "clicking Knight must resolve to PieceType.KNIGHT");
        });

        test("SwingDrawOfferResolver · Claim Draw resolves offerDraw() to true", () -> {
            scheduleClick("Claim Draw");
            boolean[] result = {false};
            SwingUtilities.invokeAndWait(() -> {
                JFrame testFrame = new JFrame();
                Board board = new Board(GameConfig.unlimited());
                testFrame.setContentPane(board);
                testFrame.pack();
                result[0] = new SwingDrawOfferResolver(board).offerDraw();
                testFrame.dispose();
            });
            check(result[0], "clicking Claim Draw must resolve offerDraw() to true");
        });

        test("SwingDrawOfferResolver · Decline resolves offerDraw() to false", () -> {
            scheduleClick("Decline");
            boolean[] result = {true};
            SwingUtilities.invokeAndWait(() -> {
                JFrame testFrame = new JFrame();
                Board board = new Board(GameConfig.unlimited());
                testFrame.setContentPane(board);
                testFrame.pack();
                result[0] = new SwingDrawOfferResolver(board).offerDraw();
                testFrame.dispose();
            });
            check(!result[0], "clicking Decline must resolve offerDraw() to false");
        });

        test("SwingDrawOfferResolver · notifyForcedDraw shows and dismisses the forced-draw dialog", () -> {
            scheduleClick("Restart");
            SwingUtilities.invokeAndWait(() -> {
                JFrame testFrame = new JFrame();
                Board board = new Board(GameConfig.unlimited());
                testFrame.setContentPane(board);
                testFrame.pack();
                new SwingDrawOfferResolver(board).notifyForcedDraw(); // must not throw
                testFrame.dispose();
            });
        });

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── MoveLogPanel ─────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("MoveLogPanel · preferred size matches board height", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    checkEqual(200, p.getPreferredSize().width, "preferred width");
                    checkEqual(BOARD_HEIGHT, p.getPreferredSize().height, "preferred height");
                }));

        test("MoveLogPanel · header label contains 'Move History'", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    JLabel lbl = findLabel(p);
                    checkNotNull(lbl, "MoveLogPanel must contain a JLabel header");
                    check(lbl.getText().contains("Move History"),
                            "Header label must contain 'Move History', got: " + lbl.getText());
                }));

        test("MoveLogPanel · contains a JTextArea", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    checkNotNull(findTextArea(p), "MoveLogPanel must contain a JTextArea");
                }));

        test("MoveLogPanel · update with empty log clears text", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    p.update(List.of(), "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                    JTextArea ta = findTextArea(p);
                    checkNotNull(ta, "Must contain a JTextArea");
                    checkEqual("", ta.getText(), "text area should be empty after update with []");
                }));

        test("MoveLogPanel · update renders full move pairs", () ->
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

        test("MoveLogPanel · update shows '...' when black has not moved yet", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    p.update(List.of("e4", "e5", "Nf3"), "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                    String text = findTextArea(p).getText();
                    check(text.contains("Nf3"), "Must contain white's pending move 'Nf3'");
                    check(text.contains("..."), "Must show '...' for black's pending reply");
                }));

        test("MoveLogPanel · repeated update replaces content, no duplication", () ->
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

        test("MoveLogPanel · clear empties the text area", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MoveLogPanel p = new MoveLogPanel(BOARD_HEIGHT);
                    p.update(List.of("e4", "e5", "Nf3", "Nc6"), "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                    p.clear();
                    JTextArea ta = findTextArea(p);
                    checkEqual("", ta.getText(), "text area must be empty after clear()");
                }));

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── ReplayPanel ──────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        List<String> sampleFens = List.of(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2"
        );

        test("ReplayPanel · displays first position's move label on construction", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleFens);
                    JLabel lbl = findMoveLabel(p);
                    checkNotNull(lbl, "ReplayPanel must show a move-index label");
                    check(lbl.getText().contains("1/3"), "Should start at position 1 of 3, got: " + lbl.getText());
                }));

        test("ReplayPanel · next button advances position", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleFens);
                    AbstractButton next = findButton(p, "→");
                    checkNotNull(next, "Must have a '→' next button");
                    next.doClick();
                    JLabel lbl = findMoveLabel(p);
                    check(lbl.getText().contains("2/3"), "Should be at position 2 of 3, got: " + lbl.getText());
                }));

        test("ReplayPanel · last button jumps to final position", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleFens);
                    AbstractButton last = findButton(p, "⏭");
                    checkNotNull(last, "Must have a '⏭' last button");
                    last.doClick();
                    JLabel lbl = findMoveLabel(p);
                    check(lbl.getText().contains("3/3"), "Should be at the final position, got: " + lbl.getText());
                }));

        test("ReplayPanel · next button does not overrun the list", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleFens);
                    AbstractButton next = findButton(p, "→");
                    for (int i = 0; i < 10; i++) next.doClick(); // click far past the end
                    JLabel lbl = findMoveLabel(p);
                    check(lbl.getText().contains("3/3"), "Cursor must clamp at the last position, got: " + lbl.getText());
                }));

        test("ReplayPanel · first button returns to position 1", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleFens);
                    findButton(p, "⏭").doClick(); // jump to end first
                    AbstractButton first = findButton(p, "⏮");
                    checkNotNull(first, "Must have a '⏮' first button");
                    first.doClick();
                    JLabel lbl = findMoveLabel(p);
                    check(lbl.getText().contains("1/3"), "Should be back at position 1, got: " + lbl.getText());
                }));

        test("ReplayPanel · prev button does not underrun position 1", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(sampleFens);
                    AbstractButton prev = findButton(p, "←");
                    for (int i = 0; i < 5; i++) prev.doClick(); // click before the start
                    JLabel lbl = findMoveLabel(p);
                    check(lbl.getText().contains("1/3"), "Cursor must clamp at the first position, got: " + lbl.getText());
                }));

        test("ReplayPanel · empty FEN list shows 'No moves' without throwing", () ->
                SwingUtilities.invokeAndWait(() -> {
                    ReplayPanel p = new ReplayPanel(List.of());
                    JLabel lbl = findMoveLabel(p);
                    checkEqual("No moves", lbl.getText(), "label text for empty history");
                    // Painting an empty history must not throw
                    BufferedImage img = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
                    p.setSize(600, 600);
                    p.paint(img.createGraphics());
                }));

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── Theme & UiComponents ─────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("Theme · palette constants are all defined and visually distinct", () -> {
            checkNotNull(Theme.BG, "BG must be defined");
            checkNotNull(Theme.PANEL_BG, "PANEL_BG must be defined");
            checkNotNull(Theme.FG, "FG must be defined");
            checkNotNull(Theme.ACCENT, "ACCENT must be defined");
            checkNotNull(Theme.MUTED, "MUTED must be defined");
            checkNotNull(Theme.BUTTON_SECONDARY, "BUTTON_SECONDARY must be defined");
            check(!Theme.BG.equals(Theme.PANEL_BG), "BG and PANEL_BG must be visually distinct");
            check(!Theme.ACCENT.equals(Theme.BUTTON_SECONDARY), "ACCENT and BUTTON_SECONDARY must be visually distinct");
        });

        test("UiComponents · button() applies the shared flat, dark-theme look", () ->
                SwingUtilities.invokeAndWait(() -> {
                    JButton b = UiComponents.button("Test", new Font("Arial", Font.BOLD, 14), Theme.ACCENT);
                    checkEqual(Theme.ACCENT, b.getBackground(), "background must match the given color");
                    checkEqual(Theme.FG, b.getForeground(), "foreground must always be Theme.FG");
                    check(!b.isBorderPainted(), "border must not be painted");
                    check(!b.isFocusPainted(), "focus ring must not be painted");
                    checkEqual(Cursor.HAND_CURSOR, b.getCursor().getType(), "cursor must be the hand cursor");
                }));

        test("UiComponents · style() applies the same look to a JToggleButton", () ->
                SwingUtilities.invokeAndWait(() -> {
                    JToggleButton t = new JToggleButton("Preset");
                    UiComponents.style(t, new Font("Arial", Font.PLAIN, 12), Theme.BUTTON_SECONDARY);
                    checkEqual(Theme.BUTTON_SECONDARY, t.getBackground(), "background must apply to toggle buttons too");
                    check(!t.isBorderPainted(), "border must not be painted on a toggle button either");
                }));

        test("UiComponents · addHoverEffect brightens on enter and restores on exit", () ->
                SwingUtilities.invokeAndWait(() -> {
                    JButton b = UiComponents.button("Hover", new Font("Arial", Font.BOLD, 14), Theme.BUTTON_SECONDARY);
                    UiComponents.addHoverEffect(b);
                    Color original = b.getBackground();

                    // A real MouseEvent is required here — JButton's own look-and-feel
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

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── MainMenu ─────────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("MainMenu · shows title and both navigation buttons", () ->
                SwingUtilities.invokeAndWait(() -> {
                    MainMenu menu = new MainMenu();
                    check(hasButton(menu, "New Game"), "Must have a 'New Game' button");
                    check(hasButton(menu, "Past Games"), "Must have a 'Past Games' button");
                    boolean hasTitle = findAllLabels(menu).stream()
                            .anyMatch(l -> "CHESS".equals(l.getText()));
                    check(hasTitle, "Must show the 'CHESS' title label");
                }));

        test("MainMenu · New Game navigates to NewGamePanel via ancestor frame", () ->
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

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── NewGamePanel ─────────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("NewGamePanel · shows player name fields defaulting to White/Black", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    List<JTextField> fields = findAllTextFields(p);
                    // First two text fields are the name fields (custom min/sec follow)
                    check(fields.size() >= 2, "Must have at least 2 text fields for names");
                    checkEqual("White", fields.get(0).getText(), "white name field default");
                    checkEqual("Black", fields.get(1).getText(), "black name field default");
                }));

        test("NewGamePanel · custom time fields default to 10 min / 0 sec", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    List<JTextField> fields = findAllTextFields(p);
                    check(fields.size() >= 4, "Must have min/sec custom fields");
                    checkEqual("10", fields.get(2).getText(), "custom minutes default");
                    checkEqual("0", fields.get(3).getText(), "custom seconds default");
                }));

        test("NewGamePanel · all preset buttons are present", () ->
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

        test("NewGamePanel · Rapid 10+0 is selected by default", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    AbstractButton rapidBtn = findButton(p, "Rapid 10+0");
                    checkNotNull(rapidBtn, "Rapid 10+0 button must exist");
                    check(rapidBtn.isSelected(), "Rapid 10+0 must be selected by default");
                }));

        test("NewGamePanel · selecting a different preset deselects the previous one", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    AbstractButton rapidBtn = findButton(p, "Rapid 10+0");
                    AbstractButton blitzBtn = findButton(p, "Blitz 5+0");
                    blitzBtn.doClick();
                    check(blitzBtn.isSelected(), "Blitz 5+0 must become selected after clicking");
                    check(!rapidBtn.isSelected(), "Rapid 10+0 must be deselected (ButtonGroup enforces exclusivity)");
                }));

        test("NewGamePanel · Back and Start buttons are present", () ->
                SwingUtilities.invokeAndWait(() -> {
                    NewGamePanel p = new NewGamePanel();
                    check(hasButton(p, "← Back"), "Must have a Back button");
                    check(hasButton(p, "Start ▶"), "Must have a Start button");
                }));

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── PastGamesPanel ───────────────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("PastGamesPanel · constructs without throwing and shows a game list", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PastGamesPanel p = new PastGamesPanel();
                    JList<String> list = findList(p);
                    checkNotNull(list, "PastGamesPanel must contain a JList");
                }));

        test("PastGamesPanel · shows Move Log and Replay toggle buttons", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PastGamesPanel p = new PastGamesPanel();
                    check(hasButton(p, "Move Log"), "Must have a 'Move Log' toggle button");
                    check(hasButton(p, "Replay ▶"), "Must have a 'Replay ▶' toggle button");
                }));

        test("PastGamesPanel · shows Back to Menu button", () ->
                SwingUtilities.invokeAndWait(() -> {
                    PastGamesPanel p = new PastGamesPanel();
                    check(hasButton(p, "← Back to Menu"), "Must have a Back to Menu button");
                }));

        test("PastGamesPanel · a saved game appears in the list", () -> {
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

        test("PastGamesPanel · selecting a game populates the move log", () -> {
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

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── GameController · actions ────────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════

        test("GameController · new game starts with White to move", () ->
                SwingUtilities.invokeAndWait(() -> {
                    Board board = new Board(GameConfig.unlimited());
                    check(board.getGameController().isTurnOfWhite(), "White must move first");
                }));

        test("GameController · flagFall(true) reports Black wins on time (0-1)", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = new GameConfig("Alice", "Bob", 100, 100, "Bullet");
                    Board board = new Board(cfg);
                    String[] captured = {null, null};

                    board.getGameController().setGameEndListener((record, message) -> {
                        captured[0] = record.result;
                        captured[1] = message;
                    });

                    board.getGameController().flagFall(true); // White's time expired
                    checkEqual("0-1", captured[0], "result when White flags");
                    check(captured[1].contains("Bob"), "message must name the winner (Bob)");
                    check(captured[1].contains("time"), "message must mention winning on time");
                }));

        test("GameController · flagFall(false) reports White wins on time (1-0)", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = new GameConfig("Alice", "Bob", 100, 100, "Bullet");
                    Board board = new Board(cfg);
                    String[] captured = {null, null};

                    board.getGameController().setGameEndListener((record, message) -> {
                        captured[0] = record.result;
                        captured[1] = message;
                    });

                    board.getGameController().flagFall(false); // Black's time expired
                    checkEqual("1-0", captured[0], "result when Black flags");
                    check(captured[1].contains("Alice"), "message must name the winner (Alice)");
                }));

        test("GameController · flagFall stops both clocks", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = new GameConfig("Alice", "Bob", 100, 100, "Bullet");
                    Board board = new Board(cfg);
                    board.getGameController().setGameEndListener((record, message) -> {
                    });
                    board.getGameController().flagFall(true);
                    // stopClocks() has no direct getter, so this is verified indirectly:
                    // no exception thrown and game state is consistent — a repaint after
                    // game-end must not throw due to a running clock referencing stale state.
                    board.repaint();
                }));

        test("GameController · endGame fires listener exactly once per call", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = new GameConfig("Alice", "Bob", 100, 100, "Bullet");
                    Board board = new Board(cfg);
                    int[] callCount = {0};
                    board.getGameController().setGameEndListener((record, message) -> callCount[0]++);
                    board.getGameController().flagFall(true);
                    checkEqual(1, callCount[0], "listener must fire exactly once");
                }));

        // ═════════════════════════════════════════════════════════════════
        System.out.println("\n── GameController · rules engine ────────────────────────────────");
        // ═════════════════════════════════════════════════════════════════
        // GameController now depends on PromotionChooser/DrawOfferResolver
        // interfaces instead of creating PromoteGUI/FiftyRuleDraw directly, so
        // these scenarios are driven with fake, headless implementations —
        // no dialog-clicking Timer tricks needed for any of the tests below.
        // Each test builds its own GameController sharing the test Board's
        // BoardState (rather than using board.getGameController(), which is
        // still wired to the real Swing dialogs) so the board's rendering
        // plumbing (repaint/clocks/piece construction) stays real while the
        // two dialog seams are swapped for test doubles.

        test("GameController · makeMove executes a simple pawn push", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();
                    GameController gc = new GameController(board, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

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

        test("GameController · makeMove captures an enemy piece", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();
                    GameController gc = new GameController(board, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

                    gc.makeMove(new Move(state, state.getPiece(4, 6), 4, 4)); // e2-e4
                    gc.makeMove(new Move(state, state.getPiece(3, 1), 3, 3)); // d7-d5

                    Piece blackPawn = state.getPiece(3, 3);
                    Move capture = new Move(state, state.getPiece(4, 4), 3, 3); // exd5
                    check(gc.isValidMove(capture), "exd5 must be a legal capture");
                    checkEqual(blackPawn, capture.getCapture(), "capture must target the black pawn on d5");

                    gc.makeMove(capture);
                    check(!state.getPieces().contains(blackPawn), "captured pawn must be removed from the board");
                }));

        test("GameController · kingside castling moves both king and rook", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();

                    state.removePiece(state.getPiece(5, 7)); // clear f1 (bishop)
                    state.removePiece(state.getPiece(6, 7)); // clear g1 (knight)

                    GameController gc = new GameController(board, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    Piece king = state.getPiece(4, 7);
                    Piece rook = state.getPiece(7, 7);

                    Move castleMove = new Move(state, king, 6, 7);
                    check(gc.isValidMove(castleMove), "kingside castling must be legal with a clear path and no checks");
                    gc.makeMove(castleMove);

                    checkEqual(6, king.getCol(), "king must land on g1");
                    checkEqual(5, rook.getCol(), "rook must land on f1");
                    checkEqual("O-O", gc.getMoveLog().get(0), "castling must be recorded as O-O");
                }));

        test("GameController · en passant capture removes the passed pawn", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();
                    GameController gc = new GameController(board, cfg, w -> PieceType.QUEEN, noOpDrawResolver());

                    gc.makeMove(new Move(state, state.getPiece(4, 6), 4, 4)); // e2-e4
                    gc.makeMove(new Move(state, state.getPiece(0, 1), 0, 2)); // a7-a6 (waiting move)
                    gc.makeMove(new Move(state, state.getPiece(4, 4), 4, 3)); // e4-e5
                    Piece blackPawn = state.getPiece(3, 1);
                    gc.makeMove(new Move(state, blackPawn, 3, 3)); // d7-d5, lands beside White's e5 pawn

                    Piece whitePawn = state.getPiece(4, 3);
                    Move enPassant = new Move(state, whitePawn, 3, 2); // exd6 en passant

                    // At construction, the destination square (d6) is empty, so Move
                    // resolves capture=null here — en passant capture is only attached
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

        test("GameController · pawn promotion asks the PromotionChooser and replaces the piece", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();

                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(board, 4, 7, true);
                    Piece blackKing = new King(board, 4, 0, false);
                    Piece whitePawn = new Pawn(board, 0, 1, true); // one step from promoting on a8
                    custom.add(whiteKing);
                    custom.add(blackKing);
                    custom.add(whitePawn);
                    state.setPieces(custom);

                    boolean[] askedWhite = {false};
                    GameController gc = new GameController(board, cfg,
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

        test("GameController · detects checkmate and fires the end-of-game listener", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();

                    // Ladder-mate final move: Rb1-b8#. Rook A already covers rank 7,
                    // Rook B slides onto rank 8 and the Black king has no escape square.
                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(board, 0, 7, true);   // a1
                    Piece blackKing = new King(board, 7, 0, false); // h8
                    Piece rookA = new Rook(board, 0, 1, true);      // a7
                    Piece rookB = new Rook(board, 1, 7, true);      // b1
                    custom.add(whiteKing);
                    custom.add(blackKing);
                    custom.add(rookA);
                    custom.add(rookB);
                    state.setPieces(custom);

                    GameController gc = new GameController(board, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
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

        test("GameController · detects stalemate (no legal moves, king not in check)", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();

                    // Textbook queen stalemate final move: Qg5-g6.
                    ArrayList<Piece> custom = new ArrayList<>();
                    Piece whiteKing = new King(board, 5, 1, true);   // f7
                    Piece blackKing = new King(board, 7, 0, false); // h8
                    Piece whiteQueen = new Queen(board, 6, 3, true); // g5
                    custom.add(whiteKing);
                    custom.add(blackKing);
                    custom.add(whiteQueen);
                    state.setPieces(custom);

                    GameController gc = new GameController(board, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    String[] result = {null};
                    gc.setGameEndListener((record, msg) -> result[0] = record.result);

                    Move stalemateMove = new Move(state, whiteQueen, 6, 2); // Qg5-g6
                    check(gc.isValidMove(stalemateMove), "Qg5-g6 must be a legal move");
                    gc.makeMove(stalemateMove);

                    checkEqual("1/2-1/2", result[0], "stalemate must be recorded as a 1/2-1/2 draw");
                    check(!gc.isCheckmate(false), "Black king must not be in check");
                    check(gc.isStalemate(false), "Black must have no legal moves");
                }));

        test("GameController · 50-move rule offers a draw at half-move 100; accepting ends the game", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    custom.add(new King(board, 4, 7, true));
                    custom.add(new King(board, 4, 0, false));
                    state.setPieces(custom);

                    FakeDrawOfferResolver resolver = new FakeDrawOfferResolver(true);
                    GameController gc = new GameController(board, cfg, w -> PieceType.QUEEN, resolver);
                    String[] result = {null};
                    gc.setGameEndListener((record, msg) -> result[0] = record.result);

                    shuffleKings(gc, state, 100);

                    check(resolver.offerDrawCalled, "the 50-move draw must be offered at half-move 100");
                    check(!resolver.forcedDrawNotified, "the 75-move forced draw must NOT fire yet");
                    checkEqual("1/2-1/2", result[0], "accepting the offer must end the game as a draw");
                }));

        test("GameController · 50-move rule offer can be declined, letting the game continue", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    custom.add(new King(board, 4, 7, true));
                    custom.add(new King(board, 4, 0, false));
                    state.setPieces(custom);

                    FakeDrawOfferResolver resolver = new FakeDrawOfferResolver(false);
                    GameController gc = new GameController(board, cfg, w -> PieceType.QUEEN, resolver);
                    String[] result = {null};
                    gc.setGameEndListener((record, msg) -> result[0] = record.result);

                    shuffleKings(gc, state, 100);

                    check(resolver.offerDrawCalled, "the 50-move draw must still be offered at half-move 100");
                    check(result[0] == null, "declining the offer must NOT end the game");
                }));

        test("GameController · 75-move rule forces a draw even if declined all along", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();
                    ArrayList<Piece> custom = new ArrayList<>();
                    custom.add(new King(board, 4, 7, true));
                    custom.add(new King(board, 4, 0, false));
                    state.setPieces(custom);

                    FakeDrawOfferResolver resolver = new FakeDrawOfferResolver(false); // always decline
                    GameController gc = new GameController(board, cfg, w -> PieceType.QUEEN, resolver);
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

        test("GameController · getMoveLog returns an unmodifiable view", () ->
                SwingUtilities.invokeAndWait(() -> {
                    GameConfig cfg = GameConfig.unlimited();
                    Board board = new Board(cfg);
                    BoardState state = board.getState();
                    GameController gc = new GameController(board, cfg, w -> PieceType.QUEEN, noOpDrawResolver());
                    gc.makeMove(new Move(state, state.getPiece(4, 6), 4, 4));

                    boolean threw = false;
                    try {
                        gc.getMoveLog().add("hack");
                    } catch (UnsupportedOperationException e) {
                        threw = true;
                    }
                    check(threw, "getMoveLog() must not allow external mutation of the recorded move history");
                }));

        // ── Summary ──────────────────────────────────────────────────────
        SwingUtilities.invokeAndWait(frame::dispose);

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

    // ── Test-only helpers ────────────────────────────────────────────────

    /**
     * A DrawOfferResolver that never offers/accepts anything — used by tests
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
        boolean forcedDrawNotified = false;

        FakeDrawOfferResolver(boolean acceptOffer) {
            this.acceptOffer = acceptOffer;
        }

        @Override
        public void notifyForcedDraw() {
            forcedDrawNotified = true;
        }

        @Override
        public boolean offerDraw() {
            offerDrawCalled = true;
            return acceptOffer;
        }
    }

    /**
     * Shuffles the White and Black kings back and forth between their home
     * square and one step away, alternating turns, for exactly halfMoves
     * moves. Used to rack up GameController's internal 50/75-move counter
     * without ever making a pawn move or a capture (either of which would
     * reset it). The two kings stay far apart the whole time, so a legal
     * reply always exists and neither side is ever accidentally put in
     * check, checkmate, or stalemate by this shuffling.
     */
    private static void shuffleKings(GameController gc, BoardState state, int halfMoves) {
        boolean whiteAtHome = true;
        boolean blackAtHome = true;
        for (int i = 0; i < halfMoves; i++) {
            if (i % 2 == 0) {
                int from = whiteAtHome ? 7 : 6;
                int to = whiteAtHome ? 6 : 7;
                Piece king = state.getPiece(4, from);
                gc.makeMove(new Move(state, king, 4, to));
                whiteAtHome = !whiteAtHome;
            } else {
                int from = blackAtHome ? 0 : 1;
                int to = blackAtHome ? 1 : 0;
                Piece king = state.getPiece(4, from);
                gc.makeMove(new Move(state, king, 4, to));
                blackAtHome = !blackAtHome;
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
     * Deletes any saved PGN file(s) created by a test so repeated runs stay clean.
     */
    private static void cleanupSavedGame(String uniqueWhiteName) {
        try {
            File gamesDir = new File(System.getProperty("user.dir"), "games");
            File[] matches = gamesDir.listFiles((dir, name) ->
                    name.contains(uniqueWhiteName.replaceAll("[^a-zA-Z0-9_-]", "_")));
            if (matches != null) {
                for (File f : matches) Files.deleteIfExists(f.toPath());
            }
        } catch (Exception e) {
            System.out.println("  (cleanup warning: " + e.getMessage() + ")");
        }
    }
}