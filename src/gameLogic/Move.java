package gameLogic;

import pieces.Piece;

public class Move {

    private final int newCol;
    private final int newRow;

    private final Piece piece;
    private Piece capture;
    private String promotionChoice;

    public Move(BoardState state, Piece p, int newCol, int newRow) {

        this.newCol = newCol;
        this.newRow = newRow;

        this.piece = p;
        this.capture = state.getPiece(newCol, newRow);
    }

    public int getNewCol() {
        return newCol;
    }

    public int getNewRow() {
        return newRow;
    }

    public Piece getPiece() {
        return piece;
    }

    public Piece getCapture() {
        return capture;
    }

    public String getPromotionChoice() {
        return promotionChoice;
    }

    public void setCapture(Piece capture) {
        this.capture = capture;
    }

    public void setPromotionChoice(String promotionChoice) {
        this.promotionChoice = promotionChoice;
    }
}