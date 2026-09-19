package ui.menu;

/*
 * Purpose: PastGamesPanel is the library of finished games. It lists every saved game, shows the
 * move log of the selected one and switches to a replay board that steps through its positions, and
 * it is also the way games get in and out of the program, by importing a PGN file somebody else
 * wrote and exporting the selected game as one. I do all of that through PgnManager, so this screen
 * never deals with PGN text itself. The text uses logical font names, which every platform provides.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.1
 */

import engine.model.GameRecord;
import engine.persistence.PgnManager;
import app.Main;
import ui.i18n.Messages;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PastGamesPanel extends JPanel {

    private final List<GameRecord> records;
    private final JPanel rightPanel;
    private final CardLayout rightCards;
    private final JTextArea moveLogArea;
    // the entries are rebuilt after an import, so the model has to stay reachable
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    // the game the player picked, which is the one an export writes
    private GameRecord selectedRecord;

    /**
     * Builds the library screen with the list of saved games and the move log and replay views.
     * <p>
     * Players come here to look at earlier games. I load all saved games, lay out a top bar with the
     * title and the way back to the menu, the game list on the left, the move log or replay on the
     * right and the buttons that switch between those two views. Selecting a game fills both views.
     * <p>
     * Time complexity: O(g + c) for g saved games with c characters of PGN text to load.
     * Space complexity: O(g + c) for the loaded records and the list entries.
     */
    public PastGamesPanel() {
        records = new ArrayList<>();

        setLayout(new BorderLayout());
        setBackground(Theme.BG);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(Theme.PANEL_BG);
        topBar.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel(Messages.get("past.title"));
        title.setForeground(Theme.FG);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));

        JButton backBtn = styledButton("\u2190 " + Messages.get("common.backToMenu"), "< " + Messages.get("common.backToMenu"), "backToMenu");
        backBtn.addActionListener(e -> Main.showMenu());

        topBar.add(title, BorderLayout.WEST);
        topBar.add(backBtn, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        fillList();

        JList<String> gameList = new JList<>(listModel);
        gameList.setBackground(Theme.PANEL_BG);
        gameList.setForeground(Theme.FG);
        gameList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        gameList.setSelectionBackground(new Color(60, 60, 70));
        gameList.setSelectionForeground(Theme.FG);
        gameList.setFixedCellHeight(36);
        gameList.setBorder(new EmptyBorder(4, 8, 4, 8));

        JScrollPane listScroll = new JScrollPane(gameList);
        listScroll.setPreferredSize(new Dimension(380, 0));
        listScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(60, 60, 65)));

        rightCards = new CardLayout();
        rightPanel = new JPanel(rightCards);
        rightPanel.setBackground(Theme.BG);

        JLabel placeholder = new JLabel(Messages.get("past.selectPrompt"), SwingConstants.CENTER);
        placeholder.setForeground(new Color(120, 120, 120));
        placeholder.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 14));
        rightPanel.add(placeholder, "empty");

        moveLogArea = new JTextArea();
        moveLogArea.setEditable(false);
        moveLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        moveLogArea.setBackground(Theme.PANEL_BG);
        moveLogArea.setForeground(Theme.FG);
        moveLogArea.setLineWrap(true);
        moveLogArea.setWrapStyleWord(true);
        moveLogArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        JScrollPane logScroll = new JScrollPane(moveLogArea);

        JPanel replayHolder = new JPanel(new BorderLayout());
        replayHolder.setBackground(Theme.BG);
        rightPanel.add(logScroll, "log");
        rightPanel.add(replayHolder, "replay");

        rightCards.show(rightPanel, "empty");

        JPanel toggleBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toggleBar.setBackground(Theme.PANEL_BG);
        toggleBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 60, 65)));

        JButton showLog = styledButton(Messages.get("past.moveLog"));
        JButton showReplay = styledButton(Messages.get("past.replay") + " \u25b6", Messages.get("past.replay") + " >", "replay");
        toggleBar.add(showLog);
        toggleBar.add(showReplay);
        toggleBar.add(importButton());
        toggleBar.add(exportButton());
        showLog.addActionListener(e -> rightCards.show(rightPanel, "log"));
        showReplay.addActionListener(e -> rightCards.show(rightPanel, "replay"));

        gameList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || records.isEmpty()) return;
            int idx = gameList.getSelectedIndex();
            if (idx < 0 || idx >= records.size()) return;
            // an export writes whichever game is shown right now
            selectedRecord = records.get(idx);
            showRecord(selectedRecord, replayHolder);
            rightCards.show(rightPanel, "log");
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, rightPanel);
        split.setDividerLocation(380);
        split.setDividerSize(4);
        split.setBorder(null);

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setBackground(Theme.BG);
        centerWrapper.add(split, BorderLayout.CENTER);
        centerWrapper.add(toggleBar, BorderLayout.SOUTH);

        add(centerWrapper, BorderLayout.CENTER);
    }

    private void showRecord(GameRecord record, JPanel replayHolder) {
        StringBuilder sb = new StringBuilder();
        sb.append(record.whiteName).append(" vs ").append(record.blackName)
                .append("   ").append(record.result)
                .append("   ").append(record.date)
                .append("   ").append(record.timeControl)
                .append("\n\n");

        List<String> moves = record.moves;
        for (int i = 0; i < moves.size(); i++) {
            if (i % 2 == 0) sb.append(i / 2 + 1).append(". ");
            sb.append(moves.get(i)).append("  ");
            if (i % 2 != 0) sb.append("\n");
        }
        moveLogArea.setText(sb.toString());
        moveLogArea.setCaretPosition(0);

        replayHolder.removeAll();
        if (!record.fenHistory.isEmpty()) {
            replayHolder.add(new ReplayPanel(record.moves, record.fenHistory), BorderLayout.CENTER);
        } else {
            JLabel noReplay = new JLabel(Messages.get("past.noPositions"), SwingConstants.CENTER);
            noReplay.setForeground(new Color(120, 120, 120));
            replayHolder.add(noReplay, BorderLayout.CENTER);
        }
        replayHolder.revalidate();
        replayHolder.repaint();
    }

    /**
     * Fills the game list from the library on disk.
     * <p>
     * The list is built when the screen opens and again after an import brought new games in, so it
     * cannot be built once in the constructor. I reload the library, replace the entries and say so
     * plainly when there is nothing in it yet.
     * <p>
     * Time complexity: O(g + c) for g saved games with c characters of PGN text.
     * Space complexity: O(g) for the records and the entries.
     */
    private void fillList() {
        records.clear();
        records.addAll(PgnManager.loadAll());

        listModel.clear();
        if (records.isEmpty()) {
            listModel.addElement(Messages.get("past.empty"));
        } else {
            for (GameRecord record : records) {
                listModel.addElement(record.getDisplayTitle());
            }
        }
    }

    /**
     * Creates the button that reads games out of a PGN file into the library.
     * <p>
     * Games from another program, a chess site or a friend had no way in until now. I ask for a file,
     * hand it to PgnManager, rebuild the list and say how many games arrived. A file that holds no
     * readable game is reported rather than silently doing nothing, and a file that cannot be read at
     * all names the reason.
     * <p>
     * Time complexity: O(1) to build the button, the import itself is what the file costs.
     * Space complexity: O(1) apart from the button.
     *
     * @return the finished button, never null
     */
    private JButton importButton() {
        JButton button = styledButton(Messages.get("past.importPgn"));
        button.setName("importPgn");
        button.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(Messages.get("past.importPgn"));
            chooser.setFileFilter(new FileNameExtensionFilter(Messages.get("past.pgnFiles"), "pgn"));
            // a player who changes their mind leaves the library alone
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            try {
                List<GameRecord> imported = PgnManager.importFrom(chooser.getSelectedFile().toPath());
                if (imported.isEmpty()) {
                    JOptionPane.showMessageDialog(this, Messages.get("past.importNothing"),
                            Messages.get("past.importNothingTitle"), JOptionPane.WARNING_MESSAGE);
                    return;
                }
                // the new games are on disk, so the list has to be built again
                fillList();
                JOptionPane.showMessageDialog(this,
                        imported.size() == 1 ? Messages.get("past.importedOne")
                                : Messages.format("past.importedMany", imported.size()),
                        Messages.get("past.importDoneTitle"), JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException problem) {
                JOptionPane.showMessageDialog(this, Messages.format("past.importFailed", problem.getMessage()),
                        Messages.get("past.importFailedTitle"), JOptionPane.ERROR_MESSAGE);
            }
        });
        return button;
    }

    /**
     * Creates the button that writes the selected game to a PGN file.
     * <p>
     * A game is worth little if it cannot leave the program. I refuse politely while nothing is
     * selected, suggest a file name built from both players, and write the game wherever the player
     * points. A file that cannot be written names the reason instead of failing quietly.
     * <p>
     * Time complexity: O(1) to build the button, the export itself is O(m) for m moves.
     * Space complexity: O(1) apart from the button.
     *
     * @return the finished button, never null
     */
    private JButton exportButton() {
        JButton button = styledButton(Messages.get("past.exportPgn"));
        button.setName("exportPgn");
        button.addActionListener(e -> {
            // there is nothing to write before a game has been picked
            if (selectedRecord == null) {
                JOptionPane.showMessageDialog(this, Messages.get("past.exportNothing"),
                        Messages.get("past.exportNothingTitle"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(Messages.get("past.exportPgn"));
            chooser.setFileFilter(new FileNameExtensionFilter(Messages.get("past.pgnFiles"), "pgn"));
            chooser.setSelectedFile(new java.io.File(suggestedFileName(selectedRecord)));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            try {
                PgnManager.exportTo(selectedRecord, chooser.getSelectedFile().toPath());
            } catch (IOException problem) {
                JOptionPane.showMessageDialog(this, Messages.format("past.exportFailed", problem.getMessage()),
                        Messages.get("past.exportFailedTitle"), JOptionPane.ERROR_MESSAGE);
            }
        });
        return button;
    }

    /**
     * Builds the file name an export starts out with.
     * <p>
     * A player should not have to invent a name, so I offer both players and the date, with every
     * character a file name cannot carry replaced.
     * <p>
     * Time complexity: O(n) in the length of the names. Space complexity: O(n) for the name.
     *
     * @param pRecord the game about to be written, never null
     * @return a file name ending in .pgn, never null
     */
    private static String suggestedFileName(GameRecord pRecord) {
        String name = pRecord.date + "_" + pRecord.whiteName + "_vs_" + pRecord.blackName;
        // whatever a file name cannot hold becomes an underscore
        return name.replaceAll("[^a-zA-Z0-9_.-]", "_") + ".pgn";
    }

    /**
     * Creates a button in the secondary style of this screen.
     * <p>
     * The back button and the view toggles all share one look. I style a button with the shared dark
     * look and a plain logical font.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the button.
     *
     * @param pText button text, never null
     * @return the finished button, never null
     */
    private JButton styledButton(String pText) {
        // logical fonts exist on every platform, Arial doesn't
        return UiComponents.button(pText, new Font(Font.SANS_SERIF, Font.PLAIN, 13), Theme.BUTTON_SECONDARY);
    }

    /**
     * Creates a button in the secondary style of this screen for a text with a symbol.
     * <p>
     * The back and replay buttons show an arrow or a play symbol, which some fonts don't contain. I
     * build the button like the plain version, let its text fall back to ASCII and give it a
     * component name that stays the same whichever text is shown.
     * <p>
     * Time complexity: O(n) for the n characters of pText. Space complexity: O(1) apart from the button.
     *
     * @param pText      button text with its symbol, never null
     * @param pAsciiText plain ASCII text for fonts without the symbol, never null
     * @param pName      component name that identifies the button, never null
     * @return the finished button, never null
     */
    private JButton styledButton(String pText, String pAsciiText, String pName) {
        JButton b = UiComponents.button(pText, pAsciiText, new Font(Font.SANS_SERIF, Font.PLAIN, 13), Theme.BUTTON_SECONDARY);
        b.setName(pName);
        return b;
    }
}