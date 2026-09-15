package ui.board;

/*
 * Purpose: SwingPromotionChooser connects the promotion question of the rules engine to a real
 * dialog. The engine only knows the PromotionChooser interface, and this class answers it by showing
 * PromoteGUI over the board's window in the colour of the promoting side. Keeping it separate lets
 * the engine stay free of Swing and lets tests answer the question without any window.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.imports.PromotionChooser;
import engine.pieces.PieceType;

import javax.swing.*;

public class SwingPromotionChooser implements PromotionChooser {

    private final Board board;

    public SwingPromotionChooser(Board board) {
        this.board = board;
    }

    /**
     * Asks the promoting player which piece the pawn becomes.
     * <p>
     * A pawn on the last rank has to become a queen, rook, bishop or knight. I open the promotion
     * dialog over the board's window with the pieces in the promoting side's colour, wait for the
     * choice and translate it into the engine's piece type. The dialog always returns a choice, so
     * closing it can't crash the move anymore.
     * <p>
     * Time complexity: O(s^2) for the dialog icons, where s is the tile size.
     * Space complexity: O(s^2) for the icons.
     *
     * @param pWhitePromoting true if White promotes, false if Black does
     * @return the chosen piece type, never null
     * @throws java.awt.HeadlessException if the JVM has no display
     */
    @Override
    public PieceType choose(boolean pWhitePromoting) {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(board);
        // the icons match the colour of the player who promotes
        PromoteGUI dialog = new PromoteGUI(frame, board.getTileSize(), pWhitePromoting);
        PromoteGUI.Choice choice = dialog.showDialog();

        return switch (choice) {
            case QUEEN -> PieceType.QUEEN;
            case ROOK -> PieceType.ROOK;
            case BISHOP -> PieceType.BISHOP;
            case KNIGHT -> PieceType.KNIGHT;
        };
    }
}
