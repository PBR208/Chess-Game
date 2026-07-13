package ui.board;

import engine.pieces.Piece;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PromoteGUI extends JDialog {

    public enum Choice {
        QUEEN, ROOK, BISHOP, KNIGHT
    }

    private Choice choice;

    // Sprite column indices for each piece type
    private static final int QUEEN_SPRITE = 1;
    private static final int ROOK_SPRITE = 4;
    private static final int BISHOP_SPRITE = 2;
    private static final int KNIGHT_SPRITE = 3;

    public PromoteGUI(JFrame parent, int tileSize) {
        super(parent, true);

        int gap = tileSize / 8;
        setLayout(new GridLayout(1, 4, gap, gap));
        setUndecorated(true);
        getContentPane().setBackground(Theme.PANEL_BG);

        BufferedImage spritesheet = Piece.getSpritesheet();
        int scale = Piece.getSpritesheetScale();

        JButton queen = createPieceButton(getPieceImage(spritesheet, scale, QUEEN_SPRITE, tileSize));
        JButton rook = createPieceButton(getPieceImage(spritesheet, scale, ROOK_SPRITE, tileSize));
        JButton bishop = createPieceButton(getPieceImage(spritesheet, scale, BISHOP_SPRITE, tileSize));
        JButton knight = createPieceButton(getPieceImage(spritesheet, scale, KNIGHT_SPRITE, tileSize));

        queen.addActionListener(e -> {
            choice = Choice.QUEEN;
            dispose();
        });
        rook.addActionListener(e -> {
            choice = Choice.ROOK;
            dispose();
        });
        bishop.addActionListener(e -> {
            choice = Choice.BISHOP;
            dispose();
        });
        knight.addActionListener(e -> {
            choice = Choice.KNIGHT;
            dispose();
        });

        add(queen);
        add(rook);
        add(bishop);
        add(knight);

        setSize(tileSize * 4, tileSize);
        setLocationRelativeTo(parent);
    }

    private BufferedImage getPieceImage(BufferedImage spritesheet, int scale, int spriteCol, int tileSize) {
        BufferedImage sprite = spritesheet.getSubimage(spriteCol * scale, 0, scale, scale);
        Image scaled = sprite.getScaledInstance(tileSize, tileSize, Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();
        return result;
    }

    private JButton createPieceButton(BufferedImage icon) {
        JButton btn = new JButton(new ImageIcon(icon));
        btn.setBackground(Theme.BUTTON_SECONDARY);
        btn.setForeground(Theme.FG);
        btn.setContentAreaFilled(false);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        UiComponents.addHoverEffect(btn);
        return btn;
    }

    public Choice showDialog() {
        setVisible(true);
        return choice;
    }
}