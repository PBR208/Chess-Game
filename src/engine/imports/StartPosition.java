package engine.imports;

/*
 * Purpose: StartPosition builds the 32 pieces of a new game on their home squares. The Swing board
 * used to own that list, which meant a restart inside the rules engine had to call back into the
 * board just to get pieces. Placing the pieces is a rule of chess and not a question of drawing, so
 * it belongs here, and both the board and a restart now ask this class. It only creates the pieces,
 * the caller decides which position they are put into.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.pieces.Bishop;
import engine.pieces.King;
import engine.pieces.Knight;
import engine.pieces.Pawn;
import engine.pieces.Piece;
import engine.pieces.Queen;
import engine.pieces.Rook;

import java.util.ArrayList;

public final class StartPosition {

    private StartPosition() {
    }

    /**
     * Creates the pieces of the standard starting position.
     * <p>
     * Every new game and every restart begins from the same 32 pieces, so this is the single place
     * that knows where they stand. Row 0 is rank 8 and holds Black, row 7 is rank 1 and holds White.
     * I place both back ranks from the a-file to the h-file and then the eight pawns of each side in
     * front of them. The pieces read their surroundings from the position they are handed, so the
     * caller only has to put the returned list into that same position.
     * <p>
     * Time complexity: O(p) for the p pieces created, 32 of them.
     * Space complexity: O(p) for the returned list and the pieces in it.
     *
     * @param pState position the new pieces belong to and read from, never null
     * @return the 32 pieces of the starting position, never null
     * @throws NullPointerException if pState is null
     */
    public static ArrayList<Piece> create(BoardState pState) {

        ArrayList<Piece> newGame = new ArrayList<>();

        // Black's back rank on row 0, which is rank 8
        newGame.add(new Rook(pState, 0, 0, false));
        newGame.add(new Rook(pState, 7, 0, false));
        newGame.add(new Knight(pState, 1, 0, false));
        newGame.add(new Knight(pState, 6, 0, false));
        newGame.add(new Bishop(pState, 2, 0, false));
        newGame.add(new Bishop(pState, 5, 0, false));
        newGame.add(new Queen(pState, 3, 0, false));
        newGame.add(new King(pState, 4, 0, false));

        // White's back rank on row 7, which is rank 1
        newGame.add(new Rook(pState, 0, 7, true));
        newGame.add(new Rook(pState, 7, 7, true));
        newGame.add(new Knight(pState, 1, 7, true));
        newGame.add(new Knight(pState, 6, 7, true));
        newGame.add(new Bishop(pState, 2, 7, true));
        newGame.add(new Bishop(pState, 5, 7, true));
        newGame.add(new Queen(pState, 3, 7, true));
        newGame.add(new King(pState, 4, 7, true));

        // both pawn ranks, one pawn per file
        for (int i = 0; i <= 7; i++) {
            newGame.add(new Pawn(pState, i, 1, false));
            newGame.add(new Pawn(pState, i, 6, true));
        }
        return newGame;
    }
}
