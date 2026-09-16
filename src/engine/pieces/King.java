package engine.pieces;

/*
 * Purpose: King is the piece the whole game turns around, so its rules decide check, checkmate and
 * castling. It steps one square in any direction and may castle towards a rook that has not moved
 * yet, as long as the squares in between are empty. Whether the king would end up in check is not
 * decided here but by the check simulation in GameController, because that needs the whole position.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.imports.BoardState;

public class King extends Piece {

    /**
     * Creates a king on a square.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pState   position the king belongs to and reads from, never null
     * @param pCol     column of its square, 0 to 7
     * @param pRow     row of its square, 0 to 7 where 0 is rank 8
     * @param pIsWhite true for the white king, false for the black one
     */
    public King(BoardState pState, int pCol, int pRow, boolean pIsWhite) {
        super(pState, pCol, pRow, pIsWhite, PieceType.KING);
    }

    /**
     * Tells whether the king's geometry allows a move to a square.
     * <p>
     * A king reaches every neighbouring square, and castling adds the two-square step towards a
     * rook. I accept a step of at most one square in each direction that actually leaves the current
     * square, and otherwise let the castling rules decide.
     * <p>
     * Time complexity: O(1), castling scans at most five squares. Space complexity: O(1).
     *
     * @param pCol column of the target square, 0 to 7
     * @param pRow row of the target square, 0 to 7 where 0 is rank 8
     * @return true if the king may move there as far as its own geometry is concerned
     */
    public boolean isValidMovement(int pCol, int pRow) {
        int colDiff = Math.abs(pCol - this.col);
        int rowDiff = Math.abs(pRow - this.row);

        return colDiff <= 1
                && rowDiff <= 1
                && (colDiff != 0 || rowDiff != 0)
                || canCastle(pCol, pRow);
    }

    /**
     * Tells whether something blocks the king's move.
     * <p>
     * A king only ever steps to a neighbouring square, so there is never a square in between that
     * could be occupied. The squares crossed while castling are checked in canCastle instead.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pCol column of the target square, 0 to 7
     * @param pRow row of the target square, 0 to 7 where 0 is rank 8
     * @return always false
     */
    public boolean isValidCollide(int pCol, int pRow) {
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

        Piece rook = state.getPiece(rookCol, row);

        if (!(rook instanceof Rook)) return false;

        if (!rook.isFirstMove()) return false;

        // check empty squares between king and rook
        for (int c = this.col + step; c != rookCol; c += step) {

            if (state.getPiece(c, row) != null) {
                return false;
            }
        }
        return true;
    }
}
