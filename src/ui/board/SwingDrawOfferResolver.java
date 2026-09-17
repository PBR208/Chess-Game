package ui.board;

/*
 * Purpose: SwingDrawOfferResolver answers the draw questions of the game session with real dialogs.
 * The session only knows its DrawArbiter interface and never opens a window itself. A draw nobody
 * can refuse is announced, while a draw a player may claim, after fifty moves without a capture or
 * after the same position came back a third time, asks that player and reports the answer. Keeping
 * this apart from the rules lets a headless game run and lets tests answer with a plain boolean.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.core.GameSession;

import javax.swing.*;

public class SwingDrawOfferResolver implements GameSession.DrawArbiter {

    // explanation shown when a position occurred for the third time
    private static final String REPETITION_CLAIM_MESSAGE = "<html><center>A draw may be claimed. The same position<br>" +
            "has now occurred three times.</center></html>";

    private final Board board;

    public SwingDrawOfferResolver(Board pBoard) {
        this.board = pBoard;
    }

    /**
     * Tells the players about a draw that happens whether they want it or not.
     * <p>
     * The seventy five move rule ends the game on its own, so there is nothing to answer here and
     * the dialog only explains what happened. I show it over the board's window.
     * <p>
     * Time complexity: O(1) apart from waiting for the player. Space complexity: O(1).
     *
     * @throws java.awt.HeadlessException if the JVM has no display
     */
    @Override
    public void notifyForcedDraw() {
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(board);
        FiftyRuleDraw dialog = new FiftyRuleDraw(parent, board.getTileSize(), true);
        dialog.setVisible(true);
    }

    /**
     * Asks the player to move whether to claim the draw after fifty moves.
     * <p>
     * After fifty moves without a capture or a pawn move the player to move may claim a draw, and
     * the game only ends if they do. I open the claim dialog over the board's window and report
     * whether Claim Draw was pressed.
     * <p>
     * Time complexity: O(1) apart from waiting for the player. Space complexity: O(1).
     *
     * @return true if the player claimed the draw, false if they declined
     * @throws java.awt.HeadlessException if the JVM has no display
     */
    @Override
    public boolean offerFiftyMoveDraw() {
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(board);
        FiftyRuleDraw dialog = new FiftyRuleDraw(parent, board.getTileSize(), false);
        dialog.setVisible(true);
        return dialog.getResult() == FiftyRuleDraw.DrawResult.ACCEPTED;
    }

    /**
     * Asks the player to move whether to claim a draw by threefold repetition.
     * <p>
     * The generic claim dialog talks about the fifty move rule, which would confuse players when the
     * real reason is a repeated position. I open the same dialog with a message about the repetition
     * and report whether Claim Draw was pressed.
     * <p>
     * Time complexity: O(1) apart from waiting for the player. Space complexity: O(1).
     *
     * @return true if the player claimed the draw, false if they declined
     * @throws java.awt.HeadlessException if the JVM has no display
     */
    @Override
    public boolean offerRepetitionDraw() {
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(board);
        // same dialog as the fifty move claim, only the reason differs
        FiftyRuleDraw dialog = new FiftyRuleDraw(parent, board.getTileSize(), REPETITION_CLAIM_MESSAGE);
        dialog.setVisible(true);
        return dialog.getResult() == FiftyRuleDraw.DrawResult.ACCEPTED;
    }
}
