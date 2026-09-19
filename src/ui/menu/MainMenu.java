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
import ui.i18n.Messages;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Locale;

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

        // plain letters stand in when the font has no chess symbols
        Font iconFont = new Font(Font.SERIF, Font.PLAIN, 40);
        JLabel icons = new JLabel(UiComponents.displayable(iconFont, "\u2654  \u265a", "K  k"), SwingConstants.CENTER);
        icons.setForeground(new Color(180, 180, 190));
        icons.setFont(iconFont);
        icons.setAlignmentX(CENTER_ALIGNMENT);
        card.add(icons);
        card.add(Box.createVerticalStrut(12));

        JLabel title = new JLabel(Messages.get("menu.title"), SwingConstants.CENTER);
        title.setForeground(Theme.FG);
        // logical fonts exist on every platform
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
        title.setAlignmentX(CENTER_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(8));

        JLabel sub = new JLabel(Messages.get("menu.subtitle"), SwingConstants.CENTER);
        sub.setForeground(Theme.MUTED);
        sub.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        sub.setAlignmentX(CENTER_ALIGNMENT);
        card.add(sub);
        card.add(Box.createVerticalStrut(36));

        card.add(menuButton(Messages.get("menu.newGame"), true, e -> showNewGamePanel()));
        card.add(Box.createVerticalStrut(10));
        card.add(menuButton(Messages.get("menu.setUpPosition"), false, e -> Main.showSetup()));
        card.add(Box.createVerticalStrut(10));
        card.add(menuButton(Messages.get("menu.pastGames"), false, e -> Main.showPastGames()));

        card.add(Box.createVerticalStrut(24));
        card.add(languageRow());

        return card;
    }

    /**
     * Builds the row where a player picks the language the game speaks.
     * <p>
     * The menu is the first screen anybody sees, so it is where the language belongs. Each language
     * names itself in its own language, because that is the word a player is looking for: somebody
     * who wants German is looking for Deutsch, not for German. The language in use is marked in the
     * accent colour. Screens read their text while they are built, so picking a language rebuilds
     * the menu straight away, which both applies the change and shows it.
     * <p>
     * Time complexity: O(k) for the k languages the game ships.
     * Space complexity: O(k) for their buttons.
     *
     * @return the language row, never null
     */
    private JPanel languageRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setBackground(Theme.PANEL_BG);
        row.setAlignmentX(CENTER_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel label = new JLabel(Messages.get("menu.language"));
        label.setForeground(Theme.MUTED);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        row.add(label);

        for (Locale supported : Messages.supportedLocales()) {
            // a language names itself in its own language, which is the word a player looks for
            JButton button = UiComponents.button(supported.getDisplayLanguage(supported),
                    new Font(Font.SANS_SERIF, Font.PLAIN, 12),
                    supported.equals(Messages.getLocale()) ? Theme.ACCENT : Theme.BUTTON_SECONDARY);
            // the name stays the same whatever language the button is written in
            button.setName("language-" + supported.getLanguage());
            button.addActionListener(e -> switchLanguage(supported));
            row.add(button);
        }
        return row;
    }

    /**
     * Switches the language and rebuilds the menu in it.
     * <p>
     * Every screen reads its text as it is built, so a language change shows up as screens are
     * built again. I rebuild this one at once, which is both how the change takes effect here and
     * how a player sees that it worked. I find the window through this panel rather than through the
     * application's own frame, so the menu also works inside a test that never started the app.
     * <p>
     * Time complexity: O(n) in the size of the language bundle, read once.
     * Space complexity: O(n) for the bundle and the new menu.
     *
     * @param pLocale the language to switch to, never null
     */
    private void switchLanguage(Locale pLocale) {
        Messages.setLocale(pLocale);
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        // a menu that is not in a window yet has nothing to rebuild into
        if (frame == null) {
            return;
        }
        frame.setContentPane(new MainMenu());
        frame.revalidate();
        frame.repaint();
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
