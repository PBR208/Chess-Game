package ui.menu;

/*
 * Purpose: ReplayPanel steps through a saved game position by position. It draws a small board from
 * the FEN recorded after every move and shows the move list and the current FEN next to it. I keep
 * the replay separate from the live board, so looking at an old game can never change a running
 * one. The text uses logical font names, which every platform provides.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.Fen;
import engine.core.Pieces;
import engine.persistence.FenLoader;
import ui.board.PieceSprites;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class ReplayPanel extends JPanel {

    // the letters a FEN uses for pieces, which is also the check for whether a square holds one
    private static final String PIECE_LETTERS = "kKqQbBnNrRpP";

    // what the parsed board holds where no piece stands, written as its code so this file needs
    // no escape sequence of its own
    private static final char EMPTY_SQUARE = (char) 0;

    // a move list line reads "  1.  e4        e5", so past this column the line is Black's move
    private static final int BLACK_MOVE_COLUMN = 15;

    // Board-tile colors mirror ui.board.Board's own palette
    private static final Color LIGHT_TILE = new Color(232, 235, 239);
    private static final Color DARK_TILE = new Color(125, 135, 150);

    private final List<String> moves;
    private final List<String> fens;
    private int cursor = 0;

    private final JLabel moveLabel;
    private final JTextArea moveHistoryArea;
    private final JTextArea fenArea;

    // piece images scaled to the size this board is currently drawn at, and the size they were
    // scaled for. The replay board grows and shrinks with the window, so the cache is thrown away
    // when that size changes and kept for every repaint that does not change it.
    private PieceSprites sprites;
    private int spriteTileSize;

    // true while the board is turned round, so a game is looked at from Black's side
    private boolean flipped;

    /**
     * Builds the replay view for one saved game.
     * <p>
     * A player wants to click or use the arrow keys through the positions of an old game. I keep the
     * moves and positions, lay out the board canvas with the navigation buttons below it and the move
     * list and FEN on the right, bind the left and right arrow keys and show the first position.
     * <p>
     * Time complexity: O(m) for filling the move list with m moves.
     * Space complexity: O(m) for the move list text.
     *
     * @param pMoves moves of the game in SAN, never null
     * @param pFens  FEN after each move, in the same order as the moves; never null, may be empty
     */
    public ReplayPanel(List<String> pMoves, List<String> pFens) {
        this.moves = pMoves;
        // the replay used to open on the position after White's first move, so the one position
        // every game has in common, the board before anybody moved, could not be looked at at all.
        // The frames start there now, which also gives a game with no moves something to show.
        List<String> frames = new ArrayList<>(pFens.size() + 1);
        frames.add(Fen.START_POSITION);
        frames.addAll(pFens);
        this.fens = frames;
        setBackground(Theme.BG);
        setLayout(new BorderLayout());

        // Left side: Board canvas
        JPanel boardPanel = new JPanel(new BorderLayout());
        boardPanel.setBackground(Theme.BG);

        JPanel boardCanvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawPosition((Graphics2D) g, getWidth(), getHeight());
            }
        };
        boardCanvas.setBackground(Theme.BG);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        nav.setBackground(Theme.BG);

        moveLabel = new JLabel(moveText(), SwingConstants.CENTER);
        moveLabel.setForeground(Theme.FG);
        moveLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        JButton first = navButton("\u21e4", "|<", "first");
        first.addActionListener(e -> {
            cursor = 0;
            refresh(boardCanvas);
        });
        JButton prev = navButton("\u2190", "<", "previous");
        prev.addActionListener(e -> {
            if (cursor > 0) cursor--;
            refresh(boardCanvas);
        });
        JButton next = navButton("\u2192", ">", "next");
        next.addActionListener(e -> {
            if (cursor < fens.size() - 1) cursor++;
            refresh(boardCanvas);
        });
        JButton last = navButton("\u21e5", ">|", "last");
        last.addActionListener(e -> {
            cursor = fens.size() - 1;
            refresh(boardCanvas);
        });

        nav.add(first);
        nav.add(prev);
        nav.add(moveLabel);
        nav.add(next);
        nav.add(last);

        JButton flip = textButton("Flip", "flip");
        flip.addActionListener(e -> {
            flipped = !flipped;
            refresh(boardCanvas);
        });
        JButton copyFen = textButton("Copy FEN", "copyFen");
        copyFen.addActionListener(e -> copyToClipboard(fens.get(cursor)));
        JButton copyMoves = textButton("Copy moves", "copyMoves");
        copyMoves.addActionListener(e -> copyToClipboard(movetext()));

        nav.add(flip);
        nav.add(copyFen);
        nav.add(copyMoves);

        boardPanel.add(boardCanvas, BorderLayout.CENTER);
        boardPanel.add(nav, BorderLayout.SOUTH);

        // Right side: Move history and FEN
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(Theme.BG);
        rightPanel.setPreferredSize(new Dimension(220, 0));

        // Move History
        JLabel moveHistoryHeader = new JLabel("  Move History");
        moveHistoryHeader.setForeground(new Color(140, 140, 140));
        moveHistoryHeader.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        moveHistoryHeader.setBackground(new Color(40, 40, 42));
        moveHistoryHeader.setOpaque(true);
        moveHistoryHeader.setPreferredSize(new Dimension(220, 30));

        moveHistoryArea = new JTextArea();
        moveHistoryArea.setEditable(false);
        moveHistoryArea.setBackground(new Color(28, 28, 30));
        moveHistoryArea.setForeground(new Color(210, 210, 210));
        moveHistoryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        moveHistoryArea.setMargin(new Insets(8, 8, 8, 8));
        // the move list was a list to look at, and the position it names was four buttons away
        moveHistoryArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent pEvent) {
                int frame = frameAt(pEvent.getPoint());
                if (frame >= 0 && frame < fens.size()) {
                    cursor = frame;
                    refresh(boardCanvas);
                }
            }
        });

        JScrollPane moveScroll = new JScrollPane(moveHistoryArea);
        moveScroll.setBorder(BorderFactory.createEmptyBorder());
        moveScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        JPanel movePanel = new JPanel(new BorderLayout());
        movePanel.setBackground(Theme.BG);
        movePanel.add(moveHistoryHeader, BorderLayout.NORTH);
        movePanel.add(moveScroll, BorderLayout.CENTER);

        // FEN Display
        JLabel fenHeader = new JLabel("  Current FEN");
        fenHeader.setForeground(new Color(140, 140, 140));
        fenHeader.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        fenHeader.setBackground(new Color(40, 40, 42));
        fenHeader.setOpaque(true);
        fenHeader.setPreferredSize(new Dimension(220, 25));

        fenArea = new JTextArea();
        fenArea.setEditable(false);
        fenArea.setBackground(new Color(28, 28, 30));
        fenArea.setForeground(new Color(210, 210, 210));
        fenArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        fenArea.setMargin(new Insets(6, 8, 6, 8));
        fenArea.setLineWrap(true);
        fenArea.setWrapStyleWord(true);
        fenArea.setRows(4);

        JScrollPane fenScroll = new JScrollPane(fenArea);
        fenScroll.setBorder(BorderFactory.createEmptyBorder());
        fenScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        JPanel fenPanel = new JPanel(new BorderLayout());
        fenPanel.setBackground(Theme.BG);
        fenPanel.add(fenHeader, BorderLayout.NORTH);
        fenPanel.add(fenScroll, BorderLayout.CENTER);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, movePanel, fenPanel);
        rightSplit.setResizeWeight(0.7);
        rightSplit.setBorder(BorderFactory.createEmptyBorder());
        rightSplit.setBackground(Theme.BG);
        rightSplit.setDividerSize(4);

        rightPanel.add(rightSplit, BorderLayout.CENTER);

        // Main layout
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, boardPanel, rightPanel);
        mainSplit.setResizeWeight(1.0);
        mainSplit.setBorder(BorderFactory.createEmptyBorder());
        mainSplit.setBackground(Theme.BG);
        mainSplit.setDividerSize(4);

        add(mainSplit, BorderLayout.CENTER);

        // Keyboard navigation
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("LEFT"), "prev");
        im.put(KeyStroke.getKeyStroke("RIGHT"), "next");
        am.put("prev", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                prev.doClick();
            }
        });
        am.put("next", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                next.doClick();
            }
        });

        refresh(boardCanvas);
    }

    private void refresh(JPanel canvas) {
        moveLabel.setText(moveText());
        updateMoveHistory();
        if (!fens.isEmpty()) {
            fenArea.setText(fens.get(cursor));
            fenArea.setCaretPosition(0);
        }
        canvas.repaint();
    }

    private void updateMoveHistory() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < moves.size(); i += 2) {
            int moveNum = i / 2 + 1;
            String white = moves.get(i);
            String black = (i + 1 < moves.size()) ? moves.get(i + 1) : "...";

            sb.append(String.format("%3d.  %-9s %s%n", moveNum, white, black));
        }

        moveHistoryArea.setText(sb.toString());
        moveHistoryArea.setCaretPosition(0);
    }

    /**
     * Describes the frame the replay is showing.
     * <p>
     * The first frame is the board before anybody moved, which belongs to no move and says so. Every
     * frame after it follows one half move, so the move number and whose move it was are worked out
     * from the frame index with the starting position taken back off. A game without moves says so
     * rather than counting a single frame.
     * <p>
     * Time complexity: O(1). Space complexity: O(n) for the line of text.
     *
     * @return the line shown between the navigation buttons, never null
     */
    private String positionText() {
        // a saved game with no moves in it has nothing to step through
        if (moves.isEmpty()) {
            return "No moves";
        }
        if (cursor == 0) {
            return "Start position - position 1/" + fens.size();
        }
        // frame one follows the first half move, so the moves are counted from there
        int move = (cursor - 1) / 2 + 1;
        String who = (cursor - 1) % 2 == 0 ? "White" : "Black";
        return "After move " + move + " (" + who + ") - position " + (cursor + 1) + "/" + fens.size();
    }

    private String moveText() {
        if (fens.isEmpty()) return "No moves";
        return positionText();
    }

    private void drawPosition(Graphics2D g2d, int width, int height) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (fens.isEmpty()) {
            g2d.setColor(Color.GRAY);
            g2d.drawString("No position to display", 20, 40);
            return;
        }

        // Calculate tile size based on available space
        int tileSize = Math.min(width, height) / 8;
        // a panel that has not been laid out yet has no room for a board
        if (tileSize <= 0) {
            return;
        }

        // every repaint used to cut all of the pieces out of the sheet again and scale each one
        // while drawing it. They are scaled once per board size now and reused after that.
        if (sprites == null || spriteTileSize != tileSize) {
            sprites = new PieceSprites(tileSize);
            spriteTileSize = tileSize;
        }

        char[][] grid = FenLoader.parse(fens.get(cursor));

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                g2d.setColor((col + row) % 2 == 0 ? LIGHT_TILE : DARK_TILE);
                g2d.fillRect(col * tileSize, row * tileSize, tileSize, tileSize);

                // turning the board round means reading the position from the other end. The square
                // colours need no turning, because a square keeps its colour either way round.
                char c = flipped ? grid[7 - row][7 - col] : grid[row][col];
                if (c != EMPTY_SQUARE && PIECE_LETTERS.indexOf(c) >= 0) {
                    // the sprite is already scaled to this board's squares, so it is drawn as it is
                    g2d.drawImage(sprites.spriteForPiece(Pieces.fromFenChar(c)),
                            col * tileSize, row * tileSize, null);
                }
            }
        }
    }

    /**
     * Works out which frame of the game a point in the move list belongs to.
     * <p>
     * Every line of the list holds one full move, White's first and Black's behind it at a fixed
     * column, because the list is laid out in a monospaced font. So the line gives the move number
     * and the column says which half was clicked. The frame that shows a move is the one after it,
     * and frame zero is the board before anybody moved, which is why the ply gets one added to it.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPoint point inside the move list, never null
     * @return the frame that shows the clicked move, or -1 when no move was hit
     */
    private int frameAt(Point pPoint) {
        try {
            int offset = moveHistoryArea.viewToModel2D(pPoint);
            int line = moveHistoryArea.getLineOfOffset(offset);
            int column = offset - moveHistoryArea.getLineStartOffset(line);
            // the first half of a line is White's move, the rest is Black's
            int half = column < BLACK_MOVE_COLUMN ? 0 : 1;
            return line * 2 + half + 1;
        } catch (BadLocationException e) {
            // a click past the end of the text names no move
            return -1;
        }
    }

    /**
     * Writes the moves of the game the way a move list is written.
     * <p>
     * Somebody looking at an old game usually wants to put it somewhere else, into a note, a message
     * or another program, and retyping thirty moves is nobody's idea of a good time. This is the
     * movetext alone, with a number in front of every move of White. The panel is handed the moves
     * and the positions and nothing else, so it cannot write the tags a complete PGN file needs.
     * <p>
     * Time complexity: O(m) for the m moves. Space complexity: O(m) for the text.
     *
     * @return the moves as one line of text, never null
     */
    private String movetext() {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < moves.size(); index++) {
            // a move number stands in front of White's move only
            if (index % 2 == 0) {
                text.append(index / 2 + 1).append(". ");
            }
            text.append(moves.get(index)).append(' ');
        }
        return text.toString().trim();
    }

    /**
     * Puts a piece of text on the system clipboard.
     * <p>
     * Copying is a convenience, so it must never be the reason anything goes wrong. A machine
     * without a clipboard, and one whose clipboard another program is holding at that moment, both
     * end here quietly rather than throwing out of a button press.
     * <p>
     * Time complexity: O(n) in the length of the text. Space complexity: O(n).
     *
     * @param pText the text to copy, never null
     */
    private void copyToClipboard(String pText) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(pText), null);
        } catch (IllegalStateException | HeadlessException problem) {
            // nothing to copy to, which is not worth interrupting anybody over
        }
    }

    /**
     * Creates one of the small text buttons beside the navigation arrows.
     * <p>
     * Flip and the two copy actions are words rather than arrows, so they need a wider button and a
     * smaller font than the arrows do. I style them with the same dark look and name each one, so a
     * test can find it whatever the button says.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the button.
     *
     * @param pText text on the button, never null
     * @param pName component name that identifies it, never null
     * @return the finished button, never null
     */
    private JButton textButton(String pText, String pName) {
        JButton b = UiComponents.button(pText, new Font(Font.SANS_SERIF, Font.PLAIN, 12), Theme.BUTTON_SECONDARY);
        b.setName(pName);
        b.setPreferredSize(new Dimension(92, 32));
        return b;
    }

    /**
     * Creates one of the four navigation buttons below the replay board.
     * <p>
     * The first, previous, next and last buttons share size and look. Their arrows are missing from
     * some fonts, so each button also carries an ASCII arrow and a component name that stays the same
     * whichever text is shown. I style a button with the shared dark look, a large bold logical font
     * for the arrow and a fixed size.
     * <p>
     * Time complexity: O(n) for the n characters of pText. Space complexity: O(1) apart from the button.
     *
     * @param pText      arrow shown on the button, never null
     * @param pAsciiText plain ASCII arrow for fonts without the symbol, never null
     * @param pName      component name that identifies the button, never null
     * @return the finished button, never null
     */
    private JButton navButton(String pText, String pAsciiText, String pName) {
        // logical fonts exist on every platform, Arial doesn't
        JButton b = UiComponents.button(pText, pAsciiText, new Font(Font.SANS_SERIF, Font.BOLD, 24), Theme.BUTTON_SECONDARY);
        b.setName(pName);
        b.setPreferredSize(new Dimension(54, 32));
        return b;
    }
}