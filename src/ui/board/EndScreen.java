package ui.board;

/*
 * Purpose: EndScreen is the modal dialog that announces the result of a finished game. It shows
 * the result message and offers the way back to the main menu. I keep it separate from the board
 * so every way a game can end, checkmate, stalemate, a draw rule or time, uses the same closing
 * screen. The dialog always leads back to the menu, whether the player presses the button or
 * closes the window, so a finished board never stays playable behind it.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import ui.i18n.Messages;
import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class EndScreen extends JDialog {

    /**
     * Builds the end-of-game dialog with the result message and a way back to the menu.
     * <p>
     * The players need to see how the game ended and then leave the finished board. I lay out the
     * message and a Return to Menu button in the dark theme, run the return callback when the
     * button is pressed, and do the same when the window is closed through the window system, for
     * example with Alt+F4, which used to leave the finished board open and playable. Closing the
     * dialog from code with dispose() does not navigate.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the Swing components.
     *
     * @param pParent   frame the modal dialog belongs to and is centered on; may be null, in which
     *                  case Swing falls back to its shared hidden owner frame
     * @param pMessage  result text shown to the players, never null
     * @param pTileSize edge length of one board tile in pixels, expected to be greater than 0
     * @param pOnReturn callback that navigates back to the menu, never null
     * @throws HeadlessException if the JVM has no display, since dialogs need a real window
     */
    public EndScreen(JFrame pParent, String pMessage, int pTileSize, Runnable pOnReturn) {
        // modal, so the finished board can't be used behind it
        super(pParent, true);

        // spacing scales with the board size
        int gap = pTileSize / 8;
        int padding = pTileSize / 4;
        setLayout(new BorderLayout(gap, gap));
        setUndecorated(true);
        getContentPane().setBackground(Theme.PANEL_BG);

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBackground(Theme.PANEL_BG);
        textPanel.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));

        JLabel txt = new JLabel(pMessage, SwingConstants.CENTER);
        txt.setFont(new Font(Font.DIALOG, Font.BOLD, Math.max(pTileSize / 4, 14)));
        txt.setForeground(Theme.FG);

        textPanel.add(txt, BorderLayout.CENTER);

        JButton returnButton = UiComponents.button(Messages.get("end.returnToMenu"),
                new Font(Font.DIALOG, Font.PLAIN, Math.max(pTileSize / 8, 12)), Theme.ACCENT);
        // the button closes the dialog and leaves the finished game
        returnButton.addActionListener(e -> {
            dispose();
            pOnReturn.run();
        });

        // closing the window any other way has to leave the finished game too
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent pEvent) {
                dispose();
                pOnReturn.run();
            }
        });

        add(textPanel, BorderLayout.CENTER);
        add(returnButton, BorderLayout.SOUTH);

        // four tiles wide and two and a half high, centered over the board
        setSize(pTileSize * 4, (int) (pTileSize * 2.5));
        setLocationRelativeTo(pParent);
    }
}