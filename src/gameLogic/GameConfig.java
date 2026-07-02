package gameLogic;

public class GameConfig {

    public final String whiteName;
    public final String blackName;
    public final long whiteTimeMs;   // 0 = unlimited
    public final long blackTimeMs;
    public final String timeLabel;   // e.g. "Blitz 5+0" — shown in saved PGN

    public GameConfig(String whiteName, String blackName,
                      long whiteTimeMs, long blackTimeMs, String timeLabel) {
        this.whiteName = whiteName.isBlank() ? "White" : whiteName.trim();
        this.blackName = blackName.isBlank() ? "Black" : blackName.trim();
        this.whiteTimeMs = whiteTimeMs;
        this.blackTimeMs = blackTimeMs;
        this.timeLabel = timeLabel;
    }

    public static GameConfig unlimited() {
        return new GameConfig("White", "Black", 0, 0, "Unlimited");
    }
}