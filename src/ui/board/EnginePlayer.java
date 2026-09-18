package ui.board;

/*
 * Purpose: EnginePlayer is the program taking one side of a game. It watches whose turn it is, works
 * out a move when the turn is its own, and plays it into the session. The thinking happens away from
 * the thread that draws the board, because a search that ran there would freeze the window for as
 * long as it thought, and the move is played back on that thread, because everything else touching a
 * running game happens there. Choosing a move and playing one are kept apart, so the part that
 * decides can be tested without a window and without waiting for anything.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.GameSession;
import engine.core.Moves;
import engine.core.Pieces;
import engine.model.EngineSettings;
import engine.search.Searcher;

import javax.swing.SwingUtilities;

public class EnginePlayer {

    private final EngineSettings settings;

    // true while a move is being thought about, so one turn cannot be started twice
    private volatile boolean thinking;

    /**
     * Builds the program's side of a game.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSettings which colour to play and how hard to think, never null
     * @throws NullPointerException if pSettings is null
     */
    public EnginePlayer(EngineSettings pSettings) {
        this.settings = pSettings;
    }

    /**
     * Tells whether it is the program's turn to move.
     * <p>
     * A game that has ended has no turn left, and a game against another person never has one that
     * belongs here. A turn already being thought about is not a second turn either, which is what
     * stops a repaint or a stray event from starting the same move twice.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSession the running game, never null
     * @return true if the program should move now
     * @throws NullPointerException if pSession is null
     */
    public boolean isEngineTurn(GameSession pSession) {
        if (!settings.engineOpponent() || thinking || pSession.result().isFinished()) {
            return false;
        }
        int sideToMove = pSession.isWhiteToMove() ? Pieces.WHITE : Pieces.BLACK;
        return settings.playsFor(sideToMove);
    }

    /**
     * Works out the move the program would play, and waits for the answer.
     * <p>
     * The search runs on a thread of its own with a stack large enough for the deepest line it is
     * allowed to follow, and on a copy of the position, so the board nobody is finished reading
     * cannot change underneath. This blocks until the search is done, which is what makes it usable
     * from a test; the game screen calls the version that does not wait.
     * <p>
     * Time complexity: the cost of the search, bounded by the depth, node and time caps of the
     * settings. Space complexity: O(1) beyond the copy the search works on.
     *
     * @param pSession the running game, never null and unchanged by this
     * @return the move to play, or Moves.NONE when there is none or the wait was interrupted
     * @throws NullPointerException if pSession is null
     */
    public int chooseMove(GameSession pSession) {
        try {
            Searcher.Result result = Searcher.searchOnThread(pSession.position(), settings.limits());
            return result.bestMove;
        } catch (InterruptedException e) {
            // whoever interrupted this wants the thread to stop, so say so and give up the move
            Thread.currentThread().interrupt();
            return Moves.NONE;
        }
    }

    /**
     * Thinks about a move and plays it, without holding anybody up.
     * <p>
     * The search happens away from the thread that draws the board, so the window stays alive while
     * the program thinks, and the move is played back on that thread, because that is where every
     * other change to a running game happens and a position changing under a repaint would tear.
     * A move that has stopped being legal by the time it arrives is dropped rather than forced,
     * which is what happens if the game ended while the search was still running.
     * <p>
     * Time complexity: O(1) here, the search costs what its limits allow on the other thread.
     * Space complexity: O(1).
     *
     * @param pSession   the running game, never null
     * @param pAfterMove run on the drawing thread once the move has been played, may be null
     * @return true if the program started thinking, false when it was not its turn
     * @throws NullPointerException if pSession is null
     */
    public boolean moveIfItsTurn(GameSession pSession, Runnable pAfterMove) {
        if (!isEngineTurn(pSession)) {
            return false;
        }
        thinking = true;

        Runnable task = () -> {
            int move = chooseMove(pSession);
            SwingUtilities.invokeLater(() -> {
                // the game may have ended while this was being worked out
                if (move != Moves.NONE) {
                    pSession.play(move);
                }
                thinking = false;
                if (pAfterMove != null) {
                    pAfterMove.run();
                }
            });
        };

        // a daemon thread, so a window closed while the program is thinking still lets it exit
        Thread thread = new Thread(task, "engine player");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    /**
     * Returns the settings this player was built with.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the settings, never null
     */
    public EngineSettings getSettings() {
        return settings;
    }

    /**
     * Tells whether a move is being thought about right now.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true while the program is thinking
     */
    public boolean isThinking() {
        return thinking;
    }
}
