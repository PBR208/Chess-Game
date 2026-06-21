package gameLogic;

import gui.Board;
import pieces.Piece;

public class Move {

    private final int newCol;
    private final int newRow;

    private final Piece piece;
    private Piece capture;

    public Move(Board b, Piece p, int newCol, int newRow) {

        this.newCol = newCol;
        this.newRow = newRow;

        this.piece = p;
        this.capture = b.getPiece(newCol, newRow);
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

    public void setCapture(Piece capture) {
        this.capture = capture;
    }
}
