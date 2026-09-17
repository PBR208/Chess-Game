package engine.imports;

/*
 * Purpose: GameView is the small window the rules engine has onto the screen. GameController has to
 * hand the clock over after a move, stop both clocks when the game ends and ask for a repaint, and
 * it used to do all of that on ui.board.Board directly, which dragged Swing into the rules. This
 * interface names those four things and nothing else, so the engine states what it needs while the
 * Swing board decides how to do it. A headless caller can pass an implementation that does nothing.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public interface GameView {

    /**
     * Hands the clock over to the side that is to move now.
     * <p>
     * After a move the player who just moved stops using time and the opponent's clock starts, with
     * the increment going to the player who finished their move. The engine knows whose turn it is
     * and says so, the view owns the clocks and performs the switch.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pWhiteToMove true if White is to move now, false if Black is
     */
    void switchClocks(boolean pWhiteToMove);

    /**
     * Stops both clocks because the game has ended.
     * <p>
     * A finished game must not keep counting down, otherwise the loser later flags and a second
     * result appears for a game that is already over. The engine calls this once, with the first
     * result that ends the game.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    void stopClocks();

    /**
     * Puts both clocks back to their starting times and starts White's clock.
     * <p>
     * A restarted game begins with full thinking time for both players. The engine calls this while
     * it resets the rest of the game state.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    void resetClocks();

    /**
     * Asks the view to show the current position.
     * <p>
     * The engine changes the position, so it is the only place that knows when the screen is out of
     * date. Swing only schedules the repaint, so this returns immediately.
     * <p>
     * Time complexity: O(1), the drawing itself happens later. Space complexity: O(1).
     */
    void repaint();
}
