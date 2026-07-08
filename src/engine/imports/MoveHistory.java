package engine.imports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MoveHistory {

    public interface Listener {
        void onUpdate(List<String> moveLog);

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

    public void record(Move m, int fromCol, int fromRow, boolean isWhiteTurn, int halfMoveClock, int fullMove) {
        moveLog.add(nh.toNotation(m, fromCol, fromRow));
        fenHistory.add(fg.generate(isWhiteTurn, halfMoveClock, fullMove));
        if (listener != null) listener.onUpdate(getMoveLog());
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