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
}