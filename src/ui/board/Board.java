package ui.board;

/*
 * Purpose: Board is the Swing panel that shows a running game. It paints the tiles, the pieces, the
 * legal move hints and both player clocks, and it translates between screen pixels and the squares
 * the engine counts in, while turning the view towards the player to move. The game itself lives in
 * a GameSession on the bitboard core, so this class holds no position data of its own and only asks
 * the session what stands where and which squares a picked up piece may go to.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.core.Bitboards;
import engine.core.GameSession;
import engine.core.MoveGen;
import engine.core.Pieces;
import engine.model.GameConfig;

import javax.swing.*;
import java.awt.*;

public class Board extends JPanel implements GameSession.View {

    // edge length of one square in pixels when no size is given
    public static final int DEFAULT_TILE_SIZE = 85;
    // smallest square that still shows the pieces clearly
    public static final int MIN_TILE_SIZE = 40;
    // room the window frame and title bar take away from the screen
    private static final int WINDOW_FRAME_PX = 60;
    // width of the move log next to the board
    private static final int MOVE_LOG_WIDTH_PX = 200;
    // stands for "no square is selected"
    private static final int NO_SQUARE = -1;

    private final int tileSize;
    private final int rows = 8;
    private final int cols = 8;
    // each clock bar is as high as one square
    private final int clockHeight;

    private final GameSession session;

    // square of the piece the mouse picked up, or NO_SQUARE while nothing is dragged
    private int selectedSquare = NO_SQUARE;
    // squares that piece may move to, filled when it is picked up
    private final int[] targets = new int[MoveGen.MAX_MOVES];
    private int targetCount;
    // pixel position of the dragged piece, it follows the mouse instead of sitting on its square
    private int dragX;
    private int dragY;

    // piece images scaled to this board's square size
    private final PieceSprites sprites;

    private final ChessClock whiteClock;
    private final ChessClock blackClock;
    // added to a player's clock after each of their moves
    private final long incrementMs;

    private final Color LIGHT_TILE = new Color(232, 235, 239);
    private final Color DARK_TILE = new Color(125, 135, 150);
    private final Color HINT_COLOR = new Color(81, 168, 0, 200);

    /**
     * Builds the game board for a new game with squares of the default size.
     * <p>
     * Tests and callers that don't care about the screen keep the size the board always had. I
     * forward to the full constructor with 85 pixel squares.
     * <p>
     * Time complexity: O(p) for the p starting pieces. Space complexity: O(1) beyond the session.
     *
     * @param pConfig names, times and increment of the new game, never null
     * @throws NullPointerException if pConfig is null
     */
    public Board(GameConfig pConfig) {
        this(pConfig, DEFAULT_TILE_SIZE);
    }

    /**
     * Builds the game board for a new game with squares of a given size.
     * <p>
     * A game needs a session to play in, two clocks, mouse input and a size that fits the player's
     * screen. I store the square size first, since everything else is measured in squares, create
     * the session on the starting position and hand it this board as its view, the promotion dialog
     * and the draw dialogs, build both clocks with the configured times, size the panel for the board
     * and the two clock bars, hook up the mouse and start White's clock.
     * <p>
     * Time complexity: O(p) for the p starting pieces. Space complexity: O(s^2) for the sprites
     * scaled to squares of s pixels.
     *
     * @param pConfig   names, times and increment of the new game, never null
     * @param pTileSize edge length of one square in pixels, at least MIN_TILE_SIZE
     * @throws NullPointerException     if pConfig is null
     * @throws IllegalArgumentException if pTileSize is smaller than MIN_TILE_SIZE
     */
    public Board(GameConfig pConfig, int pTileSize) {
        // pieces this small would be hard to see and click
        if (pTileSize < MIN_TILE_SIZE) {
            throw new IllegalArgumentException("tile size " + pTileSize + " is below " + MIN_TILE_SIZE);
        }
        this.tileSize = pTileSize;
        this.clockHeight = pTileSize;
        // the board draws the pieces, so it owns their images
        this.sprites = new PieceSprites(pTileSize);

        this.session = new GameSession();
        session.setView(this);
        session.setPromotionPicker(new SwingPromotionChooser(this));
        session.setDrawArbiter(new SwingDrawOfferResolver(this));

        this.whiteClock = new ChessClock(true, pConfig.whiteTimeMs(), this::repaint, this::onTimeExpired);
        this.blackClock = new ChessClock(false, pConfig.blackTimeMs(), this::repaint, this::onTimeExpired);
        // the same increment applies to both players
        this.incrementMs = pConfig.incrementMs();

        this.setPreferredSize(new Dimension(cols * tileSize, rows * tileSize + clockHeight * 2));

        Input input = new Input(this, session);
        this.addMouseListener(input);
        this.addMouseMotionListener(input);

        whiteClock.start();
    }

    /**
     * Picks the largest square size that lets the game screen fit into a screen area.
     * <p>
     * The board used fixed 85 pixel squares with a clock bar above and below, 850 pixels in total,
     * which doesn't fit on a 1366 by 768 laptop once the window frame and the task bar take their
     * share. The game screen needs ten squares of height for the board and both clock bars plus the
     * window frame, and eight squares of width plus the move log. I take the largest square that
     * fits in both directions, but never more than the default size and never less than the minimum.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pAvailableWidth  usable screen width in pixels
     * @param pAvailableHeight usable screen height in pixels
     * @return square size in pixels, between MIN_TILE_SIZE and DEFAULT_TILE_SIZE
     */
    public static int tileSizeFor(int pAvailableWidth, int pAvailableHeight) {
        // ten squares high: eight rows and two clock bars, plus the window frame
        int byHeight = (pAvailableHeight - WINDOW_FRAME_PX) / 10;
        // eight squares wide next to the move log, plus the window frame
        int byWidth = (pAvailableWidth - MOVE_LOG_WIDTH_PX - WINDOW_FRAME_PX) / 8;
        return Math.max(MIN_TILE_SIZE, Math.min(DEFAULT_TILE_SIZE, Math.min(byHeight, byWidth)));
    }

    /**
     * Paints the clocks, the board, the move hints and every piece.
     * <p>
     * The view is turned towards the player to move, so the clock of the waiting player is drawn at
     * the top and the one of the player to move at the bottom. I draw the squares, then the hints for
     * a picked up piece, then every piece the position holds, with the dragged one following the
     * mouse instead of sitting on its square.
     * <p>
     * Time complexity: O(64) for the squares plus O(p) for the p pieces.
     * Space complexity: O(1), the sprites are cached.
     *
     * @param pGraphics graphics context handed in by Swing, never null
     */
    @Override
    public void paintComponent(Graphics pGraphics) {
        super.paintComponent(pGraphics);
        Graphics2D g2d = (Graphics2D) pGraphics;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        boolean whiteAtBottom = session.isWhiteToMove();
        int boardWidth = cols * tileSize;
        int bottomY = clockHeight + rows * tileSize;

        if (whiteAtBottom) {
            blackClock.draw(g2d, 0, boardWidth, clockHeight);
        } else {
            whiteClock.draw(g2d, 0, boardWidth, clockHeight);
        }

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                g2d.setColor((col + row) % 2 == 0 ? LIGHT_TILE : DARK_TILE);
                g2d.fillRect(toVisualX(col), toVisualY(row), tileSize, tileSize);
            }
        }

        // the squares a picked up piece may go to
        for (int index = 0; index < targetCount; index++) {
            g2d.setColor(HINT_COLOR);
            g2d.fillRect(toVisualX(colOf(targets[index])), toVisualY(rowOf(targets[index])), tileSize, tileSize);
        }

        for (int square = 0; square < Bitboards.SQUARE_COUNT; square++) {
            int piece = session.position().pieceAt(square);
            if (piece == Pieces.NONE) {
                continue;
            }
            // the piece under the mouse follows the cursor, all others sit on their square
            if (square == selectedSquare) {
                g2d.drawImage(sprites.spriteForPiece(piece), dragX, dragY, null);
            } else {
                g2d.drawImage(sprites.spriteForPiece(piece),
                        toVisualX(colOf(square)), toVisualY(rowOf(square)), null);
            }
        }

        if (whiteAtBottom) {
            whiteClock.draw(g2d, bottomY, boardWidth, clockHeight);
        } else {
            blackClock.draw(g2d, bottomY, boardWidth, clockHeight);
        }
    }

    /**
     * Picks up the piece on a square and works out where it may go.
     * <p>
     * A player can only pick up a piece of the side to move, and only while the game is running. I
     * remember the square and ask the session for the squares that piece may move to, which is what
     * the hints are painted from. Anything else clears the selection.
     * <p>
     * Time complexity: O(m) for the m legal moves of the position. Space complexity: O(1).
     *
     * @param pSquare square that was pressed, 0 to 63
     */
    public void selectSquare(int pSquare) {
        int piece = session.position().pieceAt(pSquare);
        boolean ownPiece = piece != Pieces.NONE
                && Pieces.isWhite(piece) == session.isWhiteToMove();
        if (!ownPiece) {
            clearSelection();
            return;
        }
        selectedSquare = pSquare;
        targetCount = session.targetsFrom(pSquare, targets);
    }

    /**
     * Drops whatever was picked up.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    public void clearSelection() {
        selectedSquare = NO_SQUARE;
        targetCount = 0;
    }

    /**
     * Remembers where the dragged piece is drawn.
     * <p>
     * While a piece is dragged it hangs on the mouse instead of standing on a square. The board
     * draws, so the board keeps that pixel position, and it is only read while a piece is selected.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pX horizontal panel coordinate of the sprite's upper left corner, any value
     * @param pY vertical panel coordinate of the sprite's upper left corner, any value
     */
    public void setDragPosition(int pX, int pY) {
        this.dragX = pX;
        this.dragY = pY;
    }

    /**
     * Starts the clock of the side to move and stops the other one.
     * <p>
     * The session hands the clock over after every move. I stop the clock of the side that just
     * moved, add the increment to it as a Fischer clock does, and start the clock of the side to
     * move.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pWhiteToMove true if White is to move now, false if Black is
     */
    @Override
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

    /**
     * Stops both clocks because the game has ended.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    @Override
    public void stopClocks() {
        whiteClock.stop();
        blackClock.stop();
    }

    /**
     * Puts both clocks back to their starting times and starts White's.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    @Override
    public void resetClocks() {
        whiteClock.reset();
        blackClock.reset();
        whiteClock.start();
    }

    /**
     * Tells whether either player's clock is currently counting down.
     * <p>
     * The end of a game has to freeze both clocks, and tests need a way to confirm that without
     * waiting for a flag to fall.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true if the white or the black clock is running
     */
    public boolean areClocksRunning() {
        return whiteClock.isRunning() || blackClock.isRunning();
    }

    /**
     * Tells whether one player's clock is currently counting down.
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
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pWhite true for White's clock, false for Black's
     * @return remaining time in milliseconds, 0 for an unlimited clock
     */
    public long getRemainingTimeMs(boolean pWhite) {
        return pWhite ? whiteClock.getTimeMs() : blackClock.getTimeMs();
    }

    /**
     * Turns a board column into the pixel where its square starts.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pCol column, 0 for the a-file up to 7 for the h-file
     * @return the horizontal pixel of that column's left edge
     */
    public int toVisualX(int pCol) {
        return (session.isWhiteToMove() ? pCol : 7 - pCol) * tileSize;
    }

    /**
     * Turns a board row into the pixel where its square starts.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pRow row, 0 for rank 8 down to 7 for rank 1
     * @return the vertical pixel of that row's upper edge
     */
    public int toVisualY(int pRow) {
        return clockHeight + (session.isWhiteToMove() ? pRow : 7 - pRow) * tileSize;
    }

    /**
     * Tells whether a point on the panel lies on one of the 64 squares.
     * <p>
     * The panel also contains the two clock bars, and mouse events can be delivered for points
     * outside the panel while a piece is dragged.
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
     * The view is turned towards the player to move, so the same pixel belongs to a different column
     * for Black. Math.floorDiv rounds down instead of towards zero, so points left of the board never
     * land on the first column. Callers check isOnBoard first.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pX horizontal panel coordinate in pixels
     * @return the column, 0 to 7 for points on the board
     */
    public int toLogicalCol(int pX) {
        int col = Math.floorDiv(pX, tileSize);
        return session.isWhiteToMove() ? col : 7 - col;
    }

    /**
     * Converts a vertical panel coordinate into a board row.
     * <p>
     * The squares start below the top clock bar and the view is turned towards the player to move.
     * Callers check isOnBoard first.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pY vertical panel coordinate in pixels
     * @return the row, 0 to 7 for points on the board
     */
    public int toLogicalRow(int pY) {
        int row = Math.floorDiv(pY - clockHeight, tileSize);
        return session.isWhiteToMove() ? row : 7 - row;
    }

    /**
     * Turns a column and a row of the screen into the square the engine counts with.
     * <p>
     * The board draws rank eight at the top, so its row 0 is rank 8, while the engine numbers a1 as
     * square 0 and counts upwards. Flipping the row is the whole difference between the two.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pCol column, 0 to 7
     * @param pRow row, 0 to 7 where 0 is rank 8
     * @return the square number, 0 to 63
     */
    public static int squareAt(int pCol, int pRow) {
        return Bitboards.square(pCol, 7 - pRow);
    }

    /**
     * Returns the column a square is drawn in.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare square number, 0 to 63
     * @return the column, 0 to 7
     */
    public static int colOf(int pSquare) {
        return Bitboards.fileOf(pSquare);
    }

    /**
     * Returns the row a square is drawn in.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare square number, 0 to 63
     * @return the row, 0 for rank 8 down to 7 for rank 1
     */
    public static int rowOf(int pSquare) {
        return 7 - Bitboards.rankOf(pSquare);
    }

    /**
     * Ends the game because a clock ran out.
     * <p>
     * The clocks report a flag fall with the colour whose time is gone, and the session decides what
     * that means, including the case where the other side could never mate and the game is drawn.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pIsWhiteExpired true if White's clock ran out, false if Black's did
     */
    private void onTimeExpired(boolean pIsWhiteExpired) {
        session.flagFall(pIsWhiteExpired ? Pieces.WHITE : Pieces.BLACK);
    }

    // GETTER

    public GameSession getSession() {
        return session;
    }

    public int getTileSize() {
        return tileSize;
    }

    public PieceSprites getSprites() {
        return sprites;
    }

    /**
     * Returns the square the mouse picked a piece up on.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the selected square, or -1 while nothing is picked up
     */
    public int getSelectedSquare() {
        return selectedSquare;
    }

    /**
     * Returns how many squares the picked up piece may move to.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the number of highlighted squares, 0 when nothing is picked up
     */
    public int getTargetCount() {
        return targetCount;
    }
}
