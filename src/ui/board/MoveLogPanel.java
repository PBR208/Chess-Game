package ui.board;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class MoveLogPanel extends JPanel {

    private final JTextArea area = new JTextArea();
    private final JTextArea fenArea = new JTextArea();

    public MoveLogPanel(int boardHeight) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(200, boardHeight));
        setBackground(new Color(28, 28, 30));

        // Style the move history text area
        area.setEditable(false);
        area.setBackground(new Color(28, 28, 30));
        area.setForeground(new Color(210, 210, 210));
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setMargin(new Insets(8, 8, 8, 8));

        // Header label for moves
        JLabel header = new JLabel("  Move History");
        header.setForeground(new Color(140, 140, 140));
        header.setFont(new Font("Arial", Font.BOLD, 12));
        header.setBackground(new Color(40, 40, 42));
        header.setOpaque(true);
        header.setPreferredSize(new Dimension(200, 30));

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // Header label for FEN
        JLabel fenHeader = new JLabel("  Current FEN");
        fenHeader.setForeground(new Color(140, 140, 140));
        fenHeader.setFont(new Font("Arial", Font.BOLD, 12));
        fenHeader.setBackground(new Color(40, 40, 42));
        fenHeader.setOpaque(true);
        fenHeader.setPreferredSize(new Dimension(200, 25));

        // Style the FEN text area
        fenArea.setEditable(false);
        fenArea.setBackground(new Color(28, 28, 30));
        fenArea.setForeground(new Color(210, 210, 210));
        fenArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
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

    public void clear() {
        area.setText("");
        fenArea.setText("");
    }
}