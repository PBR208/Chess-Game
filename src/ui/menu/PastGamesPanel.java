package ui.menu;

/*
 * Purpose: PastGamesPanel is the library of finished games. It lists every saved game, shows the
 * move log of the selected one and switches to a replay board that steps through its positions, and
 * it is also where a game is searched for, named or thrown away. I load the games through PgnManager,
 * which hands each record over together with the file it came from, so this screen never deals with
 * PGN text itself but can still act on a single game. Everything it has to ask the player goes
 * through LibraryPrompts, because a modal dialog would hang a test run that has nobody to answer it.
 * The text uses logical font names, which every platform provides.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.model.GameRecord;
import engine.persistence.PgnManager;
import app.Main;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PastGamesPanel extends JPanel {

    // every saved game together with the file it came from, so an entry can be renamed or deleted
    private List<PgnManager.SavedGame> games;

    // the games the list is showing, which is the whole library until a search narrows it down
    private List<PgnManager.SavedGame> shown;

    private final JPanel rightPanel;
    private final CardLayout rightCards;
    private final JTextArea moveLogArea;
    private final DefaultListModel<String> listModel;
    private final JList<String> gameList;
    private final JTextField searchField;

    // where the replay board goes, kept so deleting the selected game can clear it again
    private JPanel replayHolder;

    // asks before a game is thrown away and for the new name when one is renamed. A dialog blocks
    // everything until somebody answers it, so the tests put their own answers in here instead.
    private LibraryPrompts prompts = new DialogPrompts();

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
        games = PgnManager.loadLibrary();
        shown = new ArrayList<>(games);

        setLayout(new BorderLayout());
        setBackground(Theme.BG);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(Theme.PANEL_BG);
        topBar.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("Past Games");
        title.setForeground(Theme.FG);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));

        JButton backBtn = styledButton("\u2190 Back to Menu", "< Back to Menu", "backToMenu");
        backBtn.addActionListener(e -> Main.showMenu());

        topBar.add(title, BorderLayout.WEST);
        topBar.add(backBtn, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        gameList = new JList<>(listModel);
        gameList.setBackground(Theme.PANEL_BG);
        gameList.setForeground(Theme.FG);
        gameList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        gameList.setSelectionBackground(new Color(60, 60, 70));
        gameList.setSelectionForeground(Theme.FG);
        gameList.setFixedCellHeight(36);
        gameList.setBorder(new EmptyBorder(4, 8, 4, 8));

        JScrollPane listScroll = new JScrollPane(gameList);
        listScroll.setBorder(null);

        searchField = new JTextField();
        searchField.setBackground(new Color(28, 28, 30));
        searchField.setForeground(Theme.FG);
        searchField.setCaretColor(Theme.FG);
        searchField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        searchField.setBorder(new EmptyBorder(8, 10, 8, 10));
        searchField.setToolTipText("Search by player, name, result, date or time control");
        // named so a test can type into the right field without going by position
        searchField.setName("librarySearch");
        // the list narrows down while a player types, because a search you have to confirm is one
        // more thing to learn for something this small
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent pEvent) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent pEvent) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent pEvent) {
                applyFilter();
            }
        });

        JButton renameBtn = styledButton("Rename");
        renameBtn.setName("renameGame");
        renameBtn.setToolTipText("Give the selected game a name of its own");
        renameBtn.addActionListener(e -> renameSelected());

        JButton deleteBtn = styledButton("Delete");
        deleteBtn.setName("deleteGame");
        deleteBtn.setToolTipText("Remove the selected game from the library");
        deleteBtn.addActionListener(e -> deleteSelected());

        JPanel libraryButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        libraryButtons.setBackground(Theme.PANEL_BG);
        libraryButtons.add(renameBtn);
        libraryButtons.add(deleteBtn);

        JPanel libraryPanel = new JPanel(new BorderLayout());
        libraryPanel.setBackground(Theme.PANEL_BG);
        libraryPanel.setPreferredSize(new Dimension(380, 0));
        libraryPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(60, 60, 65)));
        libraryPanel.add(searchField, BorderLayout.NORTH);
        libraryPanel.add(listScroll, BorderLayout.CENTER);
        libraryPanel.add(libraryButtons, BorderLayout.SOUTH);

        rightCards = new CardLayout();
        rightPanel = new JPanel(rightCards);
        rightPanel.setBackground(Theme.BG);

        JLabel placeholder = new JLabel("Select a game from the list", SwingConstants.CENTER);
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

        replayHolder = new JPanel(new BorderLayout());
        replayHolder.setBackground(Theme.BG);
        rightPanel.add(logScroll, "log");
        rightPanel.add(replayHolder, "replay");

        rightCards.show(rightPanel, "empty");

        JPanel toggleBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toggleBar.setBackground(Theme.PANEL_BG);
        toggleBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 60, 65)));

        JButton showLog = styledButton("Move Log");
        JButton showReplay = styledButton("Replay \u25b6", "Replay >", "replay");
        toggleBar.add(showLog);
        toggleBar.add(showReplay);
        showLog.addActionListener(e -> rightCards.show(rightPanel, "log"));
        showReplay.addActionListener(e -> rightCards.show(rightPanel, "replay"));

        gameList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            PgnManager.SavedGame game = selectedGame();
            if (game == null) return;
            showRecord(game.record);
            rightCards.show(rightPanel, "log");
        });

        // fills the list, which also covers the empty library and the empty search
        applyFilter();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, libraryPanel, rightPanel);
        split.setDividerLocation(380);
        split.setDividerSize(4);
        split.setBorder(null);

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setBackground(Theme.BG);
        centerWrapper.add(split, BorderLayout.CENTER);
        centerWrapper.add(toggleBar, BorderLayout.SOUTH);

        add(centerWrapper, BorderLayout.CENTER);
    }

    /**
     * Narrows the list down to the games matching what was typed in the search field.
     * <p>
     * A library of a hundred games is a wall of text, and the one a player is after is usually
     * remembered by something in the line, an opponent, a name, a date or how long the game was. I
     * match the search against the whole line rather than one field, ignoring case, so any of those
     * finds the game. An empty search shows everything, and the list says which of the two kinds of
     * nothing it is showing, because an empty library and a search that matched nothing look the same
     * otherwise.
     * <p>
     * Time complexity: O(g) for g saved games. Space complexity: O(g) for the matches.
     */
    public void applyFilter() {
        String needle = searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<PgnManager.SavedGame> matches = new ArrayList<>();
        for (PgnManager.SavedGame game : games) {
            if (needle.isEmpty() || game.title().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(game);
            }
        }
        shown = matches;

        listModel.clear();
        if (shown.isEmpty()) {
            // an empty library and a search that found nothing need different answers
            listModel.addElement(games.isEmpty() ? "No saved games yet." : "No games match this search.");
        } else {
            for (PgnManager.SavedGame game : shown) {
                listModel.addElement(game.title());
            }
        }
    }

    /**
     * Returns the game the player has selected.
     * <p>
     * The list holds the games currently shown, which a search can change under the selection, and it
     * also holds a line of its own when there is nothing to show. Reading the selected index against
     * that list is the only way to be sure which game is meant, so everything that acts on the
     * selection comes through here rather than indexing into the library.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the selected game, or null when nothing is selected or the list is showing a message
     */
    public PgnManager.SavedGame selectedGame() {
        int index = gameList.getSelectedIndex();
        if (index < 0 || index >= shown.size()) {
            return null;
        }
        return shown.get(index);
    }

    /**
     * Gives the selected game a name of its own.
     * <p>
     * Asking for the name goes through the prompts, so the dialog can be replaced in a test. A player
     * who cancels changes nothing, and an empty name puts the game back to being listed by its
     * players. After a successful rename I read the library again, because the name lives in the file
     * and the list has to show what is really on the disk rather than what I hoped I wrote.
     * <p>
     * Time complexity: O(g + c) for g saved games with c characters of PGN text to read again.
     * Space complexity: O(g + c) for the reloaded library.
     *
     * @return true if a game was renamed, false when nothing was selected, the player cancelled or
     *         the file could not be written
     */
    public boolean renameSelected() {
        PgnManager.SavedGame game = selectedGame();
        if (game == null) {
            return false;
        }
        String name = prompts.askName(game.title(), game.name);
        // null means the player cancelled, which is not the same as clearing the name
        if (name == null) {
            return false;
        }
        if (!PgnManager.rename(game.file, name)) {
            prompts.sayFailed("This game could not be renamed.");
            return false;
        }
        reload();
        return true;
    }

    /**
     * Deletes the selected game after asking.
     * <p>
     * Deleting is the one thing on this screen that cannot be undone, so it asks first, through the
     * prompts so a test can answer without a dialog. Afterwards the library is read again and the
     * right hand side goes back to its placeholder, because the move log and the replay would
     * otherwise keep showing a game that no longer exists.
     * <p>
     * Time complexity: O(g + c) for g saved games with c characters of PGN text to read again.
     * Space complexity: O(g + c) for the reloaded library.
     *
     * @return true if a game was deleted, false when nothing was selected, the player said no or the
     *         file could not be removed
     */
    public boolean deleteSelected() {
        PgnManager.SavedGame game = selectedGame();
        if (game == null) {
            return false;
        }
        if (!prompts.confirmDelete(game.title())) {
            return false;
        }
        if (!PgnManager.delete(game.file)) {
            prompts.sayFailed("This game could not be deleted.");
            return false;
        }

        reload();
        // the views still hold the game that was just deleted
        moveLogArea.setText("");
        replayHolder.removeAll();
        replayHolder.revalidate();
        replayHolder.repaint();
        rightCards.show(rightPanel, "empty");
        return true;
    }

    /**
     * Reads the library again and rebuilds the list, keeping the current search.
     * <p>
     * Time complexity: O(g + c) for g saved games with c characters of PGN text.
     * Space complexity: O(g + c) for the loaded games.
     */
    private void reload() {
        games = PgnManager.loadLibrary();
        applyFilter();
    }

    /**
     * Replaces what this screen asks the player, which lets a test answer without a dialog.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPrompts the answers to use from now on, never null
     */
    public void setPrompts(LibraryPrompts pPrompts) {
        this.prompts = pPrompts;
    }

    /**
     * Purpose: LibraryPrompts is everything this screen has to ask the player about, kept behind an
     * interface so the library can be driven without a dialog. A modal dialog stops the whole
     * program until somebody clicks it, which a test run has nobody to do, and it would hang the
     * headless build rather than fail it.
     *
     * Owner: PBR208 - https://github.com/PBR208/
     * Version: 1.0
     */
    public interface LibraryPrompts {

        /**
         * Asks whether a game really should be deleted.
         *
         * @param pTitle the game as the library lists it, never null
         * @return true to delete it
         */
        boolean confirmDelete(String pTitle);

        /**
         * Asks for the new name of a game.
         *
         * @param pTitle       the game as the library lists it, never null
         * @param pCurrentName the name it has now, empty when it has none; never null
         * @return the new name, an empty string to clear it, or null when the player cancelled
         */
        String askName(String pTitle, String pCurrentName);

        /**
         * Tells the player that something could not be written.
         *
         * @param pMessage what went wrong, never null
         */
        void sayFailed(String pMessage);
    }

    /**
     * Purpose: DialogPrompts is the real implementation of the library prompts, the one a player
     * sees. It puts each question in a Swing dialog belonging to this screen.
     *
     * Owner: PBR208 - https://github.com/PBR208/
     * Version: 1.0
     */
    private final class DialogPrompts implements LibraryPrompts {

        @Override
        public boolean confirmDelete(String pTitle) {
            int answer = JOptionPane.showConfirmDialog(PastGamesPanel.this,
                    "Delete this game?\n\n" + pTitle + "\n\nThis cannot be undone.",
                    "Delete game", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            return answer == JOptionPane.YES_OPTION;
        }

        @Override
        public String askName(String pTitle, String pCurrentName) {
            Object answer = JOptionPane.showInputDialog(PastGamesPanel.this,
                    "Name for this game:\n\n" + pTitle,
                    "Rename game", JOptionPane.PLAIN_MESSAGE, null, null, pCurrentName);
            return answer == null ? null : answer.toString();
        }

        @Override
        public void sayFailed(String pMessage) {
            JOptionPane.showMessageDialog(PastGamesPanel.this, pMessage,
                    "Past Games", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showRecord(GameRecord record) {
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
            JLabel noReplay = new JLabel("No position data for this game", SwingConstants.CENTER);
            noReplay.setForeground(new Color(120, 120, 120));
            replayHolder.add(noReplay, BorderLayout.CENTER);
        }
        replayHolder.revalidate();
        replayHolder.repaint();
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