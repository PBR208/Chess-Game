package engine.model;

/*
 * Purpose: GameConfig carries the settings a new game starts with: the player names, both starting
 * times, the time control label and the increment. The menu builds it and hands it to the board,
 * the clocks and the saved game record, so every part of a game works with the same settings. As
 * a record it cannot change once a game has started. Blank names fall back to White and Black.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

/**
 * @param whiteTimeMs 0 = unlimited
 * @param blackTimeMs 0 = unlimited, and it may differ from White's for a game at time odds
 * @param timeLabel   e.g. "Blitz 5+0" - shown in saved PGN
 * @param incrementMs time added to a player's clock after each of their moves, 0 for none
 * @param clockMode   how the clock treats the time around a move, never null after construction
 * @param delayMs     the delay a Bronstein or simple delay clock works with, 0 for none
 */
public record GameConfig(String whiteName, String blackName, long whiteTimeMs, long blackTimeMs, String timeLabel,
                         long incrementMs, ClockMode clockMode, long delayMs, java.util.List<ClockStage> stages) {

    /**
     * Normalises the settings of every new configuration.
     * <p>
     * An empty name field should still show a sensible name on the board and in the saved game, so I
     * replace blank names with White or Black and trim the others. A configuration that names no
     * clock mode comes from a caller that only knows about increments, which is what every preset
     * used to be, so it plays a Fischer clock when it has an increment and sudden death otherwise. A
     * negative delay is treated as none, because a clock cannot wait for less than no time.
     * <p>
     * Time complexity: O(n) in the length of the names. Space complexity: O(n) for the trimmed
     * names.
     *
     * @throws NullPointerException if one of the names is null
     */
    public GameConfig {
        // blank names fall back to the colour, everything else is trimmed
        whiteName = whiteName.isBlank() ? "White" : whiteName.trim();
        blackName = blackName.isBlank() ? "Black" : blackName.trim();
        // a caller that knows nothing about modes still gets the clock its increment implies
        if (clockMode == null) {
            clockMode = incrementMs > 0 ? ClockMode.FISCHER : ClockMode.SUDDEN_DEATH;
        }
        // a clock cannot wait for less than no time
        delayMs = Math.max(0, delayMs);
        // most games have no stages at all, and a copy keeps a configuration from changing later
        stages = stages == null ? java.util.List.of() : java.util.List.copyOf(stages);
    }

    /**
     * Creates a configuration for a time control that has no stages.
     * <p>
     * Only a tournament control hands out more time part way through a game, and almost nothing
     * does. I let every other caller leave the stages out entirely.
     * <p>
     * Time complexity: O(n) in the length of the names. Space complexity: O(n) for the names.
     *
     * @param pWhiteName   White's name, blank for the default "White"; never null
     * @param pBlackName   Black's name, blank for the default "Black"; never null
     * @param pWhiteTimeMs White's starting time in milliseconds, 0 for unlimited
     * @param pBlackTimeMs Black's starting time in milliseconds, 0 for unlimited
     * @param pTimeLabel   label of the time control such as "Blitz 5+0", never null
     * @param pIncrementMs time added after each move in milliseconds, 0 for none
     * @param pClockMode   how the clock treats the time around a move, or null to follow the increment
     * @param pDelayMs     the delay of a Bronstein or simple delay clock, 0 for none
     * @throws NullPointerException if one of the names is null
     */
    public GameConfig(String pWhiteName, String pBlackName, long pWhiteTimeMs, long pBlackTimeMs,
                      String pTimeLabel, long pIncrementMs, ClockMode pClockMode, long pDelayMs) {
        this(pWhiteName, pBlackName, pWhiteTimeMs, pBlackTimeMs, pTimeLabel, pIncrementMs, pClockMode, pDelayMs,
                java.util.List.of());
    }

    /**
     * Creates a configuration for a clock that only knows an increment.
     * <p>
     * Every preset and every existing caller describes its time control as a starting time and an
     * increment, which is a Fischer clock when the increment is there and sudden death when it is
     * not. I work that out rather than making each caller say it, and leave the delay at zero.
     * <p>
     * Time complexity: O(n) in the length of the names. Space complexity: O(n) for the names.
     *
     * @param pWhiteName   White's name, blank for the default "White"; never null
     * @param pBlackName   Black's name, blank for the default "Black"; never null
     * @param pWhiteTimeMs White's starting time in milliseconds, 0 for unlimited
     * @param pBlackTimeMs Black's starting time in milliseconds, 0 for unlimited
     * @param pTimeLabel   label of the time control such as "Blitz 5+0", never null
     * @param pIncrementMs time added after each move in milliseconds, 0 for none
     * @throws NullPointerException if one of the names is null
     */
    public GameConfig(String pWhiteName, String pBlackName, long pWhiteTimeMs, long pBlackTimeMs,
                      String pTimeLabel, long pIncrementMs) {
        // the mode follows from the increment, which is what every caller knew about until now
        this(pWhiteName, pBlackName, pWhiteTimeMs, pBlackTimeMs, pTimeLabel, pIncrementMs, null, 0);
    }

    /**
     * Creates a configuration without an increment.
     * <p>
     * Most time controls and all existing callers don't add time after a move. I forward to the full
     * constructor with an increment of zero.
     * <p>
     * Time complexity: O(n) in the length of the names. Space complexity: O(n) for the names.
     *
     * @param pWhiteName   White's name, blank for the default "White"; never null
     * @param pBlackName   Black's name, blank for the default "Black"; never null
     * @param pWhiteTimeMs White's starting time in milliseconds, 0 for unlimited
     * @param pBlackTimeMs Black's starting time in milliseconds, 0 for unlimited
     * @param pTimeLabel   label of the time control such as "Blitz 5+0", never null
     * @throws NullPointerException if one of the names is null
     */
    public GameConfig(String pWhiteName, String pBlackName, long pWhiteTimeMs, long pBlackTimeMs, String pTimeLabel) {
        // no increment unless one is asked for
        this(pWhiteName, pBlackName, pWhiteTimeMs, pBlackTimeMs, pTimeLabel, 0);
    }

    public static GameConfig unlimited() {
        return new GameConfig("White", "Black", 0, 0, "Unlimited");
    }
}
