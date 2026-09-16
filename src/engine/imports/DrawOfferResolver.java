package engine.imports;

/*
 * Purpose: DrawOfferResolver is the small interface the rules engine uses whenever a draw needs a
 * decision or an announcement from the players. GameController only knows this interface, while
 * the Swing board supplies real dialogs and tests supply simple fakes, so every draw rule stays
 * testable without a window. Forced draws are only announced. Claimable draws return whether the
 * player to move took the draw.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public interface DrawOfferResolver {
    void notifyForcedDraw();      // 75-move rule: informational only, game always ends

    boolean offerDraw();          // 50-move rule: true if the player claimed/accepted the draw

    /**
     * Offers the player to move a draw because the same position occurred for the third time.
     * <p>
     * Threefold repetition lets a player claim a draw but does not force it. The default answer
     * falls back to offerDraw, so resolvers written before this rule existed keep working, and a
     * user interface can override it to explain the actual reason to the player.
     * <p>
     * Time complexity: O(1) apart from waiting for the player. Space complexity: O(1).
     *
     * @return true if the player claims the draw, false to play on
     */
    default boolean offerRepetitionDraw() {
        // older resolvers only know the generic claim
        return offerDraw();
    }
}
