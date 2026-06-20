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

        int rookCol = (col == 6) ? 7 : 0;
        Piece rook = b.getPiece(rookCol, row);

        if (!(rook instanceof Rook)) return false;
        if (!rook.isFirstMove()) return false;

        // path must be empty
        int step = (col == 6) ? 1 : -1;

        for (int c = this.col + step; c != rookCol; c += step) {
            if (b.getPiece(c, row) != null) return false;
        }

        // king may NOT be in check or pass through check
        if (b.getCs().isKingInCheckRN(this.isWhite)) return false;

        // simulate squares king passes through
        int mid1 = this.col + step;
        int mid2 = col;

        Move test1 = new Move(b, this, mid1, row);
        Move test2 = new Move(b, this, mid2, row);

        if (b.getCs().isKingLeftInCheck(test1)) return false;
        return !b.getCs().isKingLeftInCheck(test2);
    }
}
