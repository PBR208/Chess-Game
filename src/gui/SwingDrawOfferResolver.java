package gui;

import gameLogic.DrawOfferResolver;

import javax.swing.*;

public class SwingDrawOfferResolver implements DrawOfferResolver {

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
}