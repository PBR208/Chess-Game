package pieces;

import gui.Board;

import java.awt.image.BufferedImage;

public class Pawn extends Piece {
    public Pawn(Board b, int col, int row, boolean isWhite) {
        super(b);
        this.col = col;
        this.row = row;
        this.xPos = col * b.getTileSize();
        this.yPos = row * b.getTileSize();

        this.isWhite = isWhite;
        this.type = PieceType.PAWN;

        this.front = img.getSubimage(5 * imgScale, isWhite ? 0 : imgScale, imgScale, imgScale).getScaledInstance(b.getTileSize(), b.getTileSize(), BufferedImage.SCALE_SMOOTH);
    }

    public boolean isValidMovement(int col, int row) {

        int colorIndex = this.isWhite ? 1 : -1;

        // push pawn after move 1 (move = 1 tile radius)
        if (this.col == col && row == this.row - colorIndex && b.getPiece(col, row) == null) {
            return true;
        }

        // push pawn move 1
        if (isFirstMove() && this.col == col && row == this.row - colorIndex * 2 && b.getPiece(col, row) == null && b.getPiece(col, row + colorIndex) == null) {
            return true;
        }

        //capture
        if (Math.abs(col - this.col) == 1 && row == this.row - colorIndex) {

            Piece target = b.getPiece(col, row);

            if (target != null && target.isWhite() != this.isWhite) {
                return true;
            }
        }

        //en passant

        if (Math.abs(col - this.col) == 1 &&
                row == this.row - colorIndex &&
                b.getTileNum(col, row) == b.getEnPassantTile()) {


            Piece target = b.getPiece(col, row + colorIndex);


            return target != null
                    && target.getType().getDisplayName().equals("Pawn")
                    && target.isWhite() != this.isWhite;
        }

        return false;
    }

}
