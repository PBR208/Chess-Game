package gameLogic;

import gui.Board;
import pieces.Pawn;
import pieces.Piece;

public class Move {

    private final int newCol;
    private final int newRow;

    private final Piece piece;
    private Piece capture;
    private String promotionChoice;

    public Move(Board b, Piece p, int newCol, int newRow) {
        this.newCol = newCol;
        this.newRow = newRow;
        this.piece = p;
        this.capture = resolveCapture(b, p, newCol, newRow);
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

    public void setPromotionChoice(String promotionChoice) {
        this.promotionChoice = promotionChoice;
    }

    // HELPER

    private static Piece resolveCapture(Board b, Piece p, int newCol, int newRow) {
        Piece direct = b.getPiece(newCol, newRow);
        if (direct != null) return direct;

        if (p instanceof Pawn && b.getTileNum(newCol, newRow) == b.getEnPassantTile()) {
            int colorIndex = p.isWhite() ? 1 : -1;
            return b.getPiece(newCol, newRow + colorIndex);
        }
        return null;
    }
}
