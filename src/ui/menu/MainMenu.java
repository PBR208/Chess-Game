package ui.menu;

import app.Main;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MainMenu extends JPanel {

    public MainMenu() {
        setBackground(Theme.BG);
        setLayout(new GridBagLayout());
        add(buildCard(), new GridBagConstraints());
    }

    private JPanel buildCard() {
        JPanel card = new JPanel();
        card.setBackground(Theme.PANEL_BG);
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
        title.setForeground(Theme.FG);
        title.setFont(new Font("Arial", Font.BOLD, 36));
        title.setAlignmentX(CENTER_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(8));

        JLabel sub = new JLabel("Two-player local game", SwingConstants.CENTER);
        sub.setForeground(Theme.MUTED);
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
        JButton b = UiComponents.button(text, new Font("Arial", Font.BOLD, 15),
                primary ? Theme.ACCENT : Theme.BUTTON_SECONDARY);
        b.setAlignmentX(CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        b.setPreferredSize(new Dimension(240, 48));
        b.addActionListener(action);
        return b;
    }
}