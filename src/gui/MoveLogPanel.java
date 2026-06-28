package gui;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class MoveLogPanel extends JPanel {

    private final JTextArea area = new JTextArea();

    public MoveLogPanel(int boardHeight) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(200, boardHeight));
        setBackground(new Color(28, 28, 30));

        // Style the text area to match the clock colour scheme
        area.setEditable(false);
        area.setBackground(new Color(28, 28, 30));
        area.setForeground(new Color(210, 210, 210));
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setMargin(new Insets(8, 8, 8, 8));

        // Header label
        JLabel header = new JLabel("  Move History");
        header.setForeground(new Color(140, 140, 140));
        header.setFont(new Font("Arial", Font.BOLD, 12));
        header.setBackground(new Color(40, 40, 42));
        header.setOpaque(true);
        header.setPreferredSize(new Dimension(200, 30));

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
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
    public void update(List<String> log) {
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
    }
    
    public void clear() {
        area.setText("");
    }
}