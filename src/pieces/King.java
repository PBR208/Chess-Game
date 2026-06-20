package pieces;

import main.Board;
import main.Move;

import java.awt.image.BufferedImage;

public class King extends Piece {
    public King(Board b, int col, int row, boolean isWhite) {
        super(b);
        this.col = col;
        this.row = row;
        this.xPos = col * b.getTileSize();
        this.yPos = row * b.getTileSize();

        this.isWhite = isWhite;
        this.name = "King";

        this.front = img.getSubimage(0, isWhite ? 0 : imgScale, imgScale, imgScale).getScaledInstance(b.getTileSize(), b.getTileSize(), BufferedImage.SCALE_SMOOTH);
    }

    public boolean isValidMovement(int col, int row) {
        int colDiff = Math.abs(col - this.col);
        int rowDiff = Math.abs(row - this.row);

        return colDiff <= 1
                && rowDiff <= 1
                && (colDiff != 0 || rowDiff != 0)
                || canCastle(col, row);
    }

    public boolean isValidCollide(int col, int row) {
        return false;
    }

    private boolean canCastle(int col, int row) {

        // must stay on same row
        if (row != this.row) return false;

        if (!this.isFirstMove()) return false;

        int rookCol;
        int step;

        // kingside
        if (col == 6) {

            rookCol = 7;
            step = 1;

        }

        // queenside
        else if (col == 2) {

            rookCol = 0;
            step = -1;

        }

        // anything else is NOT castling
        else {
            return false;
        }

        Piece rook = b.getPiece(rookCol, row);

        if (!(rook instanceof Rook)) return false;

        if (!rook.isFirstMove()) return false;

        // check empty squares between king and rook
        for (int c = this.col + step; c != rookCol; c += step) {

            if (b.getPiece(c, row) != null) {
                return false;
            }
        }

        // king cannot start in check
        if (b.getCs().isKingInCheckRN(this.isWhite)) {
            return false;
        }

        // king cannot pass through check
        Move middle = new Move(b, this, this.col + step, row);
        Move destination = new Move(b, this, col, row);

        if (b.getCs().isKingLeftInCheck(middle)) {
            return false;
        }

        if (b.getCs().isKingLeftInCheck(destination)) {
            return false;
        }
        
        return true;
    }
}
