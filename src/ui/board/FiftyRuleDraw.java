package ui.board;

/*
 * Purpose: FiftyRuleDraw is the modal dialog for draws that come from the rules instead of a
 * result on the board. It either announces a forced draw, such as the 75-move rule, or offers a
 * draw the player may claim, such as the 50-move rule or a threefold repetition. I use one dialog
 * for all of these so every draw decision looks and behaves the same. The claim version reports
 * through getResult whether the player accepted or declined.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import ui.theme.Theme;
import ui.theme.UiComponents;

import javax.swing.*;
import java.awt.*;

public class FiftyRuleDraw extends JDialog {

    public enum DrawResult {
        ACCEPTED,
        DECLINED,
        NONE
    }

    // explanation for the 50-move claim, used by the original constructor
    private static final String FIFTY_MOVE_CLAIM_MESSAGE = "<html><center>A draw may be claimed. 50 moves have<br>" +
            "been played without a capture or pawn move.</center></html>";

    private DrawResult result = DrawResult.NONE;

    /**
     * Builds either the 50-move claim dialog or the 75-move forced draw notice.
     * <p>
     * This is the original way to open the dialog and it keeps its old texts. I forward to the
     * shared constructor with the 50-move explanation for the claim version.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the Swing components.
     *
     * @param pParent   frame the modal dialog belongs to; may be null for Swing's shared owner
     * @param pTileSize edge length of one board tile in pixels, expected to be greater than 0
     * @param pIsForced true for the 75-move notice, false for the 50-move claim
     * @throws HeadlessException if the JVM has no display
     */
    public FiftyRuleDraw(JFrame pParent, int pTileSize, boolean pIsForced) {
        this(pParent, pTileSize, pIsForced, FIFTY_MOVE_CLAIM_MESSAGE);
    }

    /**
     * Builds a claim dialog with a custom explanation, for example for a threefold repetition.
     * <p>
     * Different draw rules need different reasons on screen, while the buttons stay the same. I
     * forward to the shared constructor as a claim with the given message.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the Swing components.
     *
     * @param pParent       frame the modal dialog belongs to; may be null for Swing's shared owner
     * @param pTileSize     edge length of one board tile in pixels, expected to be greater than 0
     * @param pClaimMessage HTML text explaining why a draw may be claimed, never null
     * @throws HeadlessException if the JVM has no display
     */
    public FiftyRuleDraw(JFrame pParent, int pTileSize, String pClaimMessage) {
        this(pParent, pTileSize, false, pClaimMessage);
    }

    /**
     * Lays out the dialog as a forced draw notice or as a claimable draw.
     * <p>
     * Both variants share the dark theme, spacing and size. A forced draw shows its fixed notice
     * with a single button that closes the dialog. A claim shows the given explanation with Claim
     * Draw and Decline buttons, which store the answer and close the dialog.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) apart from the Swing components.
     *
     * @param pParent       frame the modal dialog belongs to; may be null for Swing's shared owner
     * @param pTileSize     edge length of one board tile in pixels, expected to be greater than 0
     * @param pIsForced     true for the forced 75-move notice, false for a claim
     * @param pClaimMessage HTML text for the claim variant, ignored for a forced draw; never null
     * @throws HeadlessException if the JVM has no display
     */
    private FiftyRuleDraw(JFrame pParent, int pTileSize, boolean pIsForced, String pClaimMessage) {
        // modal, so the game waits for the decision
        super(pParent, true);

        int gap = pTileSize / 8;
        int padding = pTileSize / 8;
        setLayout(new BorderLayout(gap, gap));
        setUndecorated(true);
        getContentPane().setBackground(Theme.PANEL_BG);

        String msg;
        JButton button1;
        JButton button2 = null;

        Font labelFont = new Font(Font.DIALOG, Font.BOLD, Math.max(pTileSize / 7, 12));
        Font buttonFont = new Font(Font.DIALOG, Font.PLAIN, Math.max(pTileSize / 8, 11));

        if (pIsForced) {
            msg = "<html><center>The game has ended in a draw under<br>" +
                    "the 75-move rule. No captures or pawn<br>" +
                    "moves occurred in the last 75 moves.</center></html>";
            // the button only closes the notice, it never restarted anything
            button1 = UiComponents.button("OK", buttonFont, Theme.BUTTON_SECONDARY);
            button1.addActionListener(e -> dispose());
        } else {
            // the reason depends on the rule that made the draw claimable
            msg = pClaimMessage;
            button1 = UiComponents.button("Claim Draw", buttonFont, Theme.ACCENT);
            button2 = UiComponents.button("Decline", buttonFont, Theme.BUTTON_SECONDARY);

            button1.addActionListener(e -> {
                result = DrawResult.ACCEPTED;
                dispose();
            });
            button2.addActionListener(e -> {
                result = DrawResult.DECLINED;
                dispose();
            });
        }

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBackground(Theme.PANEL_BG);
        textPanel.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));

        JLabel txt = new JLabel(msg, SwingConstants.CENTER);
        txt.setFont(labelFont);
        txt.setForeground(Theme.FG);
        textPanel.add(txt, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(1, button2 != null ? 2 : 1, gap, gap));
        buttonPanel.setBackground(Theme.PANEL_BG);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, padding, padding, padding));
        buttonPanel.add(button1);
        if (button2 != null) {
            buttonPanel.add(button2);
        }

        add(textPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(pTileSize * 4, (int) (pTileSize * 2.5));
        setLocationRelativeTo(pParent);
    }

    public DrawResult getResult() {
        return result;
    }
}
