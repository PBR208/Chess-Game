package engine.imports;

/*
 * Purpose: MoveLogView is what the rules engine sees of the move log next to the board. GameController
 * used to take the Swing panel itself, which meant the engine imported a component only to push two
 * strings into it. This interface describes the two things the log has to do, receive a new list of
 * moves with the current position and start over, so the engine stays free of the ui package. The
 * Swing panel implements it directly, and tests can pass a recording stand-in instead.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import java.util.List;

public interface MoveLogView {

    /**
     * Shows the moves played so far together with the position after the last move.
     * <p>
     * The log is rebuilt from the full list after every move, which keeps it correct even when a
     * move is recorded in an unusual order. The engine passes a read-only list, so the view must not
     * try to change it.
     * <p>
     * Time complexity: O(m) for m recorded moves. Space complexity: O(m) for the rendered text.
     *
     * @param pMoveLog   moves in standard algebraic notation, oldest first; never null, may be empty
     * @param pCurrentFen FEN of the position after the last move, never null
     */
    void update(List<String> pMoveLog, String pCurrentFen);

    /**
     * Empties the log because a new game started.
     * <p>
     * A restarted game must not show the moves of the previous one. The engine calls this when it
     * clears its history.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    void clear();
}
