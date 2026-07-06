package gameLogic;

import gui.Board;
import gui.MoveLogPanel;
import pieces.*;

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

    public void restartGame() {
        b.setPieces(b.addPieces());
        turnOfWhite = true;
        passedMoves = 0;
        state.setEnPassantTile(-1);
        b.resetClocks();
        history.clear();
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

    public boolean isValidMove(Move m) {

        if (m.getPiece().isWhite() != turnOfWhite) {
            return false;
        }

        if (!isSameTeam(m.getPiece(), m.getCapture())) {
            if (m.getPiece().isValidMovement(m.getNewCol(), m.getNewRow())) {
                if (!m.getPiece().isValidCollide(m.getNewCol(), m.getNewRow())) {
                    if (m.getPiece() instanceof King
                            && Math.abs(m.getNewCol() - m.getPiece().getCol()) == 2) {
                        return isValidCastle(m);
                    }

                    return !cs.isKingLeftInCheck(m);
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

    public void makeMove(Move m) {

        int fromCol = m.getPiece().getCol();
        int fromRow = m.getPiece().getRow();

        if (m.getPiece() instanceof King && Math.abs(m.getNewCol() - m.getPiece().getCol()) == 2) {
            castle((King) m.getPiece(), m.getNewCol());
        }

        if (m.getPiece() instanceof Pawn) {
            movePawn(m);
            passedMoves = -1;
        } else {
            m.getPiece().setCol(m.getNewCol());
            m.getPiece().setRow(m.getNewRow());
            m.getPiece().setxPos(m.getNewCol() * b.getTileSize());
            m.getPiece().setyPos(m.getNewRow() * b.getTileSize());

            m.getPiece().setFirstMove(false);

            state.capture(m);
            state.moveOnGrid(m.getPiece(), fromCol, fromRow);
            if (m.getCapture() != null) {
                passedMoves = -1;
            }
        }

        passedMoves++;
        turnOfWhite = !turnOfWhite;

        history.record(m, fromCol, fromRow, turnOfWhite, passedMoves, fullMove);

        checkGameEnd(m);
        flip();
    }

    private void movePawn(Move m) {

        int fromCol = m.getPiece().getCol();
        int fromRow = m.getPiece().getRow();

        int colorIndex = m.getPiece().isWhite() ? 1 : -1;

        if (state.getTileNum(m.getNewCol(), m.getNewRow()) == state.getEnPassantTile()) {
            m.setCapture(state.getPiece(m.getNewCol(), m.getNewRow() + colorIndex));
        }

        if (Math.abs(m.getPiece().getRow() - m.getNewRow()) == 2) {
            state.setEnPassantTile(state.getTileNum(m.getNewCol(), m.getNewRow() + colorIndex));
        } else {
            state.setEnPassantTile(-1);
        }

        colorIndex = m.getPiece().isWhite() ? 0 : 7;
        if (m.getNewRow() == colorIndex) {
            promotePawn(m);
            return;
        }

        m.getPiece().setCol(m.getNewCol());
        m.getPiece().setRow(m.getNewRow());
        m.getPiece().setxPos(m.getNewCol() * b.getTileSize());
        m.getPiece().setyPos(m.getNewRow() * b.getTileSize());

        m.getPiece().setFirstMove(false);

        state.capture(m);
        state.moveOnGrid(m.getPiece(), fromCol, fromRow);
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

    private void flip() {
        b.switchClocks();
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

    private void endGame(String result, String displayMessage) {
        gameOver = true;
        b.stopClocks();
        GameRecord record = new GameRecord(config, result,
                history.getMoveLog(), history.getFenHistory());
        if (gameEndListener != null) {
            gameEndListener.onGameEnd(record, displayMessage);
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
            public void onUpdate(List<String> moveLog) {
                panel.update(moveLog);
            }

            @Override
            public void onClear() {
                panel.clear();
            }
        });
    }
}