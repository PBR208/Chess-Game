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
}