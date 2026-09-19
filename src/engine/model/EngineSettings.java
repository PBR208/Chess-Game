package engine.model;

/*
 * Purpose: EngineSettings says who the second player is and, when that is the program itself, which
 * colour it takes, how hard it thinks and how carelessly it judges what it finds. I keep this apart
 * from GameConfig because a game already carries names and clocks that every game has, while these
 * answers only exist once somebody chooses to play the computer, and widening the older record would
 * have changed every place that builds one. It also says whether the board turns to face the player
 * to move, which is right between two people sharing a screen and wrong against an engine.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.Pieces;
import engine.search.Searcher;

/**
 * @param engineOpponent   true when the program plays one of the sides itself
 * @param engineColour     the colour the program plays, Pieces.WHITE or Pieces.BLACK
 * @param depth            how many moves deep it looks
 * @param maxNodes         how many positions it may visit before answering
 * @param maxTimeMs        how long it may think about one move
 * @param noiseCentipawns  how carelessly it judges the moves on offer, 0 to always play the best
 * @param autoFlip         true to turn the board towards whoever is to move
 */
public record EngineSettings(boolean engineOpponent, int engineColour, int depth, long maxNodes,
                             long maxTimeMs, int noiseCentipawns, boolean autoFlip) {

    // how strong the levels are, from one that barely looks ahead to one that plays its best
    private static final int[] LEVEL_DEPTH = {1, 2, 3, 4, 6};
    private static final int[] LEVEL_NOISE = {150, 90, 45, 15, 0};

    // how long any level may think, so a weak machine can never leave a player waiting
    private static final long LEVEL_TIME_MS = 3_000L;

    // and a cap on positions, which bounds the thinking even where the clock is coarse
    private static final long LEVEL_NODES = 400_000L;

    /** the weakest level a player can choose */
    public static final int MIN_LEVEL = 1;

    /** the strongest level a player can choose */
    public static final int MAX_LEVEL = 5;

    /**
     * Checks the settings make sense before a game is built on them.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @throws IllegalArgumentException if the colour is not a colour or the depth is below one
     */
    public EngineSettings {
        // a colour that is neither side would leave the engine waiting for a turn that never comes
        if (engineColour != Pieces.WHITE && engineColour != Pieces.BLACK) {
            throw new IllegalArgumentException("the engine must play White or Black, got " + engineColour);
        }
        if (depth < 1) {
            throw new IllegalArgumentException("depth " + depth + " must be at least 1");
        }
    }

    /**
     * Settings for a game between two people at one screen.
     * <p>
     * The board turns to face whoever is to move, which is what two players sharing a screen want.
     * The engine colour is left at White and means nothing while nobody is playing the engine.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return settings with no engine in them, never null
     */
    public static EngineSettings humanOpponent() {
        return new EngineSettings(false, Pieces.WHITE, 1, Long.MAX_VALUE, Long.MAX_VALUE, 0, true);
    }

    /**
     * Settings for a game against the program at one of its levels.
     * <p>
     * A level is a depth and an amount of carelessness together. The weak levels look barely any
     * distance ahead and judge what they find loosely, so they lose pieces the way a beginner does
     * rather than simply playing slower. Every level is also capped in time and positions, so no
     * level can leave a player waiting on a slow machine. The board stops turning round, because
     * against an engine there is only one player to face and a board that flips under them is wrong.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pLevel        how strong, MIN_LEVEL up to MAX_LEVEL
     * @param pEngineColour the colour the program takes, Pieces.WHITE or Pieces.BLACK
     * @return the settings, never null
     * @throws IllegalArgumentException if the level is outside its range or the colour is not one
     */
    public static EngineSettings level(int pLevel, int pEngineColour) {
        if (pLevel < MIN_LEVEL || pLevel > MAX_LEVEL) {
            throw new IllegalArgumentException("level " + pLevel + " must be between "
                    + MIN_LEVEL + " and " + MAX_LEVEL);
        }
        int index = pLevel - MIN_LEVEL;
        return new EngineSettings(true, pEngineColour, LEVEL_DEPTH[index], LEVEL_NODES,
                LEVEL_TIME_MS, LEVEL_NOISE[index], false);
    }

    /**
     * Turns these settings into the limits a search understands.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the limits for one move, never null
     */
    public Searcher.Limits limits() {
        return new Searcher.Limits(depth, maxNodes, maxTimeMs, noiseCentipawns);
    }

    /**
     * Tells whether the program plays a given colour.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pColour the colour to ask about, Pieces.WHITE or Pieces.BLACK
     * @return true if the program moves for that side
     */
    public boolean playsFor(int pColour) {
        return engineOpponent && pColour == engineColour;
    }

    /**
     * Returns the colour the person is playing.
     * <p>
     * The board is drawn from this side when it does not turn round, so that a player against the
     * engine always sees their own pieces nearest to them.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the person's colour, which is White in a game between two people
     */
    public int humanColour() {
        return engineOpponent ? 1 - engineColour : Pieces.WHITE;
    }
}
