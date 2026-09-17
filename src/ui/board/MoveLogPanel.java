package ui.board;

/*
 * Purpose: MoveLogPanel shows the moves of the running game next to the board, together with the FEN
 * of the current position. It pairs White's and Black's moves on one line, scrolls to the latest move
 * and lets a player click any move to take the game back to it. I keep it as a separate panel that
 * only receives updates and reports clicks, so the rules engine never needs to know how the log is
 * displayed. The text uses logical font names, which every platform provides.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.1
 */

import engine.core.GameSession;
import engine.imports.MoveLogView;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.IntConsumer;

public class MoveLogPanel extends JPanel implements MoveLogView, GameSession.MoveLog {

    // a line reads "  1.  e4        e5", so everything past this column belongs to Black's move
    private static final int BLACK_MOVE_COLUMN = 15;

    private final JTextArea area = new JTextArea();
    private final JTextArea fenArea = new JTextArea();
    // told which ply a player clicked, so somebody else can decide what to do about it
    private IntConsumer onPlySelected = ply -> {
    };

    /**
     * Builds the move log with its move history and current FEN sections.
     * <p>
     * The log sits to the right of the board and should be exactly as tall. I lay out a header and a
     * scrolling text area for the moves above a header and a wrapping text area for the FEN, split so
     * the moves get most of the height, with headers in a bold logical font and monospaced text.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the Swing components.
     *
     * @param pBoardHeight preferred height in pixels, the height of the board panel; greater than 0
     */
    public MoveLogPanel(int pBoardHeight) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(200, pBoardHeight));
        setBackground(new Color(28, 28, 30));

        // Style the move history text area
        area.setEditable(false);
        area.setBackground(new Color(28, 28, 30));
        area.setForeground(new Color(210, 210, 210));
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setMargin(new Insets(8, 8, 8, 8));
        // clicking a move means "show me the game as it stood after it"
        area.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent pEvent) {
                int ply = plyAt(pEvent.getPoint());
                if (ply >= 0) {
                    onPlySelected.accept(ply);
                }
            }
        });

        // Header label for moves
        JLabel header = new JLabel("  Move History");
        header.setForeground(new Color(140, 140, 140));
        header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        header.setBackground(new Color(40, 40, 42));
        header.setOpaque(true);
        header.setPreferredSize(new Dimension(200, 30));

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // Header label for FEN
        JLabel fenHeader = new JLabel("  Current FEN");
        fenHeader.setForeground(new Color(140, 140, 140));
        fenHeader.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        fenHeader.setBackground(new Color(40, 40, 42));
        fenHeader.setOpaque(true);
        fenHeader.setPreferredSize(new Dimension(200, 25));

        // Style the FEN text area
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

        // Create a panel for the bottom section (FEN)
        JPanel fenPanel = new JPanel(new BorderLayout());
        fenPanel.setBackground(new Color(28, 28, 30));
        fenPanel.add(fenHeader, BorderLayout.NORTH);
        fenPanel.add(fenScroll, BorderLayout.CENTER);

        // Create a panel for the top section (moves)
        JPanel movePanel = new JPanel(new BorderLayout());
        movePanel.setBackground(new Color(28, 28, 30));
        movePanel.add(header, BorderLayout.NORTH);
        movePanel.add(scroll, BorderLayout.CENTER);

        // Main layout: moves on top, FEN on bottom
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, movePanel, fenPanel);
        splitPane.setResizeWeight(0.7);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setBackground(new Color(28, 28, 30));
        splitPane.setDividerSize(4);

        add(splitPane, BorderLayout.CENTER);
    }

    /**
     * Rebuilds the text area from the current log.
     * Call this after every move.
     * <p>
     * Pairs up entries so White and Black appear on the same line:
     * <p>
     * 1.  e4        e5
     * 2.  Nf3       Nc6
     * 3.  O-O       ...
     */
    @Override
    public void update(List<String> log, String currentFen) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < log.size(); i += 2) {
            int moveNum = i / 2 + 1;
            String white = log.get(i);
            String black = (i + 1 < log.size()) ? log.get(i + 1) : "...";

            sb.append(String.format("%3d.  %-9s %s%n", moveNum, white, black));
        }

        area.setText(sb.toString());

        // Auto-scroll to the latest move
        area.setCaretPosition(area.getDocument().getLength());

        // Update FEN display
        fenArea.setText(currentFen);
        fenArea.setCaretPosition(0);
    }

    @Override
    public void clear() {
        area.setText("");
        fenArea.setText("");
    }

    /**
     * Sets who is told when a player clicks a move in the log.
     * <p>
     * The log knows which move was clicked but nothing about games, so it reports the ply and lets
     * the caller decide what happens. Without a listener a click does nothing at all.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pListener told the number of moves the game should be at, or null for nobody
     */
    public void setPlySelectedListener(IntConsumer pListener) {
        this.onPlySelected = pListener == null ? ply -> {
        } : pListener;
    }

    /**
     * Works out which move of the game a point in the log belongs to.
     * <p>
     * Every line of the log holds one full move, White's first and Black's behind it at a fixed
     * column, because the text is laid out in a monospaced font. So the line gives the move number
     * and the column says which of the two halves was hit. I report the number of moves the game
     * would have played after that move, which is what jumping there means. A click past the end of
     * the text belongs to no move.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPoint point inside the move area, never null
     * @return how many moves are played after the clicked move, or -1 when none was hit
     */
    private int plyAt(Point pPoint) {
        try {
            int offset = area.viewToModel2D(pPoint);
            int line = area.getLineOfOffset(offset);
            int column = offset - area.getLineStartOffset(line);
            // the first half of the line is White's move, the rest is Black's
            int half = column < BLACK_MOVE_COLUMN ? 0 : 1;
            // the game stands after the move that was clicked, so one ply further than its index
            return line * 2 + half + 1;
        } catch (BadLocationException e) {
            // a point outside the text names no move
            return -1;
        }
    }
}