package engine.pieces;

/*
 * Purpose: Knight is the only piece that jumps, so it needs no path check at all and the base class
 * answer of "nothing blocks the way" is exactly right for it. Its move covers two squares in one
 * direction and one in the other, which is why multiplying the two distances is enough to recognise
 * it. A single knight can never checkmate on its own, which the insufficient material rule uses.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.imports.BoardState;

public class Knight extends Piece {

    /**
     * Creates a knight on a square.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pState   position the knight belongs to and reads from, never null
     * @param pCol     column of its square, 0 to 7
     * @param pRow     row of its square, 0 to 7 where 0 is rank 8
     * @param pIsWhite true for a white knight, false for a black one
     */
    public Knight(BoardState pState, int pCol, int pRow, boolean pIsWhite) {
        super(pState, pCol, pRow, pIsWhite, PieceType.KNIGHT);
    }

    /**
     * Tells whether the knight's geometry allows a move to a square.
     * <p>
     * A knight move covers two squares in one direction and one in the other, so the two distances
     * multiplied are always two, and no other combination of distances gives that product. Pieces in
     * between don't matter, because a knight jumps over them.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pCol column of the target square, 0 to 7
     * @param pRow row of the target square, 0 to 7 where 0 is rank 8
     * @return true if the target is a knight's jump away
     */
    public boolean isValidMovement(int pCol, int pRow) {
        return Math.abs(pCol - this.col) * Math.abs(pRow - this.row) == 2;
    }
}
