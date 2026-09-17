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

import engine.persistence.FenLoader;
import ui.board.PieceSprites;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReplayPanel extends JPanel {

    // Spritesheet column order: King=0, Queen=1, Bishop=2, Knight=3, Rook=4, Pawn=5
    private static final Map<Character, Integer> PIECE_COL = new HashMap<>();

    static {
        PIECE_COL.put('k', 0);
        PIECE_COL.put('K', 0);
        PIECE_COL.put('q', 1);
        PIECE_COL.put('Q', 1);
        PIECE_COL.put('b', 2);
        PIECE_COL.put('B', 2);
        PIECE_COL.put('n', 3);
        PIECE_COL.put('N', 3);
        PIECE_COL.put('r', 4);
        PIECE_COL.put('R', 4);
        PIECE_COL.put('p', 5);
        PIECE_COL.put('P', 5);
    }

    // Board-tile colors mirror ui.board.Board's own palette
    private static final Color LIGHT_TILE = new Color(232, 235, 239);
    private static final Color DARK_TILE = new Color(125, 135, 150);

    private final List<String> moves;
    private final List<String> fens;
    private int cursor = 0;

    private final JLabel moveLabel;
    private final JTextArea moveHistoryArea;
    private final JTextArea fenArea;

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
        this.fens = pFens;
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

    private String moveText() {
        if (fens.isEmpty()) return "No moves";
        int move = cursor / 2 + 1;
        String who = cursor % 2 == 0 ? "White" : "Black";
        return "After move " + move + " (" + who + ") \u2014 position " + (cursor + 1) + "/" + fens.size();
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

        char[][] grid = FenLoader.parse(fens.get(cursor));
        BufferedImage sheet = PieceSprites.getSheet();
        int scale = PieceSprites.getSheetScale();

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                g2d.setColor((col + row) % 2 == 0 ? LIGHT_TILE : DARK_TILE);
                g2d.fillRect(col * tileSize, row * tileSize, tileSize, tileSize);

                char c = grid[row][col];
                if (c != '\0' && sheet != null && PIECE_COL.containsKey(c)) {
                    int spriteCol = PIECE_COL.get(c);
                    int spriteRow = Character.isUpperCase(c) ? 0 : 1;

                    BufferedImage sprite = sheet.getSubimage(
                            spriteCol * scale, spriteRow * scale, scale, scale);
                    g2d.drawImage(sprite, col * tileSize, row * tileSize,
                            tileSize, tileSize, null);
                }
            }
        }
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