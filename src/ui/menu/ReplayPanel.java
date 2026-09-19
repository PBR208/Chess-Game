package ui.menu;

/*
 * Purpose: ReplayPanel steps through a saved game position by position. It draws a small board from
 * the FEN recorded after every move and shows the move list and the current FEN next to it, and it
 * looks over the game in the background to mark the moves that threw something away and to show who
 * was standing better as a bar beside the board. That work is
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
import ui.i18n.Messages;
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

    /** stands for a position nobody has worked out a score for yet */
    public static final int UNKNOWN_SCORE = Integer.MIN_VALUE;

    // How hard to look at each position of a finished game. This runs over the whole game while
    // somebody is reading it, so it is shallow on purpose: a rough score for every move is worth
    // far more here than a deep one for the first two.
    private static final Searcher.Limits REVIEW_LIMITS = new Searcher.Limits(3, 40_000, 400);

    private final List<String> moves;
    private final List<String> fens;
    private int cursor = 0;

    // The review running right now, kept so it can be called off. Every run gets one of its own,
    // because a search keeps its working state in arrays it reuses: two runs sharing one would tread
    // on each other, and a run that has been replaced can still be finishing the position it was on.
    private volatile Analyst currentReview;

    // What each position is worth, always from White's point of view. The search answers from the
    // point of view of whoever is to move, which is not comparable between one position and the
    // next, and comparing them is the whole point of looking for a move that threw something away.
    private final int[] frameScores;

    // Counts the times the analysis has been restarted. A score that arrives from an older run is
    // about a game somebody has already stopped reading, so it is dropped rather than shown.
    private volatile int analysisRun;

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

    // the canvas the position is drawn on, kept so anything that changes the frame, or a score
    // arriving later, can redraw it
    private JPanel boardCanvas;

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
        this(null, pMoves, pFens);
    }

    /**
     * Builds the replay view for a saved game that may have begun from a position of its own.
     * <p>
     * An imported game can start from any position its FEN tag names, and replaying it from the
     * standard one would show a board the moves never happened on. So the first frame is the
     * position the game really began from, and the standard one only when it began the usual way.
     * <p>
     * Time complexity: O(m) for filling the move list with m moves.
     * Space complexity: O(m) for the move list text.
     *
     * @param pStartFen the position the game began from, null or blank for the standard one
     * @param pMoves    moves of the game in SAN, never null
     * @param pFens     FEN after each move, in the same order as the moves; never null, may be empty
     */
    public ReplayPanel(String pStartFen, List<String> pMoves, List<String> pFens) {
        this.moves = pMoves;
        // the replay used to open on the position after White's first move, so the one position
        // every game has in common, the board before anybody moved, could not be looked at at all.
        // The frames start there now, which also gives a game with no moves something to show.
        List<String> frames = new ArrayList<>(pFens.size() + 1);
        frames.add(pStartFen == null || pStartFen.isBlank() ? Fen.START_POSITION : pStartFen);
        frames.addAll(pFens);
        this.fens = frames;
        // one score for every frame, the board before anybody moved included
        this.frameScores = new int[frames.size()];
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

        JButton flip = textButton(Messages.get("replay.flip"), "flip");
        flip.addActionListener(e -> {
            flipped = !flipped;
            refresh();
        });
        JButton copyFen = textButton(Messages.get("replay.copyFen"), "copyFen");
        copyFen.addActionListener(e -> copyToClipboard(fens.get(cursor)));
        JButton copyMoves = textButton(Messages.get("replay.copyMoves"), "copyMoves");
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
        // the padding stays here, because a properties file drops the spaces in front of a value
        JLabel moveHistoryHeader = new JLabel("  " + Messages.get("log.moveHistory"));
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
        // the move list was a list to look at, and the position it names was four buttons away
        moveHistoryArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent pEvent) {
                showMove(plyAt(pEvent.getPoint()));
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
        JLabel fenHeader = new JLabel("  " + Messages.get("log.currentFen"));
        fenHeader.setForeground(new Color(140, 140, 140));
        fenHeader.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        fenHeader.setBackground(new Color(40, 40, 42));
        fenHeader.setOpaque(true);
        fenHeader.setPreferredSize(new Dimension(220, 25));

        fenArea = new JTextArea();
        fenArea.setEditable(false);
        // named for the same reason as the move list, a test has to tell the two areas apart
        fenArea.setName("replayFen");
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
        // frame zero is the board before anybody moved, so the frame before a move shares its number
        return scoreAt(pPly);
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
        return scoreAt(pPly + 1);
    }

    /**
     * Returns what one recorded position was worth, from White's point of view.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pFrame which frame, 0 for the board before anybody moved
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
     * @param pFrame   which frame, 0 for the board before anybody moved
     * @param pAnalyst the analysis belonging to this run, never null
     */
    private void scoreFrame(int pFrame, Analyst pAnalyst) {
        if (frameScores[pFrame] != UNKNOWN_SCORE) {
            return;
        }
        frameScores[pFrame] = whiteScoreOf(fens.get(pFrame), pAnalyst);
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
            return Messages.get("replay.noMoves");
        }
        if (cursor == 0) {
            return Messages.format("replay.startPosition", String.valueOf(fens.size()));
        }
        // frame one follows the first half move, so the moves are counted from there
        int move = (cursor - 1) / 2 + 1;
        String who = (cursor - 1) % 2 == 0 ? Messages.get("replay.white") : Messages.get("replay.black");
        return Messages.format("replay.afterMove", String.valueOf(move), who, String.valueOf(cursor + 1),
                String.valueOf(fens.size()));
    }

    private String moveText() {
        if (fens.isEmpty()) return Messages.get("replay.noMoves");
        return positionText();
    }

    private void drawPosition(Graphics2D g2d, int width, int height) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (fens.isEmpty()) {
            g2d.setColor(Color.GRAY);
            g2d.drawString(Messages.get("replay.noPosition"), 20, 40);
            return;
        }

        // A strip down the side carries the evaluation bar, so the board gets what is left. The
        // width comes from the canvas rather than from the tile size, which would be circular: the
        // tile size is what is left once the strip has been taken off.
        int barWidth = Math.max(8, Math.min(width, height) / 40);
        int gap = Math.max(2, barWidth / 2);
        int tileSize = Math.min(Math.max(0, width - barWidth - gap), height) / 8;
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

        drawEvaluationBar(g2d, 8 * tileSize + gap, 0, barWidth, 8 * tileSize);
    }

    /**
     * Draws the bar that says who is standing better, and by how much.
     * <p>
     * A number in hundredths of a pawn means nothing at a glance, while how far the bar has moved
     * says it without being read. White fills from the bottom and Black from the top, which is the
     * way round every chess program draws it, so nobody has to learn this one, and a board turned
     * round turns the bar with it. A position nobody has
     * scored yet is drawn level rather than guessed at, the same way an unscored move carries no
     * mark, and neither side is ever squeezed out completely, because a bar with one colour missing
     * reads as a finished game rather than a lost one.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pGraphics graphics context of the canvas, never null
     * @param pX        left edge of the bar in pixels
     * @param pY        top edge of the bar in pixels
     * @param pWidth    width of the bar in pixels
     * @param pHeight   height of the bar in pixels
     */
    private void drawEvaluationBar(Graphics2D pGraphics, int pX, int pY, int pWidth, int pHeight) {
        if (pWidth <= 0 || pHeight <= 0) {
            return;
        }
        double whiteShare = barShareForWhite(scoreAt(cursor));
        int whiteHeight = (int) Math.round(pHeight * whiteShare);
        int blackHeight = pHeight - whiteHeight;

        // Black above and White below, turned round with the board so each side grows from its own
        // end, and the outline keeps the bar readable against any background
        pGraphics.setColor(new Color(45, 45, 48));
        pGraphics.fillRect(pX, flipped ? pY + whiteHeight : pY, pWidth, blackHeight);
        pGraphics.setColor(new Color(235, 235, 235));
        pGraphics.fillRect(pX, flipped ? pY : pY + blackHeight, pWidth, whiteHeight);
        pGraphics.setColor(new Color(90, 90, 95));
        pGraphics.drawRect(pX, pY, pWidth - 1, pHeight - 1);
    }

    /**
     * Turns a score into how much of the bar White fills.
     * <p>
     * Scores run from a pawn or two in an ordinary game to tens of thousands for a forced mate, so
     * showing them to scale would leave the bar pinned at one end for most of a game and useless for
     * the rest. I treat eight pawns as the end of the scale, since a game that one sided is decided
     * whatever the exact number is, and give a mate the whole bar. Both sides always keep a sliver,
     * so the bar never reads as one side having disappeared from the board.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pWhiteScore the score from White's point of view, or UNKNOWN_SCORE when there is none
     * @return how much of the bar belongs to White, between 0 and 1
     */
    public static double barShareForWhite(int pWhiteScore) {
        // nobody has looked at this position yet, so it is drawn level rather than guessed at
        if (pWhiteScore == UNKNOWN_SCORE) {
            return 0.5;
        }
        if (pWhiteScore > Searcher.MATE_BOUND) {
            return 1.0;
        }
        if (pWhiteScore < -Searcher.MATE_BOUND) {
            return 0.0;
        }
        // eight pawns either way is as far as the scale goes
        double clamped = Math.max(-800, Math.min(800, pWhiteScore));
        double share = 0.5 + clamped / 1600.0;
        return Math.max(0.05, Math.min(0.95, share));
    }

    /**
     * Shows the position a move produced.
     * <p>
     * This is what clicking a move in the list means, and it is worth being a method of its own
     * rather than something buried in a mouse listener, because the rule is the interesting part:
     * the frame that shows a move is the one after it, and frame zero is the board before anybody
     * moved, so the ply gets one added to it. A move the game never had is ignored, which is what a
     * click below the last move or on a line that is only half filled amounts to.
     * <p>
     * Time complexity: O(m) for redrawing the record of m moves. Space complexity: O(m) for it.
     *
     * @param pPly the move to show, counted in half moves from 0 for White's first
     */
    public void showMove(int pPly) {
        int frame = pPly + 1;
        // a move that was never played has no position to show
        if (pPly < 0 || frame >= fens.size()) {
            return;
        }
        cursor = frame;
        refresh();
    }

    /**
     * Works out which move of the game a point in the move list belongs to.
     * <p>
     * Every line of the list holds one full move, White's first and Black's behind it at a fixed
     * column, because the list is laid out in a monospaced font. So the line gives the move number
     * and the column says which of the two halves was hit.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPoint point inside the move list, never null
     * @return the move as a count of half moves from 0, or -1 when no move was hit
     */
    private int plyAt(Point pPoint) {
        try {
            int offset = moveHistoryArea.viewToModel2D(pPoint);
            int line = moveHistoryArea.getLineOfOffset(offset);
            int column = offset - moveHistoryArea.getLineStartOffset(line);
            // the first half of a line is White's move, the rest is Black's
            int half = column < BLACK_MOVE_COLUMN ? 0 : 1;
            return line * 2 + half;
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