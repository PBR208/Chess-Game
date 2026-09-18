package ui.board;

/*
 * Purpose: Input turns mouse actions on the board into moves of the game session. A piece can be
 * dragged, pressed to pick it up, carried with the mouse and dropped on its square, or moved with
 * two clicks, one on the piece and one on where it should go, which is what a player with a shaky
 * hand or a touchpad needs since a drag that slips cancels the move. I keep this apart from the
 * painting so the mouse handling can be tested with synthetic events. Only points on the 8 by 8
 * squares count, so the clock bars and everything outside the board are ignored.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.core.GameSession;
import engine.core.Moves;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class Input extends MouseAdapter {

    // stands for "no square", the same way the board counts
    private static final int NO_SQUARE = -1;

    private final Board board;
    private final GameSession session;

    // a piece that was clicked and is waiting for a second click to say where it goes
    private int armedSquare = NO_SQUARE;
    // square the mouse went down on, so releasing can tell a click from a drag
    private int pressedSquare = NO_SQUARE;

    public Input(Board pBoard, GameSession pSession) {
        this.board = pBoard;
        this.session = pSession;
    }

    /**
     * Picks up the piece under the mouse.
     * <p>
     * A move starts by pressing on a piece. Presses on the clock bars or outside the squares used to
     * pick up pieces on the edge rank or crash with an index out of bounds, so I ignore them. For a
     * press on a square I centre the sprite under the mouse and select the square, which is where
     * the board works out the squares that piece may move to.
     * <p>
     * Time complexity: O(m) for the m legal moves, generated once for the hints.
     * Space complexity: O(1).
     *
     * @param pEvent mouse press on the board panel, never null
     */
    @Override
    public void mousePressed(MouseEvent pEvent) {
        // clicks on the clock bars or outside the squares select nothing
        if (!board.isOnBoard(pEvent.getX(), pEvent.getY())) {
            return;
        }

        int square = Board.squareAt(board.toLogicalCol(pEvent.getX()), board.toLogicalRow(pEvent.getY()));

        if (armedSquare >= 0) {
            // pressing the armed piece again puts it back down
            if (square == armedSquare) {
                disarm();
                return;
            }
            int move = session.moveFor(armedSquare, square);
            // a square the armed piece may go to finishes the move that the first click began
            if (move != Moves.NONE) {
                board.playMove(move);
                disarm();
                return;
            }
            // anything else falls through, which arms another piece or clears the selection
        }

        pressedSquare = square;
        // keep the piece centred under the mouse while it is dragged
        board.setDragPosition(pEvent.getX() - board.getTileSize() / 2, pEvent.getY() - board.getTileSize() / 2);
        board.selectSquare(square);
        board.repaint();
    }

    /**
     * Carries the picked up piece along with the mouse.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pEvent mouse drag on or around the board panel, never null
     */
    @Override
    public void mouseDragged(MouseEvent pEvent) {
        if (board.getSelectedSquare() >= 0) {
            // half a tile keeps the sprite centred on the square under the mouse
            board.setDragPosition(pEvent.getX() - board.getTileSize() / 2,
                    pEvent.getY() - board.getTileSize() / 2);
            board.repaint();
        }
    }

    /**
     * Drops the piece and plays the move when the two squares make one.
     * <p>
     * A move ends when the mouse button is released. A release outside the squares used to count as
     * the nearest edge square, which could play a move nobody meant, so it cancels the drag instead.
     * For a release on a square I ask the session for the legal move between the square the piece
     * came from and the one it was dropped on, which is also where a promotion asks the player what
     * to take, and play it if there is one. The selection is cleared in every case.
     * <p>
     * Time complexity: O(m) for the m legal moves of the position, plus the cost of the move itself.
     * Space complexity: O(1).
     *
     * @param pEvent mouse release on or around the board panel, never null
     */
    @Override
    public void mouseReleased(MouseEvent pEvent) {
        int from = board.getSelectedSquare();

        if (from >= 0 && board.isOnBoard(pEvent.getX(), pEvent.getY())) {
            int to = Board.squareAt(board.toLogicalCol(pEvent.getX()), board.toLogicalRow(pEvent.getY()));

            // letting go on the square the press began on is a click rather than a drag, so the
            // piece stays picked up with its hints showing and waits for a second click
            if (to == from && to == pressedSquare) {
                armedSquare = from;
                board.repaint();
                return;
            }
            // the board plays it, so the move that was just made is marked whoever entered it
            board.playMove(session.moveFor(from, to));
        }

        disarm();
    }

    /**
     * Puts down whatever was picked up or armed.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    private void disarm() {
        armedSquare = NO_SQUARE;
        pressedSquare = NO_SQUARE;
        board.clearSelection();
        board.repaint();
    }
}
