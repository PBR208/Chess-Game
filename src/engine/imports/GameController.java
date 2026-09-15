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
import java.util.List;

public class GameController {

    public interface GameEndListener {
        void onGameEnd(GameRecord record, String message);
    }

    Board b;
    BoardState state;
    CheckScanner cs;
    MoveHistory history;

    private final GameConfig config;
    private final PromotionChooser promotionChooser;
    private final DrawOfferResolver drawOfferResolver;
    private GameEndListener gameEndListener;

    private boolean turnOfWhite = true;
    private boolean gameOver = false;

    private int passedMoves = 0;
    private final int fullMove = 1;

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
     * fifty move counter and the en passant square, reset both clocks, clear the history and mark
     * the game as running again.
     * <p>
     * Time complexity: O(p + m) where p is the number of pieces placed and m the number of moves
     * cleared from the history. Space complexity: O(p) for the new piece objects.
     */
    public void restartGame() {
        b.setPieces(b.addPieces());
        turnOfWhite = true;
        passedMoves = 0;
        state.setEnPassantTile(-1);
        b.resetClocks();
        history.clear();
        // a restarted game accepts moves again
        gameOver = false;
    }

    public void flagFall(boolean isWhiteExpired) {
        String result = isWhiteExpired ? "0-1" : "1-0";
        String winner = isWhiteExpired ? config.blackName() : config.whiteName();
        endGame(result, winner + " wins on time!");
    }

    private void checkGameEnd(Move m) {
        boolean nextPlayer = !m.getPiece().isWhite();

        if (isCheckmate(nextPlayer)) {
            String winner = m.getPiece().isWhite() ? config.whiteName() : config.blackName();
            endGame(m.getPiece().isWhite() ? "1-0" : "0-1", winner + " wins by checkmate!");
            return;
        }

        if (isStalemate(nextPlayer)) {
            endGame("1/2-1/2", "Stalemate — Draw");
            return;
        }

        if (passedMoves >= 150) {
            drawOfferResolver.notifyForcedDraw();
            endGame("1/2-1/2", "75-move rule — Draw");

        } else if (passedMoves >= 100) {
            if (drawOfferResolver.offerDraw()) {
                endGame("1/2-1/2", "Draw agreed");
            }
        }
    }

    /**
     * Decides whether a move is legal in the current position.
     * <p>
     * Moves from the board, the legal move highlighting and the checkmate and stalemate detection
     * all go through this check. It rejects every move once the game is over, moves by the side not
     * to move and captures of own pieces, asks the piece whether its geometry and path allow the
     * move, hands two-square king moves to the castling rules, attaches a pawn captured en passant
     * and finally simulates the move to make sure the own king is not left in check.
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
     * I ignore the call, so nothing can alter the final position. Otherwise I move the rook along
     * when castling, let pawn moves handle their special rules, move any other piece and remove what
     * it captures, update the fifty move counter and the side to move, record notation and FEN,
     * check whether the game has ended and finally hand the clock over.
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
            pMove.getPiece().setxPos(pMove.getNewCol() * b.getTileSize());
            pMove.getPiece().setyPos(pMove.getNewRow() * b.getTileSize());

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

        history.record(pMove, fromCol, fromRow, turnOfWhite, passedMoves, fullMove);

        // look for mate, stalemate and draw rules before the clock moves on
        checkGameEnd(pMove);
        flip();
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
        pMove.getPiece().setxPos(pMove.getNewCol() * b.getTileSize());
        pMove.getPiece().setyPos(pMove.getNewRow() * b.getTileSize());

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
            rook.setxPos(5 * b.getTileSize());
            rook.setFirstMove(false);
            state.moveOnGrid(rook, 7, row);
        }

        if (newCol == 2) {
            Piece rook = state.getPiece(0, row);
            rook.setCol(3);
            rook.setxPos(3 * b.getTileSize());
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

    public boolean isCheckmate(boolean teamColorWhite) {
        return cs.isKingInCheckRN(teamColorWhite) && !hasLegalMoves(teamColorWhite);
    }

    public boolean isStalemate(boolean teamColorWhite) {
        return !cs.isKingInCheckRN(teamColorWhite) && !hasLegalMoves(teamColorWhite);
    }

    /**
     * Hands the clock over to the next player and repaints the board after a move.
     * <p>
     * After a normal move the player who just moved stops and the opponent's clock starts. Once the
     * game has ended both clocks must stay stopped, otherwise the loser's clock keeps running and
     * later reports a time forfeit for a game that is already over. I only switch clocks while the
     * game is still running and always repaint so the final position is shown.
     * <p>
     * Time complexity: O(1), the repaint is only scheduled. Space complexity: O(1).
     */
    private void flip() {
        // a finished game keeps both clocks stopped
        if (!gameOver) {
            b.switchClocks();
        }
        b.repaint();
    }

    private boolean hasLegalMoves(boolean teamColorWhite) {
        for (Piece p : new ArrayList<>(state.getPieces())) {
            if (p.isWhite() != teamColorWhite) continue;
            for (int row = 0; row < 8; row++)
                for (int col = 0; col < 8; col++)
                    if (isValidMove(new Move(state, p, col, row))) return true;
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