package ui.board;

/*
 * Purpose: SwingDrawOfferResolver connects the draw decisions of the rules engine to real dialogs.
 * The engine only knows the DrawOfferResolver interface, and this class answers it with modal
 * dialogs over the board's window. Forced draws are announced, while claimable draws such as the
 * 50-move rule or a threefold repetition ask the player and report the answer back. Keeping this
 * separate lets the engine stay free of Swing and lets tests use simple fakes instead.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.imports.DrawOfferResolver;

import javax.swing.*;

public class SwingDrawOfferResolver implements DrawOfferResolver {

    // explanation shown when a position occurred for the third time
    private static final String REPETITION_CLAIM_MESSAGE = "<html><center>A draw may be claimed. The same position<br>" +
            "has now occurred three times.</center></html>";

    private final Board board;

    public SwingDrawOfferResolver(Board board) {
        this.board = board;
    }

    @Override
    public void notifyForcedDraw() {
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(board);
        FiftyRuleDraw dialog = new FiftyRuleDraw(parent, board.getTileSize(), true);
        dialog.setVisible(true);
    }

    @Override
    public boolean offerDraw() {
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(board);
        FiftyRuleDraw dialog = new FiftyRuleDraw(parent, board.getTileSize(), false);
        dialog.setVisible(true);
        return dialog.getResult() == FiftyRuleDraw.DrawResult.ACCEPTED;
    }

    /**
     * Asks the player to move whether to claim a draw by threefold repetition.
     * <p>
     * The generic claim dialog talks about the 50-move rule, which would confuse players when the
     * real reason is a repeated position. I open the same claim dialog over the board's window with
     * a message about the repetition and report whether Claim Draw was pressed.
     * <p>
     * Time complexity: O(1) apart from waiting for the player. Space complexity: O(1).
     *
     * @return true if the player claimed the draw, false if they declined
     * @throws java.awt.HeadlessException if the JVM has no display
     */
    @Override
    public boolean offerRepetitionDraw() {
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(board);
        // same dialog as the 50-move claim, only the reason differs
        FiftyRuleDraw dialog = new FiftyRuleDraw(parent, board.getTileSize(), REPETITION_CLAIM_MESSAGE);
        dialog.setVisible(true);
        return dialog.getResult() == FiftyRuleDraw.DrawResult.ACCEPTED;
    }
}
