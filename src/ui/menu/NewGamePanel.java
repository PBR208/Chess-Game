package ui.menu;

/*
 * Purpose: NewGamePanel is the screen where players set up a new game before it starts. It asks for
 * both player names, who the second player is, and a time control, either one of the presets or a
 * custom duration, together with the two things that change how a clock behaves rather than how long
 * it runs: which mode it plays and how much time one side gets when the players want a game at odds.
 * I collect the names and the clocks into a GameConfig and the opponent into EngineSettings, so the
 * board, the clocks and the saved game all start from the same answers. The side and level choices
 * are only enabled while the program is the opponent, because against another person they would
 * change nothing.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.1
 */

import engine.core.Pieces;
import engine.model.EngineSettings;
import engine.model.ClockMode;
import engine.model.ClockStage;
import engine.model.GameConfig;
import app.Main;
import ui.i18n.Messages;
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
    // selecting it makes the game use the minutes and seconds typed next to it
    private final JToggleButton customBtn = new JToggleButton(Messages.get("newgame.custom"));
    // explains why a custom time can't be used, a single space keeps the line's height
    private final JLabel customError = new JLabel(" ");

    // who the second player is, and which side and level to use when it is the program
    private final JToggleButton personOpponent = new JToggleButton(Messages.get("newgame.opponentPerson"));
    private final JToggleButton computerOpponent = new JToggleButton(Messages.get("newgame.opponentComputer"));
    private final JToggleButton playWhite = new JToggleButton(Messages.get("newgame.playWhite"));
    private final JToggleButton playBlack = new JToggleButton(Messages.get("newgame.playBlack"));
    private final JToggleButton[] levels =
            new JToggleButton[EngineSettings.MAX_LEVEL - EngineSettings.MIN_LEVEL + 1];

    // the level that is selected when the screen opens, in the middle of what is on offer
    private static final int DEFAULT_LEVEL = 3;

    private int selectedLevel = DEFAULT_LEVEL;

    // longest custom time the screen accepts
    private static final long MAX_CUSTOM_TIME_MS = 24 * 60 * 60 * 1000L;

    // preset that is selected when the screen opens
    private static final String DEFAULT_PRESET = "Rapid 10+0";

    // filled in from the default preset while the buttons are built
    private long selectedWhiteMs;
    private long selectedBlackMs;
    private String selectedLabel;
    private long selectedIncrementMs;

    // the clock mode a player picked, or null while the mode follows the preset's increment
    private ClockMode selectedMode;
    // seconds a Bronstein or simple delay clock waits on every move
    private final JTextField delaySec = new JTextField("0", 4);
    // Black's own starting time in minutes for a game at odds, empty for the same as White
    private final JTextField blackOddsMin = new JTextField("", 4);
    // a tournament control written the way players write it, such as "40/90, 30"
    private final JTextField stagesField = new JTextField("", 10);

    /**
     * Builds the New Game screen with player names, time controls and the start and back buttons.
     * <p>
     * Before a game starts the players pick their names and a time control. I lay out the name
     * fields, one toggle button per preset that remembers its times and increment, with Rapid 10+0
     * selected and filled in from the start, the custom time row with a line for input problems, and
     * the Back and Start buttons. Start hands the resulting configuration to the main window unless
     * the custom time can't be used.
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

        JLabel title = new JLabel(Messages.get("newgame.title"));
        title.setForeground(Theme.FG);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        title.setAlignmentX(CENTER_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(28));

        card.add(sectionLabel(Messages.get("newgame.players")));
        card.add(Box.createVerticalStrut(10));

        JPanel names = new JPanel(new GridLayout(2, 2, 8, 8));
        names.setBackground(Theme.PANEL_BG);
        names.add(fieldLabel(Messages.get("newgame.white")));
        names.add(fieldLabel(Messages.get("newgame.black")));
        styleField(whiteField);
        styleField(blackField);
        names.add(whiteField);
        names.add(blackField);
        card.add(names);
        card.add(Box.createVerticalStrut(28));

        card.add(sectionLabel(Messages.get("newgame.opponent")));
        card.add(Box.createVerticalStrut(10));

        JPanel opponentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        opponentRow.setBackground(Theme.PANEL_BG);
        ButtonGroup opponentGroup = new ButtonGroup();
        choiceButton(personOpponent, "opponentPerson", opponentGroup, opponentRow);
        choiceButton(computerOpponent, "opponentComputer", opponentGroup, opponentRow);
        card.add(opponentRow);
        card.add(Box.createVerticalStrut(10));

        JPanel sideRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sideRow.setBackground(Theme.PANEL_BG);
        ButtonGroup sideGroup = new ButtonGroup();
        choiceButton(playWhite, "sideWhite", sideGroup, sideRow);
        choiceButton(playBlack, "sideBlack", sideGroup, sideRow);
        card.add(sideRow);
        card.add(Box.createVerticalStrut(10));

        JPanel levelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        levelRow.setBackground(Theme.PANEL_BG);
        levelRow.add(fieldLabel(Messages.get("newgame.level")));
        ButtonGroup levelGroup = new ButtonGroup();
        for (int level = EngineSettings.MIN_LEVEL; level <= EngineSettings.MAX_LEVEL; level++) {
            JToggleButton button = new JToggleButton(String.valueOf(level));
            int chosen = level;
            button.addActionListener(e -> selectedLevel = chosen);
            choiceButton(button, "level" + level, levelGroup, levelRow);
            levels[level - EngineSettings.MIN_LEVEL] = button;
        }
        card.add(levelRow);
        card.add(Box.createVerticalStrut(28));

        // a game between two people starts with White at the bottom and needs no level
        select(personOpponent);
        select(playWhite);
        select(levels[DEFAULT_LEVEL - EngineSettings.MIN_LEVEL]);
        personOpponent.addActionListener(e -> showEngineChoices());
        computerOpponent.addActionListener(e -> showEngineChoices());
        showEngineChoices();

        card.add(sectionLabel(Messages.get("newgame.timeControl")));
        card.add(Box.createVerticalStrut(10));

        JPanel presets = new JPanel(new GridLayout(0, 4, 6, 6));
        presets.setBackground(Theme.PANEL_BG);
        ButtonGroup group = new ButtonGroup();

        for (Object[] p : PRESETS) {
            JToggleButton btn = new JToggleButton((String) p[0]);
            UiComponents.style(btn, new Font(Font.SANS_SERIF, Font.PLAIN, 12), Theme.BUTTON_SECONDARY);

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

        // the modes live in a group of their own, so picking one never deselects the time control
        JPanel modes = new JPanel(new GridLayout(0, 4, 6, 6));
        modes.setBackground(Theme.PANEL_BG);
        ButtonGroup modeGroup = new ButtonGroup();
        addModeButton(modes, modeGroup, Messages.get("newgame.modeSuddenDeath"), ClockMode.SUDDEN_DEATH);
        addModeButton(modes, modeGroup, Messages.get("newgame.modeFischer"), ClockMode.FISCHER);
        addModeButton(modes, modeGroup, Messages.get("newgame.modeBronstein"), ClockMode.BRONSTEIN);
        addModeButton(modes, modeGroup, Messages.get("newgame.modeDelay"), ClockMode.SIMPLE_DELAY);
        card.add(modes);
        card.add(Box.createVerticalStrut(10));

        JPanel customRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        customRow.setBackground(Theme.PANEL_BG);

        UiComponents.style(customBtn, new Font(Font.SANS_SERIF, Font.PLAIN, 12), Theme.BUTTON_SECONDARY);
        group.add(customBtn);

        styleField(customMin);
        styleField(customSec);

        // the fields are read when the game starts, so selecting Custom only clears an old message
        customBtn.addActionListener(e -> customError.setText(" "));

        customBtn.addItemListener(e -> {
            customBtn.setBackground(customBtn.isSelected() ? Theme.ACCENT : Theme.BUTTON_SECONDARY);
        });

        // pressing Enter in a field picks the custom time
        customMin.addActionListener(e -> customBtn.doClick());
        customSec.addActionListener(e -> customBtn.doClick());

        customRow.add(customBtn);
        customRow.add(customMin);
        customRow.add(fieldLabel(Messages.get("newgame.minutes")));
        customRow.add(customSec);
        customRow.add(fieldLabel(Messages.get("newgame.seconds")));
        card.add(customRow);

        // these two fields come after the custom row on purpose, because the tests that check the
        // custom time find the minute and second fields by their place among all the text fields
        JPanel extraRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        extraRow.setBackground(Theme.PANEL_BG);
        styleField(delaySec);
        styleField(blackOddsMin);
        extraRow.add(fieldLabel(Messages.get("newgame.delay")));
        extraRow.add(delaySec);
        extraRow.add(fieldLabel(Messages.get("newgame.seconds")));
        extraRow.add(fieldLabel("    " + Messages.get("newgame.blackGets")));
        extraRow.add(blackOddsMin);
        extraRow.add(fieldLabel(Messages.get("newgame.minutes")));
        card.add(extraRow);

        // the stages field comes last, for the same reason the two above it do
        JPanel stageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        stageRow.setBackground(Theme.PANEL_BG);
        styleField(stagesField);
        stageRow.add(fieldLabel(Messages.get("newgame.stages")));
        stageRow.add(stagesField);
        stageRow.add(fieldLabel(Messages.get("newgame.stagesExample")));
        card.add(stageRow);

        // tells the player why a custom time can't be used
        customError.setForeground(new Color(210, 90, 90));
        customError.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        customError.setAlignmentX(LEFT_ALIGNMENT);
        card.add(customError);
        card.add(Box.createVerticalStrut(24));

        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.setBackground(Theme.PANEL_BG);

        JButton backBtn = actionButton("\u2190 " + Messages.get("common.back"), "< " + Messages.get("common.back"), "back", false);
        JButton startBtn = actionButton(Messages.get("common.start") + " \u25b6", Messages.get("common.start") + " >", "start", true);

        backBtn.addActionListener(e -> Main.showMenu());
        // start the game with everything selected on this screen, unless the custom time is unusable
        startBtn.addActionListener(e -> {
            GameConfig config = createConfig();
            if (config != null) {
                Main.startGame(config, createEngineSettings());
            }
        });

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
     * Reads the custom time from the minute and second fields and checks that it can be used.
     * <p>
     * Players who want a time that isn't a preset type it into the two fields, and the value has to
     * make sense for a clock. I parse both fields as whole numbers, accept 0 to 59 seconds and a
     * total above zero and at most 24 hours, and show the reason under the fields when something is
     * wrong. A valid time clears the message.
     * <p>
     * Time complexity: O(n) in the length of the field texts. Space complexity: O(1).
     *
     * @return the custom time in milliseconds, or -1 when the input can't be used
     */
    private long readCustomTimeMs() {
        long mins;
        long secs;
        try {
            mins = Long.parseLong(customMin.getText().trim());
            secs = Long.parseLong(customSec.getText().trim());
        } catch (NumberFormatException ex) {
            customError.setText(Messages.get("newgame.errorWholeNumbers"));
            return -1;
        }
        // seconds above 59 belong in the minutes field
        if (mins < 0 || secs < 0 || secs > 59) {
            customError.setText(Messages.get("newgame.errorRange"));
            return -1;
        }
        // checking the minutes first also keeps the multiplication below from overflowing
        if (mins > MAX_CUSTOM_TIME_MS / 60_000 || (mins * 60 + secs) * 1000L > MAX_CUSTOM_TIME_MS
                || mins + secs == 0) {
            customError.setText(Messages.get("newgame.errorTooLong"));
            return -1;
        }
        customError.setText(" ");
        return (mins * 60 + secs) * 1000L;
    }

    /**
     * Builds the configuration for a new game from the current selections.
     * <p>
     * The Start button needs one object with everything the game has to know. I take the trimmed
     * names from the two fields and the times, label and increment of the selected preset. When
     * Custom is selected, I read the minute and second fields right now, so the value counts without
     * pressing Enter, and use it for both players without an increment. An unusable custom time gives
     * no configuration, and the reason is shown under the fields.
     * <p>
     * Time complexity: O(n) in the length of the names and field texts.
     * Space complexity: O(n) for the configuration.
     *
     * @return the configuration for the new game, or null when the custom time can't be used
     */
    public GameConfig createConfig() {
        long whiteMs = selectedWhiteMs;
        long blackMs = selectedBlackMs;
        String label = selectedLabel;
        long incrementMs = selectedIncrementMs;

        // a selected custom time is taken from the fields as they are now
        if (customBtn.isSelected()) {
            long customMs = readCustomTimeMs();
            if (customMs < 0) {
                return null;
            }
            whiteMs = customMs;
            blackMs = customMs;
            // minutes and seconds, so it can't be mistaken for an increment like 5+30
            label = "Custom " + customMs / 60_000 + ":" + String.format("%02d", customMs / 1000 % 60);
            incrementMs = 0;
        }

        // a player who picked no mode gets the one their time control implies, exactly as before
        ClockMode mode = selectedMode != null ? selectedMode
                : (incrementMs > 0 ? ClockMode.FISCHER : ClockMode.SUDDEN_DEATH);
        // only a Fischer clock pays an increment, the two delay modes work with the delay instead
        long increment = mode == ClockMode.FISCHER ? incrementMs : 0;
        long delayMs = mode.usesDelay() ? readDelayMs() : 0;
        // a game at odds gives Black a time of their own, an empty field gives both the same
        blackMs = readBlackTimeMs(whiteMs);

        // a tournament control starts on the time of its first stage, whatever the preset said
        java.util.List<ClockStage> stages = ClockStage.parse(stagesField.getText());
        if (!stages.isEmpty()) {
            whiteMs = stages.get(0).timeMs();
            blackMs = whiteMs;
            label = "Stages " + stagesField.getText().trim();
        }
        // a game at odds gives Black a time of their own, and an empty field reads back as White's,
        // which leaves whatever the stages or the preset already put there
        long blackOddsMs = readBlackTimeMs(whiteMs);
        blackMs = blackOddsMs == whiteMs ? blackMs : blackOddsMs;

        return new GameConfig(
                whiteField.getText().trim(),
                blackField.getText().trim(),
                whiteMs,
                blackMs,
                label,
                increment,
                mode,
                delayMs,
                stages);
    }

    /**
     * Adds one clock mode button to the row of modes.
     * <p>
     * The four modes are a choice of their own, next to the time control rather than part of it, so
     * they share a button group that has nothing to do with the presets. I style the button like the
     * presets, let it record its mode when it is clicked and mark it while it is selected.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the button.
     *
     * @param pRow   the row the button is added to, never null
     * @param pGroup the group that keeps the modes exclusive, never null
     * @param pText  the button text, never null
     * @param pMode  the mode this button stands for, never null
     */
    private void addModeButton(JPanel pRow, ButtonGroup pGroup, String pText, ClockMode pMode) {
        JToggleButton button = new JToggleButton(pText);
        UiComponents.style(button, new Font(Font.SANS_SERIF, Font.PLAIN, 12), Theme.BUTTON_SECONDARY);
        button.addActionListener(e -> selectedMode = pMode);
        button.addItemListener(e -> button.setBackground(button.isSelected() ? Theme.ACCENT : Theme.BUTTON_SECONDARY));
        pGroup.add(button);
        pRow.add(button);
    }

    /**
     * Reads the delay a Bronstein or simple delay clock should work with.
     * <p>
     * The field holds whole seconds, because no time control in practice asks for less. A field that
     * holds nothing readable means no delay, which is the same as not using one of those modes, so
     * there is nothing to refuse the player over.
     * <p>
     * Time complexity: O(n) in the length of the field text. Space complexity: O(1).
     *
     * @return the delay in milliseconds, 0 or more
     */
    private long readDelayMs() {
        try {
            return Math.max(0, Long.parseLong(delaySec.getText().trim())) * 1000L;
        } catch (NumberFormatException e) {
            // a delay nobody can read is no delay at all
            return 0;
        }
    }

    /**
     * Reads the time Black starts with, which is White's unless the players want a game at odds.
     * <p>
     * Giving the weaker side more time is the oldest handicap in chess, and the clocks have always
     * been able to start from different times. An empty field means both sides get the same, and so
     * does a field nobody can read, because refusing to start a game over it would help no one.
     * <p>
     * Time complexity: O(n) in the length of the field text. Space complexity: O(1).
     *
     * @param pWhiteMs White's starting time in milliseconds, used when Black wants no odds
     * @return Black's starting time in milliseconds, 0 or more
     */
    private long readBlackTimeMs(long pWhiteMs) {
        String text = blackOddsMin.getText().trim();
        // an empty field is the normal case: both players start from the same time
        if (text.isEmpty()) {
            return pWhiteMs;
        }
        try {
            return Math.max(0, Long.parseLong(text)) * 60_000L;
        } catch (NumberFormatException e) {
            return pWhiteMs;
        }
    }

    /**
     * Builds the opponent for the next game from the current selections.
     * <p>
     * A game against another person needs no side and no level, so those answers are thrown away
     * rather than carried into a game nobody asked them for. Against the program the person picks
     * the colour they want and the program takes the other one, which is the way round a player
     * thinks about it: nobody chooses which colour their opponent has.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the settings.
     *
     * @return the opponent for the new game, never null
     */
    public EngineSettings createEngineSettings() {
        if (!computerOpponent.isSelected()) {
            return EngineSettings.humanOpponent();
        }
        // the person names their own colour, so the program plays the other one
        int engineColour = playWhite.isSelected() ? Pieces.BLACK : Pieces.WHITE;
        return EngineSettings.level(selectedLevel, engineColour);
    }

    /**
     * Shows the side and level choices only while the program is the opponent.
     * <p>
     * Leaving them enabled against another person would offer a choice that changes nothing, which
     * is worse than offering none: a player who sets one is entitled to expect it to matter.
     * <p>
     * Time complexity: O(k) for the k levels. Space complexity: O(1).
     */
    private void showEngineChoices() {
        boolean againstComputer = computerOpponent.isSelected();
        playWhite.setEnabled(againstComputer);
        playBlack.setEnabled(againstComputer);
        for (JToggleButton level : levels) {
            level.setEnabled(againstComputer);
        }
    }

    /**
     * Gives a toggle button the look of this screen and puts it in a group.
     * <p>
     * The opponent, the side and the level are all one choice out of several, and they share the
     * look the time control presets already use, including going to the accent colour when chosen.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pButton button to style, never null
     * @param pName   component name that identifies it, never null
     * @param pGroup  group that keeps one of the choices selected, never null
     * @param pParent panel the button is added to, never null
     */
    private void choiceButton(JToggleButton pButton, String pName, ButtonGroup pGroup, JPanel pParent) {
        UiComponents.style(pButton, new Font(Font.SANS_SERIF, Font.PLAIN, 12), Theme.BUTTON_SECONDARY);
        pButton.setName(pName);
        pButton.addItemListener(e ->
                pButton.setBackground(pButton.isSelected() ? Theme.ACCENT : Theme.BUTTON_SECONDARY));
        pGroup.add(pButton);
        pParent.add(pButton);
    }

    /**
     * Selects a choice and colours it, the way clicking it would.
     * <p>
     * Selecting a button in code does not run its action, so the starting choices are set here and
     * their colour set with them.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pButton the button to select, never null
     */
    private void select(JToggleButton pButton) {
        pButton.setSelected(true);
        pButton.setBackground(Theme.ACCENT);
    }

    /**
     * Creates the small heading above a group of settings.
     * <p>
     * The screen is split into player names and time control, and each group gets a heading. I make
     * a left aligned label in the muted heading colour with a bold logical font.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the label.
     *
     * @param pText heading text, never null
     * @return the heading label, never null
     */
    private JLabel sectionLabel(String pText) {
        JLabel l = new JLabel(pText);
        l.setForeground(new Color(160, 160, 170));
        // logical fonts exist on every platform, Arial doesn't
        l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /**
     * Creates a small label that names an input, such as White, min or sec.
     * <p>
     * Inputs on this screen need short captions in the muted colour. I make a label with the given
     * text and a plain logical font.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the label.
     *
     * @param pText caption text, never null
     * @return the caption label, never null
     */
    private JLabel fieldLabel(String pText) {
        JLabel l = new JLabel(pText);
        l.setForeground(new Color(160, 160, 170));
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return l;
    }

    /**
     * Gives a text field the dark look of this screen.
     * <p>
     * The name and custom time fields should match the dark theme instead of the default light
     * look. I set the dark background, light text and caret, a thin border with some padding and a
     * plain logical font.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pField text field to style, never null
     */
    private void styleField(JTextField pField) {
        pField.setBackground(new Color(50, 50, 55));
        pField.setForeground(Theme.FG);
        pField.setCaretColor(Theme.FG);
        pField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 75)),
                new EmptyBorder(4, 8, 4, 8)));
        pField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    }

    /**
     * Creates one of the two large buttons at the bottom of the screen, Back or Start.
     * <p>
     * Both buttons share size and font, and the primary one stands out in the accent colour. Their
     * arrow symbols are missing from some fonts, so each button also carries an ASCII text and a
     * component name that stays the same whichever text is shown. I style a button with the shared
     * look, a bold logical font and a fixed height.
     * <p>
     * Time complexity: O(n) for the n characters of pText. Space complexity: O(1) apart from the button.
     *
     * @param pText      button text with its arrow symbol, never null
     * @param pAsciiText plain ASCII text for fonts without the symbol, never null
     * @param pName      component name that identifies the button, never null
     * @param pPrimary   true for the accent coloured main action, false for a secondary one
     * @return the finished button, never null
     */
    private JButton actionButton(String pText, String pAsciiText, String pName, boolean pPrimary) {
        JButton b = UiComponents.button(pText, pAsciiText, new Font(Font.SANS_SERIF, Font.BOLD, 14),
                pPrimary ? Theme.ACCENT : Theme.BUTTON_SECONDARY);
        b.setName(pName);
        b.setPreferredSize(new Dimension(0, 44));
        return b;
    }
}