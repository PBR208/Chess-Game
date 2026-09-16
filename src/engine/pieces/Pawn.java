package engine.pieces;

/*
 * Purpose: Pawn carries more special rules than any other piece, which is why they all live here.
 * It pushes one square forward, two from its starting square, captures diagonally and may take a
 * pawn that just passed it en passant. Promotion is not decided here but in GameController, because
 * reaching the last rank needs a new piece and a choice from the player.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.imports.BoardState;

public class Pawn extends Piece {

    /**
     * Creates a pawn on a square.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pState   position the pawn belongs to and reads from, never null
     * @param pCol     column of its square, 0 to 7
     * @param pRow     row of its square, 0 to 7 where 0 is rank 8
     * @param pIsWhite true for a white pawn, false for a black one
     */
    public Pawn(BoardState pState, int pCol, int pRow, boolean pIsWhite) {
        super(pState, pCol, pRow, pIsWhite, PieceType.PAWN);
    }

    /**
     * Tells whether the pawn's rules allow a move to a square.
     * <p>
     * Pawns are the only pieces that move and capture differently, so all four cases are checked
     * here. White moves towards row 0 and Black towards row 7. I allow the single push onto an empty
     * square, the double push from the starting square when both squares are empty, the diagonal
     * capture of an enemy piece, and the diagonal step onto the en passant square when the pawn that
     * just passed stands next to it.
     * <p>
     * Time complexity: O(1), at most three squares are looked at. Space complexity: O(1).
     *
     * @param pCol column of the target square, 0 to 7
     * @param pRow row of the target square, 0 to 7 where 0 is rank 8
     * @return true if the pawn may move there
     */
    public boolean isValidMovement(int pCol, int pRow) {

        int colorIndex = this.isWhite ? 1 : -1;

        // push pawn after move 1 (move = 1 tile radius)
        if (this.col == pCol && pRow == this.row - colorIndex && state.getPiece(pCol, pRow) == null) {
            return true;
        }

        // push pawn move 1
        if (isFirstMove() && this.col == pCol && pRow == this.row - colorIndex * 2
                && state.getPiece(pCol, pRow) == null && state.getPiece(pCol, pRow + colorIndex) == null) {
            return true;
        }

        //capture
        if (Math.abs(pCol - this.col) == 1 && pRow == this.row - colorIndex) {

            Piece target = state.getPiece(pCol, pRow);

            if (target != null && target.isWhite() != this.isWhite) {
                return true;
            }
        }

        //en passant

        if (Math.abs(pCol - this.col) == 1 &&
                pRow == this.row - colorIndex &&
                state.getTileNum(pCol, pRow) == state.getEnPassantTile()) {


            Piece target = state.getPiece(pCol, pRow + colorIndex);


            return target instanceof Pawn && target.isWhite() != this.isWhite;
        }

        return false;
    }

}
