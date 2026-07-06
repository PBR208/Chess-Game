package gui;

import gameLogic.PromotionChooser;
import pieces.PieceType;

import javax.swing.*;

public class SwingPromotionChooser implements PromotionChooser {

    private final Board board;

    public SwingPromotionChooser(Board board) {
        this.board = board;
    }

    @Override
    public PieceType choose(boolean whitePromoting) {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(board);
        PromoteGUI dialog = new PromoteGUI(frame, board.getTileSize());
        PromoteGUI.Choice choice = dialog.showDialog();

        return switch (choice) {
            case QUEEN -> PieceType.QUEEN;
            case ROOK -> PieceType.ROOK;
            case BISHOP -> PieceType.BISHOP;
            case KNIGHT -> PieceType.KNIGHT;
        };
    }
}