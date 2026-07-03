package gameLogic;

import java.time.LocalDate;
import java.util.List;

public class GameRecord {

    public final String whiteName;
    public final String blackName;
    public final String result;        // "1-0", "0-1", "1/2-1/2"
    public final String date;          // "2026.07.03"
    public final String timeControl;
    public final List<String> moves;
    public final List<String> fenHistory;

    // Used when game ends
    public GameRecord(GameConfig config, String result,
                      List<String> moves, List<String> fenHistory) {
        this.whiteName = config.whiteName();
        this.blackName = config.blackName();
        this.result = result;
        this.date = LocalDate.now().toString().replace("-", ".");
        this.timeControl = config.timeLabel();
        this.moves = List.copyOf(moves);
        this.fenHistory = List.copyOf(fenHistory);
    }

    // Used when loading a game back from a PGN file
    public GameRecord(String white, String black, String result,
                      String date, String timeControl,
                      List<String> moves, List<String> fenHistory) {
        this.whiteName = white;
        this.blackName = black;
        this.result = result;
        this.date = date;
        this.timeControl = timeControl;
        this.moves = List.copyOf(moves);
        this.fenHistory = List.copyOf(fenHistory);
    }

    public String getDisplayTitle() {
        return whiteName + " vs " + blackName
                + "  —  " + result
                + "  —  " + date
                + "  —  " + timeControl;
    }
}