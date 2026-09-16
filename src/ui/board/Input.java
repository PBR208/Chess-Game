package ui.board;

/*
 * Purpose: Input turns mouse actions on the board into game actions. Pressing on a piece picks it up
 * and shows its legal moves, dragging moves it with the mouse and releasing it asks the rules
 * engine whether the move is legal and plays it. I keep this apart from the board painting so the
 * mouse handling can be tested with synthetic events. Only points on the 8 by 8 squares count, so
 * the clock bars and everything outside the board are ignored.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.imports.GameController;
import engine.imports.Move;
import engine.pieces.Piece;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class Input extends MouseAdapter {

    private final Board b;
    private final GameController gc;

    public Input(Board b, GameController gc) {
        this.b = b;
        this.gc = gc;
    }

    /**
     * Picks up the piece under the mouse.
     * <p>
     * A move starts by pressing on a piece. Presses on the clock bars or outside the squares used to
     * pick up pieces on the edge rank or crash with an index out of bounds, so I ignore them now.
     * Otherwise I find the piece on the pressed square, center it under the mouse and select it,
     * which also works out its legal move hints.
     * <p>
     * Time complexity: O(s * p) for the legal move hints of the selected piece over s squares and p
     * pieces. Space complexity: O(s) for the hint squares.
     *
     * @param pEvent mouse press on the board panel, never null
     */
    @Override
    public void mousePressed(MouseEvent pEvent) {
        // clicks on the clock bars or outside the squares select nothing
        if (!b.isOnBoard(pEvent.getX(), pEvent.getY())) {
            return;
        }

        int col = b.toLogicalCol(pEvent.getX());
        int row = b.toLogicalRow(pEvent.getY());

        Piece pAtLocation = b.getPiece(col, row);
        if (pAtLocation != null) {
            // keep the piece centered under the mouse while it is dragged
            b.setDragPosition(pEvent.getX() - b.getTileSize() / 2, pEvent.getY() - b.getTileSize() / 2);

            b.setSelectedPiece(pAtLocation);
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {

        if (b.getSelectedPiece() != null) {
            // /2 keeps the sprite centered on the tile under the mouse
            b.setDragPosition(e.getX() - b.getTileSize() / 2, e.getY() - b.getTileSize() / 2);

            b.repaint();
        }
    }

    /**
     * Drops the selected piece and plays the move if it is legal.
     * <p>
     * A move ends when the mouse button is released. A release outside the squares used to count as
     * the nearest edge square, which could play a move nobody meant, so it now cancels the drag. For a
     * release on a square I build the move and play it when the rules allow it. A move the rules
     * refuse needs no cleanup, because the piece is drawn on its own square again as soon as the
     * selection is gone. The selection is cleared in every case.
     * <p>
     * Time complexity: O(s * p) when a move is played, for the end of game search over s squares and p
     * pieces. Space complexity: O(1) apart from the recorded move.
     *
     * @param pEvent mouse release on or around the board panel, never null
     */
    @Override
    public void mouseReleased(MouseEvent pEvent) {
        Piece selected = b.getSelectedPiece();

        if (selected != null) {
            // a release outside the squares cancels the drag instead of guessing a square
            Move m = b.isOnBoard(pEvent.getX(), pEvent.getY())
                    ? new Move(b.getState(), selected, b.toLogicalCol(pEvent.getX()), b.toLogicalRow(pEvent.getY()))
                    : null;

            if (m != null && gc.isValidMove(m)) {
                gc.makeMove(m);
            }
        }

        b.setSelectedPiece(null);
        b.repaint();
    }
}
