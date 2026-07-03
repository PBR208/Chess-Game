package gui;

import gameLogic.GameRecord;
import gameLogic.PgnManager;
import main.Main;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class PastGamesPanel extends JPanel {

    private static final Color BG = new Color(28, 28, 30);
    private static final Color PANEL_BG = new Color(38, 38, 42);
    private static final Color FG = Color.WHITE;

    private final List<GameRecord> records;
    private final JPanel rightPanel;
    private final CardLayout rightCards;
    private final JTextArea moveLogArea;

    public PastGamesPanel() {
        records = PgnManager.loadAll();

        setLayout(new BorderLayout());
        setBackground(BG);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(PANEL_BG);
        topBar.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("Past Games");
        title.setForeground(FG);
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
        gameList.setBackground(PANEL_BG);
        gameList.setForeground(FG);
        gameList.setFont(new Font("Arial", Font.PLAIN, 13));
        gameList.setSelectionBackground(new Color(60, 60, 70));
        gameList.setSelectionForeground(FG);
        gameList.setFixedCellHeight(36);
        gameList.setBorder(new EmptyBorder(4, 8, 4, 8));

        JScrollPane listScroll = new JScrollPane(gameList);
        listScroll.setPreferredSize(new Dimension(380, 0));
        listScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(60, 60, 65)));

        rightCards = new CardLayout();
        rightPanel = new JPanel(rightCards);
        rightPanel.setBackground(BG);

        JLabel placeholder = new JLabel("Select a game from the list", SwingConstants.CENTER);
        placeholder.setForeground(new Color(120, 120, 120));
        placeholder.setFont(new Font("Arial", Font.ITALIC, 14));
        rightPanel.add(placeholder, "empty");

        moveLogArea = new JTextArea();
        moveLogArea.setEditable(false);
        moveLogArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        moveLogArea.setBackground(PANEL_BG);
        moveLogArea.setForeground(FG);
        moveLogArea.setLineWrap(true);
        moveLogArea.setWrapStyleWord(true);
        moveLogArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        JScrollPane logScroll = new JScrollPane(moveLogArea);

        JPanel replayHolder = new JPanel(new BorderLayout());
        replayHolder.setBackground(BG);
        rightPanel.add(logScroll, "log");
        rightPanel.add(replayHolder, "replay");

        rightCards.show(rightPanel, "empty");

        JPanel toggleBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toggleBar.setBackground(PANEL_BG);
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
        centerWrapper.setBackground(BG);
        centerWrapper.add(split, BorderLayout.CENTER);
        centerWrapper.add(toggleBar, BorderLayout.SOUTH);

        add(centerWrapper, BorderLayout.CENTER);
    }
}
