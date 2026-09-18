package ui.menu;

/*
 * Purpose: ReplayPanel steps through a saved game position by position. It draws a small board from
 * the FEN recorded after every move and shows the move list and the current FEN next to it, and it
 * looks over the game in the background to mark the moves that threw something away. That work is
 * given up whenever the reader moves on, because its answers would be about a position they have
 * already left, and whatever was worked out before is kept rather than started again. I keep the
 * replay separate from the live board, so looking at an old game can never change a running one.
 * The text uses logical font names, which every platform provides.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.Fen;
import engine.core.Pieces;
import engine.persistence.FenLoader;
import engine.search.Analyst;
import engine.search.Searcher;
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

    // stands for a position nobody has worked out a score for yet
    private static final int UNKNOWN_SCORE = Integer.MIN_VALUE;

    // How hard to look at each position of a finished game. This runs over the whole game while
    // somebody is reading it, so it is shallow on purpose: a rough score for every move is worth
    // far more here than a deep one for the first two.
    private static final Searcher.Limits REVIEW_LIMITS = new Searcher.Limits(3, 40_000, 400);

    private final List<String> moves;
    private final List<String> fens;
    private int cursor = 0;

    // the canvas the position is drawn on, kept so a score arriving later can redraw it
    private JPanel boardCanvas;

    // The review running right now, kept so it can be called off. Every run gets one of its own,
    // because a search keeps its working state in arrays it reuses: two runs sharing one would tread
    // on each other, and a run that has been replaced can still be finishing the position it was on.
    private volatile Analyst currentReview;

    // What each position is worth, always from White's point of view. The search answers from the
    // point of view of whoever is to move, which is not comparable between one position and the
    // next, and comparing them is the whole point of looking for a move that threw something away.
    private final int[] frameScores;

    // what the board was worth before anybody moved, which no frame holds
    private volatile int startScore = UNKNOWN_SCORE;

    // Counts the times the analysis has been restarted. A score that arrives from an older run is
    // about a game somebody has already stopped reading, so it is dropped rather than shown.
    private volatile int analysisRun;

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
        this.frameScores = new int[pFens.size()];
        java.util.Arrays.fill(frameScores, UNKNOWN_SCORE);
        setBackground(Theme.BG);
        setLayout(new BorderLayout());

        // Left side: Board canvas
        JPanel boardPanel = new JPanel(new BorderLayout());
        boardPanel.setBackground(Theme.BG);

        boardCanvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawPosition((Graphics2D) g, getWidth(), getHeight());
            }
        };
        boardCanvas.setBackground(Theme.BG);
        // named so what is actually drawn can be looked at without a window around it
        boardCanvas.setName("replayBoard");

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        nav.setBackground(Theme.BG);

        moveLabel = new JLabel(moveText(), SwingConstants.CENTER);
        moveLabel.setForeground(Theme.FG);
        moveLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        JButton first = navButton("\u21e4", "|<", "first");
        first.addActionListener(e -> {
            cursor = 0;
            refresh();
        });
        JButton prev = navButton("\u2190", "<", "previous");
        prev.addActionListener(e -> {
            if (cursor > 0) cursor--;
            refresh();
        });
        JButton next = navButton("\u2192", ">", "next");
        next.addActionListener(e -> {
            if (cursor < fens.size() - 1) cursor++;
            refresh();
        });
        JButton last = navButton("\u21e5", ">|", "last");
        last.addActionListener(e -> {
            cursor = fens.size() - 1;
            refresh();
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
        // named because this panel has two text areas, and the moves are the one worth finding
        moveHistoryArea.setName("replayMoveList");

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

        refresh();
    }

    private void refresh() {
        moveLabel.setText(moveText());
        updateMoveHistory();
        if (!fens.isEmpty()) {
            fenArea.setText(fens.get(cursor));
            fenArea.setCaretPosition(0);
        }
        boardCanvas.repaint();

        // The reader has moved, so whatever was being worked out is about a position they have left.
        // Stopping it and starting again is not wasteful: every score already found is kept, and
        // only the positions still missing one are looked at.
        Analyst running = currentReview;
        if (running != null) {
            running.cancel();
        }
        reviewInBackground();
    }

    private void updateMoveHistory() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < moves.size(); i += 2) {
            int moveNum = i / 2 + 1;
            String white = moves.get(i) + markerFor(i);
            String black = (i + 1 < moves.size()) ? moves.get(i + 1) + markerFor(i + 1) : "...";

            sb.append(String.format("%3d.  %-9s %s%n", moveNum, white, black));
        }

        moveHistoryArea.setText(sb.toString());
        moveHistoryArea.setCaretPosition(0);
    }

    /**
     * Works out the mark that belongs after a move, if any.
     * <p>
     * A move is judged by what the position was worth before it against what it was worth after,
     * both read from the point of view of the player who made it. That turning round is the part
     * worth getting right: the stored scores are all from White's point of view, so for a black move
     * both numbers have to be negated before they mean anything about the player who chose it.
     * A position nobody has scored yet is marked with nothing rather than guessed at.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPly the move, counted in half moves from 0 for White's first
     * @return "??" for a blunder, "?" for a mistake, or an empty string
     */
    public String markerFor(int pPly) {
        int before = scoreBefore(pPly);
        int after = scoreAfter(pPly);
        if (before == UNKNOWN_SCORE || after == UNKNOWN_SCORE) {
            return "";
        }
        // White wants the score high and Black wants it low, so Black reads both the other way up
        boolean whiteMoved = pPly % 2 == 0;
        int beforeForMover = whiteMoved ? before : -before;
        int afterForMover = whiteMoved ? after : -after;

        if (Analyst.isBlunder(beforeForMover, afterForMover)) {
            return "??";
        }
        return Analyst.isMistake(beforeForMover, afterForMover) ? "?" : "";
    }

    /**
     * Returns what the position before a move was worth, from White's point of view.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPly the move, counted in half moves from 0
     * @return the score, or UNKNOWN_SCORE when nobody has worked it out yet
     */
    private int scoreBefore(int pPly) {
        // the board before the first move is the one no frame holds
        return pPly == 0 ? startScore : scoreAt(pPly - 1);
    }

    /**
     * Returns what the position after a move was worth, from White's point of view.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPly the move, counted in half moves from 0
     * @return the score, or UNKNOWN_SCORE when nobody has worked it out yet
     */
    private int scoreAfter(int pPly) {
        return scoreAt(pPly);
    }

    /**
     * Returns what one recorded position was worth, from White's point of view.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pFrame which recorded position, 0 for the one after the first move
     * @return the score, or UNKNOWN_SCORE when it is outside the game or not worked out yet
     */
    public int scoreAt(int pFrame) {
        if (pFrame < 0 || pFrame >= frameScores.length) {
            return UNKNOWN_SCORE;
        }
        return frameScores[pFrame];
    }

    /**
     * Looks at every position of the game and waits for the answers.
     * <p>
     * This is the waiting version, which is what makes the marks testable without anybody having to
     * watch for them to appear. The screen uses the version that does not wait.
     * <p>
     * Time complexity: O(f) searches for f recorded positions, each bounded by the review limits.
     * Space complexity: O(f) for the scores.
     */
    public void reviewNow() {
        // its own, so waiting for the answers here cannot collide with the run the screen started
        Analyst review = new Analyst();
        scoreStart(review);
        for (int frame = 0; frame < frameScores.length; frame++) {
            scoreFrame(frame, review);
        }
        // The list was written before any of these scores existed, so it still shows a game with
        // nothing marked. A review that finished and left that standing would be no review at all.
        SwingUtilities.invokeLater(this::updateMoveHistory);
    }

    /**
     * Looks at every position of the game in the background, and gives up when the reader moves on.
     * <p>
     * The scores are worth having but nobody should wait for them, so they are filled in one at a
     * time and the marks appear as they arrive. Positions already scored are left alone, so stepping
     * through a game does not start the whole job again each time: the work already done is kept and
     * only what is missing is worked out.
     * <p>
     * Time complexity: O(f) searches for f positions, spread over a background thread.
     * Space complexity: O(1) beyond the scores.
     */
    private void reviewInBackground() {
        int run = ++analysisRun;
        Analyst review = new Analyst();
        currentReview = review;
        Thread thread = new Thread(() -> {
            scoreStart(review);
            for (int frame = 0; frame < frameScores.length; frame++) {
                // somebody has moved on, so these answers are about a game nobody is reading
                if (run != analysisRun) {
                    return;
                }
                if (frameScores[frame] == UNKNOWN_SCORE) {
                    scoreFrame(frame, review);
                    SwingUtilities.invokeLater(() -> {
                        if (run == analysisRun) {
                            updateMoveHistory();
                            boardCanvas.repaint();
                        }
                    });
                }
            }
        }, "replay review");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Works out what one recorded position is worth and writes it down.
     * <p>
     * Time complexity: as for a search with the review limits. Space complexity: O(1).
     *
     * @param pFrame   which recorded position, 0 for the one after the first move
     * @param pAnalyst the analysis belonging to this run, never null
     */
    private void scoreFrame(int pFrame, Analyst pAnalyst) {
        if (frameScores[pFrame] != UNKNOWN_SCORE) {
            return;
        }
        frameScores[pFrame] = whiteScoreOf(fens.get(pFrame), pAnalyst);
    }

    /**
     * Works out what the board before the first move is worth.
     * <p>
     * Time complexity: as for a search with the review limits. Space complexity: O(1).
     *
     * @param pAnalyst the analysis belonging to this run, never null
     */
    private void scoreStart(Analyst pAnalyst) {
        if (startScore == UNKNOWN_SCORE) {
            startScore = whiteScoreOf(Fen.START_POSITION, pAnalyst);
        }
    }

    /**
     * Scores one position from White's point of view.
     * <p>
     * The search answers from the point of view of whoever is to move, so a position with Black to
     * move comes back the other way up and has to be turned round before it can be compared with
     * the one before it. A position that cannot be read at all scores nothing rather than throwing,
     * because one unreadable line of an old file must not stop the rest of the game being looked at.
     * <p>
     * Time complexity: as for a search with the review limits. Space complexity: O(1).
     *
     * @param pFen     the position to score, never null
     * @param pAnalyst the analysis belonging to this run, never null
     * @return the score from White's point of view, or UNKNOWN_SCORE when it could not be read
     */
    private int whiteScoreOf(String pFen, Analyst pAnalyst) {
        try {
            engine.core.Position position = Fen.parse(pFen);
            int score = pAnalyst.analyse(position, REVIEW_LIMITS).score;
            return position.sideToMove() == Pieces.WHITE ? score : -score;
        } catch (RuntimeException e) {
            // a saved game from an older version may hold something this cannot read
            return UNKNOWN_SCORE;
        }
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