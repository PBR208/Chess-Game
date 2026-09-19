package engine.search;

/*
 * Purpose: Searcher picks a move by looking ahead. It walks the tree of moves with negamax and alpha
 * beta, which throws away lines as soon as they are proved worse than one already found, and it does
 * that to one depth after another so there is always a finished answer to fall back on when the time
 * runs out. Captures are followed past the nominal depth until the position is quiet, because
 * stopping in the middle of an exchange values a board that nobody would ever agree to stop at. Every
 * array it uses exists before the search starts, so the hot path allocates nothing and the recursion
 * is bounded by a fixed maximum depth rather than by how much stack happens to be left. A search can
 * also be told to judge the moves at the root a little carelessly, which is how a weaker level of
 * play is built out of the same search rather than out of a second, worse one.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.Fen;
import engine.core.MoveGen;
import engine.core.Moves;
import engine.core.Pieces;
import engine.core.Position;

public final class Searcher {

    // deepest the search may ever go, which is what bounds the recursion
    public static final int MAX_PLY = 128;

    // the score of being mated, counted down by the number of moves it takes so that a mate in one
    // beats a mate in three rather than being judged the same
    public static final int MATE_SCORE = 30_000;

    // any score past this is a forced mate rather than an ordinary advantage
    public static final int MATE_BOUND = MATE_SCORE - MAX_PLY;

    // wider than any real score, so it can stand for "nothing found yet" on both sides
    private static final int INFINITY = 32_000;

    // how much shallower the null move search looks than the real one
    private static final int NULL_MOVE_REDUCTION = 2;

    // below this depth a null move saves less than it risks
    private static final int NULL_MOVE_MIN_DEPTH = 3;

    // asking the clock on every node would cost more than the search saves
    private static final int NODES_BETWEEN_TIME_CHECKS = 2048;

    // rough worth of each piece type, used only to order moves, never to judge a position
    private static final int[] ORDER_VALUE = {100, 320, 330, 500, 900, 20_000};

    // a capture is tried before any quiet move, however good the quiet move looks
    private static final int CAPTURE_BONUS = 1_000_000;
    private static final int FIRST_KILLER_BONUS = 900_000;
    private static final int SECOND_KILLER_BONUS = 800_000;

    // the stack a search thread gets, far more than MAX_PLY frames can use
    private static final long SEARCH_STACK_BYTES = 16L << 20;

    // one move list and one score list per depth, so a level never overwrites the one above it
    private final int[][] moveLists = new int[MAX_PLY][MoveGen.MAX_MOVES];
    private final int[][] moveScores = new int[MAX_PLY][MoveGen.MAX_MOVES];

    // the best line found, one row per depth, each holding the moves from that depth downwards
    private final int[][] principalVariation = new int[MAX_PLY][MAX_PLY];
    private final int[] variationLength = new int[MAX_PLY];

    // two quiet moves per depth that caused a cutoff last time, tried early because they often do
    private final int[][] killers = new int[MAX_PLY][2];

    // how often a quiet move from one square to another has caused a cutoff, by side
    private final int[][][] history = new int[2][64][64];

    private long nodes;
    private long deadline;
    private long nodeLimit;

    // Read by the search and written by whoever calls it off, which are different threads, so this
    // has to be volatile: without it the search is free to keep reading a cached false for ever.
    private volatile boolean stopped;

    // how far a root move's score may be out, and the seed that decides which way for each move
    private int noiseCentipawns;
    private final long noiseSeed;

    /**
     * Builds a searcher whose careless judgements differ from one game to the next.
     * <p>
     * Two games against the same level should not follow the same moves, so the seed comes from the
     * clock. With no noise asked for this changes nothing at all and the search stays exact.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) beyond the preallocated arrays.
     */
    public Searcher() {
        this(System.nanoTime());
    }

    /**
     * Builds a searcher whose careless judgements are decided by a given seed.
     * <p>
     * A test cannot check a search that wanders differently on every run, so the seed can be fixed,
     * which makes the whole search reproducible even with noise switched on.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) beyond the preallocated arrays.
     *
     * @param pNoiseSeed the seed deciding which way each root move's score is put out
     */
    public Searcher(long pNoiseSeed) {
        this.noiseSeed = pNoiseSeed;
    }

    /**
     * Purpose: Limits says when a search has to stop. A search can be bounded by how deep it looks,
     * by how many positions it visits or by how long it takes, and a level of play is exactly a
     * choice of those three. Anything left out is unbounded, so a depth on its own is a search that
     * takes as long as it takes.
     *
     * Owner: PBR208 - https://github.com/PBR208/
     * Version: 1.0
     */
    public static final class Limits {

        /** deepest iteration to finish, at least 1 and at most MAX_PLY - 1 */
        public final int depth;

        /** how many positions may be visited before the search gives up its current iteration */
        public final long maxNodes;

        /** how long the search may take in milliseconds */
        public final long maxTimeMs;

        /** how carelessly the moves at the root are judged, in hundredths of a pawn; 0 plays best */
        public final int noiseCentipawns;

        /**
         * Builds a set of limits that always plays the best move it finds.
         * <p>
         * Time complexity: O(1). Space complexity: O(1).
         *
         * @param pDepth     deepest iteration to finish, at least 1
         * @param pMaxNodes  how many positions may be visited, Long.MAX_VALUE for no limit
         * @param pMaxTimeMs how long the search may take, Long.MAX_VALUE for no limit
         * @throws IllegalArgumentException if the depth is below 1 or not below MAX_PLY
         */
        public Limits(int pDepth, long pMaxNodes, long pMaxTimeMs) {
            this(pDepth, pMaxNodes, pMaxTimeMs, 0);
        }

        /**
         * Builds a set of limits that may judge the moves at the root carelessly.
         * <p>
         * A weaker opponent should play worse moves, not think less clearly, and the difference
         * matters: a search cut short still plays the best move it found and simply misses deep
         * ideas, which feels like an engine being slow rather than like an opponent one can beat.
         * Judging each root move a little wrongly lets a plainly good move still win while a close
         * decision can go either way, which is how a human plays badly.
         * <p>
         * Time complexity: O(1). Space complexity: O(1).
         *
         * @param pDepth            deepest iteration to finish, at least 1
         * @param pMaxNodes         how many positions may be visited, Long.MAX_VALUE for no limit
         * @param pMaxTimeMs        how long the search may take, Long.MAX_VALUE for no limit
         * @param pNoiseCentipawns  how far a root move's score may be out, 0 to play the best move
         * @throws IllegalArgumentException if the depth is below 1 or not below MAX_PLY, or the
         *                                  noise is negative
         */
        public Limits(int pDepth, long pMaxNodes, long pMaxTimeMs, int pNoiseCentipawns) {
            // a depth past the preallocated rows would reach past the end of them
            if (pDepth < 1 || pDepth >= MAX_PLY) {
                throw new IllegalArgumentException("depth " + pDepth + " must be between 1 and " + (MAX_PLY - 1));
            }
            if (pNoiseCentipawns < 0) {
                throw new IllegalArgumentException("noise " + pNoiseCentipawns + " must not be negative");
            }
            this.depth = pDepth;
            this.maxNodes = pMaxNodes;
            this.maxTimeMs = pMaxTimeMs;
            this.noiseCentipawns = pNoiseCentipawns;
        }

        /**
         * Builds limits that only bound the depth.
         * <p>
         * Time complexity: O(1). Space complexity: O(1).
         *
         * @param pDepth deepest iteration to finish, at least 1
         * @return the limits, never null
         */
        public static Limits toDepth(int pDepth) {
            return new Limits(pDepth, Long.MAX_VALUE, Long.MAX_VALUE);
        }
    }

    /**
     * Purpose: Result is what a finished search has to say: the move it would play, what it thinks
     * of the position, how deep it managed to look, how many positions that took and the line it
     * expects to follow. The line is worth keeping because it is the honest explanation of the
     * score, and a score without it cannot be checked by anybody reading the output.
     *
     * Owner: PBR208 - https://github.com/PBR208/
     * Version: 1.0
     */
    public static final class Result {

        /** the move to play, or Moves.NONE when the position has none */
        public final int bestMove;

        /** the score in hundredths of a pawn from the side to move's point of view */
        public final int score;

        /** the deepest iteration that finished */
        public final int depth;

        /** how many positions were visited */
        public final long nodes;

        /** the line the search expects, starting with the best move; never null */
        public final int[] line;

        /**
         * Builds a search result.
         * <p>
         * Time complexity: O(n) for the n moves of the line. Space complexity: O(n) for its copy.
         *
         * @param pBestMove the move to play
         * @param pScore    the score from the side to move's point of view
         * @param pDepth    the deepest finished iteration
         * @param pNodes    how many positions were visited
         * @param pLine     the expected line, never null
         */
        Result(int pBestMove, int pScore, int pDepth, long pNodes, int[] pLine) {
            this.bestMove = pBestMove;
            this.score = pScore;
            this.depth = pDepth;
            this.nodes = pNodes;
            this.line = pLine.clone();
        }

        /**
         * Writes the expected line the way the UCI protocol spells moves.
         * <p>
         * Time complexity: O(n) for the n moves. Space complexity: O(n) for the text.
         *
         * @return the line as space separated moves, empty when there is none; never null
         */
        public String lineText() {
            StringBuilder text = new StringBuilder();
            for (int move : line) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(Moves.toUci(move));
            }
            return text.toString();
        }

        /**
         * Tells whether this score is a forced mate rather than an ordinary advantage.
         * <p>
         * Time complexity: O(1). Space complexity: O(1).
         *
         * @return true if one side is being mated in the line
         */
        public boolean isMate() {
            return Math.abs(score) > MATE_BOUND;
        }
    }

    /**
     * Searches a position and returns the move it would play.
     * <p>
     * The search looks one move deeper at a time rather than going straight to the target depth,
     * because a search that is stopped halfway still has the finished answer from the depth before,
     * and because the best move from one depth makes the next depth far quicker by being tried first.
     * The position is left exactly as it was found, every move being taken back on the way out.
     * <p>
     * Time complexity: O(b^d) for a branching factor b and depth d in the worst case, far less in
     * practice because alpha beta cuts most of it away. Space complexity: O(1), every array exists
     * before the search starts.
     *
     * @param pPosition position to search, never null and unchanged when this returns
     * @param pLimits   when to stop, never null
     * @return what the search found, never null
     * @throws NullPointerException if either argument is null
     */
    public Result search(Position pPosition, Limits pLimits) {
        nodes = 0;
        stopped = false;
        noiseCentipawns = pLimits.noiseCentipawns;
        nodeLimit = pLimits.maxNodes;
        deadline = pLimits.maxTimeMs == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : System.nanoTime() + pLimits.maxTimeMs * 1_000_000L;

        for (int[] row : killers) {
            java.util.Arrays.fill(row, Moves.NONE);
        }
        for (int[][] side : history) {
            for (int[] row : side) {
                java.util.Arrays.fill(row, 0);
            }
        }

        int bestMove = Moves.NONE;
        int bestScore = 0;
        int reachedDepth = 0;
        int[] bestLine = new int[0];

        for (int depth = 1; depth <= pLimits.depth; depth++) {
            int score = negamax(pPosition, depth, -INFINITY, INFINITY, 0, true);
            // a search that ran out of time or nodes has only seen part of this depth
            if (stopped) {
                break;
            }
            bestScore = score;
            reachedDepth = depth;
            bestMove = variationLength[0] > 0 ? principalVariation[0][0] : Moves.NONE;
            bestLine = java.util.Arrays.copyOf(principalVariation[0], variationLength[0]);

            // a forced mate is the end of the matter, looking deeper cannot improve on it
            if (Math.abs(score) > MATE_BOUND) {
                break;
            }
        }

        return new Result(bestMove, bestScore, reachedDepth, nodes, bestLine);
    }

    /**
     * Searches a copy of a position on a thread of its own.
     * <p>
     * A search that ran on the thread drawing the board would freeze it, and one that ran on the
     * live position would change it under whoever else is reading it. So this takes a copy through
     * the FEN codec, which is the one honest way to duplicate a position here, and runs the search
     * on a thread with a stack large enough that the bounded recursion cannot overflow it whatever
     * the platform default happens to be.
     * <p>
     * Time complexity: the cost of the search itself. Space complexity: O(1) beyond the copy.
     *
     * @param pPosition position to search, never null and never touched by the search
     * @param pLimits   when to stop, never null
     * @return what the search found, never null
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public static Result searchOnThread(Position pPosition, Limits pLimits) throws InterruptedException {
        // the copy also starts with an empty undo stack, so a long game cannot crowd the search out
        Position copy = Fen.parse(Fen.write(pPosition));
        Result[] holder = new Result[1];

        Runnable task = () -> holder[0] = new Searcher().search(copy, pLimits);
        Thread thread = new Thread(null, task, "search", SEARCH_STACK_BYTES);
        thread.start();
        thread.join();
        return holder[0];
    }

    /**
     * Scores a position by looking ahead, keeping only what could still matter.
     * <p>
     * Negamax judges every position from the point of view of whoever is to move, so one piece of
     * code serves both sides and the score simply changes sign on the way back up. Alpha and beta are
     * the best either side has already been promised elsewhere in the tree: once a move proves at
     * least as good for the opponent as something they can already reach, the rest of that branch
     * cannot change the outcome and is abandoned. Being in check extends the search rather than
     * counting against the depth, because a forced sequence judged at its most violent moment is
     * judged wrongly.
     * <p>
     * Time complexity: O(b^d) worst case for branching factor b and depth d.
     * Space complexity: O(1), the per depth arrays are preallocated.
     *
     * @param pPosition  position to search, restored exactly before this returns
     * @param pDepth     how much further to look
     * @param pAlpha     the best the side to move is already assured of
     * @param pBeta      the best the other side is already assured of
     * @param pPly       how deep this call is, 0 at the root
     * @param pAllowNull whether a null move may be tried here
     * @return the score from the side to move's point of view
     */
    private int negamax(Position pPosition, int pDepth, int pAlpha, int pBeta, int pPly, boolean pAllowNull) {
        variationLength[pPly] = 0;

        // one row short of the end, so the line below can still be written down
        if (pPly >= MAX_PLY - 1) {
            return Evaluator.evaluate(pPosition);
        }
        if (outOfTime()) {
            return 0;
        }

        int us = pPosition.sideToMove();
        boolean inCheck = MoveGen.isInCheck(pPosition, us);
        // a check is a forced sequence, so it is looked at to the end rather than cut off midway
        int depth = inCheck ? pDepth + 1 : pDepth;

        if (depth <= 0) {
            return quiescence(pPosition, pAlpha, pBeta, pPly);
        }
        nodes++;

        // Giving the opponent a free move and still coming out ahead means this position is so good
        // that the real moves need not be looked at. It is wrong exactly when being forced to move
        // is the problem, which is why it is skipped without pieces and while in check.
        if (pAllowNull && !inCheck && depth >= NULL_MOVE_MIN_DEPTH && pBeta < MATE_BOUND
                && hasPieces(pPosition, us)) {
            int savedEpSquare = pPosition.epSquare();
            pPosition.setSideToMove(1 - us);
            pPosition.setEpSquare(Position.NO_EN_PASSANT);

            int score = -negamax(pPosition, depth - 1 - NULL_MOVE_REDUCTION, -pBeta, -pBeta + 1,
                    pPly + 1, false);

            // both setters only touch the key when the value really changes, so this restores it
            pPosition.setEpSquare(savedEpSquare);
            pPosition.setSideToMove(us);

            if (!stopped && score >= pBeta) {
                return pBeta;
            }
        }

        int[] moves = moveLists[pPly];
        int moveCount = MoveGen.generateLegal(pPosition, moves, 0);

        // no legal reply at all is either mate or stalemate, and mate counts the moves it took so
        // that a quicker mate is preferred to a slower one
        if (moveCount == 0) {
            return inCheck ? -MATE_SCORE + pPly : 0;
        }

        scoreMoves(pPosition, moves, moveCount, pPly);

        int alpha = pAlpha;
        int bestMove = Moves.NONE;
        for (int index = 0; index < moveCount; index++) {
            int move = pickMove(moves, moveScores[pPly], moveCount, index);
            pPosition.makeMove(move);

            int score;
            if (index == 0) {
                // the first move is searched in full, since it is the one most likely to be best
                score = -negamax(pPosition, depth - 1, -pBeta, -alpha, pPly + 1, true);
            } else {
                // the rest only have to be shown to be worse, which a one point window does quickly
                score = -negamax(pPosition, depth - 1, -alpha - 1, -alpha, pPly + 1, true);
                if (score > alpha && score < pBeta) {
                    score = -negamax(pPosition, depth - 1, -pBeta, -alpha, pPly + 1, true);
                }
            }

            pPosition.unmakeMove(move);

            if (stopped) {
                return 0;
            }
            // only the moves actually on offer are judged carelessly. Doing it deeper down would
            // make the same position worth different amounts in different branches, which is how a
            // search talks itself into nonsense rather than how a weak player chooses.
            if (pPly == 0 && noiseCentipawns > 0) {
                score += noiseFor(move);
            }
            if (score >= pBeta) {
                rememberCutoff(pPosition, move, depth, pPly, us);
                return pBeta;
            }
            if (score > alpha) {
                alpha = score;
                bestMove = move;
                recordLine(move, pPly);
            }
        }

        // a move that was never better than alpha leaves no line to follow
        if (bestMove == Moves.NONE) {
            variationLength[pPly] = 0;
        }
        return alpha;
    }

    /**
     * Follows captures until the position is quiet enough to judge.
     * <p>
     * Stopping a search in the middle of an exchange values a board nobody would ever agree to stop
     * at: a queen taken but not yet recaptured looks like winning a queen. So at the end of the
     * ordinary search the captures carry on being played until none are left. Standing pat, the
     * score of simply not capturing, is the floor, because a side is never forced to capture.
     * <p>
     * Time complexity: O(c^k) for c captures and the depth k they run to, which is short in practice.
     * Space complexity: O(1).
     *
     * @param pPosition position to search, restored exactly before this returns
     * @param pAlpha    the best the side to move is already assured of
     * @param pBeta     the best the other side is already assured of
     * @param pPly      how deep this call is
     * @return the score from the side to move's point of view
     */
    private int quiescence(Position pPosition, int pAlpha, int pBeta, int pPly) {
        nodes++;
        if (outOfTime() || pPly >= MAX_PLY - 1) {
            return Evaluator.evaluate(pPosition);
        }

        // nobody has to capture, so the score for standing still is the worst this can be
        int standPat = Evaluator.evaluate(pPosition);
        if (standPat >= pBeta) {
            return pBeta;
        }
        int alpha = Math.max(pAlpha, standPat);

        int[] moves = moveLists[pPly];
        int moveCount = MoveGen.generateLegal(pPosition, moves, 0);
        scoreMoves(pPosition, moves, moveCount, pPly);

        for (int index = 0; index < moveCount; index++) {
            int move = pickMove(moves, moveScores[pPly], moveCount, index);
            // only moves that change the material on the board are followed here
            if (!isCapture(pPosition, move) && !Moves.isPromotion(move)) {
                continue;
            }

            pPosition.makeMove(move);
            int score = -quiescence(pPosition, -pBeta, -alpha, pPly + 1);
            pPosition.unmakeMove(move);

            if (stopped) {
                return 0;
            }
            if (score >= pBeta) {
                return pBeta;
            }
            alpha = Math.max(alpha, score);
        }
        return alpha;
    }

    /**
     * Gives every move a number saying how promising it looks.
     * <p>
     * Alpha beta cuts away the most when the best move is tried first, so the order moves are tried
     * in matters more than almost anything else in the search. Captures come first, the most valuable
     * victim taken by the least valuable attacker ahead of the rest, because those are where material
     * changes hands. Then the two quiet moves that caused a cutoff at this depth before, and then
     * every other quiet move by how often it has caused one anywhere.
     * <p>
     * Time complexity: O(m) for the m moves. Space complexity: O(1), the score array is preallocated.
     *
     * @param pPosition position the moves belong to, never null
     * @param pMoves    the moves to score, never null
     * @param pCount    how many of them there are
     * @param pPly      how deep the search is, which killers belong to a depth
     */
    private void scoreMoves(Position pPosition, int[] pMoves, int pCount, int pPly) {
        int[] scores = moveScores[pPly];
        int us = pPosition.sideToMove();

        for (int index = 0; index < pCount; index++) {
            int move = pMoves[index];
            int from = Moves.from(move);
            int to = Moves.to(move);

            if (isCapture(pPosition, move)) {
                int victim = Moves.isEnPassant(move)
                        ? Pieces.PAWN
                        : Pieces.typeOf(pPosition.pieceAt(to));
                int attacker = Pieces.typeOf(pPosition.pieceAt(from));
                // the biggest gain taken by the smallest risk is the capture worth trying first
                scores[index] = CAPTURE_BONUS + ORDER_VALUE[victim] * 16 - ORDER_VALUE[attacker];
            } else if (move == killers[pPly][0]) {
                scores[index] = FIRST_KILLER_BONUS;
            } else if (move == killers[pPly][1]) {
                scores[index] = SECOND_KILLER_BONUS;
            } else {
                scores[index] = history[us][from][to];
            }
        }
    }

    /**
     * Brings the best of the remaining moves to the front and returns it.
     * <p>
     * Sorting the whole list would be wasted work, because a cutoff usually happens after the first
     * few moves and the rest are never looked at. Choosing the best one left each time costs nothing
     * when the list is abandoned early and no more than a sort when it is not.
     * <p>
     * Time complexity: O(m) per call for m moves. Space complexity: O(1).
     *
     * @param pMoves  the move list, reordered in place, never null
     * @param pScores their scores, reordered along with them, never null
     * @param pCount  how many moves the list holds
     * @param pIndex  how many have already been taken
     * @return the best move of those that are left
     */
    private int pickMove(int[] pMoves, int[] pScores, int pCount, int pIndex) {
        int best = pIndex;
        for (int index = pIndex + 1; index < pCount; index++) {
            if (pScores[index] > pScores[best]) {
                best = index;
            }
        }
        int move = pMoves[best];
        pMoves[best] = pMoves[pIndex];
        pMoves[pIndex] = move;
        int score = pScores[best];
        pScores[best] = pScores[pIndex];
        pScores[pIndex] = score;
        return move;
    }

    /**
     * Remembers a quiet move that proved good enough to end the search of its branch.
     * <p>
     * A move that refutes one line usually refutes its neighbours too, so it is worth trying early
     * next time. Captures are left out because they are already tried first on their own merits.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPosition position the move belongs to, never null
     * @param pMove     the move that caused the cutoff
     * @param pDepth    how deep the search was, deeper cutoffs count for more
     * @param pPly      how deep this call is
     * @param pColour   the side that played the move
     */
    private void rememberCutoff(Position pPosition, int pMove, int pDepth, int pPly, int pColour) {
        if (isCapture(pPosition, pMove)) {
            return;
        }
        if (killers[pPly][0] != pMove) {
            killers[pPly][1] = killers[pPly][0];
            killers[pPly][0] = pMove;
        }
        // a cutoff found deeper down was worth more work, so it counts for more
        history[pColour][Moves.from(pMove)][Moves.to(pMove)] += pDepth * pDepth;
    }

    /**
     * Writes down the line that follows a move that improved on everything before it.
     * <p>
     * The line at this depth is the move itself followed by whatever the depth below expects, which
     * is how the whole expected sequence is carried back up to the root one level at a time.
     * <p>
     * Time complexity: O(n) for the n moves of the line below. Space complexity: O(1).
     *
     * @param pMove the move that improved the score
     * @param pPly  how deep this call is
     */
    private void recordLine(int pMove, int pPly) {
        principalVariation[pPly][0] = pMove;
        int below = variationLength[pPly + 1];
        System.arraycopy(principalVariation[pPly + 1], 0, principalVariation[pPly], 1, below);
        variationLength[pPly] = below + 1;
    }

    /**
     * Tells whether a move takes something.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPosition position the move belongs to, never null
     * @param pMove     the move to look at
     * @return true if the move captures, en passant included
     */
    private static boolean isCapture(Position pPosition, int pMove) {
        // the pawn taken en passant does not stand on the square the capturing pawn moves to
        return pPosition.pieceAt(Moves.to(pMove)) != Pieces.NONE || Moves.isEnPassant(pMove);
    }

    /**
     * Tells whether a side still has something other than pawns and its king.
     * <p>
     * Passing the turn is only sound while a side has a move it can afford to make. With nothing but
     * pawns and a king, having to move is often the whole problem, and pretending a side could pass
     * would hide exactly that.
     * <p>
     * Time complexity: O(1), a few bitboard tests. Space complexity: O(1).
     *
     * @param pPosition position to look at, never null
     * @param pColour   side to look at
     * @return true if that side has at least one piece that is not a pawn or the king
     */
    private static boolean hasPieces(Position pPosition, int pColour) {
        long pawns = pPosition.pieces(Pieces.make(pColour, Pieces.PAWN));
        long king = pPosition.pieces(Pieces.make(pColour, Pieces.KING));
        return (pPosition.occupancy(pColour) & ~pawns & ~king) != 0L;
    }

    /**
     * Calls off a search that is running.
     * <p>
     * A search started for somebody who has since moved on is work nobody will read, and analysis in
     * particular is asked for constantly and wanted briefly. The search notices at its next position
     * rather than at once, which is close enough: it checks between every one of them.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     */
    public void stop() {
        stopped = true;
    }

    /**
     * Works out how far this move's score is put out at the root.
     * <p>
     * The same move has to be judged the same way throughout one search, otherwise looking one move
     * deeper would change its score for no reason and the search would chase its own noise from
     * depth to depth. So this is worked out from the move and the seed rather than drawn fresh each
     * time, which makes it stable within a search and different between searches.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove the root move being judged
     * @return how much to add to its score, between minus and plus the noise asked for
     */
    private int noiseFor(int pMove) {
        // a cheap mix, enough to scatter neighbouring move numbers into unrelated offsets
        long mixed = (pMove * 0x9E3779B97F4A7C15L) ^ noiseSeed;
        mixed ^= mixed >>> 29;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 32;
        int span = 2 * noiseCentipawns + 1;
        return (int) Math.floorMod(mixed, span) - noiseCentipawns;
    }

    /**
     * Tells whether the search has run past what it was allowed.
     * <p>
     * The clock is only read every so many positions, because asking it on every one of them costs
     * more than the search saves by stopping a moment earlier.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return true once the search has to stop
     */
    private boolean outOfTime() {
        if (stopped) {
            return true;
        }
        if (nodes >= nodeLimit) {
            stopped = true;
            return true;
        }
        if (deadline != Long.MAX_VALUE && (nodes & (NODES_BETWEEN_TIME_CHECKS - 1)) == 0
                && System.nanoTime() >= deadline) {
            stopped = true;
            return true;
        }
        return false;
    }
}
