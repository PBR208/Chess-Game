package engine.pieces;

/*
 * Purpose: Piece is the base class of all six chess pieces. It holds the square, colour, type and
 * first-move flag every piece needs, and it defines the movement hooks the concrete pieces override.
 * A piece answers questions about its own moves, so it reads the surrounding position from
 * BoardState. Everything about drawing, from the sprite sheet to pixel coordinates, moved to
 * ui.board.PieceSprites, which is what keeps this class and the whole engine free of AWT and Swing.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.imports.BoardState;

public class Piece {

    protected int col, row;

    protected boolean isWhite;
    protected PieceType type;
    protected int value;

    private boolean isFirstMove = true;

    // the position this piece looks at when it judges its own moves
    protected BoardState state;

    /**
     * Creates a piece of one type and colour on a square.
     * <p>
     * Every piece needs to know where it stands, which side it belongs to and which position it is
     * part of, because its movement rules depend on the pieces around it. I store the position it
     * reads from, its square, its colour and its type. The piece used to receive the Swing board
     * here and slice its own sprite from it, which is why the rules could not run without a display.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pState   position the piece belongs to and reads from, never null
     * @param pCol     column of its square, 0 to 7 from the a-file
     * @param pRow     row of its square, 0 to 7 where 0 is rank 8
     * @param pIsWhite true for a white piece, false for a black one
     * @param pType    type of the piece, never null
     */
    protected Piece(BoardState pState, int pCol, int pRow, boolean pIsWhite, PieceType pType) {
        this.state = pState;
        this.col = pCol;
        this.row = pRow;
        this.isWhite = pIsWhite;
        this.type = pType;
    }

    // GETTER

    public int getCol() {
        return col;
    }

    public int getRow() {
        return row;
    }

    public boolean isWhite() {
        return isWhite;
    }

    public PieceType getType() {
        return type;
    }

    public boolean isFirstMove() {
        return isFirstMove;
    }

    // SETTER

    public void setCol(int col) {
        this.col = col;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public void setFirstMove(boolean firstMove) {
        isFirstMove = firstMove;
    }

    // HELPER

    /**
     * Puts the piece on another square and marks it as moved.
     * <p>
     * A piece that has moved loses what its first move allowed, such as castling for a king and the
     * double step for a pawn. I store the new square and clear the first-move flag. The caller keeps
     * the grid in BoardState in step with this.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pCol column of the target square, 0 to 7
     * @param pRow row of the target square, 0 to 7 where 0 is rank 8
     */
    public void moveTo(int pCol, int pRow) {
        this.col = pCol;
        this.row = pRow;
        this.isFirstMove = false;
    }

    /**
     * Tells whether the geometry of this piece allows a move to a square.
     * <p>
     * Each piece type moves differently, so the concrete classes override this with their own rule
     * and the base class allows everything. Blocked paths and the safety of the own king are checked
     * elsewhere, this answers the shape of the move alone.
     * <p>
     * Time complexity: O(1) here, the overrides stay constant or scan one line of squares.
     * Space complexity: O(1).
     *
     * @param pCol column of the target square, 0 to 7
     * @param pRow row of the target square, 0 to 7 where 0 is rank 8
     * @return true if the move fits the way this piece moves
     */
    public boolean isValidMovement(int pCol, int pRow) {
        return true;
    }

    /**
     * Tells whether another piece stands between this piece and a target square.
     * <p>
     * Sliding pieces may not jump, so they override this and walk the squares in between. Knights
     * and kings never need it, so the base class reports no collision. The target square itself is
     * not part of the path, capturing is decided by the caller.
     * <p>
     * Time complexity: O(1) here, the overrides walk at most seven squares.
     * Space complexity: O(1).
     *
     * @param pCol column of the target square, 0 to 7
     * @param pRow row of the target square, 0 to 7 where 0 is rank 8
     * @return true if at least one piece blocks the way
     */
    public boolean isValidCollide(int pCol, int pRow) {
        return false;
    }
}
