package engine.core;

/*
 * Purpose: GameSession is the referee of a running game on the new core. It owns the position, the
 * written record of the game, how often each position has occurred and the state machine that says
 * whether the game is still running and, if not, why it ended. Everything it needs from the outside,
 * the clocks, the move log, a promotion choice and an answer to a draw claim, arrives through small
 * interfaces, so the session never touches a window and can be played through in a test. It replaces
 * a controller that judged moves by mutating live pieces and asked its dialogs in the middle of that.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GameSession {

    /** What the session needs from the screen: the clocks and a repaint. */
    public interface View {
        void switchClocks(boolean pWhiteToMove);

        void stopClocks();

        void resetClocks();

        void repaint();

        /**
         * Remembers what both clocks show after the move that was just played.
         * <p>
         * A screen without clocks has nothing to remember, so this does nothing unless a view says
         * otherwise.
         *
         * @param pPly how many moves have been played, 1 after the first move
         */
        default void recordClocks(int pPly) {
        }

        /**
         * Puts both clocks back to what they showed at a ply and hands them to the player to move.
         * <p>
         * This is what a taken back move uses instead of switching the clocks, because switching
         * them would pay out the increment of a move that is no longer played.
         *
         * @param pPly         how many moves are played now, 0 at the starting position
         * @param pWhiteToMove true if White is to move at that ply
         */
        default void restoreClocks(int pPly, boolean pWhiteToMove) {
        }
    }

    /** Where the written record of the game is shown. */
    public interface MoveLog {
        void update(List<String> pMoveLog, String pCurrentFen);

        void clear();
    }

    /** Asked which piece a promoting pawn becomes. */
    public interface PromotionPicker {
        int pick(boolean pWhite);
    }

    /** Asked about draws a player may claim, and told about the ones nobody can refuse. */
    public interface DrawArbiter {
        boolean offerFiftyMoveDraw();

        boolean offerRepetitionDraw();

        void notifyForcedDraw();
    }

    /** Told once when the game ends, with the result and the reason. */
    public interface EndListener {
        void onGameEnd(GameResult pResult, Termination pTermination);
    }

    // half moves without a capture or a pawn move that let a player claim a draw
    private static final int FIFTY_MOVE_PLIES = 100;
    // half moves after which the draw happens whether anybody claims it or not
    private static final int SEVENTY_FIVE_MOVE_PLIES = 150;

    private Position position;
    private final ArrayList<String> moveLog = new ArrayList<>();
    private final ArrayList<String> fenHistory = new ArrayList<>();
    // how often each position occurred since the last capture or pawn move, by Zobrist key
    private final Map<Long, Integer> positionCounts = new HashMap<>();

    // every move as it was played, packed, because taking a move back needs the move itself
    private final ArrayList<Integer> playedMoves = new ArrayList<>();
    // whether the move at that ply made every earlier position unreachable
    private final ArrayList<Boolean> irreversibleMoves = new ArrayList<>();
    // the key of the position after each ply, which is what the repetition counts are rebuilt from
    private final ArrayList<Long> positionKeys = new ArrayList<>();
    // moves that were taken back and can be played again, the next one to redo last
    private final ArrayList<Integer> redoMoves = new ArrayList<>();
    // the key of the position this session started from, before any move was played
    private long startKey;

    private GameResult result = GameResult.ONGOING;
    private Termination termination;

    // each player is offered the fifty move claim once per stretch without captures or pawn moves
    private boolean whiteWasOfferedFiftyMoveDraw;
    private boolean blackWasOfferedFiftyMoveDraw;

    private View view = NO_VIEW;
    private MoveLog moveLogView;
    private PromotionPicker promotionPicker;
    private DrawArbiter drawArbiter = NO_ARBITER;
    private EndListener endListener;

    // reused for every move generation, so playing a game allocates nothing per move
    private final int[] moveBuffer = new int[MoveGen.MAX_MOVES];

    // a session without a screen still has to run, for tests and for a headless engine
    private static final View NO_VIEW = new View() {
        @Override
        public void switchClocks(boolean pWhiteToMove) {
        }

        @Override
        public void stopClocks() {
        }

        @Override
        public void resetClocks() {
        }

        @Override
        public void repaint() {
        }
    };

    // a session nobody can answer never agrees to a claimable draw
    private static final DrawArbiter NO_ARBITER = new DrawArbiter() {
        @Override
        public boolean offerFiftyMoveDraw() {
            return false;
        }

        @Override
        public boolean offerRepetitionDraw() {
            return false;
        }

        @Override
        public void notifyForcedDraw() {
        }
    };

    /**
     * Starts a session on the standard starting position.
     * <p>
     * Time complexity: O(p) for the 32 pieces. Space complexity: O(1) beyond the position.
     */
    public GameSession() {
        this(Position.startPosition());
    }

    /**
     * Starts a session on a given position.
     * <p>
     * A game can begin from a position that was set up or loaded rather than played, which is what
     * the replay viewer and later a position editor need. I take the position as it is and count it
     * as the first occurrence, so a repetition is judged from the start of this session.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) beyond the position.
     *
     * @param pPosition position the game starts from, never null
     */
    public GameSession(Position pPosition) {
        this.position = pPosition;
        // the position the game starts from is where rebuilding the repetition counts stops
        this.startKey = pPosition.key();
        countCurrentPosition();
    }

    /**
     * Plays a move and applies every rule that can end the game.
     * <p>
     * This is the one place a game moves forward. A finished game and a move that is not legal right
     * now are both refused, so nothing can change a final position. Otherwise I write the move down
     * in algebraic notation while the position still shows where it came from, play it, record the
     * new position as FEN, count the repetition, hand the clock to the other player and repaint. Only
     * then do I look for an end of the game, which means every dialog a draw claim needs is opened
     * after the position is already committed, instead of in the middle of the move like before.
     * <p>
     * Time complexity: O(m) for the m legal moves, generated to check the move and to see whether
     * the opponent still has a reply. Space complexity: O(1) per move beyond the growing record.
     *
     * @param pMove a legal move of this position, packed by the Moves class
     * @return true if the move was played, false if it was refused
     */
    public boolean play(int pMove) {
        // a finished game keeps its final position
        if (result.isFinished() || !isLegal(pMove)) {
            return false;
        }

        int from = Moves.from(pMove);
        int to = Moves.to(pMove);
        // a capture or a pawn move makes every earlier position unreachable
        boolean irreversible = Pieces.typeOf(position.pieceAt(from)) == Pieces.PAWN
                || position.pieceAt(to) != Pieces.NONE
                || Moves.isEnPassant(pMove);

        // the notation has to be written while the position still shows where the move came from
        String notation = San.of(position, pMove);
        position.makeMove(pMove);

        // the move itself is what taking it back needs, the notation cannot be unplayed
        playedMoves.add(pMove);
        irreversibleMoves.add(irreversible);
        positionKeys.add(position.key());
        // playing on abandons whatever was taken back before, there is only one line of play
        redoMoves.clear();

        moveLog.add(notation);
        fenHistory.add(Fen.write(position));
        if (moveLogView != null) {
            moveLogView.update(getMoveLog(), fenHistory.get(fenHistory.size() - 1));
        }

        if (irreversible) {
            positionCounts.clear();
            // a new stretch towards the fifty move rule begins as well
            whiteWasOfferedFiftyMoveDraw = false;
            blackWasOfferedFiftyMoveDraw = false;
        }
        int repetitions = countCurrentPosition();

        // the clock goes over first, so a draw claim below runs on the claiming player's time
        view.switchClocks(position.sideToMove() == Pieces.WHITE);
        // the times as they stand after this move, so taking it back can put them back
        view.recordClocks(playedMoves.size());
        view.repaint();

        checkForEnd(repetitions);
        return true;
    }

    /**
     * Finds the legal move that goes from one square to another.
     * <p>
     * The board knows which square a player picked a piece up on and where they let it go, not which
     * of the packed moves that is. I look for a legal move between those two squares, and when there
     * are several, which only happens for a promotion, I ask the promotion picker which piece the
     * pawn becomes. Without a picker a promotion defaults to a queen, which is what almost every
     * promotion is.
     * <p>
     * Time complexity: O(m) for the m legal moves of the position. Space complexity: O(1).
     *
     * @param pFrom square the piece started on, 0 to 63
     * @param pTo   square the piece was let go on, 0 to 63
     * @return the packed move, or Moves.NONE when no legal move connects the two squares
     */
    public int moveFor(int pFrom, int pTo) {
        int count = MoveGen.generateLegal(position, moveBuffer, 0);
        int found = Moves.NONE;
        boolean promotion = false;

        for (int index = 0; index < count; index++) {
            int move = moveBuffer[index];
            if (Moves.from(move) == pFrom && Moves.to(move) == pTo) {
                found = move;
                promotion |= Moves.isPromotion(move);
            }
        }
        // a promotion is four moves between the same two squares, so the player has to choose
        if (promotion) {
            int wanted = promotionPicker != null
                    ? promotionPicker.pick(position.sideToMove() == Pieces.WHITE)
                    : Pieces.QUEEN;
            for (int index = 0; index < count; index++) {
                int move = moveBuffer[index];
                if (Moves.from(move) == pFrom && Moves.to(move) == pTo
                        && Moves.promotionType(move) == wanted) {
                    return move;
                }
            }
        }
        return found;
    }

    /**
     * Tells whether a move may be played right now.
     * <p>
     * The board asks this before it plays a move and to work out which squares to highlight. A
     * finished game has no legal moves at all.
     * <p>
     * Time complexity: O(m) for the m legal moves. Space complexity: O(1).
     *
     * @param pMove packed move to check
     * @return true if the move is legal in this position and the game is still running
     */
    public boolean isLegal(int pMove) {
        if (result.isFinished() || pMove == Moves.NONE) {
            return false;
        }
        int count = MoveGen.generateLegal(position, moveBuffer, 0);
        for (int index = 0; index < count; index++) {
            if (moveBuffer[index] == pMove) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects the squares a piece may move to.
     * <p>
     * The board highlights where a picked up piece can go. It used to ask the rules once for every
     * one of the 64 squares, which ran the whole legality machinery 64 times; now it asks for the
     * moves of that piece once.
     * <p>
     * Time complexity: O(m) for the m legal moves of the position. Space complexity: O(1), the
     * caller owns the array.
     *
     * @param pFrom    square the piece stands on, 0 to 63
     * @param pTargets array that receives the target squares, at least MoveGen.MAX_MOVES long
     * @return how many target squares were written
     */
    public int targetsFrom(int pFrom, int[] pTargets) {
        if (result.isFinished()) {
            return 0;
        }
        int count = MoveGen.generateLegal(position, moveBuffer, 0);
        int written = 0;
        for (int index = 0; index < count; index++) {
            if (Moves.from(moveBuffer[index]) == pFrom) {
                int target = Moves.to(moveBuffer[index]);
                // the four promotions of one pawn all end on the same square
                boolean known = false;
                for (int seen = 0; seen < written; seen++) {
                    known |= pTargets[seen] == target;
                }
                if (!known) {
                    pTargets[written++] = target;
                }
            }
        }
        return written;
    }

    /**
     * Ends the game because a player's clock ran out.
     * <p>
     * Normally the other player wins, but FIDE rule 6.9 makes it a draw when that player could never
     * deliver mate with the material left, so a lone king does not win on time. I check that first
     * and otherwise give the win to the side that still has time.
     * <p>
     * Time complexity: O(1), a few bitboard counts. Space complexity: O(1).
     *
     * @param pColour the side whose clock ran out, Pieces.WHITE or Pieces.BLACK
     */
    public void flagFall(int pColour) {
        int opponent = 1 - pColour;
        // a side that cannot mate does not win on time
        if (hasOnlyKing(opponent) || isInsufficientMaterial()) {
            end(GameResult.DRAW, Termination.TIME_OUT_WITHOUT_MATING_MATERIAL);
            return;
        }
        end(GameResult.wonBy(opponent), Termination.TIME_OUT);
    }

    /**
     * Ends the game because a player gave up.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pColour the side that resigns, Pieces.WHITE or Pieces.BLACK
     */
    public void resign(int pColour) {
        end(GameResult.wonBy(1 - pColour), Termination.RESIGNATION);
    }

    /**
     * Starts a new game from the standard starting position.
     * <p>
     * A restart has to clear every piece of state, including the result, otherwise the new game
     * would refuse every move. I replace the position, empty the record and the repetition counts,
     * let both players be offered the fifty move claim again, reset the clocks and clear the log.
     * <p>
     * Time complexity: O(p + m) for the pieces placed and the moves cleared. Space complexity: O(p).
     */
    public void restart() {
        position = Position.startPosition();
        startKey = position.key();
        moveLog.clear();
        fenHistory.clear();
        positionCounts.clear();
        playedMoves.clear();
        irreversibleMoves.clear();
        positionKeys.clear();
        redoMoves.clear();
        result = GameResult.ONGOING;
        termination = null;
        whiteWasOfferedFiftyMoveDraw = false;
        blackWasOfferedFiftyMoveDraw = false;
        countCurrentPosition();

        view.resetClocks();
        view.repaint();
        if (moveLogView != null) {
            moveLogView.clear();
        }
    }

    /**
     * Takes back the move that was played last.
     * <p>
     * Players want a move back, whether they mis-clicked or want to try something else, and a game
     * that ended by a mistake should be playable again. I unplay the move on the position itself,
     * which restores the castling rights, the en passant square, the fifty move counter and the
     * position key exactly rather than recomputing them, drop the last entry from the written record
     * and put the move on the redo branch. A game that was already finished goes back to running,
     * because the move that ended it is gone, and both players may be asked about the fifty move
     * claim again. The repetition counts are rebuilt, since a count cannot simply be decremented
     * once an irreversible move has cleared it.
     * <p>
     * Time complexity: O(p) for the p plies since the last capture or pawn move, which is what the
     * repetition counts are rebuilt from. Space complexity: O(1).
     *
     * @return true if a move was taken back, false when the game is at its starting position
     */
    public boolean undo() {
        // there is nothing to take back before the first move
        if (playedMoves.isEmpty()) {
            return false;
        }

        int lastMove = playedMoves.remove(playedMoves.size() - 1);
        irreversibleMoves.remove(irreversibleMoves.size() - 1);
        positionKeys.remove(positionKeys.size() - 1);
        redoMoves.add(lastMove);

        // the position restores itself from what it saved when the move was made
        position.unmakeMove(lastMove);
        moveLog.remove(moveLog.size() - 1);
        fenHistory.remove(fenHistory.size() - 1);

        // the move that ended the game has been taken back, so the game runs again
        result = GameResult.ONGOING;
        termination = null;
        whiteWasOfferedFiftyMoveDraw = false;
        blackWasOfferedFiftyMoveDraw = false;

        rebuildPositionCounts();
        refreshAfterCursorMove();
        return true;
    }

    /**
     * Plays a move that was taken back again.
     * <p>
     * Undo and redo belong together, so a player can step back and forth through the game. I replay
     * the move through the normal path, so everything a move records is recorded again. That path
     * abandons the redo branch, which is exactly right for a new move and wrong for this one, so I
     * put the rest of the branch back afterwards.
     * <p>
     * Time complexity: O(m) for the m legal moves, as for any played move.
     * Space complexity: O(r) for the r moves of the redo branch that are kept.
     *
     * @return true if a move was played again, false when nothing was taken back
     */
    public boolean redo() {
        // nothing was taken back, so there is nothing to play again
        if (redoMoves.isEmpty()) {
            return false;
        }

        int move = redoMoves.get(redoMoves.size() - 1);
        // play abandons the branch, so the rest of it is kept here and put back afterwards
        List<Integer> remaining = new ArrayList<>(redoMoves.subList(0, redoMoves.size() - 1));
        if (!play(move)) {
            return false;
        }
        redoMoves.clear();
        redoMoves.addAll(remaining);
        return true;
    }

    /**
     * Tells whether there is a move to take back.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true if at least one move has been played in this session
     */
    public boolean canUndo() {
        return !playedMoves.isEmpty();
    }

    /**
     * Tells whether there is a move to play again.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true if a move was taken back and no new move was played since
     */
    public boolean canRedo() {
        return !redoMoves.isEmpty();
    }

    /**
     * Counts the positions of the current stretch again from the start.
     * <p>
     * A repetition count cannot be decremented on the way back, because an irreversible move clears
     * the whole map, and undoing that move has to bring the earlier counts back. Everything before
     * the last capture or pawn move can never occur again, so I walk back to it and count the
     * position it left behind together with every position since.
     * <p>
     * Time complexity: O(p) for the p plies of the current stretch. Space complexity: O(p) for the
     * counts of those positions.
     */
    private void rebuildPositionCounts() {
        positionCounts.clear();

        // everything before the last irreversible move is unreachable and does not count
        int stretchStart = 0;
        for (int ply = irreversibleMoves.size() - 1; ply >= 0; ply--) {
            if (irreversibleMoves.get(ply)) {
                stretchStart = ply + 1;
                break;
            }
        }

        // the position the stretch began in counts as an occurrence of its own
        long beforeStretch = stretchStart == 0 ? startKey : positionKeys.get(stretchStart - 1);
        positionCounts.merge(beforeStretch, 1, Integer::sum);
        for (int ply = stretchStart; ply < positionKeys.size(); ply++) {
            positionCounts.merge(positionKeys.get(ply), 1, Integer::sum);
        }
    }

    /**
     * Hands the clock over and refreshes the screen after the game moved back or forward.
     * <p>
     * Stepping through the game changes whose turn it is and what the record says, and the screen
     * has to follow both. I hand the clock to whoever is to move now, show the record as it stands
     * and ask for a repaint.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    private void refreshAfterCursorMove() {
        // the clocks go back to what they showed at this ply, which also hands them to the right
        // player. Switching them instead would pay out the increment of a move nobody plays now.
        view.restoreClocks(playedMoves.size(), position.sideToMove() == Pieces.WHITE);
        view.repaint();
        if (moveLogView == null) {
            return;
        }
        // a game back at its start has an empty record rather than a last position
        if (moveLog.isEmpty()) {
            moveLogView.clear();
        } else {
            moveLogView.update(getMoveLog(), fenHistory.get(fenHistory.size() - 1));
        }
    }

    /**
     * Looks for every reason the game could be over after the move that was just played.
     * <p>
     * The order matters, because several reasons can apply at once and the strongest one wins. No
     * legal reply while in check is mate and no legal reply without check is stalemate. Then come the
     * draws nobody can refuse, a position that can never be mated in, the fifth occurrence of a
     * position and seventy five moves without a capture or a pawn move. Only then the draws a player
     * may claim, a third occurrence and the fifty move rule, and the fifty move claim is offered to
     * each player once per stretch rather than after every move.
     * <p>
     * Time complexity: O(m) for the m legal replies. Space complexity: O(1).
     *
     * @param pRepetitions how often the current position has occurred, 1 or more
     */
    private void checkForEnd(int pRepetitions) {
        int sideToMove = position.sideToMove();
        boolean inCheck = MoveGen.isInCheck(position, sideToMove);
        boolean hasReply = MoveGen.generateLegal(position, moveBuffer, 0) > 0;

        if (!hasReply) {
            // the player who just moved is the one who mated
            end(inCheck ? GameResult.wonBy(1 - sideToMove) : GameResult.DRAW,
                    inCheck ? Termination.CHECKMATE : Termination.STALEMATE);
            return;
        }
        if (isInsufficientMaterial()) {
            end(GameResult.DRAW, Termination.INSUFFICIENT_MATERIAL);
            return;
        }
        if (pRepetitions >= 5) {
            end(GameResult.DRAW, Termination.FIVEFOLD_REPETITION);
            return;
        }
        if (position.halfmoveClock() >= SEVENTY_FIVE_MOVE_PLIES) {
            drawArbiter.notifyForcedDraw();
            end(GameResult.DRAW, Termination.SEVENTY_FIVE_MOVE_RULE);
            return;
        }
        if (pRepetitions >= 3 && drawArbiter.offerRepetitionDraw()) {
            end(GameResult.DRAW, Termination.THREEFOLD_REPETITION);
            return;
        }

        boolean alreadyOffered = sideToMove == Pieces.WHITE
                ? whiteWasOfferedFiftyMoveDraw : blackWasOfferedFiftyMoveDraw;
        if (position.halfmoveClock() >= FIFTY_MOVE_PLIES && !alreadyOffered) {
            // a player who declined is not asked again until a pawn moves or something is captured
            if (sideToMove == Pieces.WHITE) {
                whiteWasOfferedFiftyMoveDraw = true;
            } else {
                blackWasOfferedFiftyMoveDraw = true;
            }
            if (drawArbiter.offerFiftyMoveDraw()) {
                end(GameResult.DRAW, Termination.FIFTY_MOVE_RULE);
            }
        }
    }

    /**
     * Finishes the game with a result and a reason, once and only once.
     * <p>
     * A game can only end one way, even when a flag falls right after a mate, so the first result is
     * the one that counts and every later call is ignored. I stop the clocks and tell the listener,
     * which is where the user interface saves the game and shows the end screen.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pResult      how the game ended, never ONGOING
     * @param pTermination why it ended, never null
     */
    private void end(GameResult pResult, Termination pTermination) {
        // the first result is final
        if (result.isFinished()) {
            return;
        }
        result = pResult;
        termination = pTermination;
        // a finished game has no running clock
        view.stopClocks();
        if (endListener != null) {
            endListener.onGameEnd(result, termination);
        }
    }

    /**
     * Counts one more occurrence of the current position.
     * <p>
     * Two positions repeat when the pieces, the side to move, the castling rights and a usable en
     * passant square all match, which is exactly what the Zobrist key covers, so the key is the
     * counter's key. The old engine built a FEN string for this on every move.
     * <p>
     * Time complexity: O(1) amortized. Space complexity: O(1) amortized per new position.
     *
     * @return how often the current position has occurred, 1 or more
     */
    private int countCurrentPosition() {
        return positionCounts.merge(position.key(), 1, Integer::sum);
    }

    /**
     * Tells whether neither side could ever deliver mate with the material left.
     * <p>
     * With bare kings, a king and one knight, or kings and bishops that all stand on squares of one
     * colour, no sequence of legal moves ends in mate, so FIDE rule 5.2.2 draws the game at once. A
     * pawn, rook or queen anywhere means mate is still possible.
     * <p>
     * Time complexity: O(1), a handful of bitboard counts. Space complexity: O(1).
     *
     * @return true if the remaining material can never produce a mate
     */
    private boolean isInsufficientMaterial() {
        // anything that can mate on its own settles the question
        if ((position.pieces(Pieces.WHITE_PAWN) | position.pieces(Pieces.BLACK_PAWN)
                | position.pieces(Pieces.WHITE_ROOK) | position.pieces(Pieces.BLACK_ROOK)
                | position.pieces(Pieces.WHITE_QUEEN) | position.pieces(Pieces.BLACK_QUEEN)) != 0L) {
            return false;
        }

        int knights = Bitboards.count(position.pieces(Pieces.WHITE_KNIGHT))
                + Bitboards.count(position.pieces(Pieces.BLACK_KNIGHT));
        long bishops = position.pieces(Pieces.WHITE_BISHOP) | position.pieces(Pieces.BLACK_BISHOP);

        boolean lightBishop = false;
        boolean darkBishop = false;
        long remaining = bishops;
        while (remaining != 0L) {
            int square = Bitboards.lowestSquare(remaining);
            remaining = Bitboards.clearLowestSquare(remaining);
            // a1 is a dark square, so file plus rank being even means dark
            if ((Bitboards.fileOf(square) + Bitboards.rankOf(square)) % 2 == 0) {
                darkBishop = true;
            } else {
                lightBishop = true;
            }
        }

        // bishops on one colour without knights, or a single knight without bishops
        return (knights == 0 && !(lightBishop && darkBishop))
                || (knights == 1 && !lightBishop && !darkBishop);
    }

    /**
     * Tells whether a side has nothing left but its king.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pColour side to look at, Pieces.WHITE or Pieces.BLACK
     * @return true if that side owns only its king
     */
    private boolean hasOnlyKing(int pColour) {
        return position.occupancy(pColour) == position.pieces(Pieces.make(pColour, Pieces.KING));
    }

    public Position position() {
        return position;
    }

    public GameResult result() {
        return result;
    }

    /**
     * Returns why the game ended.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the reason, or null while the game is still running
     */
    public Termination termination() {
        return termination;
    }

    public boolean isWhiteToMove() {
        return position.sideToMove() == Pieces.WHITE;
    }

    /**
     * Returns the moves played so far in algebraic notation.
     * <p>
     * Time complexity: O(1). Space complexity: O(1), the list is only wrapped.
     *
     * @return the move log, never null and not modifiable
     */
    public List<String> getMoveLog() {
        return Collections.unmodifiableList(moveLog);
    }

    /**
     * Returns the position after every move as FEN.
     * <p>
     * Time complexity: O(1). Space complexity: O(1), the list is only wrapped.
     *
     * @return one FEN per played move, never null and not modifiable
     */
    public List<String> getFenHistory() {
        return Collections.unmodifiableList(fenHistory);
    }

    /**
     * Sets where the clocks and the repaint requests go.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pView the screen showing this game, or null for a game without one
     */
    public void setView(View pView) {
        this.view = pView == null ? NO_VIEW : pView;
    }

    /**
     * Sets where the written record of the game is shown.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMoveLog the move log panel, or null for a game without one
     */
    public void setMoveLogView(MoveLog pMoveLog) {
        this.moveLogView = pMoveLog;
    }

    /**
     * Sets who decides what a promoting pawn becomes.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPicker asked on every promotion, or null to always promote to a queen
     */
    public void setPromotionPicker(PromotionPicker pPicker) {
        this.promotionPicker = pPicker;
    }

    /**
     * Sets who answers a draw claim.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pArbiter asked about claimable draws, or null to never agree to one
     */
    public void setDrawArbiter(DrawArbiter pArbiter) {
        this.drawArbiter = pArbiter == null ? NO_ARBITER : pArbiter;
    }

    /**
     * Sets who is told when the game ends.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pListener told once with the result and the reason, or null for nobody
     */
    public void setEndListener(EndListener pListener) {
        this.endListener = pListener;
    }
}
