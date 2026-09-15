package engine.imports;

/*
 * Purpose: MoveHistory keeps the written record of a running game. For every move it stores the
 * move in standard algebraic notation and the FEN of the resulting position, and it tells a
 * listener such as the move log panel about every change. I keep this bookkeeping out of
 * GameController, so the controller decides the rules while this class decides what gets written
 * down. Both lists end up in the saved PGN file and drive the replay viewer.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MoveHistory {

    public interface Listener {
        void onUpdate(List<String> moveLog, String currentFen);

        void onClear();
    }

    private final NotationHelper nh = new NotationHelper();
    private final FenGenerator fg;

    private final ArrayList<String> moveLog = new ArrayList<>();
    private final ArrayList<String> fenHistory = new ArrayList<>();

    private Listener listener;

    public MoveHistory(BoardState state) {
        this.fg = new FenGenerator(state);
    }

    /**
     * Records a move without disambiguation or check marker.
     * <p>
     * Callers that don't know about rival pieces or check, such as a few tests, still need to
     * record moves. I forward to the full version with empty notation extras.
     * <p>
     * Time complexity: O(1) amortized plus O(64) for the FEN.
     * Space complexity: O(1) amortized for the two new list entries.
     *
     * @param pMove          move that was just played, never null
     * @param pFromCol       column the piece started on, 0 to 7
     * @param pFromRow       row the piece started on, 0 to 7 where 0 is rank 8
     * @param pIsWhiteTurn   true if White is to move after this move
     * @param pHalfMoveClock half moves since the last capture or pawn move, 0 or more
     * @param pFullMove      full move number for the FEN, 1 or more
     * @throws NullPointerException if pMove or its piece is null
     */
    public void record(Move pMove, int pFromCol, int pFromRow, boolean pIsWhiteTurn, int pHalfMoveClock, int pFullMove) {
        // no rivals and no check known here
        record(pMove, pFromCol, pFromRow, pIsWhiteTurn, pHalfMoveClock, pFullMove, "", "");
    }

    /**
     * Records a move in SAN together with the FEN of the resulting position.
     * <p>
     * Every played move has to show up in the move log, the saved PGN and the replay viewer. I add
     * the SAN of the move, including the disambiguation and check marker the rules engine worked
     * out, then generate and store the FEN of the new position and finally notify the listener.
     * <p>
     * Time complexity: O(1) amortized plus O(64) for the FEN.
     * Space complexity: O(1) amortized for the two new list entries.
     *
     * @param pMove           move that was just played, never null
     * @param pFromCol        column the piece started on, 0 to 7
     * @param pFromRow        row the piece started on, 0 to 7 where 0 is rank 8
     * @param pIsWhiteTurn    true if White is to move after this move
     * @param pHalfMoveClock  half moves since the last capture or pawn move, 0 or more
     * @param pFullMove       full move number for the FEN, 1 or more
     * @param pDisambiguation origin file, rank or both for identical pieces, "" if none; never null
     * @param pSuffix         "+" for check, "#" for checkmate, "" otherwise; never null
     * @throws NullPointerException if pMove, its piece or one of the strings is null
     */
    public void record(Move pMove, int pFromCol, int pFromRow, boolean pIsWhiteTurn, int pHalfMoveClock,
                       int pFullMove, String pDisambiguation, String pSuffix) {
        // the notation describes the move itself
        moveLog.add(nh.toNotation(pMove, pFromCol, pFromRow, pDisambiguation, pSuffix));
        // the FEN describes the position it produced
        String fen = fg.generate(pIsWhiteTurn, pHalfMoveClock, pFullMove);
        fenHistory.add(fen);
        // keep the move log panel in sync
        if (listener != null) listener.onUpdate(getMoveLog(), fen);
    }

    public void clear() {
        moveLog.clear();
        fenHistory.clear();
        if (listener != null) listener.onClear();
    }

    public List<String> getMoveLog() {
        return Collections.unmodifiableList(moveLog);
    }

    public List<String> getFenHistory() {
        return Collections.unmodifiableList(fenHistory);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }
}
