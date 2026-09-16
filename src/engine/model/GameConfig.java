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
 * @param timeLabel   e.g. "Blitz 5+0" — shown in saved PGN
 * @param incrementMs time added to a player's clock after each of their moves, 0 for none
 */
public record GameConfig(String whiteName, String blackName, long whiteTimeMs, long blackTimeMs, String timeLabel,
                         long incrementMs) {

    /**
     * Normalises the player names of every new configuration.
     * <p>
     * An empty name field should still show a sensible name on the board and in the saved game. I
     * replace blank names with White or Black and trim the others, all other settings are kept as
     * given.
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
