package ui.board;

/*
 * Purpose: Board is the Swing panel that shows a running game. It paints the tiles, the pieces,
 * the legal move hints and both player clocks, and it translates between screen pixels and board
 * squares while turning the view towards the player to move. The position data itself lives in
 * BoardState and the rules in GameController, so this class stays focused on presentation and on
 * owning the two clocks.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.imports.BoardState;
import engine.model.GameConfig;
import engine.imports.GameController;
import engine.imports.Move;
import engine.pieces.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class Board extends JPanel {

    private final int tileSize = 85;
    private final int rows = 8;
    private final int cols = 8;
    private final int clockHeight = tileSize;

    private final BoardState state = new BoardState();
    private Piece selectedPiece;
    private final HashSet<Integer> legalMoveTiles = new HashSet<>();

    private final GameController gc;

    private final ChessClock whiteClock;
    private final ChessClock blackClock;
    // added to a player's clock after each of their moves
    private final long incrementMs;

    private final Color LIGHT_TILE = new Color(232, 235, 239);
    private final Color DARK_TILE = new Color(125, 135, 150);
    private final Color HINT_COLOR = new Color(81, 168, 0, 200);

    /**
     * Builds the game board for a new game.
     * <p>
     * A game needs its rules controller, two clocks, mouse input and the starting position. I create
     * the controller with the Swing dialogs, both clocks with the configured times, remember the
     * increment, size the panel for the board and the two clock bars, hook up the mouse, place the
     * pieces and start White's clock.
     * <p>
     * Time complexity: O(p) for placing the p starting pieces. Space complexity: O(p).
     *
     * @param pConfig names, times and increment of the new game, never null
     * @throws NullPointerException if pConfig is null
     */
    public Board(GameConfig pConfig) {
        this.gc = new GameController(this, pConfig, new SwingPromotionChooser(this), new SwingDrawOfferResolver(this));
        this.whiteClock = new ChessClock(true, pConfig.whiteTimeMs(), this::repaint, this::onTimeExpired);
        this.blackClock = new ChessClock(false, pConfig.blackTimeMs(), this::repaint, this::onTimeExpired);
        // the same increment applies to both players
        this.incrementMs = pConfig.incrementMs();

        this.setPreferredSize(new Dimension(cols * tileSize, rows * tileSize + clockHeight * 2));

        Input input = new Input(this, gc);
        this.addMouseListener(input);
        this.addMouseMotionListener(input);

        state.setPieces(addPieces());

        whiteClock.start();
    }

    public ArrayList<Piece> addPieces() {

        ArrayList<Piece> newGame = new ArrayList<>();

        newGame.add(new Rook(this, 0, 0, false));
        newGame.add(new Rook(this, 7, 0, false));
        newGame.add(new Knight(this, 1, 0, false));
        newGame.add(new Knight(this, 6, 0, false));
        newGame.add(new Bishop(this, 2, 0, false));
        newGame.add(new Bishop(this, 5, 0, false));
        newGame.add(new Queen(this, 3, 0, false));
        newGame.add(new King(this, 4, 0, false));

        newGame.add(new Rook(this, 0, 7, true));
        newGame.add(new Rook(this, 7, 7, true));
        newGame.add(new Knight(this, 1, 7, true));
        newGame.add(new Knight(this, 6, 7, true));
        newGame.add(new Bishop(this, 2, 7, true));
        newGame.add(new Bishop(this, 5, 7, true));
        newGame.add(new Queen(this, 3, 7, true));
        newGame.add(new King(this, 4, 7, true));

        for (int i = 0; i <= 7; i++) {
            newGame.add(new Pawn(this, i, 1, false));
            newGame.add(new Pawn(this, i, 6, true));
        }
        return newGame;
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        boolean whiteAtBottom = gc.isTurnOfWhite();
        int boardWidth = cols * tileSize;
        int bottomY = clockHeight + rows * tileSize;

        if (whiteAtBottom) {
            blackClock.draw(g2d, 0, boardWidth, clockHeight);
        } else {
            whiteClock.draw(g2d, 0, boardWidth, clockHeight);
        }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                g2d.setColor((c + r) % 2 == 0
                        ? LIGHT_TILE
                        : DARK_TILE);
                g2d.fillRect(toVisualX(c), toVisualY(r), tileSize, tileSize);
            }
        }

        if (selectedPiece != null) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (legalMoveTiles.contains(getTileNum(c, r))) {
                        g2d.setColor(HINT_COLOR);
                        g2d.fillRect(toVisualX(c), toVisualY(r), tileSize, tileSize);
                    }
                }
            }
        }

        for (Piece p : state.getPieces()) {
            if (p == selectedPiece) {
                p.paint(g2d, p.getxPos(), p.getyPos());
            } else {
                p.paint(g2d, toVisualX(p.getCol()), toVisualY(p.getRow()));
            }
        }

        if (whiteAtBottom) {
            whiteClock.draw(g2d, bottomY, boardWidth, clockHeight);
        } else {
            blackClock.draw(g2d, bottomY, boardWidth, clockHeight);
        }
    }

    /**
     * Hands the clock to the side to move according to the board's own controller.
     * <p>
     * Older callers don't say whose turn it is. I forward to the explicit version with the side to
     * move of the board's own controller.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    public void switchClocks() {
        switchClocks(gc.isTurnOfWhite());
    }

    /**
     * Starts the clock of the side to move and stops the other one.
     * <p>
     * The controller that just played a move knows best whose turn it is, and that also holds for a
     * controller other than the board's own one. I stop the clock of the side that just moved, add
     * the increment to it as a Fischer clock does, and start the clock of the side to move.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pWhiteToMove true if White is to move now, false if Black is
     */
    public void switchClocks(boolean pWhiteToMove) {
        // only the side to move uses up time, the side that just moved earns its increment
        if (pWhiteToMove) {
            blackClock.stop();
            blackClock.addTime(incrementMs);
            whiteClock.start();
        } else {
            whiteClock.stop();
            whiteClock.addTime(incrementMs);
            blackClock.start();
        }
    }

    public void stopClocks() {
        whiteClock.stop();
        blackClock.stop();
    }

    public void resetClocks() {
        whiteClock.reset();
        blackClock.reset();
        whiteClock.start();
    }

    /**
     * Tells whether either player's clock is currently counting down.
     * <p>
     * The end of a game has to freeze both clocks, and tests need a way to confirm that without
     * waiting for a flag to fall. I simply ask both clocks for their running state.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true if the white or the black clock is running, false when both are stopped
     */
    public boolean areClocksRunning() {
        // a single running clock is enough
        return whiteClock.isRunning() || blackClock.isRunning();
    }

    /**
     * Tells whether one player's clock is currently counting down.
     * <p>
     * Tests and the UI need to know whose time is running, for example while a draw claim is on the
     * screen. I return the running state of the requested clock.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pWhite true for White's clock, false for Black's
     * @return true if that clock is running
     */
    public boolean isClockRunning(boolean pWhite) {
        return pWhite ? whiteClock.isRunning() : blackClock.isRunning();
    }

    /**
     * Returns the time one player has left.
     * <p>
     * Tests and features such as the result logic need the exact remaining time of a player. I ask
     * the requested clock for its current value.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pWhite true for White's clock, false for Black's
     * @return remaining time in milliseconds, 0 for an unlimited clock
     */
    public long getRemainingTimeMs(boolean pWhite) {
        return pWhite ? whiteClock.getTimeMs() : blackClock.getTimeMs();
    }

    public int toVisualX(int col) {
        return (gc.isTurnOfWhite() ? col : 7 - col) * tileSize;
    }

    public int toVisualY(int row) {
        return clockHeight + (gc.isTurnOfWhite() ? row : 7 - row) * tileSize;
    }

    /**
     * Tells whether a point on the panel lies on one of the 64 squares.
     * <p>
     * The panel also contains the two clock bars, and mouse events can be delivered for points
     * outside the panel while a piece is dragged. I check that the point is inside the board width
     * and between the top and bottom clock bars.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pX horizontal panel coordinate in pixels, any value including negative ones
     * @param pY vertical panel coordinate in pixels, any value including negative ones
     * @return true if the point is on a board square
     */
    public boolean isOnBoard(int pX, int pY) {
        // the squares start below the top clock bar and end above the bottom one
        return pX >= 0 && pX < cols * tileSize
                && pY >= clockHeight && pY < clockHeight + rows * tileSize;
    }

    /**
     * Converts a horizontal panel coordinate into a board column.
     * <p>
     * The view is turned towards the player to move, so the same pixel belongs to a different
     * column for Black. I divide by the tile size with Math.floorDiv, which rounds down instead of
     * towards zero, so points left of the board never land on the first column, and mirror the
     * result when Black is to move. Callers check isOnBoard first.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pX horizontal panel coordinate in pixels
     * @return the column, 0 to 7 for points on the board and outside that range otherwise
     */
    public int toLogicalCol(int pX) {
        // floorDiv keeps negative coordinates off the first column
        int c = Math.floorDiv(pX, tileSize);
        return gc.isTurnOfWhite() ? c : 7 - c;
    }

    /**
     * Converts a vertical panel coordinate into a board row.
     * <p>
     * The squares start below the top clock bar and the view is turned towards the player to move. I
     * subtract the clock bar height, divide with Math.floorDiv so points above the board never land
     * on the first row, and mirror the result when Black is to move. Callers check isOnBoard first.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pY vertical panel coordinate in pixels
     * @return the row, 0 to 7 for points on the board and outside that range otherwise
     */
    public int toLogicalRow(int pY) {
        // floorDiv keeps points on the top clock bar off the first row
        int r = Math.floorDiv(pY - clockHeight, tileSize);
        return gc.isTurnOfWhite() ? r : 7 - r;
    }

    private void onTimeExpired(boolean isWhiteExpired) {
        gc.flagFall(isWhiteExpired);
    }

    // GETTER

    public Piece getPiece(int col, int row) {
        return state.getPiece(col, row);
    }

    public int getTileSize() {
        return tileSize;
    }

    public Piece getSelectedPiece() {
        return selectedPiece;
    }

    public int getTileNum(int col, int row) {
        return state.getTileNum(col, row);
    }

    public int getEnPassantTile() {
        return state.getEnPassantTile();
    }

    public List<Piece> getPieces() {
        return state.getPieces();
    }

    public GameController getGameController() {
        return gc;
    }

    public BoardState getState() {
        return state;
    }

    // SETTER

    public void setSelectedPiece(Piece selectedPiece) {
        this.selectedPiece = selectedPiece;
        legalMoveTiles.clear();

        if (selectedPiece != null) {
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (gc.isValidMove(new Move(state, selectedPiece, c, r))) {
                        legalMoveTiles.add(getTileNum(c, r));
                    }
                }
            }
        }
    }

    public void removePiece(Piece p) {
        state.removePiece(p);
    }

    public void setPieces(ArrayList<Piece> pieces) {
        state.setPieces(pieces);
    }

    public void setEnPassantTile(int enPassantTile) {
        state.setEnPassantTile(enPassantTile);
    }

    public void addPiece(Piece p) {
        state.addPiece(p);
    }

    // HELPER

    public void capture(Move m) {
        state.capture(m);
    }

    public void moveOnGrid(Piece p, int fromCol, int fromRow) {
        state.moveOnGrid(p, fromCol, fromRow);
    }
}