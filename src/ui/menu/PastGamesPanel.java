package ui.menu;

import engine.model.GameRecord;
import engine.persistence.PgnManager;
import app.Main;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class PastGamesPanel extends JPanel {

    private final List<GameRecord> records;
    private final JPanel rightPanel;
    private final CardLayout rightCards;
    private final JTextArea moveLogArea;

    public PastGamesPanel() {
        records = PgnManager.loadAll();

        setLayout(new BorderLayout());
        setBackground(Theme.BG);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(Theme.PANEL_BG);
        topBar.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("Past Games");
        title.setForeground(Theme.FG);
        title.setFont(new Font("Arial", Font.BOLD, 20));

        JButton backBtn = styledButton("← Back to Menu");
        backBtn.addActionListener(e -> Main.showMenu());

        topBar.add(title, BorderLayout.WEST);
        topBar.add(backBtn, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        if (records.isEmpty()) {
            listModel.addElement("No saved games yet.");
        } else {
            for (GameRecord r : records) listModel.addElement(r.getDisplayTitle());
        }

        JList<String> gameList = new JList<>(listModel);
        gameList.setBackground(Theme.PANEL_BG);
        gameList.setForeground(Theme.FG);
        gameList.setFont(new Font("Arial", Font.PLAIN, 13));
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

        JLabel placeholder = new JLabel("Select a game from the list", SwingConstants.CENTER);
        placeholder.setForeground(new Color(120, 120, 120));
        placeholder.setFont(new Font("Arial", Font.ITALIC, 14));
        rightPanel.add(placeholder, "empty");

        moveLogArea = new JTextArea();
        moveLogArea.setEditable(false);
        moveLogArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
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

        JButton showLog = styledButton("Move Log");
        JButton showReplay = styledButton("Replay ▶");
        toggleBar.add(showLog);
        toggleBar.add(showReplay);
        showLog.addActionListener(e -> rightCards.show(rightPanel, "log"));
        showReplay.addActionListener(e -> rightCards.show(rightPanel, "replay"));

        gameList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || records.isEmpty()) return;
            int idx = gameList.getSelectedIndex();
            if (idx < 0 || idx >= records.size()) return;
            showRecord(records.get(idx), replayHolder);
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
            JLabel noReplay = new JLabel("No position data for this game", SwingConstants.CENTER);
            noReplay.setForeground(new Color(120, 120, 120));
            replayHolder.add(noReplay, BorderLayout.CENTER);
        }
        replayHolder.revalidate();
        replayHolder.repaint();
    }

    private JButton styledButton(String text) {
        return UiComponents.button(text, new Font("Arial", Font.PLAIN, 13), Theme.BUTTON_SECONDARY);
    }
}