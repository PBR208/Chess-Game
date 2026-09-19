package ui.board;

/*
 * Purpose: EngineDrawOfferArbiter answers a draw offer when the opponent is the program. The dialog
 * a person answers would ask the only person at the board to decide for the program, so the program
 * decides for itself: it takes the draw when it does not stand better, and turns it down otherwise.
 * A player who is turned down is told so, because an offer that silently did nothing looks like a
 * button that is broken.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.GameSession;
import engine.core.Position;
import engine.search.Evaluator;
import ui.i18n.Messages;

import javax.swing.*;

public class EngineDrawOfferArbiter implements GameSession.DrawOfferArbiter {

    private final Board board;

    /**
     * Creates the program's answer to draw offers for one board.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pBoard the board of the game against the program, never null
     */
    public EngineDrawOfferArbiter(Board pBoard) {
        this.board = pBoard;
    }

    /**
     * Answers a draw offer on the program's behalf.
     * <p>
     * Time complexity: O(p) for the p pieces the evaluation looks at. Space complexity: O(1).
     *
     * @param pWhiteOffers true when White is the player offering the draw
     * @return true if the program accepts the draw
     * @throws java.awt.HeadlessException if the offer is declined and the JVM has no display
     */
    @Override
    public boolean acceptsDrawOffer(boolean pWhiteOffers) {
        boolean accepted = accepts(board.getSession().position(), board.getSettings().engineColour());
        if (!accepted) {
            JOptionPane.showMessageDialog(board, Messages.get("drawOffer.programDeclines"),
                    Messages.get("drawOffer.title"), JOptionPane.INFORMATION_MESSAGE);
        }
        return accepted;
    }

    /**
     * Decides whether the program takes a draw in a position.
     * <p>
     * The program judges the position the same way its search does, and a side that stands no better
     * than level has nothing to play on for. The evaluation is from the side to move, so it is turned
     * round when that is not the program.
     * <p>
     * Time complexity: O(p) for the p pieces on the board. Space complexity: O(1).
     *
     * @param pPosition     the position the offer is made in, never null and unchanged
     * @param pEngineColour the colour the program plays
     * @return true if the program does not stand better and so accepts
     */
    public static boolean accepts(Position pPosition, int pEngineColour) {
        int score = Evaluator.evaluate(pPosition);
        int forProgram = pPosition.sideToMove() == pEngineColour ? score : -score;
        return forProgram <= 0;
    }
}
