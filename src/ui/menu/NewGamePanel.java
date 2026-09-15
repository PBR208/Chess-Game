package ui.menu;

/*
 * Purpose: NewGamePanel is the screen where players set up a new game before it starts. It asks
 * for both player names and a time control, either one of the presets or a custom duration. I
 * collect everything into a GameConfig, so the board, the clocks and the saved game all start from
 * the same settings. Presets such as Bullet 2+1 also carry their increment.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.model.GameConfig;
import app.Main;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class NewGamePanel extends JPanel {

    // button text, label, white time, black time and increment in milliseconds
    private static final Object[][] PRESETS = {
            {"Unlimited", "Unlimited", 0L, 0L, 0L},
            {"Bullet 1+0", "Bullet 1+0", 60_000L, 60_000L, 0L},
            {"Bullet 2+1", "Bullet 2+1", 120_000L, 120_000L, 1_000L},
            {"Blitz 3+0", "Blitz 3+0", 180_000L, 180_000L, 0L},
            {"Blitz 5+0", "Blitz 5+0", 300_000L, 300_000L, 0L},
            {"Rapid 10+0", "Rapid 10+0", 600_000L, 600_000L, 0L},
            {"Rapid 15+10", "Rapid 15+10", 900_000L, 900_000L, 10_000L},
            {"Classical 30+0", "Classical 30+0", 1_800_000L, 1_800_000L, 0L},
    };

    private final JTextField whiteField = new JTextField("White", 14);
    private final JTextField blackField = new JTextField("Black", 14);
    private final JTextField customMin = new JTextField("10", 4);
    private final JTextField customSec = new JTextField("0", 4);

    // preset that is selected when the screen opens
    private static final String DEFAULT_PRESET = "Rapid 10+0";

    // filled in from the default preset while the buttons are built
    private long selectedWhiteMs;
    private long selectedBlackMs;
    private String selectedLabel;
    private long selectedIncrementMs;

    /**
     * Builds the New Game screen with player names, time controls and the start and back buttons.
     * <p>
     * Before a game starts the players pick their names and a time control. I lay out the name
     * fields, one toggle button per preset that remembers its times and increment, with Rapid 10+0
     * selected and filled in from the start, the custom time row and the Back and Start buttons.
     * Start hands the resulting configuration to the main window.
     * <p>
     * Time complexity: O(k) for k presets. Space complexity: O(k) for their buttons.
     */
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
            // Bullet 2+1 and Rapid 15+10 add time after every move
            long incMs = (long) p[4];

            btn.addActionListener(e -> selectPreset(wMs, bMs, label, incMs));

            btn.addItemListener(e -> {
                btn.setBackground(btn.isSelected() ? Theme.ACCENT : Theme.BUTTON_SECONDARY);
            });

            // setSelected doesn't run the button's action, so the default fills in its values here
            if (label.equals(DEFAULT_PRESET)) {
                btn.setSelected(true);
                btn.setBackground(Theme.ACCENT);
                selectPreset(wMs, bMs, label, incMs);
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
            applyCustomTime();
        });

        customBtn.addItemListener(e -> {
            customBtn.setBackground(customBtn.isSelected() ? Theme.ACCENT : Theme.BUTTON_SECONDARY);
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

        JButton backBtn = actionButton("\u2190 Back", false);
        JButton startBtn = actionButton("Start \u25b6", true);

        backBtn.addActionListener(e -> Main.showMenu());
        // start the game with everything selected on this screen
        startBtn.addActionListener(e -> Main.startGame(createConfig()));

        buttons.add(backBtn);
        buttons.add(startBtn);
        card.add(buttons);

        add(card, new GridBagConstraints());
    }

    /**
     * Makes a preset the time control of the next game.
     * <p>
     * A preset has to fill in everything a game needs, whether it was clicked or preselected when
     * the screen opened. I store both starting times, the label and the increment of the preset.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pWhiteMs     White's starting time in milliseconds, 0 for unlimited
     * @param pBlackMs     Black's starting time in milliseconds, 0 for unlimited
     * @param pLabel       label of the preset such as "Rapid 10+0", never null
     * @param pIncrementMs time added after each move in milliseconds, 0 for none
     */
    private void selectPreset(long pWhiteMs, long pBlackMs, String pLabel, long pIncrementMs) {
        selectedWhiteMs = pWhiteMs;
        selectedBlackMs = pBlackMs;
        selectedLabel = pLabel;
        selectedIncrementMs = pIncrementMs;
    }

    /**
     * Uses the minutes and seconds from the custom fields as the time control.
     * <p>
     * Players who want a time that isn't a preset type it into the two fields. I parse both fields,
     * set the same total time for both players with no increment and build a matching label.
     * Invalid input keeps the previous selection.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    private void applyCustomTime() {
        try {
            long mins = Long.parseLong(customMin.getText().trim());
            long secs = Long.parseLong(customSec.getText().trim());
            selectedWhiteMs = (mins * 60 + secs) * 1000L;
            selectedBlackMs = selectedWhiteMs;
            selectedLabel = "Custom " + mins + "+" + secs;
            // custom times don't have an increment
            selectedIncrementMs = 0;
        } catch (NumberFormatException ex) {
            // ignore invalid input, keep previous selection
        }
    }

    /**
     * Builds the configuration for a new game from the current selections.
     * <p>
     * The Start button needs one object with everything the game has to know. I take the trimmed
     * names from the two fields and the times, label and increment of the selected time control.
     * <p>
     * Time complexity: O(n) in the length of the names. Space complexity: O(n) for the configuration.
     *
     * @return the configuration for the new game, never null
     */
    public GameConfig createConfig() {
        return new GameConfig(
                whiteField.getText().trim(),
                blackField.getText().trim(),
                selectedWhiteMs,
                selectedBlackMs,
                selectedLabel,
                selectedIncrementMs);
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