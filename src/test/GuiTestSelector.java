package test;

import app.Main;
import engine.model.GameConfig;
import ui.board.*;
import ui.menu.MainMenu;
import ui.menu.NewGamePanel;
import ui.menu.PastGamesPanel;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import java.awt.*;

/**
 * Test class that displays a selector GUI allowing users to choose
 * which GUI component to preview from the entire project.
 */
public class GuiTestSelector extends JFrame {

    private static final int WINDOW_WIDTH = 600;
    private static final int WINDOW_HEIGHT = 700;

    public GuiTestSelector() {
        setTitle("GUI Test Selector");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(Theme.BG);

        buildUI();
    }

    private void buildUI() {
        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(Theme.BG);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("GUI Test Selector");
        title.setFont(new Font(Font.DIALOG, Font.BOLD, 28));
        title.setForeground(Theme.FG);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(title);

        mainPanel.add(Box.createVerticalStrut(10));

        JLabel subtitle = new JLabel("Choose a GUI component to preview:");
        subtitle.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        subtitle.setForeground(Theme.MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(subtitle);

        mainPanel.add(Box.createVerticalStrut(30));

        // Menu GUIs
        mainPanel.add(createSectionLabel("Menu Components"));
        mainPanel.add(createButton("Main Menu", this::showMainMenu));
        mainPanel.add(createButton("New Game Panel", this::showNewGamePanel));
        mainPanel.add(createButton("Past Games Panel", this::showPastGamesPanel));

        mainPanel.add(Box.createVerticalStrut(15));

        // Board GUIs
        mainPanel.add(createSectionLabel("Board Components"));
        mainPanel.add(createButton("Chess Board (Unlimited)", this::showBoardUnlimited));
        mainPanel.add(createButton("Chess Board (Rapid 15+10)", this::showBoardRapid));
        mainPanel.add(createButton("Promotion Chooser", this::showPromotionChooser));

        mainPanel.add(Box.createVerticalStrut(15));

        // Dialog GUIs
        mainPanel.add(createSectionLabel("Dialog Components"));
        mainPanel.add(createButton("End Screen (Checkmate)", this::showEndScreenCheckmate));
        mainPanel.add(createButton("End Screen (Stalemate)", this::showEndScreenStalemate));
        mainPanel.add(createButton("Fifty-Move Draw (Claim)", this::showFiftyRuleDrawClaim));
        mainPanel.add(createButton("Fifty-Move Draw (Forced)", this::showFiftyRuleDrawForced));

        mainPanel.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBackground(Theme.BG);
        scrollPane.getViewport().setBackground(Theme.BG);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        setContentPane(scrollPane);
    }

    private JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
        label.setForeground(Theme.ACCENT);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private JButton createButton(String text, Runnable action) {
        Font font = new Font(Font.DIALOG, Font.PLAIN, 13);
        JButton btn = UiComponents.button(text, font, Theme.BUTTON_SECONDARY);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(300, 40));
        btn.setPreferredSize(new Dimension(300, 40));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    // Menu GUIs
    private void showMainMenu() {
        displayPanel(new MainMenu(), "Main Menu");
    }

    private void showNewGamePanel() {
        displayPanel(new NewGamePanel(), "New Game Panel");
    }

    private void showPastGamesPanel() {
        displayPanel(new PastGamesPanel(), "Past Games Panel");
    }

    // Board GUIs
    private void showBoardUnlimited() {
        displayPanel(new Board(GameConfig.unlimited()), "Chess Board - Unlimited");
    }

    private void showBoardRapid() {
        GameConfig config = new GameConfig("White", "Black", 900_000, 900_000, "Rapid 15+10");
        displayPanel(new Board(config), "Chess Board - Rapid 15+10");
    }

    private void showPromotionChooser() {
        JFrame frame = new JFrame("Promotion Chooser");
        frame.setSize(400, 150);
        frame.setLocationRelativeTo(this);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().setBackground(Theme.BG);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BG);
        JLabel label = new JLabel("Select a piece to promote to:", SwingConstants.CENTER);
        label.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        label.setForeground(Theme.FG);
        panel.add(label, BorderLayout.NORTH);

        // Note: PromoteGUI requires Board context, so we create a mock board
        JPanel mockBoardPanel = new JPanel();
        mockBoardPanel.setBackground(Theme.PANEL_BG);
        mockBoardPanel.setPreferredSize(new Dimension(400, 100));
        JLabel info = new JLabel("(PromoteGUI requires Board context - visual representation)", SwingConstants.CENTER);
        info.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        info.setForeground(Theme.MUTED);
        mockBoardPanel.add(info);
        panel.add(mockBoardPanel, BorderLayout.CENTER);

        frame.setContentPane(panel);
        frame.setVisible(true);
    }

    // Dialog GUIs
    private void showEndScreenCheckmate() {
        JFrame tempFrame = new JFrame();
        tempFrame.setSize(100, 100);
        EndScreen dialog = new EndScreen(tempFrame, "White wins by checkmate!", 85, () -> {});
        dialog.setVisible(true);
    }

    private void showEndScreenStalemate() {
        JFrame tempFrame = new JFrame();
        tempFrame.setSize(100, 100);
        EndScreen dialog = new EndScreen(tempFrame, "Draw by stalemate", 85, () -> {});
        dialog.setVisible(true);
    }

    private void showFiftyRuleDrawClaim() {
        JFrame tempFrame = new JFrame();
        tempFrame.setSize(100, 100);
        FiftyRuleDraw dialog = new FiftyRuleDraw(tempFrame, 85, false);
        dialog.setVisible(true);
    }

    private void showFiftyRuleDrawForced() {
        JFrame tempFrame = new JFrame();
        tempFrame.setSize(100, 100);
        FiftyRuleDraw dialog = new FiftyRuleDraw(tempFrame, 85, true);
        dialog.setVisible(true);
    }

    private void displayPanel(JPanel panel, String title) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1000, 800);
        frame.setLocationRelativeTo(this);
        frame.getContentPane().setBackground(Theme.BG);
        frame.setContentPane(panel);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GuiTestSelector selector = new GuiTestSelector();
            selector.setVisible(true);
        });
    }
}
