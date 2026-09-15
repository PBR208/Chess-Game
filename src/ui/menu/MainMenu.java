package ui.menu;

/*
 * Purpose: MainMenu is the landing screen of the application. It shows the title and leads to the
 * two places a player can go from here, a new game or the library of past games. I keep it as its
 * own panel, so the main window can swap it in and out like every other screen. The text uses
 * logical font names, which every platform provides, instead of fonts that only exist on some.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

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

    /**
     * Builds the centered card with the chess symbols, the title and both navigation buttons.
     * <p>
     * The menu is the first thing a player sees and should look the same on every system. I stack
     * the chess symbols, the title, the subtitle and the New Game and Past Games buttons in a padded
     * card. All text uses logical fonts, because Arial is missing on most Linux systems and Java then
     * falls back to a different font without telling anyone.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the Swing components.
     *
     * @return the finished card, never null
     */
    private JPanel buildCard() {
        JPanel card = new JPanel();
        card.setBackground(Theme.PANEL_BG);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(48, 64, 48, 64));
        card.setMaximumSize(new Dimension(360, Integer.MAX_VALUE));

        JLabel icons = new JLabel("\u2654  \u265a", SwingConstants.CENTER);
        icons.setForeground(new Color(180, 180, 190));
        icons.setFont(new Font(Font.SERIF, Font.PLAIN, 40));
        icons.setAlignmentX(CENTER_ALIGNMENT);
        card.add(icons);
        card.add(Box.createVerticalStrut(12));

        JLabel title = new JLabel("CHESS", SwingConstants.CENTER);
        title.setForeground(Theme.FG);
        // logical fonts exist on every platform
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
        title.setAlignmentX(CENTER_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(8));

        JLabel sub = new JLabel("Two-player local game", SwingConstants.CENTER);
        sub.setForeground(Theme.MUTED);
        sub.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
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

    /**
     * Creates one of the large navigation buttons of the menu.
     * <p>
     * Both menu buttons share size, font and colours, and the primary one stands out in the accent
     * colour. I style a button with the shared look, give it a logical bold font and a fixed height,
     * and attach the action.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the button.
     *
     * @param pText    button text, never null
     * @param pPrimary true for the accent coloured main action, false for a secondary one
     * @param pAction  what happens when the button is pressed, never null
     * @return the finished button, never null
     */
    private JButton menuButton(String pText, boolean pPrimary,
                               java.awt.event.ActionListener pAction) {
        JButton b = UiComponents.button(pText, new Font(Font.SANS_SERIF, Font.BOLD, 15),
                pPrimary ? Theme.ACCENT : Theme.BUTTON_SECONDARY);
        b.setAlignmentX(CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        b.setPreferredSize(new Dimension(240, 48));
        b.addActionListener(pAction);
        return b;
    }
}
