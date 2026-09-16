package engine.imports;

/*
 * Purpose: GameController is the referee of a running game. It decides whether a move is legal,
 * applies it to the board state, tracks whose turn it is and detects checkmate, stalemate and the
 * move-count draw rules. I keep these rules here instead of in the Swing board so they can be
 * tested without a window. Promotion choices and draw offers are requested through small
 * interfaces, so no dialog is ever created from inside this class.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.model.GameConfig;
import engine.model.GameRecord;
import ui.board.Board;
import ui.board.MoveLogPanel;
import engine.pieces.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameController {

    public interface GameEndListener {
        void onGameEnd(GameRecord record, String message);
    }

    Board b;
    BoardState state;
    CheckScanner cs;
    MoveHistory history;

    // works out disambiguation and check markers for the recorded notation
    private final NotationHelper notation = new NotationHelper();

    // how often each position occurred since the last pawn move or capture
    private final Map<String, Integer> positionCounts = new HashMap<>();

    private final GameConfig config;
    private final PromotionChooser promotionChooser;
    private final DrawOfferResolver drawOfferResolver;
    private GameEndListener gameEndListener;

    private boolean turnOfWhite = true;
    private boolean gameOver = false;

    private int passedMoves = 0;
    // FEN full-move number, starts at 1 and grows after every Black move
    private int fullMove = 1;

    // each player is offered the 50-move claim once, until a pawn move or capture starts over
    private boolean whiteOfferedFiftyMoveClaim = false;
    private boolean blackOfferedFiftyMoveClaim = false;

    public GameController(Board b, GameConfig config,
                          PromotionChooser promotionChooser, DrawOfferResolver drawOfferResolver) {
        this.b = b;
        this.state = b.getState();
        this.cs = new CheckScanner(state);
        this.config = config;
        this.promotionChooser = promotionChooser;
        this.drawOfferResolver = drawOfferResolver;
        this.history = new MoveHistory(state);
    }

    /**
     * Resets the controller and board to a fresh game with White to move.
     * <p>
     * A restart has to clear every piece of game state, including the finished flag, otherwise the
     * new game would reject all moves. I put the starting pieces back, reset the side to move, the
     * fifty move counter, the full move number and the en passant square, reset both clocks, clear
     * the history, the position counts and the 50-move claim offers and mark the game as running
     * again.
     * <p>
     * Time complexity: O(p + m) where p is the number of pieces placed and m the number of moves
     * cleared from the history. Space complexity: O(p) for the new piece objects.
     */
    public void restartGame() {
        b.setPieces(b.addPieces());
        turnOfWhite = true;
        passedMoves = 0;
        // a new game starts again at move 1
        fullMove = 1;
        state.setEnPassantTile(-1);
        b.resetClocks();
        history.clear();
        // repetitions only count within one game
        positionCounts.clear();
        // both players may be offered the 50-move claim again
        whiteOfferedFiftyMoveClaim = false;
        blackOfferedFiftyMoveClaim = false;
        // a restarted game accepts moves again
        gameOver = false;
    }

    /**
     * Ends the game because a player's clock ran out.
     * <p>
     * Normally the opponent wins on time. FIDE rule 6.9 makes it a draw instead when the opponent
     * could never checkmate by any series of legal moves. I treat the clear cases as a draw, a side
     * that has nothing but its king and a position with insufficient material for both sides, and
     * give the win on time in every other case.
     * <p>
     * Time complexity: O(p + m) for the material checks over p pieces and the record of m moves.
     * Space complexity: O(m) for the game record.
     *
     * @param pIsWhiteExpired true if White's clock ran out, false if Black's did
     */
    public void flagFall(boolean pIsWhiteExpired) {
        // FIDE 6.9: a side that could never checkmate doesn't win on time
        if (onlyKingLeft(!pIsWhiteExpired) || isInsufficientMaterial()) {
            endGame("1/2-1/2", "Time out \u2014 Draw");
            return;
        }
        String result = pIsWhiteExpired ? "0-1" : "1-0";
        String winner = pIsWhiteExpired ? config.blackName() : config.whiteName();
        endGame(result, winner + " wins on time!");
    }

    /**
     * Ends the game when the move just played finished it.
     * <p>
     * After every move the game may be over by checkmate, stalemate, insufficient material,
     * repetition or the move-count rules. The check state, whether the opponent has any legal reply
     * and how often the new position occurred come from makeMove, so the expensive search over all
     * replies runs only once per move. No reply while in check is checkmate, no reply without check
     * is stalemate, and material that can never checkmate ends the game as a draw. After that the
     * automatic draws come first, the fifth occurrence of a position and the 75-move rule, and then
     * the draws the player may claim, a third or fourth occurrence and the 50-move rule. The 50-move
     * claim is offered to each player once per stretch without pawn moves or captures, not after
     * every single move.
     * <p>
     * Time complexity: O(1) here, the reply search already happened in makeMove.
     * Space complexity: O(1).
     *
     * @param pMove            move that was just played, never null
     * @param pOpponentInCheck true if the side to move now is in check
     * @param pOpponentCanMove true if the side to move now has at least one legal move
     * @param pRepetitions     how often the current position has occurred, 1 or more
     * @throws NullPointerException if pMove or its piece is null
     */
    private void checkGameEnd(Move pMove, boolean pOpponentInCheck, boolean pOpponentCanMove, int pRepetitions) {
        boolean moverIsWhite = pMove.getPiece().isWhite();

        // no legal reply while in check is checkmate
        if (pOpponentInCheck && !pOpponentCanMove) {
            String winner = moverIsWhite ? config.whiteName() : config.blackName();
            endGame(moverIsWhite ? "1-0" : "0-1", winner + " wins by checkmate!");
            return;
        }

        // no legal reply without check is stalemate
        if (!pOpponentCanMove) {
            endGame("1/2-1/2", "Stalemate \u2014 Draw");
            return;
        }

        // a position where nobody can ever checkmate is a draw right away
        if (isInsufficientMaterial()) {
            endGame("1/2-1/2", "Insufficient material \u2014 Draw");
            return;
        }

        // the fifth occurrence of a position ends the game automatically
        if (pRepetitions >= 5) {
            endGame("1/2-1/2", "Fivefold repetition \u2014 Draw");
            return;
        }

        if (passedMoves >= 150) {
            drawOfferResolver.notifyForcedDraw();
            endGame("1/2-1/2", "75-move rule \u2014 Draw");
            return;
        }

        // a third or fourth occurrence lets the player to move claim the draw
        if (pRepetitions >= 3 && drawOfferResolver.offerRepetitionDraw()) {
            endGame("1/2-1/2", "Threefold repetition \u2014 Draw");
            return;
        }

        // ask the player to move once, a declined claim is not repeated after every move
        boolean alreadyOffered = turnOfWhite ? whiteOfferedFiftyMoveClaim : blackOfferedFiftyMoveClaim;
        if (passedMoves >= 100 && !alreadyOffered) {
            if (turnOfWhite) {
                whiteOfferedFiftyMoveClaim = true;
            } else {
                blackOfferedFiftyMoveClaim = true;
            }
            // TODO [PBR208]: Add a permanent "Claim draw" action so a player can still claim after declining once.
            if (drawOfferResolver.offerDraw()) {
                endGame("1/2-1/2", "Draw agreed");
            }
        }
    }

    /**
     * Decides whether a move may be played now.
     * <p>
     * Moves from the board and the legal move highlighting go through this check. It rejects every
     * move once the game is over and every move by the side that is not to move, and leaves the
     * rules of the move itself to isLegalForItsSide.
     * <p>
     * Time complexity: O(p) where p is the number of pieces, because the check simulation scans
     * every opposing piece. Space complexity: O(1).
     *
     * @param pMove candidate move whose piece belongs to this game's board, never null
     * @return true if the move may be played now, false otherwise
     * @throws NullPointerException if pMove or its piece is null
     */
    public boolean isValidMove(Move pMove) {

        // the final position of a finished game can't change anymore
        if (gameOver) {
            return false;
        }

        // only the side to move may move
        if (pMove.getPiece().isWhite() != turnOfWhite) {
            return false;
        }

        return isLegalForItsSide(pMove);
    }

    /**
     * Decides whether a move follows the rules for the side that owns the piece, whoever is to move.
     * <p>
     * Checkmate and stalemate have to be judged for either side, but the turn check in isValidMove
     * made every move of the side not to move look illegal, so that side always looked mated or
     * stalemated. This check leaves the turn out. It rejects captures of own pieces, asks the piece
     * whether its geometry and path allow the move, hands two-square king moves to the castling
     * rules, attaches a pawn captured en passant and finally simulates the move to make sure the own
     * king is not left in check.
     * <p>
     * Time complexity: O(p) where p is the number of pieces, because the check simulation scans
     * every opposing piece. Space complexity: O(1).
     *
     * @param pMove candidate move whose piece belongs to this game's board, never null
     * @return true if the move follows the rules for the side that owns the piece
     * @throws NullPointerException if pMove or its piece is null
     */
    private boolean isLegalForItsSide(Move pMove) {

        // never capture an own piece
        if (!isSameTeam(pMove.getPiece(), pMove.getCapture())) {
            // piece geometry first, then blocked paths
            if (pMove.getPiece().isValidMovement(pMove.getNewCol(), pMove.getNewRow())) {
                if (!pMove.getPiece().isValidCollide(pMove.getNewCol(), pMove.getNewRow())) {
                    // a two-square king move is castling and follows its own rules
                    if (pMove.getPiece() instanceof King
                            && Math.abs(pMove.getNewCol() - pMove.getPiece().getCol()) == 2) {
                        return isValidCastle(pMove);
                    }

                    // the passed pawn has to leave the board in the check simulation as well
                    attachEnPassantCapture(pMove);
                    // the own king must not be in check after the move
                    return !cs.isKingLeftInCheck(pMove);
                }
            }
        }

        return false;
    }

    private boolean isSameTeam(Piece p1, Piece p2) {
        if (p1 != null && p2 != null) {
            return p1.isWhite() == p2.isWhite();
        }
        return false;
    }

    /**
     * Plays a move on the board and advances the game.
     * <p>
     * This is the single place where a validated move changes the position. Once the game is over
     * I ignore the call, so nothing can alter the final position. Otherwise I look up identical
     * pieces that could also reach the target square, move the rook along when castling, let pawn
     * moves handle their special rules, move any other piece and remove what it captures, and update
     * the fifty move counter, the side to move and the full move number. Then I judge check and
     * legal replies once, record the notation with its disambiguation and check marker plus the FEN,
     * hand the clock over and finally apply the end of game rules, so a draw prompt already runs on
     * the time of the player who has to decide.
     * <p>
     * Time complexity: O(p * s) where p is the number of pieces and s the 64 squares, dominated by
     * the checkmate and stalemate search after the move. Space complexity: O(m) for the growing
     * move history, where m is the number of moves played.
     *
     * @param pMove move that was accepted by isValidMove, never null
     * @throws NullPointerException if pMove or its piece is null
     */
    public void makeMove(Move pMove) {

        // a finished game ignores further moves, even direct calls
        if (gameOver) {
            return;
        }

        int fromCol = pMove.getPiece().getCol();
        int fromRow = pMove.getPiece().getRow();

        // the position before the very first move counts as an occurrence as well
        if (positionCounts.isEmpty()) {
            countCurrentPosition();
        }

        // rivals have to be found before the move changes the position
        String disambiguation = notation.disambiguation(fromCol, fromRow, findRivals(pMove));

        // castling moves the rook first, the king follows below
        if (pMove.getPiece() instanceof King && Math.abs(pMove.getNewCol() - pMove.getPiece().getCol()) == 2) {
            castle((King) pMove.getPiece(), pMove.getNewCol());
        }

        if (pMove.getPiece() instanceof Pawn) {
            // pawn moves always reset the fifty move counter
            movePawn(pMove);
            passedMoves = -1;
        } else {
            pMove.getPiece().setCol(pMove.getNewCol());
            pMove.getPiece().setRow(pMove.getNewRow());

            pMove.getPiece().setFirstMove(false);

            state.capture(pMove);
            state.moveOnGrid(pMove.getPiece(), fromCol, fromRow);
            // en passant is only possible right after the double step
            state.setEnPassantTile(-1);
            // captures reset the fifty move counter as well
            if (pMove.getCapture() != null) {
                passedMoves = -1;
            }
        }

        passedMoves++;
        turnOfWhite = !turnOfWhite;
        // a full move is complete once Black has moved, the FEN below must already show the new number
        if (turnOfWhite) {
            fullMove++;
        }

        // pawn moves and captures make every earlier position unreachable
        if (passedMoves == 0) {
            positionCounts.clear();
            // and a new stretch towards the 50-move rule begins
            whiteOfferedFiftyMoveClaim = false;
            blackOfferedFiftyMoveClaim = false;
        }
        int repetitions = countCurrentPosition();

        // judge check and legal replies once, both the notation and the end rules need them
        boolean opponentInCheck = cs.isKingInCheckRN(turnOfWhite);
        boolean opponentCanMove = hasLegalMoves(turnOfWhite);
        String suffix = notation.checkSuffix(opponentInCheck, opponentInCheck && !opponentCanMove);

        history.record(pMove, fromCol, fromRow, turnOfWhite, passedMoves, fullMove, disambiguation, suffix);

        // hand the clock over first, a draw prompt below must run on the claiming player's time
        flip();
        // look for mate, stalemate and draw rules
        checkGameEnd(pMove, opponentInCheck, opponentCanMove, repetitions);
    }

    /**
     * Applies a legal pawn move, including en passant, double steps and promotion.
     * <p>
     * Pawns carry most of the special rules, so they get their own move handling. I attach the pawn
     * captured en passant, remember the skipped square after a double step or clear it otherwise,
     * hand moves onto the last rank to the promotion logic and for every other move update the
     * pawn's coordinates, clear its first-move flag, remove the captured piece and sync the grid.
     * <p>
     * Time complexity: O(p) where p is the number of pieces, because removing a captured piece
     * searches the piece list. Space complexity: O(1).
     *
     * @param pMove legal pawn move that was already validated by isValidMove, never null
     * @throws NullPointerException if pMove or its piece is null
     */
    private void movePawn(Move pMove) {

        int fromCol = pMove.getPiece().getCol();
        int fromRow = pMove.getPiece().getRow();

        // rows count downwards for white and upwards for black
        int colorIndex = pMove.getPiece().isWhite() ? 1 : -1;

        // same en passant resolution the validation used
        attachEnPassantCapture(pMove);

        // a double step leaves the skipped square open for en passant
        if (Math.abs(pMove.getPiece().getRow() - pMove.getNewRow()) == 2) {
            state.setEnPassantTile(state.getTileNum(pMove.getNewCol(), pMove.getNewRow() + colorIndex));
        } else {
            state.setEnPassantTile(-1);
        }

        // reaching the last rank means promotion
        colorIndex = pMove.getPiece().isWhite() ? 0 : 7;
        if (pMove.getNewRow() == colorIndex) {
            promotePawn(pMove);
            return;
        }

        pMove.getPiece().setCol(pMove.getNewCol());
        pMove.getPiece().setRow(pMove.getNewRow());

        // a moved pawn loses its double step
        pMove.getPiece().setFirstMove(false);

        // remove the captured piece, then move the pawn on the grid
        state.capture(pMove);
        state.moveOnGrid(pMove.getPiece(), fromCol, fromRow);
    }

    /**
     * Attaches the pawn captured en passant to a move.
     * <p>
     * An en passant capture lands on an empty square, so a freshly built Move reports no capture.
     * Without the victim attached, the check simulation left the passed pawn on the board, which
     * rejected legal replies to a pawn check and allowed captures that expose the own king along
     * the rank. I only act when a pawn steps diagonally onto the current en passant square without a
     * regular capture, and then take the pawn standing directly behind that square.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove move to inspect and complete in place, never null
     * @throws NullPointerException if pMove or its piece is null
     */
    private void attachEnPassantCapture(Move pMove) {
        Piece piece = pMove.getPiece();
        // only a diagonal pawn step onto the empty en passant square qualifies
        if (piece instanceof Pawn && pMove.getCapture() == null
                && pMove.getNewCol() != piece.getCol()
                && state.getTileNum(pMove.getNewCol(), pMove.getNewRow()) == state.getEnPassantTile()) {
            // the passed pawn stands one row behind the target square, seen from the mover
            int behind = piece.isWhite() ? 1 : -1;
            pMove.setCapture(state.getPiece(pMove.getNewCol(), pMove.getNewRow() + behind));
        }
    }

    /**
     * Finds the other pieces of the same type and colour that could make the same move.
     * <p>
     * SAN has to name the origin of a piece when an identical piece could reach the same square,
     * for example Nbd2 when the knight on f3 could go to d2 as well. Pawns and kings never need
     * this, so they get an empty list. For every other piece I run the normal legality check for
     * each other piece of the same type and colour against the target square, before the move is
     * played.
     * <p>
     * Time complexity: O(k * p) where k is the number of same type pieces and p the number of
     * pieces scanned by each check simulation. Space complexity: O(r) for the r rivals found.
     *
     * @param pMove move that is about to be played, never null
     * @return pieces that could legally reach the same square, never null, possibly empty
     * @throws NullPointerException if pMove or its piece is null
     */
    private List<Piece> findRivals(Move pMove) {
        List<Piece> rivals = new ArrayList<>();
        Piece mover = pMove.getPiece();
        // pawn captures already carry their file and there is only one king
        if (mover instanceof Pawn || mover instanceof King) {
            return rivals;
        }
        // copy, the check simulation temporarily changes the piece list
        for (Piece other : new ArrayList<>(state.getPieces())) {
            if (other == mover || other.getType() != mover.getType() || other.isWhite() != mover.isWhite()) {
                continue;
            }
            // a rival is any identical piece that could legally land on the same square
            if (isValidMove(new Move(state, other, pMove.getNewCol(), pMove.getNewRow()))) {
                rivals.add(other);
            }
        }
        return rivals;
    }

    /**
     * Counts one more occurrence of the current position and returns the new total.
     * <p>
     * Repetition draws depend on how often the same position came back. I build the key of the
     * current position and add one to its counter.
     * <p>
     * Time complexity: O(64 + p) for building the key.
     * Space complexity: O(1) amortized for the map entry.
     *
     * @return how often the current position has occurred so far, 1 or more
     */
    private int countCurrentPosition() {
        return positionCounts.merge(positionKey(), 1, Integer::sum);
    }

    /**
     * Builds a key that is equal for two positions exactly when they count as the same position.
     * <p>
     * For repetition two positions are the same when the same pieces stand on the same squares, the
     * same side is to move and the same castling and en passant options exist. I take the piece
     * placement, side to move and castling rights from the FEN generator. The en passant square only
     * stays in the key when a pawn can really capture there, because the FEN records it after every
     * double step and the position would otherwise never match its later repetition.
     * <p>
     * Time complexity: O(64 + p) for the FEN and the en passant check.
     * Space complexity: O(1), the key has a bounded length.
     *
     * @return the repetition key of the current position, never null
     */
    private String positionKey() {
        // the counters don't matter for repetition, so any values work here
        String[] fields = new FenGenerator(state).generate(turnOfWhite, 0, 1).split(" ");
        // an en passant square nobody can use doesn't make the position different
        String enPassant = canCaptureEnPassant() ? fields[3] : "-";
        return fields[0] + " " + fields[1] + " " + fields[2] + " " + enPassant;
    }

    /**
     * Tells whether the side to move can legally capture en passant right now.
     * <p>
     * Only a usable en passant square changes a position for the repetition rule. I return false
     * when there is no en passant square, and otherwise check every pawn of the side to move with
     * the normal legality rules against that square.
     * <p>
     * Time complexity: O(p), plus at most two check simulations of O(p) for the adjacent pawns.
     * Space complexity: O(p) for the copy of the piece list.
     *
     * @return true if at least one pawn of the side to move can capture en passant
     */
    private boolean canCaptureEnPassant() {
        int tile = state.getEnPassantTile();
        // no double step just happened
        if (tile == -1) {
            return false;
        }
        int col = tile % 8;
        int row = tile / 8;
        // copy, the legality check simulates moves on the piece list
        for (Piece piece : new ArrayList<>(state.getPieces())) {
            if (piece instanceof Pawn && piece.isWhite() == turnOfWhite
                    && isValidMove(new Move(state, piece, col, row))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether neither side has enough material left to ever checkmate.
     * <p>
     * With only kings, a king and a single knight, or kings and bishops that all stand on squares of
     * one colour, no sequence of legal moves can end in checkmate, so FIDE rule 5.2.2 treats the game
     * as drawn right away. I go through all pieces and give up as soon as a queen, rook or pawn shows
     * up, count the knights and remember which square colours the bishops stand on.
     * <p>
     * Time complexity: O(p) for p pieces. Space complexity: O(1).
     *
     * @return true if the remaining material can never produce a checkmate
     */
    private boolean isInsufficientMaterial() {
        int knights = 0;
        boolean lightBishop = false;
        boolean darkBishop = false;
        for (Piece piece : state.getPieces()) {
            switch (piece.getType()) {
                // both kings are always on the board
                case KING -> {
                }
                case KNIGHT -> knights++;
                // a8 is a light square, so an even column plus row means light
                case BISHOP -> {
                    if ((piece.getCol() + piece.getRow()) % 2 == 0) {
                        lightBishop = true;
                    } else {
                        darkBishop = true;
                    }
                }
                // a queen, rook or pawn can still lead to a mate
                default -> {
                    return false;
                }
            }
        }
        // bishops on a single colour without knights, or one knight without bishops
        return (knights == 0 && !(lightBishop && darkBishop))
                || (knights == 1 && !lightBishop && !darkBishop);
    }

    /**
     * Tells whether one side has nothing left but its king.
     * <p>
     * A lone king can never deliver checkmate, which decides the result when the other player runs
     * out of time. I look for any piece of that colour other than the king.
     * <p>
     * Time complexity: O(p) for p pieces. Space complexity: O(1).
     *
     * @param pWhite true to look at White's pieces, false for Black's
     * @return true if that side has only its king
     */
    private boolean onlyKingLeft(boolean pWhite) {
        for (Piece piece : state.getPieces()) {
            // any other piece of that colour could still help to mate
            if (piece.isWhite() == pWhite && piece.getType() != PieceType.KING) {
                return false;
            }
        }
        return true;
    }

    private void promotePawn(Move m) {

        boolean white = m.getPiece().isWhite();
        PieceType choice = promotionChooser.choose(white);

        state.capture(m);

        Piece newPiece = switch (choice) {
            case QUEEN -> new Queen(b, m.getNewCol(), m.getNewRow(), white);
            case ROOK -> new Rook(b, m.getNewCol(), m.getNewRow(), white);
            case BISHOP -> new Bishop(b, m.getNewCol(), m.getNewRow(), white);
            case KNIGHT -> new Knight(b, m.getNewCol(), m.getNewRow(), white);
            default -> throw new IllegalStateException("Cannot promote to " + choice);
        };

        state.removePiece(m.getPiece());
        state.addPiece(newPiece);

        m.setPromotionChoice(switch (choice) {
            case ROOK -> "R";
            case BISHOP -> "B";
            case KNIGHT -> "N";
            default -> "Q";
        });
    }

    private void castle(King king, int newCol) {

        int row = king.getRow();

        if (newCol == 6) {
            Piece rook = state.getPiece(7, row);
            rook.setCol(5);
            rook.setFirstMove(false);
            state.moveOnGrid(rook, 7, row);
        }

        if (newCol == 2) {
            Piece rook = state.getPiece(0, row);
            rook.setCol(3);
            rook.setFirstMove(false);
            state.moveOnGrid(rook, 0, row);
        }
    }

    private boolean isValidCastle(Move m) {
        King king = (King) m.getPiece();
        int step = m.getNewCol() > king.getCol() ? 1 : -1;

        if (cs.isKingInCheckRN(king.isWhite())) return false;

        Move middle = new Move(state, king, king.getCol() + step, king.getRow());
        if (cs.isKingLeftInCheck(middle)) return false;

        return !cs.isKingLeftInCheck(m);
    }

    /**
     * Tells whether a side is checkmated in the current position.
     * <p>
     * Tests and the end of game rules ask this for either side, not only for the side to move. A
     * side is checkmated when its king is in check and none of its moves follows the rules. Both
     * parts are judged for that side alone, whoever is to move.
     * <p>
     * Time complexity: O(p * s * p) in the worst case, for trying every square with every piece and
     * simulating each candidate. Space complexity: O(p) for the copied piece list.
     *
     * @param pWhite true to judge White, false to judge Black
     * @return true if that side is checkmated
     */
    public boolean isCheckmate(boolean pWhite) {
        return cs.isKingInCheckRN(pWhite) && !hasLegalMoves(pWhite);
    }

    /**
     * Tells whether a side is stalemated in the current position.
     * <p>
     * A side is stalemated when its king is not in check but none of its moves follows the rules. In
     * the starting position this used to report Black as stalemated while White was to move, because
     * every Black move was rejected for being out of turn. Both parts are now judged for that side
     * alone, whoever is to move.
     * <p>
     * Time complexity: O(p * s * p) in the worst case, for trying every square with every piece and
     * simulating each candidate. Space complexity: O(p) for the copied piece list.
     *
     * @param pWhite true to judge White, false to judge Black
     * @return true if that side is stalemated
     */
    public boolean isStalemate(boolean pWhite) {
        return !cs.isKingInCheckRN(pWhite) && !hasLegalMoves(pWhite);
    }

    /**
     * Hands the clock over to the next player and repaints the board after a move.
     * <p>
     * After a normal move the player who just moved stops and the opponent's clock starts. Once the
     * game has ended both clocks must stay stopped, otherwise the loser's clock keeps running and
     * later reports a time forfeit for a game that is already over. I only switch clocks while the
     * game is still running, pass the side to move from this controller so the right clock runs
     * whichever controller the board was built with, and always repaint so the position is shown.
     * <p>
     * Time complexity: O(1), the repaint is only scheduled. Space complexity: O(1).
     */
    private void flip() {
        // a finished game keeps both clocks stopped
        if (!gameOver) {
            b.switchClocks(turnOfWhite);
        }
        b.repaint();
    }

    /**
     * Tells whether a side has at least one move that follows the rules.
     * <p>
     * Checkmate, stalemate and the check marker in the notation all need to know whether a side can
     * still move. I try every square for every piece of that side with the turn-agnostic legality
     * check and stop at the first legal move, so the answer is right for either side and doesn't
     * depend on whose turn it is.
     * <p>
     * Time complexity: O(p * s * p) in the worst case, for p pieces, s squares and a check simulation
     * per candidate. Space complexity: O(p) for the copied piece list.
     *
     * @param pWhite true to look at White's pieces, false for Black's
     * @return true if that side has a legal move
     */
    private boolean hasLegalMoves(boolean pWhite) {
        // copy, the check simulation temporarily changes the piece list
        for (Piece p : new ArrayList<>(state.getPieces())) {
            if (p.isWhite() != pWhite) continue;
            for (int row = 0; row < 8; row++)
                for (int col = 0; col < 8; col++)
                    // the turn doesn't matter, only the rules for this side
                    if (isLegalForItsSide(new Move(state, p, col, row))) return true;
        }
        return false;
    }

    /**
     * Finishes the game with a result and notifies the listener exactly once.
     * <p>
     * A game can only end one way, even when a flag falls right after a checkmate. I ignore every
     * call after the first, then mark the game as over, stop both clocks, build the record from the
     * move and FEN history and hand it to the listener, which saves the PGN and shows the end
     * screen.
     * <p>
     * Time complexity: O(m) where m is the number of recorded moves, for copying the history into
     * the record. Space complexity: O(m) for the record.
     *
     * @param pResult         PGN result token, one of "1-0", "0-1" or "1/2-1/2"; never null
     * @param pDisplayMessage human readable message for the end screen, never null
     */
    private void endGame(String pResult, String pDisplayMessage) {
        // the first result is final
        if (gameOver) {
            return;
        }
        gameOver = true;
        // a finished game has no running clock
        b.stopClocks();
        GameRecord record = new GameRecord(config, pResult,
                history.getMoveLog(), history.getFenHistory());
        // the listener saves the game and shows the end screen
        if (gameEndListener != null) {
            gameEndListener.onGameEnd(record, pDisplayMessage);
        }
    }

    // GETTER

    public boolean isTurnOfWhite() {
        return turnOfWhite;
    }

    public List<String> getMoveLog() {
        return history.getMoveLog();
    }

    // SETTER

    public void setGameEndListener(GameEndListener l) {
        this.gameEndListener = l;
    }

    public void setMoveLogPanel(MoveLogPanel panel) {
        history.setListener(panel == null ? null : new MoveHistory.Listener() {
            @Override
            public void onUpdate(List<String> moveLog, String currentFen) {
                panel.update(moveLog, currentFen);
            }

            @Override
            public void onClear() {
                panel.clear();
            }
        });
    }
}