package engine.pieces;

/*
 * Purpose: Queen combines the moves of rook and bishop and is the piece a promoting pawn becomes in
 * almost every game. Its rules are kept in one class instead of inheriting from two pieces, because
 * Java has single inheritance and the combined check is short enough to read at a glance. Whether a
 * queen move leaves the own king in check is decided by the check simulation, not here.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.imports.BoardState;

public class Queen extends Piece {

    /**
     * Creates a queen on a square.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pState   position the queen belongs to and reads from, never null
     * @param pCol     column of its square, 0 to 7
     * @param pRow     row of its square, 0 to 7 where 0 is rank 8
     * @param pIsWhite true for a white queen, false for a black one
     */
    public Queen(BoardState pState, int pCol, int pRow, boolean pIsWhite) {
        super(pState, pCol, pRow, pIsWhite, PieceType.QUEEN);
    }

    /**
     * Tells whether the queen's geometry allows a move to a square.
     * <p>
     * A queen moves like a rook and like a bishop, so the target square has to share the column or
     * the row with the current square, or be as many columns away as it is rows. How far she may go
     * is a question of blocked squares and answered by isValidCollide.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pCol column of the target square, 0 to 7
     * @param pRow row of the target square, 0 to 7 where 0 is rank 8
     * @return true if the target lies on the same rank, file or diagonal
     */
    public boolean isValidMovement(int pCol, int pRow) {

        int colDiff = Math.abs(pCol - this.col);
        int rowDiff = Math.abs(pRow - this.row);

        // horizontal OR vertical OR diagonal
        return this.col == pCol
                || this.row == pRow
                || colDiff == rowDiff;
    }

    /**
     * Tells whether a piece blocks the queen's path.
     * <p>
     * A queen may not jump, so every square between her own and the target square has to be empty. I
     * take one step at a time towards the target, which works for straight lines and diagonals
     * alike, and report the first piece I meet. The target square itself is left out, since
     * capturing there is decided by the caller.
     * <p>
     * Time complexity: O(k) for the k squares between start and target, at most seven.
     * Space complexity: O(1).
     *
     * @param pCol column of the target square, 0 to 7
     * @param pRow row of the target square, 0 to 7 where 0 is rank 8
     * @return true if at least one piece stands in the way
     */
    public boolean isValidCollide(int pCol, int pRow) {

        int colStep = Integer.compare(pCol, this.col);
        int rowStep = Integer.compare(pRow, this.row);

        int c = this.col + colStep;
        int r = this.row + rowStep;

        while (c != pCol || r != pRow) {

            if (state.getPiece(c, r) != null) {
                return true;
            }

            c += colStep;
            r += rowStep;
        }

        return false;
    }
}
