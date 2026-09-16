package engine.pieces;

/*
 * Purpose: Rook slides along its rank and file for as far as the position allows. Besides its own
 * moves it matters for castling, because a rook that has not moved yet is what lets the king castle
 * towards it, which is why its first-move flag is kept up to date like the king's. Whether a rook
 * move leaves the own king in check is decided by the check simulation, not here.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.imports.BoardState;

public class Rook extends Piece {

    /**
     * Creates a rook on a square.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pState   position the rook belongs to and reads from, never null
     * @param pCol     column of its square, 0 to 7
     * @param pRow     row of its square, 0 to 7 where 0 is rank 8
     * @param pIsWhite true for a white rook, false for a black one
     */
    public Rook(BoardState pState, int pCol, int pRow, boolean pIsWhite) {
        super(pState, pCol, pRow, pIsWhite, PieceType.ROOK);
    }

    /**
     * Tells whether the rook's geometry allows a move to a square.
     * <p>
     * A rook moves in a straight line, so the target has to share either the column or the row with
     * its current square. How far it may go is a question of blocked squares and answered by
     * isValidCollide.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pCol column of the target square, 0 to 7
     * @param pRow row of the target square, 0 to 7 where 0 is rank 8
     * @return true if the target lies on the same rank or file
     */
    public boolean isValidMovement(int pCol, int pRow) {
        return pCol == this.col || pRow == this.row;
    }

    /**
     * Tells whether a piece blocks the rook's path.
     * <p>
     * A rook may not jump, so every square between its own and the target square has to be empty. I
     * take one step at a time towards the target and report the first piece I meet. The target square
     * itself is left out, since capturing there is decided by the caller.
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

        int currentCol = this.col + colStep;
        int currentRow = this.row + rowStep;

        while (currentCol != pCol || currentRow != pRow) {
            if (state.getPiece(currentCol, currentRow) != null) {
                return true;
            }

            currentCol += colStep;
            currentRow += rowStep;
        }

        return false;
    }
}
