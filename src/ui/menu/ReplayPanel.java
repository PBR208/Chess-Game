package ui.menu;

import engine.persistence.FenLoader;
import engine.pieces.Piece;
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

    // Board-tile colors mirror ui.board.Board's own palette; out of scope for this
    // pass since Board isn't one of the four menu-style panels being unified.
    private static final Color LIGHT_TILE = new Color(232, 235, 239);
    private static final Color DARK_TILE = new Color(125, 135, 150);

    private final int tileSize = 70;
    private final List<String> fens;
    private int cursor = 0;

    private final JLabel moveLabel;

    public ReplayPanel(List<String> fens) {
        this.fens = fens;
        setBackground(Theme.BG);
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel boardCanvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawPosition((Graphics2D) g);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(tileSize * 8, tileSize * 8);
            }
        };
        boardCanvas.setBackground(Theme.BG);
        add(boardCanvas, BorderLayout.CENTER);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        nav.setBackground(Theme.BG);

        moveLabel = new JLabel(moveText(), SwingConstants.CENTER);
        moveLabel.setForeground(Theme.FG);
        moveLabel.setFont(new Font("Arial", Font.PLAIN, 13));

        JButton first = navButton("⏮");
        first.addActionListener(e -> {
            cursor = 0;
            refresh(boardCanvas);
        });
        JButton prev = navButton("←");
        prev.addActionListener(e -> {
            if (cursor > 0) cursor--;
            refresh(boardCanvas);
        });
        JButton next = navButton("→");
        next.addActionListener(e -> {
            if (cursor < fens.size() - 1) cursor++;
            refresh(boardCanvas);
        });
        JButton last = navButton("⏭");
        last.addActionListener(e -> {
            cursor = fens.size() - 1;
            refresh(boardCanvas);
        });

        nav.add(first);
        nav.add(prev);
        nav.add(moveLabel);
        nav.add(next);
        nav.add(last);
        add(nav, BorderLayout.SOUTH);

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
    }

    private void refresh(JPanel canvas) {
        moveLabel.setText(moveText());
        canvas.repaint();
    }

    private String moveText() {
        if (fens.isEmpty()) return "No moves";
        int move = cursor / 2 + 1;
        String who = cursor % 2 == 0 ? "White" : "Black";
        return "After move " + move + " (" + who + ") — position " + (cursor + 1) + "/" + fens.size();
    }

    private void drawPosition(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (fens.isEmpty()) {
            g2d.setColor(Color.GRAY);
            g2d.drawString("No position to display", 20, 40);
            return;
        }

        char[][] grid = FenLoader.parse(fens.get(cursor));
        BufferedImage sheet = Piece.getSpritesheet();
        int scale = Piece.getSpritesheetScale();

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

    private JButton navButton(String text) {
        JButton b = UiComponents.button(text, new Font("Arial", Font.BOLD, 16), Theme.BUTTON_SECONDARY);
        b.setPreferredSize(new Dimension(44, 32));
        return b;
    }
}