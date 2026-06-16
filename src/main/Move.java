package main;

import pieces.Piece;

public class Move {

    int oldCol;
    int newCol;
    int oldRow;
    int newRow;

    Piece piece;
    Piece capture;

    public Move(Board b, Piece p, int newCol, int newRow){

        this.oldCol = p.getCol();
        this.oldRow = p.getRow();
        this.newCol = newCol;
        this.newRow = newRow;

        this.piece = p;
        this.capture = b.getPiece(newCol, newRow);
    }
}
