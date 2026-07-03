package gui;

import main.Main;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MainMenu extends JPanel {

    private static final Color BG = new Color(28, 28, 30);
    private static final Color PANEL_BG = new Color(38, 38, 42);
    private static final Color FG = Color.WHITE;
    private static final Color ACCENT = new Color(81, 168, 0);
    private static final Color MUTED = new Color(110, 110, 115);

    public MainMenu() {
        setBackground(BG);
        setLayout(new GridBagLayout());
        add(buildCard(), new GridBagConstraints());
    }

    private JPanel buildCard() {
        JPanel card = new JPanel();
        card.setBackground(PANEL_BG);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(48, 64, 48, 64));
        card.setMaximumSize(new Dimension(360, Integer.MAX_VALUE));

        JLabel icons = new JLabel("♔  ♚", SwingConstants.CENTER);
        icons.setForeground(new Color(180, 180, 190));
        icons.setFont(new Font("Serif", Font.PLAIN, 40));
        icons.setAlignmentX(CENTER_ALIGNMENT);
        card.add(icons);
        card.add(Box.createVerticalStrut(12));

        JLabel title = new JLabel("CHESS", SwingConstants.CENTER);
        title.setForeground(FG);
        title.setFont(new Font("Arial", Font.BOLD, 36));
        title.setAlignmentX(CENTER_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(8));

        JLabel sub = new JLabel("Two-player local game", SwingConstants.CENTER);
        sub.setForeground(MUTED);
        sub.setFont(new Font("Arial", Font.PLAIN, 13));
        sub.setAlignmentX(CENTER_ALIGNMENT);
        card.add(sub);
        card.add(Box.createVerticalStrut(36));

        card.add(menuButton("New Game", true, e -> showNewGamePanel()));
        card.add(Box.createVerticalStrut(10));
        card.add(menuButton("Past Games", false, e -> Main.showPastGames()));

        return card;
    }

    private void showNewGamePanel() {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (frame == null) return;
        frame.setContentPane(new NewGamePanel());
        frame.revalidate();
        frame.repaint();
    }

    private JButton menuButton(String text, boolean primary,
                               java.awt.event.ActionListener action) {
        JButton b = new JButton(text);
        b.setFont(new Font("Arial", Font.BOLD, 15));
        b.setForeground(FG);
        b.setBackground(primary ? ACCENT : new Color(55, 55, 60));
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setAlignmentX(CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        b.setPreferredSize(new Dimension(240, 48));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(action);

        Color normal = b.getBackground();
        Color hover = normal.brighter();
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                b.setBackground(hover);
            }

            public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(normal);
            }
        });

        return b;
    }
}