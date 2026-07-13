package ui.theme;

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