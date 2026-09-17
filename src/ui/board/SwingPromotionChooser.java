package ui.board;

/*
 * Purpose: SwingPromotionChooser answers the promotion question of the game session with a real
 * dialog. The session only knows its PromotionPicker interface and never opens a window itself, and
 * this class does that part by showing PromoteGUI over the board's frame in the colour of the side
 * that promotes. Keeping the two apart is what lets a game run without a screen at all, and lets a
 * test answer the question by returning a piece instead of clicking a button.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.core.GameSession;
import engine.core.Pieces;

import javax.swing.*;

public class SwingPromotionChooser implements GameSession.PromotionPicker {

    private final Board board;

    public SwingPromotionChooser(Board pBoard) {
        this.board = pBoard;
    }

    /**
     * Asks the promoting player which piece the pawn becomes.
     * <p>
     * A pawn on the last rank has to become a queen, rook, bishop or knight, and the session cannot
     * finish the move before it knows which. I open the promotion dialog over the board's window
     * with the pieces in the promoting side's colour, wait for the choice and translate it into the
     * piece type the engine counts with. The dialog always returns a choice, so closing it cannot
     * leave the move half played.
     * <p>
     * Time complexity: O(s^2) for the dialog icons, where s is the tile size.
     * Space complexity: O(s^2) for the icons.
     *
     * @param pWhitePromoting true if White promotes, false if Black does
     * @return the chosen piece type, Pieces.QUEEN, ROOK, BISHOP or KNIGHT
     * @throws java.awt.HeadlessException if the JVM has no display
     */
    @Override
    public int pick(boolean pWhitePromoting) {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(board);
        // the icons match the colour of the player who promotes
        PromoteGUI dialog = new PromoteGUI(frame, board.getTileSize(), pWhitePromoting);
        PromoteGUI.Choice choice = dialog.showDialog();

        return switch (choice) {
            case QUEEN -> Pieces.QUEEN;
            case ROOK -> Pieces.ROOK;
            case BISHOP -> Pieces.BISHOP;
            case KNIGHT -> Pieces.KNIGHT;
        };
    }
}
