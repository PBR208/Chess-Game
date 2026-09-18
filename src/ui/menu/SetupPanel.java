package ui.menu;

/*
 * Purpose: SetupPanel is the position editor. A player puts pieces on an empty board or pastes a FEN,
 * says who is to move and which castling rights are left, and starts a game from that position. The
 * editor keeps nothing but a square per piece and writes the position out as a FEN, so Fen is the one
 * thing that decides whether a position is legal and its complaint is what the player reads. That
 * keeps the rules in a single place instead of repeating them in a screen that would drift from them.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import app.Main;
import engine.core.Bitboards;
import engine.core.Fen;
import engine.core.Pieces;
import engine.core.Position;
import engine.model.GameConfig;
import ui.board.PieceSprites;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SetupPanel extends JPanel {

    // the brush that moves a piece already on the board instead of placing a new one
    public static final int BRUSH_MOVE = -1;

    // edge length of the piece pictures in the palette
    private static final int PALETTE_TILE = 36;

    // what the editor shows when no en passant square applies
    private static final String NO_EN_PASSANT = "-";

    // Board-tile colors mirror ui.board.Board's own palette
    private static final Color LIGHT_TILE = new Color(232, 235, 239);
    private static final Color DARK_TILE = new Color(125, 135, 150);

    // one piece code per square, indexed the way the engine numbers squares with a1 as 0
    private final int[] squares = new int[Bitboards.SQUARE_COUNT];

    private int sideToMove = Pieces.WHITE;

    // the en passant square of the position, only ever set by a pasted FEN
    private String enPassant = NO_EN_PASSANT;
    private int halfmoveClock = 0;
    private int fullmoveNumber = 1;

    // what a click on the board does: place this piece, rub one out, or move one that is there
    private int brush = BRUSH_MOVE;
    // square the mouse went down on, so a drag knows where the piece came from
    private int pressedSquare = -1;

    private final JCheckBox whiteKingside = new JCheckBox("K");
    private final JCheckBox whiteQueenside = new JCheckBox("Q");
    private final JCheckBox blackKingside = new JCheckBox("k");
    private final JCheckBox blackQueenside = new JCheckBox("q");

    private final JTextField fenField = new JTextField();
    private final JLabel errorLabel = new JLabel(" ");
    private final JToggleButton sideButton = new JToggleButton("White to move");
    private final JButton startButton;
    private final JPanel boardCanvas;

    // piece images scaled to the size the board is currently drawn at, and the size they were scaled
    // for. The editor board grows with the window, so the cache is thrown away when that changes.
    private PieceSprites sprites;
    private int spriteTileSize;

    /**
     * Builds the position editor on the standard starting position.
     * <p>
     * A player who wants to study an endgame usually clears a few pieces away rather than building a
     * position from nothing, so the editor opens on the position every game starts from. I lay out the
     * editor board on the left, the palette of pieces with the eraser and the move brush on the right
     * together with the side to move, the castling rights and the FEN field, and the buttons along the
     * bottom. Every change writes the position into the FEN field and shows what is wrong with it.
     * <p>
     * Time complexity: O(64) for the squares of the starting position.
     * Space complexity: O(64) for the board plus the palette pictures.
     */
    public SetupPanel() {
        setBackground(Theme.BG);
        setLayout(new BorderLayout());

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(Theme.PANEL_BG);
        topBar.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("Set Up Position");
        title.setForeground(Theme.FG);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));

        JButton backButton = secondaryButton("Back to Menu", "backToMenu");
        backButton.addActionListener(e -> Main.showMenu());

        topBar.add(title, BorderLayout.WEST);
        topBar.add(backButton, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        boardCanvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics pGraphics) {
                super.paintComponent(pGraphics);
                drawBoard((Graphics2D) pGraphics, getWidth(), getHeight());
            }
        };
        boardCanvas.setBackground(Theme.BG);
        boardCanvas.setPreferredSize(new Dimension(480, 480));
        // named so what is actually drawn can be looked at without a window around it
        boardCanvas.setName("setupBoard");
        boardCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent pEvent) {
                pressedSquare = squareAt(pEvent.getPoint());
            }

            @Override
            public void mouseReleased(MouseEvent pEvent) {
                applyStroke(pressedSquare, squareAt(pEvent.getPoint()));
            }
        });

        JPanel boardHolder = new JPanel(new GridBagLayout());
        boardHolder.setBackground(Theme.BG);
        boardHolder.add(boardCanvas, new GridBagConstraints());

        add(boardHolder, BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);

        startButton = primaryButton("Start Game", "startPosition");
        startButton.addActionListener(e -> startGame());

        JButton clearButton = secondaryButton("Clear board", "clearBoard");
        clearButton.addActionListener(e -> clearBoard());

        JButton resetButton = secondaryButton("Start position", "resetPosition");
        resetButton.addActionListener(e -> resetToStartPosition());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.setBackground(Theme.PANEL_BG);
        buttons.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 60, 65)));
        buttons.add(startButton);
        buttons.add(clearButton);
        buttons.add(resetButton);
        add(buttons, BorderLayout.SOUTH);

        resetToStartPosition();
    }

    /**
     * Builds the column of controls next to the board.
     * <p>
     * Everything that is not the board itself belongs together on one side: the pieces to place, the
     * eraser and the move brush, who is to move, the castling rights, the FEN field and the line that
     * explains what is wrong with the position.
     * <p>
     * Time complexity: O(1), a fixed number of controls.
     * Space complexity: O(1) apart from the components and the palette pictures.
     *
     * @return the finished panel, never null
     */
    private JPanel buildSidePanel() {
        JPanel side = new JPanel();
        side.setBackground(Theme.PANEL_BG);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(12, 16, 12, 16));
        side.setPreferredSize(new Dimension(320, 0));

        side.add(sectionLabel("Pieces"));
        side.add(Box.createVerticalStrut(8));

        ButtonGroup brushes = new ButtonGroup();
        PieceSprites palette = new PieceSprites(PALETTE_TILE);

        JPanel pieceGrid = new JPanel(new GridLayout(2, 6, 4, 4));
        pieceGrid.setBackground(Theme.PANEL_BG);
        // white pieces on the first row, black ones below, each in the engine's own order
        for (int colour = Pieces.WHITE; colour <= Pieces.BLACK; colour++) {
            for (int type = Pieces.PAWN; type < Pieces.TYPE_COUNT; type++) {
                int piece = Pieces.make(colour, type);
                JToggleButton button = new JToggleButton(new ImageIcon(palette.spriteForPiece(piece)));
                UiComponents.style(button, new Font(Font.SANS_SERIF, Font.PLAIN, 12), Theme.BUTTON_SECONDARY);
                // the FEN letter is a name that says exactly which piece this is
                button.setName("brush" + Pieces.fenCharOf(piece));
                button.setToolTipText("Place " + Pieces.fenCharOf(piece));
                button.addActionListener(e -> setBrush(piece));
                brushes.add(button);
                pieceGrid.add(button);
            }
        }
        side.add(pieceGrid);
        side.add(Box.createVerticalStrut(8));

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tools.setBackground(Theme.PANEL_BG);

        JToggleButton moveBrush = toolButton("Move", "brushMove", brushes, tools);
        moveBrush.addActionListener(e -> setBrush(BRUSH_MOVE));
        moveBrush.setSelected(true);

        JToggleButton eraser = toolButton("Erase", "brushErase", brushes, tools);
        eraser.addActionListener(e -> setBrush(Pieces.NONE));

        side.add(tools);
        side.add(Box.createVerticalStrut(20));

        side.add(sectionLabel("Side to move"));
        side.add(Box.createVerticalStrut(8));
        UiComponents.style(sideButton, new Font(Font.SANS_SERIF, Font.PLAIN, 13), Theme.BUTTON_SECONDARY);
        sideButton.setName("sideToMove");
        sideButton.setAlignmentX(LEFT_ALIGNMENT);
        sideButton.addActionListener(e -> setSideToMove(sideButton.isSelected() ? Pieces.BLACK : Pieces.WHITE));
        side.add(sideButton);
        side.add(Box.createVerticalStrut(20));

        side.add(sectionLabel("Castling rights"));
        side.add(Box.createVerticalStrut(8));
        JPanel castling = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        castling.setBackground(Theme.PANEL_BG);
        styleCheckBox(whiteKingside, "castleK", castling);
        styleCheckBox(whiteQueenside, "castleQ", castling);
        styleCheckBox(blackKingside, "castlek", castling);
        styleCheckBox(blackQueenside, "castleq", castling);
        side.add(castling);
        side.add(Box.createVerticalStrut(20));

        side.add(sectionLabel("FEN"));
        side.add(Box.createVerticalStrut(8));
        fenField.setBackground(new Color(28, 28, 30));
        fenField.setForeground(Theme.FG);
        fenField.setCaretColor(Theme.FG);
        fenField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        fenField.setBorder(new EmptyBorder(8, 10, 8, 10));
        fenField.setName("setupFen");
        fenField.setToolTipText("Paste a position here and press Enter");
        // pressing Enter reads the pasted position back onto the board
        fenField.addActionListener(e -> loadFen(fenField.getText()));
        side.add(fenField);
        side.add(Box.createVerticalStrut(8));

        JButton loadButton = secondaryButton("Load FEN", "loadFen");
        loadButton.setAlignmentX(LEFT_ALIGNMENT);
        loadButton.addActionListener(e -> loadFen(fenField.getText()));
        side.add(loadButton);
        side.add(Box.createVerticalStrut(12));

        // says why a position cannot be played, a single space keeps the line's height
        errorLabel.setForeground(new Color(210, 90, 90));
        errorLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        errorLabel.setName("setupError");
        errorLabel.setAlignmentX(LEFT_ALIGNMENT);
        side.add(errorLabel);

        return side;
    }

    /**
     * Puts a piece on a square, or empties it.
     * <p>
     * This is what placing a piece with the palette means, and it is a method of its own rather than
     * something buried in a mouse listener, so the editor can be driven without a mouse. Any en
     * passant square the position carried is dropped, because a square that was recorded before the
     * board changed almost certainly no longer has the pawn that passed standing behind it.
     * <p>
     * Time complexity: O(64) for rebuilding the FEN. Space complexity: O(1) beyond the FEN text.
     *
     * @param pSquare square to change, 0 to 63; anything else is ignored
     * @param pPiece  piece code to put there, or Pieces.NONE to empty the square
     */
    public void putPiece(int pSquare, int pPiece) {
        if (pSquare < 0 || pSquare >= Bitboards.SQUARE_COUNT) {
            return;
        }
        squares[pSquare] = pPiece;
        // the board no longer matches whatever en passant square was recorded for it
        enPassant = NO_EN_PASSANT;
        refresh();
    }

    /**
     * Moves whatever stands on one square to another.
     * <p>
     * Dragging a piece across the editor board is how a position is nudged into shape once the pieces
     * are roughly right. The square that was left behind becomes empty and whatever stood on the
     * target is replaced, which is what dropping a piece on top of another one means.
     * <p>
     * Time complexity: O(64) for rebuilding the FEN. Space complexity: O(1) beyond the FEN text.
     *
     * @param pFrom square the piece comes from, 0 to 63
     * @param pTo   square it goes to, 0 to 63
     */
    public void movePiece(int pFrom, int pTo) {
        boolean onBoard = pFrom >= 0 && pFrom < Bitboards.SQUARE_COUNT
                && pTo >= 0 && pTo < Bitboards.SQUARE_COUNT;
        // moving a square onto itself, or off the board, changes nothing
        if (!onBoard || pFrom == pTo || squares[pFrom] == Pieces.NONE) {
            return;
        }
        squares[pTo] = squares[pFrom];
        squares[pFrom] = Pieces.NONE;
        enPassant = NO_EN_PASSANT;
        refresh();
    }

    /**
     * Reads a position from a FEN and puts it into the editor.
     * <p>
     * Pasting a position is the fastest way to set one up, and it is also how a position from a book
     * or another program gets here. I let Fen read it, so a text that is not a chess position is
     * refused for exactly the reason Fen gives, and the board keeps whatever it held before. A
     * position that reads correctly fills the board, the side to move, the castling rights, the en
     * passant square and both counters.
     * <p>
     * Time complexity: O(c) in the length of the text plus O(64) for the board.
     * Space complexity: O(1) beyond the parsed position.
     *
     * @param pFen a position in Forsyth Edwards notation, may be anything a player typed; may be null
     * @return true if the position was read, false if the text could not be used
     */
    public boolean loadFen(String pFen) {
        Position position;
        try {
            position = Fen.parse(pFen);
        } catch (IllegalArgumentException | NullPointerException e) {
            // the board keeps what it had, the player only loses the paste
            errorLabel.setText(problemText(e.getMessage()));
            startButton.setEnabled(false);
            return false;
        }

        for (int square = 0; square < Bitboards.SQUARE_COUNT; square++) {
            squares[square] = position.pieceAt(square);
        }
        sideToMove = position.sideToMove();
        sideButton.setSelected(sideToMove == Pieces.BLACK);
        sideButton.setText(sideToMove == Pieces.WHITE ? "White to move" : "Black to move");

        int rights = position.castlingRights();
        whiteKingside.setSelected((rights & Position.WHITE_KINGSIDE) != 0);
        whiteQueenside.setSelected((rights & Position.WHITE_QUEENSIDE) != 0);
        blackKingside.setSelected((rights & Position.BLACK_KINGSIDE) != 0);
        blackQueenside.setSelected((rights & Position.BLACK_QUEENSIDE) != 0);

        enPassant = position.epSquare() == Position.NO_EN_PASSANT
                ? NO_EN_PASSANT
                : Bitboards.nameOf(position.epSquare());
        halfmoveClock = position.halfmoveClock();
        fullmoveNumber = position.fullmoveNumber();

        refresh();
        return true;
    }

    /**
     * Writes the position currently in the editor as a FEN.
     * <p>
     * The editor holds a piece per square and a handful of settings, and every part of this screen
     * that has to judge the position works from the text rather than from those fields, so this is
     * the single place that turns one into the other.
     * <p>
     * Time complexity: O(64) for the squares. Space complexity: O(1), the text has a bounded length.
     *
     * @return the position in Forsyth Edwards notation, never null
     */
    public String fen() {
        return placement() + (sideToMove == Pieces.WHITE ? " w " : " b ")
                + castlingText() + " " + enPassant + " " + halfmoveClock + " " + fullmoveNumber;
    }

    /**
     * Says what is wrong with the position in the editor.
     * <p>
     * A position editor can build something that is not a chess position at all, a board with two
     * white kings or a pawn on the last rank, and the player should learn that while building it
     * rather than when the game refuses to start. Fen already refuses every one of those and names
     * the reason, so I ask it and hand its complaint back instead of repeating the rules here.
     * <p>
     * Time complexity: O(64) for writing and reading the position back.
     * Space complexity: O(1) beyond the parsed position.
     *
     * @return the reason the position cannot be played, or null when it is a legal position
     */
    public String validationError() {
        try {
            Fen.parse(fen());
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /**
     * Tells whether a game could start from the position in the editor.
     * <p>
     * Time complexity: O(64). Space complexity: O(1).
     *
     * @return true if the position is a legal one
     */
    public boolean canStart() {
        return validationError() == null;
    }

    /**
     * Starts a game from the position in the editor.
     * <p>
     * An illegal position never gets this far, because the button is disabled while the position
     * cannot be played, but a caller could ask anyway and would otherwise build a broken game screen.
     * The game is unlimited, since this screen asks for a position rather than for a time control.
     * <p>
     * Time complexity: O(64) for checking the position. Space complexity: O(1).
     *
     * @return true if a game was started, false when the position cannot be played
     */
    public boolean startGame() {
        if (!canStart()) {
            return false;
        }
        // TODO [PBR208]: Let a set up position start with a chosen time control, not only unlimited.
        Main.startGame(GameConfig.unlimited(), fen());
        return true;
    }

    /**
     * Empties every square of the editor board.
     * <p>
     * Time complexity: O(64). Space complexity: O(1).
     */
    public void clearBoard() {
        java.util.Arrays.fill(squares, Pieces.NONE);
        // an empty board has nothing to castle with and nothing that just passed
        whiteKingside.setSelected(false);
        whiteQueenside.setSelected(false);
        blackKingside.setSelected(false);
        blackQueenside.setSelected(false);
        enPassant = NO_EN_PASSANT;
        halfmoveClock = 0;
        fullmoveNumber = 1;
        refresh();
    }

    /**
     * Puts the standard starting position back into the editor.
     * <p>
     * Time complexity: O(64). Space complexity: O(1).
     */
    public void resetToStartPosition() {
        loadFen(Fen.START_POSITION);
    }

    /**
     * Chooses what a click on the board does.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pBrush a piece code to place, Pieces.NONE for the eraser, or BRUSH_MOVE to drag a piece
     *               that is already on the board
     */
    public void setBrush(int pBrush) {
        this.brush = pBrush;
    }

    /**
     * Sets which side is to move in the position.
     * <p>
     * Time complexity: O(64) for rebuilding the FEN. Space complexity: O(1).
     *
     * @param pColour Pieces.WHITE or Pieces.BLACK
     */
    public void setSideToMove(int pColour) {
        this.sideToMove = pColour;
        sideButton.setSelected(pColour == Pieces.BLACK);
        sideButton.setText(pColour == Pieces.WHITE ? "White to move" : "Black to move");
        // the en passant square belongs to the side that was to move
        enPassant = NO_EN_PASSANT;
        refresh();
    }

    /**
     * Carries out one stroke of the mouse on the board.
     * <p>
     * A stroke means different things depending on the brush: with a piece selected it places that
     * piece where the mouse came up, with the eraser it empties that square, and with the move brush
     * it drags whatever was under the mouse when it went down to where it came up.
     * <p>
     * Time complexity: O(64) for rebuilding the FEN. Space complexity: O(1).
     *
     * @param pFrom square the mouse went down on, or -1 when that was off the board
     * @param pTo   square the mouse came up on, or -1 when that is off the board
     */
    private void applyStroke(int pFrom, int pTo) {
        if (pTo < 0) {
            return;
        }
        if (brush == BRUSH_MOVE) {
            movePiece(pFrom, pTo);
        } else {
            putPiece(pTo, brush);
        }
    }

    /**
     * Writes the piece placement field of the FEN.
     * <p>
     * The placement lists the ranks from eight down to one with a letter per piece and a digit for a
     * run of empty squares, which is the same shape Fen writes and reads.
     * <p>
     * Time complexity: O(64). Space complexity: O(1), the field has a bounded length.
     *
     * @return the placement field, never null
     */
    private String placement() {
        StringBuilder text = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int piece = squares[Bitboards.square(file, rank)];
                if (piece == Pieces.NONE) {
                    empty++;
                } else {
                    // a run of empty squares is written as its length
                    if (empty > 0) {
                        text.append(empty);
                        empty = 0;
                    }
                    text.append(Pieces.fenCharOf(piece));
                }
            }
            if (empty > 0) {
                text.append(empty);
            }
            if (rank > 0) {
                text.append('/');
            }
        }
        return text.toString();
    }

    /**
     * Writes the castling field of the FEN from the four check boxes.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the rights as KQkq in that order, or a dash when none are left
     */
    private String castlingText() {
        StringBuilder text = new StringBuilder();
        if (whiteKingside.isSelected()) {
            text.append('K');
        }
        if (whiteQueenside.isSelected()) {
            text.append('Q');
        }
        if (blackKingside.isSelected()) {
            text.append('k');
        }
        if (blackQueenside.isSelected()) {
            text.append('q');
        }
        // a position where nobody may castle writes a dash, the same way an absent square does
        return text.length() == 0 ? "-" : text.toString();
    }

    /**
     * Shows the current position in the FEN field and says whether it can be played.
     * <p>
     * Every change to the board goes through here, so the text and the message never fall behind what
     * is drawn. The Start button follows the same answer, which is why an illegal position cannot be
     * started by clicking rather than only by being refused afterwards.
     * <p>
     * Time complexity: O(64) for writing and checking the position. Space complexity: O(1).
     */
    private void refresh() {
        String fen = fen();
        fenField.setText(fen);
        fenField.setCaretPosition(0);

        String problem = validationError();
        errorLabel.setText(problem == null ? " " : problemText(problem));
        startButton.setEnabled(problem == null);
        boardCanvas.repaint();
    }

    /**
     * Turns a complaint from the FEN reader into the line the player reads.
     * <p>
     * Time complexity: O(n) in the length of the message. Space complexity: O(n) for the line.
     *
     * @param pMessage what was wrong with the position, may be null
     * @return the line for the error label, never null
     */
    private String problemText(String pMessage) {
        return pMessage == null ? "This is not a position that can be played." : pMessage;
    }

    /**
     * Works out which square of the editor board a point lies on.
     * <p>
     * The board is drawn as a square in the middle of its canvas with rank eight at the top, so the
     * row has to be turned round to give the rank the engine counts in.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPoint point inside the canvas, never null
     * @return the square, 0 to 63, or -1 when the point is not on the board
     */
    private int squareAt(Point pPoint) {
        int tile = tileSizeFor(boardCanvas.getWidth(), boardCanvas.getHeight());
        // a canvas with no size yet has no squares to hit
        if (tile <= 0) {
            return -1;
        }
        int file = pPoint.x / tile;
        int row = pPoint.y / tile;
        if (file < 0 || file > 7 || row < 0 || row > 7) {
            return -1;
        }
        // the top row of the screen is rank eight
        return Bitboards.square(file, 7 - row);
    }

    /**
     * Returns the edge length one square is drawn at.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pWidth  width of the canvas in pixels
     * @param pHeight height of the canvas in pixels
     * @return the square size in pixels, 0 when the canvas has no room
     */
    private int tileSizeFor(int pWidth, int pHeight) {
        return Math.min(pWidth, pHeight) / 8;
    }

    /**
     * Draws the editor board and the pieces standing on it.
     * <p>
     * The pieces are scaled to the size the squares are currently drawn at, and that scaling is by far
     * the most expensive part of painting, so the sprites are kept until the size changes.
     * <p>
     * Time complexity: O(64) per repaint, plus O(s^2) per piece the first time a size is drawn.
     * Space complexity: O(s^2) for the cached sprites.
     *
     * @param pGraphics canvas to draw on, never null
     * @param pWidth    width of the canvas in pixels
     * @param pHeight   height of the canvas in pixels
     */
    private void drawBoard(Graphics2D pGraphics, int pWidth, int pHeight) {
        int tile = tileSizeFor(pWidth, pHeight);
        if (tile <= 0) {
            return;
        }
        // scaling every piece on every repaint would be the expensive part, so it happens once
        if (sprites == null || spriteTileSize != tile) {
            sprites = new PieceSprites(tile);
            spriteTileSize = tile;
        }

        for (int row = 0; row < 8; row++) {
            for (int file = 0; file < 8; file++) {
                pGraphics.setColor((file + row) % 2 == 0 ? LIGHT_TILE : DARK_TILE);
                pGraphics.fillRect(file * tile, row * tile, tile, tile);

                int piece = squares[Bitboards.square(file, 7 - row)];
                if (piece != Pieces.NONE) {
                    pGraphics.drawImage(sprites.spriteForPiece(piece), file * tile, row * tile, null);
                }
            }
        }
    }

    /**
     * Creates one of the small toggle buttons for the eraser and the move brush.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the button.
     *
     * @param pText   button text, never null
     * @param pName   component name that identifies the button, never null
     * @param pGroup  group that keeps one brush selected at a time, never null
     * @param pParent panel the button is added to, never null
     * @return the finished button, never null
     */
    private JToggleButton toolButton(String pText, String pName, ButtonGroup pGroup, JPanel pParent) {
        JToggleButton button = new JToggleButton(pText);
        UiComponents.style(button, new Font(Font.SANS_SERIF, Font.PLAIN, 12), Theme.BUTTON_SECONDARY);
        button.setName(pName);
        pGroup.add(button);
        pParent.add(button);
        return button;
    }

    /**
     * Gives a castling check box the dark look of this screen and adds it to a panel.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pBox    check box to style, never null
     * @param pName   component name that identifies it, never null
     * @param pParent panel it is added to, never null
     */
    private void styleCheckBox(JCheckBox pBox, String pName, JPanel pParent) {
        pBox.setBackground(Theme.PANEL_BG);
        pBox.setForeground(Theme.FG);
        pBox.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        pBox.setName(pName);
        pBox.setFocusPainted(false);
        // a right that is taken away changes the position, so the FEN follows at once
        pBox.addActionListener(e -> refresh());
        pParent.add(pBox);
    }

    /**
     * Creates the small heading above a group of controls.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the label.
     *
     * @param pText heading text, never null
     * @return the heading label, never null
     */
    private JLabel sectionLabel(String pText) {
        JLabel label = new JLabel(pText);
        label.setForeground(new Color(160, 160, 170));
        // logical fonts exist on every platform, Arial doesn't
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    /**
     * Creates a button in the secondary style of this screen.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the button.
     *
     * @param pText button text, never null
     * @param pName component name that identifies the button, never null
     * @return the finished button, never null
     */
    private JButton secondaryButton(String pText, String pName) {
        // logical fonts exist on every platform, Arial doesn't
        JButton button = UiComponents.button(pText, new Font(Font.SANS_SERIF, Font.PLAIN, 13),
                Theme.BUTTON_SECONDARY);
        button.setName(pName);
        return button;
    }

    /**
     * Creates the accent coloured main button of this screen.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the button.
     *
     * @param pText button text, never null
     * @param pName component name that identifies the button, never null
     * @return the finished button, never null
     */
    private JButton primaryButton(String pText, String pName) {
        JButton button = UiComponents.button(pText, new Font(Font.SANS_SERIF, Font.BOLD, 14), Theme.ACCENT);
        button.setName(pName);
        return button;
    }
}
