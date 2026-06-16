package main;

import pieces.Piece;

public class Move {

    private int oldCol;
    private int newCol;
    private int oldRow;
    private int newRow;

    private Piece piece;
    private Piece capture;

    public Move(Board b, Piece p, int newCol, int newRow){

        this.oldCol = p.getCol();
        this.oldRow = p.getRow();
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
}
