package ui.board;

/*
 * Purpose: SwingDrawOfferArbiter puts one player's draw offer to the other as a real dialog. The
 * session only knows its DrawOfferArbiter interface and never opens a window itself, so this is the
 * part that asks. Both players sit at the same board here, which is why the question names who is
 * offering and who has to answer, rather than assuming whoever is looking at the screen. Keeping the
 * question apart from the rules is what lets a game run without a screen and lets a test answer it
 * with a plain boolean instead of a click.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.GameSession;
import ui.i18n.Messages;

import javax.swing.*;

public class SwingDrawOfferArbiter implements GameSession.DrawOfferArbiter {

    private final Board board;

    public SwingDrawOfferArbiter(Board pBoard) {
        this.board = pBoard;
    }

    /**
     * Asks the opponent whether they accept the draw that was just offered.
     * <p>
     * A draw is an agreement, so the player who did not offer it is the one who decides. I name both
     * of them in the question, because both are sitting at the same board and the dialog has to say
     * whose answer it is waiting for. Anything but a clear yes leaves the game exactly as it was,
     * which is also what closing the dialog does.
     * <p>
     * Time complexity: O(1) apart from waiting for the opponent. Space complexity: O(1).
     *
     * @param pWhiteOffers true when White is the player offering the draw
     * @return true if the opponent accepted
     * @throws java.awt.HeadlessException if the JVM has no display
     */
    @Override
    public boolean acceptsDrawOffer(boolean pWhiteOffers) {
        String white = Messages.get("newgame.white");
        String black = Messages.get("newgame.black");
        String offering = pWhiteOffers ? white : black;
        String deciding = pWhiteOffers ? black : white;

        int answer = JOptionPane.showConfirmDialog(board,
                Messages.format("drawOffer.question", offering, deciding),
                Messages.get("drawOffer.title"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        // only a clear yes draws the game
        return answer == JOptionPane.YES_OPTION;
    }
}
