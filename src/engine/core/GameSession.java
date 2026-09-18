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

    /** Asked whether the opponent accepts a draw that one player has offered. */
    public interface DrawOfferArbiter {

        /**
         * Answers whether the offered draw is accepted.
         *
         * @param pWhiteOffers true when White is the player offering the draw
         * @return true if the opponent agrees to a draw
         */
        boolean acceptsDrawOffer(boolean pWhiteOffers);
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

    private GameResult result = GameResult.ONGOING;
    private Termination termination;

    // each player is offered the fifty move claim once per stretch without captures or pawn moves
    private boolean whiteWasOfferedFiftyMoveDraw;
    private boolean blackWasOfferedFiftyMoveDraw;

    private View view = NO_VIEW;
    private MoveLog moveLogView;
    private PromotionPicker promotionPicker;
    private DrawArbiter drawArbiter = NO_ARBITER;
    private DrawOfferArbiter drawOfferArbiter = NO_DRAW_OFFER;
    private EndListener endListener;
    // run after every change to the game, so the actions beside the board can follow it
    private Runnable stateListener = () -> {
    };

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

    // nobody to ask means nobody agreed, which leaves the game running rather than drawing it
    private static final DrawOfferArbiter NO_DRAW_OFFER = pWhiteOffers -> false;

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
        view.repaint();

        checkForEnd(repetitions);
        // whatever this move changed, the actions beside the board may have to look different now
        notifyStateChanged();
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
     * Tells whether the position on the board has come back for the third time.
     * <p>
     * A threefold repetition is a draw a player may claim rather than one that happens on its own,
     * and until now the only moment anybody was asked was the instant the third occurrence appeared.
     * A player who wants to claim a move later has to be able to, so the right to claim has to be a
     * question that can be asked at any time rather than an event that passes.
     * <p>
     * Time complexity: O(1), one lookup by position key. Space complexity: O(1).
     *
     * @return true if the current position has occurred three times or more in a running game
     */
    public boolean isRepetitionClaimable() {
        return !result.isFinished() && positionCounts.getOrDefault(position.key(), 0) >= 3;
    }

    /**
     * Tells whether fifty moves have passed without a capture or a pawn move.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true if the fifty move rule may be claimed in a running game
     */
    public boolean isFiftyMoveClaimable() {
        return !result.isFinished() && position.halfmoveClock() >= FIFTY_MOVE_PLIES;
    }

    /**
     * Tells whether a draw may be claimed right now.
     * <p>
     * The claim button is only worth pressing while one of the two claimable rules applies, so the
     * screen asks this to decide whether the action is live at all.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true if either the repetition or the fifty move rule is claimable
     */
    public boolean isDrawClaimable() {
        return isRepetitionClaimable() || isFiftyMoveClaimable();
    }

    /**
     * Claims the draw the rules allow at this moment.
     * <p>
     * Both claimable rules end the game as a draw, but they are different reasons and a saved game
     * has to say which one it was. A repeated position is named first when both apply, because it is
     * the more specific of the two. A claim nobody is entitled to changes nothing at all, so a stray
     * click cannot end a game.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true if the game was drawn by this claim, false when there was nothing to claim
     */
    public boolean claimDraw() {
        if (isRepetitionClaimable()) {
            end(GameResult.DRAW, Termination.THREEFOLD_REPETITION);
            return true;
        }
        if (isFiftyMoveClaimable()) {
            end(GameResult.DRAW, Termination.FIFTY_MOVE_RULE);
            return true;
        }
        return false;
    }

    /**
     * Offers the opponent a draw and ends the game when they accept.
     * <p>
     * Agreeing to a draw is how most games between players of similar strength actually end, and it
     * was the one ending this game could not produce. The player to move is the one offering, which
     * is the moment a draw is normally offered, so the other player is the one who answers through
     * the arbiter the screen installed. A session nobody can ask declines, which leaves the game
     * exactly as it was.
     * <p>
     * Time complexity: O(1) apart from waiting for the opponent. Space complexity: O(1).
     *
     * @return true if the opponent accepted and the game is now drawn
     */
    public boolean offerDraw() {
        // a finished game cannot be drawn again
        if (result.isFinished()) {
            return false;
        }
        // the player to move offers, so the player who is not to move decides
        boolean whiteOffers = position.sideToMove() == Pieces.WHITE;
        if (!drawOfferArbiter.acceptsDrawOffer(whiteOffers)) {
            return false;
        }
        end(GameResult.DRAW, Termination.DRAW_AGREED);
        return true;
    }

    /**
     * Sets who answers a draw one player offers the other.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pArbiter asked on every draw offer, or null to decline every one of them
     */
    public void setDrawOfferArbiter(DrawOfferArbiter pArbiter) {
        this.drawOfferArbiter = pArbiter == null ? NO_DRAW_OFFER : pArbiter;
    }

    /**
     * Sets who is told that something about the game changed.
     * <p>
     * The actions beside the board are only worth pressing at certain moments, and only the session
     * knows when those are. Rather than having the screen ask after every click it might have
     * missed, the session says when something changed and the screen looks again.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pListener run after every change to the game, or null for nobody
     */
    public void setStateListener(Runnable pListener) {
        this.stateListener = pListener == null ? () -> {
        } : pListener;
    }

    /**
     * Tells the listener that something about the game changed.
     * <p>
     * Time complexity: O(1) beyond whatever the listener does. Space complexity: O(1).
     */
    private void notifyStateChanged() {
        stateListener.run();
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
        moveLog.clear();
        fenHistory.clear();
        positionCounts.clear();
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
        notifyStateChanged();
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
        // every action beside the board is dead once the game is over
        notifyStateChanged();
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
