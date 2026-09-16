package ui.theme;

/*
 * Purpose: UiComponents holds the small helpers every screen uses to give buttons the shared flat,
 * dark look and hover effect. It also picks plain ASCII text for fonts that can't draw arrows or
 * chess symbols, so a missing glyph shows up as readable text instead of an empty box. I keep these
 * helpers in one place, so all screens look and behave the same on every platform.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class UiComponents {

    private UiComponents() {
    }

    public static void style(AbstractButton b, Font font, Color background) {
        b.setFont(font);
        b.setForeground(Theme.FG);
        b.setBackground(background);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addHoverEffect(b);
    }

    public static JButton button(String text, Font font, Color background) {
        JButton b = new JButton(text);
        style(b, font, background);
        return b;
    }

    /**
     * Creates a styled button whose text falls back to ASCII when the font lacks a symbol.
     * <p>
     * Several buttons show arrows or play symbols, which some fonts, mostly on Linux, don't contain.
     * I choose the text with {@link #displayable} for the button's own font and then build the button
     * exactly like {@link #button(String, Font, Color)} does.
     * <p>
     * Time complexity: O(n) for the n characters of pText. Space complexity: O(1) apart from the button.
     *
     * @param pText       preferred button text, possibly with symbols, never null
     * @param pAsciiText  plain ASCII text shown when pFont can't draw pText, never null
     * @param pFont       font of the button, never null
     * @param pBackground background colour of the button, never null
     * @return the finished button, never null
     */
    public static JButton button(String pText, String pAsciiText, Font pFont, Color pBackground) {
        return button(displayable(pFont, pText, pAsciiText), pFont, pBackground);
    }

    /**
     * Picks a text the font can draw, or the plain ASCII replacement when it can't.
     * <p>
     * Swing draws an empty box for every character a font doesn't contain, so a button labelled with
     * an arrow can end up showing nothing useful. I ask the font whether it covers the whole text and
     * return the replacement as soon as a single character is missing.
     * <p>
     * Time complexity: O(n) for the n characters of pText. Space complexity: O(1).
     *
     * @param pFont      font the text will be drawn with, never null
     * @param pText      preferred text, possibly with symbols, never null
     * @param pAsciiText plain ASCII text to use instead, never null
     * @return pText when pFont can draw all of it, otherwise pAsciiText
     * @throws NullPointerException if pFont or pText is null
     */
    public static String displayable(Font pFont, String pText, String pAsciiText) {
        // -1 means the font covers every character
        return pFont.canDisplayUpTo(pText) == -1 ? pText : pAsciiText;
    }

    public static void addHoverEffect(AbstractButton b) {
        b.addMouseListener(new MouseAdapter() {
            private Color baseColor;

            public void mouseEntered(MouseEvent e) {
                baseColor = b.getBackground();
                b.setBackground(baseColor.brighter());
            }

            public void mouseExited(MouseEvent e) {
                // Don't restore color for selected toggle buttons - keep them highlighted
                if (b instanceof JToggleButton && ((JToggleButton) b).isSelected()) {
                    return;
                }
                b.setBackground(baseColor);
            }
        });
    }
}
