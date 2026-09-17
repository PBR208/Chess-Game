package ui.board;

/*
 * Purpose: SwingTakebackArbiter answers the session's takeback question with a real dialog. Between
 * friends a move back costs nobody anything, so a game without a clock simply agrees and the move
 * comes straight back. On a clock the moves a player is allowed to unplay decide games, so there the
 * opponent is asked first and the game only steps back when they agree. Keeping the question apart
 * from the rules is what lets a game without a screen take moves back freely and lets a test answer
 * it with a plain boolean instead of clicking a button.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.GameSession;

import javax.swing.*;

public class SwingTakebackArbiter implements GameSession.TakebackArbiter {

    private final Board board;
    // only a game on a clock has to ask, a casual one agrees on its own
    private final boolean timed;

    public SwingTakebackArbiter(Board pBoard, boolean pTimed) {
        this.board = pBoard;
        this.timed = pTimed;
    }

    /**
     * Answers whether the player who just moved may have that move back.
     * <p>
     * A casual game agrees without asking anybody, because nothing is at stake in taking a move
     * back. In a timed game the move cost the opponent time as well, and giving it back changes the
     * game, so I ask them over the board's window and report what they answered. Closing the dialog
     * counts as a no, which leaves the game exactly as it was.
     * <p>
     * Time complexity: O(1) apart from waiting for the opponent. Space complexity: O(1).
     *
     * @param pWhiteAsks true when White made the last move and wants it back
     * @return true if the move may be taken back
     * @throws java.awt.HeadlessException if the JVM has no display
     */
    @Override
    public boolean agreesToTakeback(boolean pWhiteAsks) {
        // without a clock nobody loses anything, so the move comes back right away
        if (!timed) {
            return true;
        }

        String asking = pWhiteAsks ? "White" : "Black";
        String deciding = pWhiteAsks ? "Black" : "White";
        int answer = JOptionPane.showConfirmDialog(board,
                asking + " would like to take the last move back. " + deciding + ", do you agree?",
                "Takeback request", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        // anything but a clear yes leaves the game as it stands
        return answer == JOptionPane.YES_OPTION;
    }
}
