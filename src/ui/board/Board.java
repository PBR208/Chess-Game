package ui.board;

/*
 * Purpose: Board is the Swing panel that shows a running game. It paints the tiles, the pieces, the
 * legal move hints, the move that was just played, a king in check and both player clocks, and it
 * translates between screen pixels and the squares the engine counts in. Between two people at one
 * screen it turns the view towards whoever is to move; against the program it holds still and faces
 * the person, because there is only one of them and a board that turned would hand them their
 * opponent's view. A move can also be typed rather than moved with the mouse, which is why this panel
 * takes the keyboard focus. It can also be asked what to play here and what the other side is
 * threatening, and draws either as an arrow, working both out away from the thread that draws so the
 * window stays alive while it thinks. The game itself lives in a GameSession on the bitboard core,
 * so this class holds no position data of its own and only asks the session what stands where and
 * which squares a picked up piece may go to.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.core.Bitboards;
import engine.core.GameSession;
import engine.core.MoveGen;
import engine.core.Moves;
import engine.core.Pieces;
import engine.core.Position;
import engine.core.San;
import engine.model.EngineSettings;
import engine.model.GameConfig;
import engine.search.Analyst;
import engine.search.Searcher;
import ui.i18n.Messages;
import ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Locale;

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

    // who the second player is, which also decides whether the board turns round after a move
    private final EngineSettings settings;

    // square of the piece the mouse picked up, or NO_SQUARE while nothing is dragged
    private int selectedSquare = NO_SQUARE;
    // squares that piece may move to, filled when it is picked up
    private final int[] targets = new int[MoveGen.MAX_MOVES];
    private int targetCount;
    // pixel position of the dragged piece, it follows the mouse instead of sitting on its square
    private int dragX;
    private int dragY;

    // the click that says a move was accepted, silent on a machine with no audio
    private final MoveSounds sounds = new MoveSounds();

    // the move a player is typing, empty while nobody is typing one
    private final StringBuilder typedMove = new StringBuilder();

    // longer than any move ever written, so a key held down cannot grow this without end
    private static final int MAX_TYPED_LENGTH = 10;

    // the characters moves are written with, in algebraic notation and in the plain square to
    // square form. Everything else, including the keys that mean enter and backspace, is ignored.
    private static final String MOVE_CHARACTERS = "abcdefgh12345678NBRQKOox=-+#0";

    // piece images scaled to this board's square size
    private final PieceSprites sprites;

    private final ChessClock whiteClock;
    private final ChessClock blackClock;
    // while a game is paused both clocks stand still and the board takes no moves
    private boolean paused;

    // told when a paused game runs again, so the program can take a turn it was holding back
    private Runnable onResume = () -> {
    };

    // laid over the squares while the game is paused, dark enough to say "not now"
    private static final Color PAUSE_VEIL = new Color(0, 0, 0, 150);
    // what both clocks showed after each ply, White's first, index 0 being the start of the game
    private final ArrayList<ChessClock.Reading[]> clockSnapshots = new ArrayList<>();

    private final Color LIGHT_TILE = new Color(232, 235, 239);
    private final Color DARK_TILE = new Color(125, 135, 150);
    // the square markings live in the theme, because which colours can be told apart is a decision
    // about the whole program rather than about this panel
    private final Color HINT_COLOR = Theme.HINT;

    // A hint and a threat have to be told apart at a glance, and they are often on the board at the
    // same time. Blue and orange stay distinguishable for a red green blind player, which green and
    // red would not.
    private static final Color ADVICE_COLOR = new Color(0, 114, 178, 220);
    private static final Color THREAT_COLOR = new Color(230, 126, 34, 220);

    // How hard to think about a hint. It has to answer while somebody is waiting for it, so it is
    // capped three ways over and the time cap is the one that usually decides.
    private static final Searcher.Limits ADVICE_LIMITS = new Searcher.Limits(4, 200_000, 1_500);

    // the move a hint is offering, and the one the other side is threatening, or NONE for neither
    private int hintMove = Moves.NONE;
    private int threatMove = Moves.NONE;

    // works hints and threats out, and can be called off when the position moves on
    private final Analyst analyst = new Analyst();

    // true while one is being worked out, so a key held down cannot start a second
    private volatile boolean advising;

    // counts the positions advice was cleared for, so an answer about an earlier one is recognised
    private volatile int adviceGeneration;

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
     * Builds the game board for a game between two people at one screen.
     * <p>
     * Most games are played by two people taking turns at one screen and begin where chess begins,
     * so this is the ordinary way in. I forward to the full constructor with the standard starting
     * position and settings that have no engine in them, which is also what makes the board turn
     * round after every move.
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
        this(pConfig, pTileSize, Position.startPosition(), EngineSettings.humanOpponent());
    }

    /**
     * Builds the game board for a new game against a given opponent.
     * <p>
     * Time complexity: O(p) for the p starting pieces. Space complexity: O(s^2) for the sprites
     * scaled to squares of s pixels.
     *
     * @param pConfig   names, times and increment of the new game, never null
     * @param pTileSize edge length of one square in pixels, at least MIN_TILE_SIZE
     * @param pSettings who the second player is and which way the board faces, never null
     * @throws NullPointerException     if pConfig or pSettings is null
     * @throws IllegalArgumentException if pTileSize is smaller than MIN_TILE_SIZE
     */
    public Board(GameConfig pConfig, int pTileSize, EngineSettings pSettings) {
        this(pConfig, pTileSize, Position.startPosition(), pSettings);
    }

    /**
     * Builds the game board for a game between two people that starts from a given position.
     * <p>
     * Time complexity: O(p) for the p pieces of the position. Space complexity: O(s^2) for the
     * sprites scaled to squares of s pixels.
     *
     * @param pConfig   names, times and increment of the new game, never null
     * @param pTileSize edge length of one square in pixels, at least MIN_TILE_SIZE
     * @param pStart    position the game begins from, never null
     * @throws NullPointerException     if pConfig or pStart is null
     * @throws IllegalArgumentException if pTileSize is smaller than MIN_TILE_SIZE
     */
    public Board(GameConfig pConfig, int pTileSize, Position pStart) {
        this(pConfig, pTileSize, pStart, EngineSettings.humanOpponent());
    }

    /**
     * Builds the game board for a game from a given position against a given opponent.
     * <p>
     * A game does not have to begin from the standard position: it can start from one that was set
     * up in the position editor or loaded from a FEN, which is what studying an endgame needs. The
     * session already accepts a position to begin from, so this only has to pass one on rather than
     * place any pieces itself. I store the square size first, since everything else is measured in
     * squares, create the session on that position and hand it this board as its view, the promotion
     * dialog and the draw dialogs, build both clocks with the configured times, size the panel for
     * the board and the two clock bars, hook up the mouse and start the clock of the side to move.
     * The settings are kept because they decide which way round the board is drawn, and against the
     * program they also decide who answers a draw offer or a takeback: the program itself, rather than
     * a dialog the person would have to answer on its behalf.
     * <p>
     * Time complexity: O(p) for the p pieces of the position. Space complexity: O(s^2) for the
     * sprites scaled to squares of s pixels.
     *
     * @param pConfig   names, times and increment of the new game, never null
     * @param pTileSize edge length of one square in pixels, at least MIN_TILE_SIZE
     * @param pStart    position the game begins from, never null
     * @param pSettings who the second player is and which way the board faces, never null
     * @throws NullPointerException     if pConfig, pStart or pSettings is null
     * @throws IllegalArgumentException if pTileSize is smaller than MIN_TILE_SIZE
     */
    public Board(GameConfig pConfig, int pTileSize, Position pStart, EngineSettings pSettings) {
        // pieces this small would be hard to see and click
        if (pTileSize < MIN_TILE_SIZE) {
            throw new IllegalArgumentException("tile size " + pTileSize + " is below " + MIN_TILE_SIZE);
        }
        this.tileSize = pTileSize;
        this.clockHeight = pTileSize;
        this.settings = pSettings;
        // the board draws the pieces, so it owns their images
        this.sprites = new PieceSprites(pTileSize);

        this.session = new GameSession(pStart);
        session.setView(this);
        session.setPromotionPicker(new SwingPromotionChooser(this));
        session.setDrawArbiter(new SwingDrawOfferResolver(this));
        // a draw one player offers the other is a different question from a draw the rules allow, and
        // the program answers an offer itself rather than asking the person to answer for it
        session.setDrawOfferArbiter(pSettings.engineOpponent()
                ? new EngineDrawOfferArbiter(this) : new SwingDrawOfferArbiter(this));
        // a game on a clock asks the opponent before a move is taken back, a casual one does not, and
        // the program never minds
        session.setTakebackArbiter(new SwingTakebackArbiter(this,
                pConfig.whiteTimeMs() > 0 && !pSettings.engineOpponent()));

        // both clocks play the same time control, but they may start from different times
        this.whiteClock = new ChessClock(true, pConfig.whiteTimeMs(), pConfig.clockMode(),
                pConfig.incrementMs(), pConfig.delayMs(), this::repaint, this::onTimeExpired);
        this.blackClock = new ChessClock(false, pConfig.blackTimeMs(), pConfig.clockMode(),
                pConfig.incrementMs(), pConfig.delayMs(), this::repaint, this::onTimeExpired);
        // both players play the same tournament control, each counting their own moves through it
        this.whiteClock.setStages(pConfig.stages());
        this.blackClock.setStages(pConfig.stages());
        // the clocks before a single move was played, which is where taking back the first move leads
        clockSnapshots.add(readClocks());

        this.setPreferredSize(new Dimension(cols * tileSize, rows * tileSize + clockHeight * 2));

        Input input = new Input(this, session);
        this.addMouseListener(input);
        this.addMouseMotionListener(input);

        // typing only reaches a component that holds the keyboard focus, and a player who clicks
        // the board has said plainly enough that this is what they are working with
        this.setFocusable(true);
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent pEvent) {
                requestFocusInWindow();
            }
        });
        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent pEvent) {
                // a character the move took is used up, so an h on the h-file does not ask for a hint
                if (typeCharacter(pEvent.getKeyChar())) {
                    pEvent.consume();
                }
            }

            @Override
            public void keyPressed(KeyEvent pEvent) {
                switch (pEvent.getKeyCode()) {
                    case KeyEvent.VK_ENTER -> submitTypedMove();
                    case KeyEvent.VK_BACK_SPACE -> backspaceTypedMove();
                    case KeyEvent.VK_ESCAPE -> clearTypedMove();
                    default -> {
                        // every other key is either a move character or none of my business
                    }
                }
            }
        });

        // H asks what to play and T asks what is coming. Bound to the window rather than to this
        // panel, so they work without the player first having to click the board to give it focus.
        // An h typed into a move on the focused board is taken by the move instead, see keyTyped.
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('h'), "hint");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('t'), "threat");
        getActionMap().put("hint", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent pEvent) {
                showHint(null);
            }
        });
        getActionMap().put("threat", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent pEvent) {
                showThreat(null);
            }
        });

        // a position that was set up may have Black to move, and then Black's time runs first
        if (session.isWhiteToMove()) {
            whiteClock.start();
        } else {
            blackClock.start();
        }
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

        boolean whiteAtBottom = isWhiteAtBottom();
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

        // the move that was just played, under the hints so a square a piece may go to still wins
        int lastFrom = getLastMoveFrom();
        if (lastFrom >= 0) {
            int lastTo = getLastMoveTo();
            g2d.setColor(Theme.LAST_MOVE);
            g2d.fillRect(toVisualX(colOf(lastFrom)), toVisualY(rowOf(lastFrom)), tileSize, tileSize);
            g2d.fillRect(toVisualX(colOf(lastTo)), toVisualY(rowOf(lastTo)), tileSize, tileSize);
        }

        // the squares a picked up piece may go to
        for (int index = 0; index < targetCount; index++) {
            g2d.setColor(HINT_COLOR);
            g2d.fillRect(toVisualX(colOf(targets[index])), toVisualY(rowOf(targets[index])), tileSize, tileSize);
        }

        // a king in check, over everything else, because it is the most urgent thing on the board
        int checkedKing = checkSquare();
        if (checkedKing >= 0) {
            g2d.setColor(Theme.CHECK);
            g2d.fillRect(toVisualX(colOf(checkedKing)), toVisualY(rowOf(checkedKing)), tileSize, tileSize);
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

        // over the pieces, because an arrow behind them says nothing
        if (threatMove != Moves.NONE) {
            drawArrow(g2d, threatMove, THREAT_COLOR);
        }
        if (hintMove != Moves.NONE) {
            drawArrow(g2d, hintMove, ADVICE_COLOR);
        }

        if (whiteAtBottom) {
            whiteClock.draw(g2d, bottomY, boardWidth, clockHeight);
        } else {
            blackClock.draw(g2d, bottomY, boardWidth, clockHeight);
        }

        // after the pieces, so a move being typed is never hidden behind one
        drawTypedMove(g2d);

        // a paused game has to look paused, or a player waits for a board that is ignoring them
        if (paused) {
            g2d.setColor(PAUSE_VEIL);
            g2d.fillRect(0, clockHeight, boardWidth, rows * tileSize);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, tileSize / 2));
            FontMetrics metrics = g2d.getFontMetrics();
            String text = Messages.get("game.paused");
            g2d.drawString(text, (boardWidth - metrics.stringWidth(text)) / 2,
                    clockHeight + rows * tileSize / 2);
        }
    }

    /**
     * Draws one move as an arrow from the square it starts on to the one it ends on.
     * <p>
     * A move is two squares, and naming them in writing makes a player hunt for them. An arrow says
     * it without being read. The line stops short of the middle of the target square so the head
     * sits inside it rather than over the piece standing there, and the head is drawn as a filled
     * triangle turned along the line, which is why the arrow reads the same in every direction.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) beyond the shape.
     *
     * @param pGraphics graphics context of this panel, never null
     * @param pMove     the move to draw
     * @param pColour   the colour to draw it in, never null
     */
    private void drawArrow(Graphics2D pGraphics, int pMove, Color pColour) {
        int from = Moves.from(pMove);
        int to = Moves.to(pMove);
        int half = tileSize / 2;

        int startX = toVisualX(colOf(from)) + half;
        int startY = toVisualY(rowOf(from)) + half;
        int endX = toVisualX(colOf(to)) + half;
        int endY = toVisualY(rowOf(to)) + half;

        double angle = Math.atan2(endY - startY, endX - startX);
        int headLength = Math.max(10, tileSize / 3);
        // the line ends where the head begins, so the two do not overlap into a blob
        int lineEndX = endX - (int) (Math.cos(angle) * headLength * 0.8);
        int lineEndY = endY - (int) (Math.sin(angle) * headLength * 0.8);

        pGraphics.setColor(pColour);
        pGraphics.setStroke(new BasicStroke(Math.max(3f, tileSize / 10f),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        pGraphics.drawLine(startX, startY, lineEndX, lineEndY);

        // a triangle turned along the line, so the arrow points the same way whatever the direction
        Path2D head = new Path2D.Double();
        head.moveTo(endX, endY);
        head.lineTo(endX - Math.cos(angle - Math.PI / 7) * headLength,
                endY - Math.sin(angle - Math.PI / 7) * headLength);
        head.lineTo(endX - Math.cos(angle + Math.PI / 7) * headLength,
                endY - Math.sin(angle + Math.PI / 7) * headLength);
        head.closePath();
        pGraphics.fill(head);
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
     * Works out what to play here and shows it, waiting for the answer.
     * <p>
     * The hint comes from the same search the program plays with, so it can never be advice the
     * program itself would not take. This waits, which is what makes it usable from a test; the game
     * screen presses a key and gets the version that does not wait.
     * <p>
     * Time complexity: as for a search with the advice limits, capped in depth, positions and time.
     * Space complexity: O(1) beyond the copy the search works on.
     *
     * @return true if there was a move to suggest
     */
    public boolean showHintNow() {
        int generation = adviceGeneration;
        int move = analyst.analyse(session.position(), ADVICE_LIMITS).bestMove;
        // advice about a position the game has left since would point at the wrong board
        if (generation == adviceGeneration) {
            hintMove = move;
        }
        repaint();
        return move != Moves.NONE;
    }

    /**
     * Works out what the other side is threatening and shows it, waiting for the answer.
     * <p>
     * Time complexity: as for a search with the advice limits. Space complexity: O(1) beyond the copy.
     *
     * @return true if there was a threat to show
     */
    public boolean showThreatNow() {
        int generation = adviceGeneration;
        int move = analyst.threatMove(session.position(), ADVICE_LIMITS);
        if (generation == adviceGeneration) {
            threatMove = move;
        }
        repaint();
        return move != Moves.NONE;
    }

    /**
     * Asks what to play here without holding the board up.
     * <p>
     * The thinking happens away from the thread that draws, so the window stays alive while it goes
     * on, and the arrow appears back on that thread because that is where everything else drawn on
     * this panel is decided. Asking twice at once does nothing the second time.
     * <p>
     * Time complexity: O(1) here. Space complexity: O(1).
     *
     * @param pAfter run on the drawing thread once the answer is showing, may be null
     * @return true if the question was asked, false if one was already being answered
     */
    public boolean showHint(Runnable pAfter) {
        return adviseInBackground(this::showHintNow, pAfter);
    }

    /**
     * Asks what the other side is threatening without holding the board up.
     * <p>
     * Time complexity: O(1) here. Space complexity: O(1).
     *
     * @param pAfter run on the drawing thread once the answer is showing, may be null
     * @return true if the question was asked, false if one was already being answered
     */
    public boolean showThreat(Runnable pAfter) {
        return adviseInBackground(this::showThreatNow, pAfter);
    }

    /**
     * Runs one piece of advice away from the drawing thread.
     * <p>
     * Time complexity: O(1) here, the thinking costs what the advice limits allow elsewhere.
     * Space complexity: O(1).
     *
     * @param pWork  the waiting version of the question, never null
     * @param pAfter run on the drawing thread when it is answered, may be null
     * @return true if the work was started
     */
    private boolean adviseInBackground(Runnable pWork, Runnable pAfter) {
        if (advising) {
            return false;
        }
        advising = true;
        Thread thread = new Thread(() -> {
            try {
                pWork.run();
            } finally {
                // an answer that threw must not leave the board refusing to be asked again
                advising = false;
            }
            SwingUtilities.invokeLater(() -> {
                repaint();
                if (pAfter != null) {
                    pAfter.run();
                }
            });
        }, "board advice");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    /**
     * Tells whether White is drawn along the bottom edge.
     * <p>
     * Two people at one screen want the board to turn after every move, so whoever is to move always
     * sees their own pieces nearest to them and reaches for the right end. Against the program that
     * is exactly wrong: there is only one person sitting there, and a board that spun away every time
     * the program answered would hand them the opponent's view of their own game. So a game against
     * the program holds the board still, facing the side the person is playing.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true if White is at the bottom of the screen
     */
    private boolean isWhiteAtBottom() {
        return settings.autoFlip()
                ? session.isWhiteToMove()
                : settings.humanColour() == Pieces.WHITE;
    }

    /**
     * Returns who this board thinks the second player is.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the settings the board was built with, never null
     */
    public EngineSettings getSettings() {
        return settings;
    }

    /**
     * Plays a move a player made on this board.
     * <p>
     * Every move a player makes comes through here, whether it was dragged, clicked or typed, so this
     * is where a paused game refuses it, where a move on the program's turn is refused, and where an
     * accepted move clicks. The session still decides
     * whether the move is legal, and it is also what remembers the move, so the board can mark the
     * last move however it was played, including one played again after a takeback.
     * <p>
     * Time complexity: O(m) for the m legal moves the session checks, plus the cost of the move.
     * Space complexity: O(1).
     *
     * @param pMove packed move to play, or Moves.NONE when the two squares make no move at all
     * @return true if the move was played
     */
    public boolean playMove(int pMove) {
        // a paused game takes no moves, whichever way they arrive, and the program moves its own pieces
        if (paused || settings.playsFor(session.position().sideToMove())) {
            return false;
        }
        // a pair of squares that is no legal move simply puts the piece back
        if (pMove == Moves.NONE || !session.play(pMove)) {
            return false;
        }
        // a move that was accepted should say so through more than one sense
        sounds.playMove();
        repaint();
        return true;
    }

    /**
     * Takes the hint and the threat off the board.
     * <p>
     * Advice is about one position, so it stops being true as soon as anybody moves, a move is taken
     * back or the game starts again, which is why every one of those clears it. Anything still being
     * worked out is called off as well, and its answer is dropped when it arrives, because it would
     * be about a board that has already gone.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    public void clearAdvice() {
        adviceGeneration++;
        analyst.cancel();
        hintMove = Moves.NONE;
        threatMove = Moves.NONE;
        repaint();
    }

    /**
     * Takes the last move back, or against the program the last move the person made.
     * <p>
     * The session decides whether a move comes back at all, because a timed game between two people
     * asks the opponent first. Against the program one move back would hand the turn to the program,
     * which would simply play its answer again, so I keep taking moves back until the person is to
     * move, which also covers a takeback while the program is still thinking.
     * <p>
     * Time complexity: O(p) for the p plies each undo rebuilds its repetition counts from.
     * Space complexity: O(1).
     *
     * @return true if a move was taken back
     */
    public boolean takeBack() {
        if (!session.requestTakeback()) {
            return false;
        }
        while (session.canUndo() && settings.playsFor(session.position().sideToMove())) {
            session.undo();
        }
        return true;
    }

    /**
     * Plays a taken back move again, and against the program the answer it had given as well.
     * <p>
     * Time complexity: O(m) for the m legal moves of each move played again. Space complexity: O(1).
     *
     * @return true if a move was played again
     */
    public boolean replayMove() {
        if (!session.redo()) {
            return false;
        }
        // the program's answer was taken back together with the person's move, so it comes back too
        while (session.canRedo() && settings.playsFor(session.position().sideToMove())) {
            session.redo();
        }
        return true;
    }

    /**
     * Returns the square of a king that is in check.
     * <p>
     * A check is the one thing on the board a player must not miss, and spotting it means scanning
     * the whole position for whatever is attacking the king. Only the side to move can be in check,
     * because the other side being in check would mean the move before it was illegal.
     * <p>
     * Time complexity: O(1), a handful of attack lookups. Space complexity: O(1).
     *
     * @return the square the king in check stands on, or -1 when nobody is in check
     */
    public int checkSquare() {
        int sideToMove = session.position().sideToMove();
        if (!MoveGen.isInCheck(session.position(), sideToMove)) {
            return NO_SQUARE;
        }
        return session.position().kingSquare(sideToMove);
    }

    /**
     * Returns the square the last move started from.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the square, or -1 before anybody has moved
     */
    public int getLastMoveFrom() {
        // the session knows the last move however it was played, and forgets one that was taken back
        int move = session.lastMove();
        return move == Moves.NONE ? NO_SQUARE : Moves.from(move);
    }

    /**
     * Returns the square the last move ended on.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the square, or -1 before anybody has moved
     */
    public int getLastMoveTo() {
        int move = session.lastMove();
        return move == Moves.NONE ? NO_SQUARE : Moves.to(move);
    }

    /**
     * Returns the move sound of this board, so it can be switched off.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the move sound, never null
     */
    public MoveSounds getSounds() {
        return sounds;
    }

    /**
     * Adds one typed character to the move being entered.
     * <p>
     * Moving with the mouse asks a player to place a piece inside a square, which is a demand a
     * keyboard does not make. Typing the move is also how anybody reading the move log already
     * thinks about it. Characters that appear in no move are dropped, which is what the keys that
     * mean enter or backspace look like from here, and a paused or finished game accepts nothing.
     * <p>
     * Time complexity: O(1). Space complexity: O(1), the text has a fixed limit.
     *
     * @param pCharacter the character that was typed
     * @return true if the character became part of the move
     */
    public boolean typeCharacter(char pCharacter) {
        boolean acceptable = MOVE_CHARACTERS.indexOf(pCharacter) >= 0;
        if (!acceptable || paused || session.result().isFinished() || typedMove.length() >= MAX_TYPED_LENGTH) {
            return false;
        }
        typedMove.append(pCharacter);
        repaint();
        return true;
    }

    /**
     * Returns the move a hint is currently offering.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the packed move, or Moves.NONE when no hint is showing
     */
    public int getHintMove() {
        return hintMove;
    }

    /**
     * Returns the move the other side is currently shown to be threatening.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the packed move, or Moves.NONE when no threat is showing
     */
    public int getThreatMove() {
        return threatMove;
    }

    /**
     * Removes the last typed character.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    public void backspaceTypedMove() {
        if (typedMove.length() > 0) {
            typedMove.setLength(typedMove.length() - 1);
            repaint();
        }
    }

    /**
     * Throws away the move being typed.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    public void clearTypedMove() {
        if (typedMove.length() > 0) {
            typedMove.setLength(0);
            repaint();
        }
    }

    /**
     * Returns the move that is being typed.
     * <p>
     * Time complexity: O(n) in the length of the text. Space complexity: O(n) for the copy.
     *
     * @return what has been typed so far, empty when nothing is being typed; never null
     */
    public String getTypedMove() {
        return typedMove.toString();
    }

    /**
     * Plays the move that was typed, if it is one.
     * <p>
     * A move that was played clears the line, ready for the next one. A move that means nothing in
     * this position leaves the text alone, because throwing away what somebody typed over a single
     * wrong character is a poor answer when the fix is one backspace away.
     * <p>
     * Time complexity: O(m) for the m legal moves of the position. Space complexity: O(1).
     *
     * @return true if a move was played
     */
    public boolean submitTypedMove() {
        int move = moveForText(getTypedMove());
        if (!playMove(move)) {
            return false;
        }
        typedMove.setLength(0);
        repaint();
        return true;
    }

    /**
     * Works out which legal move a piece of text names.
     * <p>
     * Two ways of writing a move are worth understanding. The square to square form, e2e4, needs no
     * knowledge of the position at all and is what a chess engine speaks, so I try it first. Failing
     * that the text is read as algebraic notation, which is what the move log shows, so a player can
     * type back exactly what they have just read. I compare against the notation of every legal move
     * rather than taking the text apart, which means the one place that writes notation is also the
     * one place that decides what it means. Check and mate marks are ignored, because a player who
     * types them is right and should not be punished for it, and a castling written with zeros is
     * read as the letter O that the standard actually asks for.
     * <p>
     * Time complexity: O(m) for the m legal moves of the position, each written out once.
     * Space complexity: O(m) for the generated moves.
     *
     * @param pText the move as it was typed, may be anything; may be null
     * @return the packed move, or Moves.NONE when the text names no legal move
     */
    public int moveForText(String pText) {
        if (pText == null || pText.isBlank() || session.result().isFinished()) {
            return Moves.NONE;
        }
        String wanted = pText.trim();

        int[] legalMoves = new int[MoveGen.MAX_MOVES];
        int count = MoveGen.generateLegal(session.position(), legalMoves, 0);

        int square = squareToSquareMove(wanted, legalMoves, count);
        if (square != Moves.NONE) {
            return square;
        }

        String normalised = withoutMarks(wanted);
        for (int index = 0; index < count; index++) {
            if (withoutMarks(San.of(session.position(), legalMoves[index])).equals(normalised)) {
                return legalMoves[index];
            }
        }
        return Moves.NONE;
    }

    /**
     * Reads a move written as the two squares it joins, such as e2e4 or e7e8q.
     * <p>
     * This is the form a chess engine speaks, and it is unambiguous without knowing the position.
     * A promotion needs the letter of the piece, because the same two squares stand for four
     * different moves, and without it I return nothing rather than guessing at a queen.
     * <p>
     * Time complexity: O(m) for the m legal moves. Space complexity: O(1).
     *
     * @param pText       the typed text, never null
     * @param pMoves      the legal moves of this position, never null
     * @param pCount      how many of them there are
     * @return the packed move, or Moves.NONE when the text is not this form or names no legal move
     */
    private int squareToSquareMove(String pText, int[] pMoves, int pCount) {
        if (pText.length() < 4 || pText.length() > 5) {
            return Moves.NONE;
        }
        String lower = pText.toLowerCase(Locale.ROOT);
        int from;
        int to;
        try {
            from = Bitboards.squareOf(lower.substring(0, 2));
            to = Bitboards.squareOf(lower.substring(2, 4));
        } catch (IllegalArgumentException e) {
            // not two square names, so this is not the square to square form
            return Moves.NONE;
        }

        String promotion = lower.length() == 5 ? "=" + lower.substring(4) : "";
        for (int index = 0; index < pCount; index++) {
            int move = pMoves[index];
            if (Moves.from(move) != from || Moves.to(move) != to) {
                continue;
            }
            if (promotion.isEmpty()) {
                // the same two squares mean four moves for a promoting pawn, so it has to be said
                if (!Moves.isPromotion(move)) {
                    return move;
                }
            } else if (San.of(session.position(), move).toLowerCase(Locale.ROOT).contains(promotion)) {
                return move;
            }
        }
        return Moves.NONE;
    }

    /**
     * Strips the marks that say nothing about which move was meant.
     * <p>
     * Check and mate marks describe what the move does rather than which move it is, and zeros are
     * what a keyboard offers somebody trying to write the letter O of a castling.
     * <p>
     * Time complexity: O(n) in the length of the text. Space complexity: O(n) for the result.
     *
     * @param pText move text, never null
     * @return the text without check marks and with castling zeros turned into letters
     */
    private static String withoutMarks(String pText) {
        return pText.replace("+", "").replace("#", "").replace('0', 'O');
    }

    /**
     * Draws the move that is being typed along the bottom of the board.
     * <p>
     * A player typing a move has to see what the program thinks they typed, otherwise a mistyped
     * character is only discovered when the move is refused. I draw nothing at all while nobody is
     * typing, so the board is unchanged for anybody using the mouse.
     * <p>
     * Time complexity: O(n) in the length of the typed text. Space complexity: O(1).
     *
     * @param pGraphics graphics context of this panel, never null
     */
    private void drawTypedMove(Graphics2D pGraphics) {
        if (typedMove.length() == 0) {
            return;
        }
        String text = typedMove.toString();
        // logical fonts exist on every platform, and a fixed width one keeps the box from jumping
        pGraphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, Math.max(12, tileSize / 3)));
        FontMetrics metrics = pGraphics.getFontMetrics();

        int padding = Math.max(4, tileSize / 6);
        int boxWidth = metrics.stringWidth(text) + padding * 2;
        int boxHeight = metrics.getHeight() + padding;
        int x = (cols * tileSize - boxWidth) / 2;
        int y = clockHeight + rows * tileSize - boxHeight - padding;

        pGraphics.setColor(new Color(0, 0, 0, 190));
        pGraphics.fillRect(x, y, boxWidth, boxHeight);
        pGraphics.setColor(Theme.FG);
        pGraphics.drawString(text, x + padding, y + padding / 2 + metrics.getAscent());
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
        // only the side to move uses up time, and the clock that just stopped settles its own mode
        if (pWhiteToMove) {
            blackClock.stop();
            blackClock.onMoveFinished();
            whiteClock.start();
        } else {
            whiteClock.stop();
            whiteClock.onMoveFinished();
            blackClock.start();
        }
        // a hint or a threat was about the position before this move
        clearAdvice();
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
        // a new game keeps none of the times the finished one left behind
        clockSnapshots.clear();
        clockSnapshots.add(readClocks());
        clearAdvice();
    }

    /**
     * Remembers what both clocks show after the move that was just played.
     * <p>
     * Taking a move back has to give both players the time they had before it, and only the clocks
     * themselves know that. I store both clocks under the ply the game is at now, including how far
     * each player has got through a tournament control, so a move that is taken back no longer
     * counts towards the end of a stage either. A move played after something was taken back drops
     * the snapshots of the line that was abandoned, so the list always describes the game as it
     * really went.
     * <p>
     * Time complexity: O(d) for the d snapshots of an abandoned line, O(1) otherwise.
     * Space complexity: O(1) per played move.
     *
     * @param pPly how many moves have been played, 1 after the first move
     */
    @Override
    public void recordClocks(int pPly) {
        // a new move after an undo replaces the times of the line nobody is playing any more
        while (clockSnapshots.size() > pPly) {
            clockSnapshots.remove(clockSnapshots.size() - 1);
        }
        clockSnapshots.add(readClocks());
    }

    /**
     * Reads both clocks at this moment.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return White's reading followed by Black's, never null
     */
    private ChessClock.Reading[] readClocks() {
        return new ChessClock.Reading[]{whiteClock.reading(), blackClock.reading()};
    }

    /**
     * Puts both clocks back to what they showed at a ply and starts the one of the player to move.
     * <p>
     * A taken back move gives the time back that was spent on it. I stop both clocks, set them to
     * what was recorded for that ply and start the clock of whoever is to move there. A ply nobody
     * recorded, which can only happen for a game that was loaded rather than played, leaves the
     * times alone and only hands the clock over. A paused game stays paused, so neither clock starts
     * until the players resume it.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPly         how many moves are played now, 0 at the starting position
     * @param pWhiteToMove true if White is to move at that ply
     */
    @Override
    public void restoreClocks(int pPly, boolean pWhiteToMove) {
        whiteClock.stop();
        blackClock.stop();

        if (pPly < clockSnapshots.size()) {
            ChessClock.Reading[] readings = clockSnapshots.get(pPly);
            whiteClock.restore(readings[0]);
            blackClock.restore(readings[1]);
        }

        // the player to move is the one whose clock runs, and resuming a paused game starts it
        if (!paused) {
            if (pWhiteToMove) {
                whiteClock.start();
            } else {
                blackClock.start();
            }
        }
        // the game is at another position now, so advice about the old one is gone
        clearAdvice();
    }

    /**
     * Pauses or resumes the game.
     * <p>
     * Players step away from a board, and until now the only way to stop the clock was to finish the
     * game. Pausing stops both clocks and makes the board ignore the mouse, so a piece cannot be
     * moved while nobody is watching the time. Resuming starts the clock of whoever is to move, and
     * never starts one at all when the game is already over. Asking for the state the game is
     * already in does nothing, so a pause cannot be stacked.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPaused true to pause the game, false to let it run again
     */
    public void setPaused(boolean pPaused) {
        // nothing to do, and pausing twice must not lose track of whose clock was running
        if (pPaused == paused) {
            return;
        }
        paused = pPaused;

        if (paused) {
            whiteClock.stop();
            blackClock.stop();
        } else if (!session.result().isFinished()) {
            // the clock of the player to move is the one that carries on
            if (session.isWhiteToMove()) {
                whiteClock.start();
            } else {
                blackClock.start();
            }
        }
        repaint();
        // a program that held its move back while the game was paused may move now
        if (!paused) {
            onResume.run();
        }
    }

    /**
     * Sets who is told when a paused game runs again.
     * <p>
     * A program opponent does not move while the game is paused, so it needs to hear when the game
     * goes on, since nothing else about the game changes at that moment.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pListener run whenever the game is resumed, or null for nobody
     */
    public void setResumeListener(Runnable pListener) {
        this.onResume = pListener == null ? () -> {
        } : pListener;
    }

    /**
     * Tells whether the game is paused.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true while both clocks stand still and the board takes no moves
     */
    public boolean isPaused() {
        return paused;
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
        return (isWhiteAtBottom() ? pCol : 7 - pCol) * tileSize;
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
        return clockHeight + (isWhiteAtBottom() ? pRow : 7 - pRow) * tileSize;
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
        return isWhiteAtBottom() ? col : 7 - col;
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
        return isWhiteAtBottom() ? row : 7 - row;
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
