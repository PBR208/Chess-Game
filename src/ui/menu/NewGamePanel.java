package ui.menu;

import engine.model.GameConfig;
import app.Main;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class NewGamePanel extends JPanel {

    private static final Object[][] PRESETS = {
            {"Unlimited", "Unlimited", 0L, 0L},
            {"Bullet 1+0", "Bullet 1+0", 60_000L, 60_000L},
            {"Bullet 2+1", "Bullet 2+1", 120_000L, 120_000L},
            {"Blitz 3+0", "Blitz 3+0", 180_000L, 180_000L},
            {"Blitz 5+0", "Blitz 5+0", 300_000L, 300_000L},
            {"Rapid 10+0", "Rapid 10+0", 600_000L, 600_000L},
            {"Rapid 15+10", "Rapid 15+10", 900_000L, 900_000L},
            {"Classical 30+0", "Classical 30+0", 1_800_000L, 1_800_000L},
    };

    private final JTextField whiteField = new JTextField("White", 14);
    private final JTextField blackField = new JTextField("Black", 14);
    private final JTextField customMin = new JTextField("10", 4);
    private final JTextField customSec = new JTextField("0", 4);

    private long selectedWhiteMs = 600_000L;
    private long selectedBlackMs = 600_000L;
    private String selectedLabel = "Rapid 10+0";

    public NewGamePanel() {
        setBackground(Theme.BG);
        setLayout(new GridBagLayout());

        JPanel card = new JPanel();
        card.setBackground(Theme.PANEL_BG);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(32, 40, 32, 40));
        card.setMaximumSize(new Dimension(520, Integer.MAX_VALUE));

        JLabel title = new JLabel("New Game");
        title.setForeground(Theme.FG);
        title.setFont(new Font("Arial", Font.BOLD, 26));
        title.setAlignmentX(CENTER_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(28));

        card.add(sectionLabel("Players"));
        card.add(Box.createVerticalStrut(10));

        JPanel names = new JPanel(new GridLayout(2, 2, 8, 8));
        names.setBackground(Theme.PANEL_BG);
        names.add(fieldLabel("White"));
        names.add(fieldLabel("Black"));
        styleField(whiteField);
        styleField(blackField);
        names.add(whiteField);
        names.add(blackField);
        card.add(names);
        card.add(Box.createVerticalStrut(28));

        card.add(sectionLabel("Time Control"));
        card.add(Box.createVerticalStrut(10));

        JPanel presets = new JPanel(new GridLayout(0, 4, 6, 6));
        presets.setBackground(Theme.PANEL_BG);
        ButtonGroup group = new ButtonGroup();

        for (Object[] p : PRESETS) {
            JToggleButton btn = new JToggleButton((String) p[0]);
            UiComponents.style(btn, new Font("Arial", Font.PLAIN, 12), Theme.BUTTON_SECONDARY);

            long wMs = (long) p[2];
            long bMs = (long) p[3];
            String label = (String) p[1];

            btn.addActionListener(e -> {
                selectedWhiteMs = wMs;
                selectedBlackMs = bMs;
                selectedLabel = label;
                btn.setBackground(Theme.ACCENT);
            });

            if (label.equals("Rapid 10+0")) {
                btn.setSelected(true);
                btn.setBackground(Theme.ACCENT);
            }

            group.add(btn);
            presets.add(btn);
        }
        card.add(presets);
        card.add(Box.createVerticalStrut(10));

        JPanel customRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        customRow.setBackground(Theme.PANEL_BG);

        JToggleButton customBtn = new JToggleButton("Custom:");
        UiComponents.style(customBtn, new Font("Arial", Font.PLAIN, 12), Theme.BUTTON_SECONDARY);
        group.add(customBtn);

        styleField(customMin);
        styleField(customSec);

        customBtn.addActionListener(e -> {
            customBtn.setBackground(Theme.ACCENT);
            applyCustomTime();
        });
        customMin.addActionListener(e -> applyCustomTime());
        customSec.addActionListener(e -> applyCustomTime());

        customRow.add(customBtn);
        customRow.add(customMin);
        customRow.add(fieldLabel("min"));
        customRow.add(customSec);
        customRow.add(fieldLabel("sec"));
        card.add(customRow);
        card.add(Box.createVerticalStrut(32));

        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.setBackground(Theme.PANEL_BG);

        JButton backBtn = actionButton("← Back", false);
        JButton startBtn = actionButton("Start ▶", true);

        backBtn.addActionListener(e -> Main.showMenu());
        startBtn.addActionListener(e -> {
            GameConfig cfg = new GameConfig(
                    whiteField.getText().trim(),
                    blackField.getText().trim(),
                    selectedWhiteMs,
                    selectedBlackMs,
                    selectedLabel);
            Main.startGame(cfg);
        });

        buttons.add(backBtn);
        buttons.add(startBtn);
        card.add(buttons);

        add(card, new GridBagConstraints());
    }

    private void applyCustomTime() {
        try {
            long mins = Long.parseLong(customMin.getText().trim());
            long secs = Long.parseLong(customSec.getText().trim());
            selectedWhiteMs = (mins * 60 + secs) * 1000L;
            selectedBlackMs = selectedWhiteMs;
            selectedLabel = "Custom " + mins + "+" + secs;
        } catch (NumberFormatException ex) {
            // ignore invalid input, keep previous selection
        }
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(160, 160, 170));
        l.setFont(new Font("Arial", Font.BOLD, 12));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(160, 160, 170));
        l.setFont(new Font("Arial", Font.PLAIN, 12));
        return l;
    }

    private void styleField(JTextField f) {
        f.setBackground(new Color(50, 50, 55));
        f.setForeground(Theme.FG);
        f.setCaretColor(Theme.FG);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 75)),
                new EmptyBorder(4, 8, 4, 8)));
        f.setFont(new Font("Arial", Font.PLAIN, 13));
    }

    private JButton actionButton(String text, boolean primary) {
        JButton b = UiComponents.button(text, new Font("Arial", Font.BOLD, 14),
                primary ? Theme.ACCENT : Theme.BUTTON_SECONDARY);
        b.setPreferredSize(new Dimension(0, 44));
        return b;
    }
}