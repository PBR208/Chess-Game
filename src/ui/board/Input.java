package ui.board;

/*
 * Purpose: Input turns mouse actions on the board into moves of the game session. Pressing on a
 * piece picks it up and shows where it may go, dragging carries it with the mouse and releasing it
 * asks the session for the move between the two squares and plays it when there is one. I keep this
 * apart from the painting so the mouse handling can be tested with synthetic events. Only points on
 * the 8 by 8 squares count, so the clock bars and everything outside the board are ignored.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.core.GameSession;
import engine.core.Moves;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class Input extends MouseAdapter {

    private final Board board;
    private final GameSession session;

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
        // a paused game takes no moves, which is the whole point of pausing it
        if (board.isPaused()) {
            return;
        }
        // clicks on the clock bars or outside the squares select nothing
        if (!board.isOnBoard(pEvent.getX(), pEvent.getY())) {
            return;
        }

        int square = Board.squareAt(board.toLogicalCol(pEvent.getX()), board.toLogicalRow(pEvent.getY()));
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
        // nothing was picked up while paused, so there is nothing to let go of either
        if (board.isPaused()) {
            return;
        }
        int from = board.getSelectedSquare();

        if (from >= 0 && board.isOnBoard(pEvent.getX(), pEvent.getY())) {
            int to = Board.squareAt(board.toLogicalCol(pEvent.getX()), board.toLogicalRow(pEvent.getY()));
            int move = session.moveFor(from, to);
            // a pair of squares that is no legal move simply puts the piece back
            if (move != Moves.NONE) {
                session.play(move);
            }
        }

        board.clearSelection();
        board.repaint();
    }
}
