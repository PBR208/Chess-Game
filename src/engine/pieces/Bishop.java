package engine.pieces;

/*
 * Purpose: Bishop slides along the diagonals and therefore never leaves the colour of the square it
 * started on. That property also decides games, because two bishops on the same colour can never
 * checkmate, which is why the insufficient material rule asks for the square colours. Whether a
 * bishop move leaves the own king in check is decided by the check simulation, not here.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.imports.BoardState;

public class Bishop extends Piece {

    /**
     * Creates a bishop on a square.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pState   position the bishop belongs to and reads from, never null
     * @param pCol     column of its square, 0 to 7
     * @param pRow     row of its square, 0 to 7 where 0 is rank 8
     * @param pIsWhite true for a white bishop, false for a black one
     */
    public Bishop(BoardState pState, int pCol, int pRow, boolean pIsWhite) {
        super(pState, pCol, pRow, pIsWhite, PieceType.BISHOP);
    }

    /**
     * Tells whether the bishop's geometry allows a move to a square.
     * <p>
     * A bishop moves diagonally, so the target square has to be as many columns away as it is rows.
     * How far it may go is a question of blocked squares and answered by isValidCollide.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pCol column of the target square, 0 to 7
     * @param pRow row of the target square, 0 to 7 where 0 is rank 8
     * @return true if the target lies on a diagonal of the current square
     */
    public boolean isValidMovement(int pCol, int pRow) {
        return Math.abs(pCol - this.col) == Math.abs(pRow - this.row);
    }

    /**
     * Tells whether a piece blocks the bishop's diagonal.
     * <p>
     * A bishop may not jump, so every square on the diagonal between its own and the target square
     * has to be empty. I refuse anything that is not a diagonal outright and otherwise walk the
     * squares in between, leaving out the target square, where capturing is decided by the caller.
     * <p>
     * Time complexity: O(k) for the k squares between start and target, at most seven.
     * Space complexity: O(1).
     *
     * @param pCol column of the target square, 0 to 7
     * @param pRow row of the target square, 0 to 7 where 0 is rank 8
     * @return true if at least one piece stands in the way
     */
    public boolean isValidCollide(int pCol, int pRow) {

        int colDiff = pCol - this.col;
        int rowDiff = pRow - this.row;

        // Not a diagonal move
        if (Math.abs(colDiff) != Math.abs(rowDiff)) {
            return false;
        }

        int colStep = Integer.compare(pCol, this.col);
        int rowStep = Integer.compare(pRow, this.row);

        // Check all squares between start and destination
        for (int i = 1; i < Math.abs(colDiff); i++) {
            if (state.getPiece(
                    this.col + i * colStep,
                    this.row + i * rowStep) != null) {
                return true; // Collision found
            }
        }

        return false; // No collision
    }
}
