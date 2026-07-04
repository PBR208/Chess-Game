package gameLogic;

import gui.*;
import pieces.*;

import javax.swing.*;
import java.util.ArrayList;

public class GameController {

    public interface GameEndListener {
        void onGameEnd(GameRecord record, String message);
    }

    Board b;
    CheckScanner cs;
    NotationHelper nh = new NotationHelper();
    MoveLogPanel moveLogPanel;

    private final GameConfig config;
    private final FenGenerator fg;
    private GameEndListener gameEndListener;

    private boolean turnOfWhite = true;
    private boolean gameOver = false;

    private int passedMoves = 0;
    private final int fullMove = 1;

    private final ArrayList<String> moveLog = new ArrayList<>();
    private final ArrayList<String> fenHistory = new ArrayList<>();

    public GameController(Board b, GameConfig config) {
        this.b = b;
        this.cs = new CheckScanner(b);
        this.config = config;
        this.fg = new FenGenerator(b);
    }

    public void restartGame() {
        b.setPieces(b.addPieces());
        turnOfWhite = true;
        passedMoves = 0;
        b.setEnPassantTile(-1);
        b.resetClocks(); // reset both clocks and start white's
        moveLog.clear();
        if (moveLogPanel != null) moveLogPanel.clear();
    }

    public void flagFall(boolean isWhiteExpired) {
        String result = isWhiteExpired ? "0-1" : "1-0";
        String winner = isWhiteExpired ? config.blackName() : config.whiteName();
        endGame(result, winner + " wins on time!");
    }

    private void checkGameEnd(Move m) {
        boolean nextPlayer = !m.getPiece().isWhite();
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(b);

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
            FiftyRuleDraw dialog = new FiftyRuleDraw(parent, b.getTileSize(), true);
            dialog.setVisible(true);
            endGame("1/2-1/2", "75-move rule — Draw");

        } else if (passedMoves >= 100) {
            FiftyRuleDraw dialog = new FiftyRuleDraw(parent, b.getTileSize(), false);
            dialog.setVisible(true);
            if (dialog.getResult() == FiftyRuleDraw.DrawResult.ACCEPTED) {
                endGame("1/2-1/2", "Draw agreed");
            }
            // declined: game continues
        }
    }

    public boolean isValidMove(Move m) {

        if (m.getPiece().isWhite() != turnOfWhite) {
            return false;
        }

        if (!isSameTeam(m.getPiece(), m.getCapture())) {
            if (m.getPiece().isValidMovement(m.getNewCol(), m.getNewRow())) {
                if (!m.getPiece().isValidCollide(m.getNewCol(), m.getNewRow())) {
                    if (m.getPiece().getType() == PieceType.KING
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

        if (m.getPiece().getType() == PieceType.KING && Math.abs(m.getNewCol() - m.getPiece().getCol()) == 2) {
            castle((King) m.getPiece(), m.getNewCol());
        }

        if (m.getPiece().getType() == PieceType.PAWN) {
            movePawn(m);
            passedMoves = -1;
        } else {
            m.getPiece().setCol(m.getNewCol());
            m.getPiece().setRow(m.getNewRow());
            m.getPiece().setxPos(m.getNewCol() * b.getTileSize());
            m.getPiece().setyPos(m.getNewRow() * b.getTileSize());

            m.getPiece().setFirstMove(false);

            b.capture(m);
            b.moveOnGrid(m.getPiece(), fromCol, fromRow);
            if (m.getCapture() != null) {
                passedMoves = -1;
            }
        }

        moveLog.add(nh.toNotation(m, fromCol, fromRow));

        if (moveLogPanel != null) {
            moveLogPanel.update(moveLog);
        }

        passedMoves++;
        turnOfWhite = !turnOfWhite;

        fenHistory.add(fg.generate(turnOfWhite, passedMoves, fullMove));

        checkGameEnd(m);
        flip();
    }

    private void movePawn(Move m) {

        int fromCol = m.getPiece().getCol();
        int fromRow = m.getPiece().getRow();

        // en passent
        int colorIndex = m.getPiece().isWhite() ? 1 : -1;

        if (b.getTileNum(m.getNewCol(), m.getNewRow()) == b.getEnPassantTile()) {
            m.setCapture(b.getPiece(m.getNewCol(), m.getNewRow() + colorIndex));
        }

        if (Math.abs(m.getPiece().getRow() - m.getNewRow()) == 2) {
            b.setEnPassantTile(b.getTileNum(m.getNewCol(), m.getNewRow() + colorIndex));
        } else {
            b.setEnPassantTile(-1);
        }

        //promotion

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

        b.capture(m);
        b.moveOnGrid(m.getPiece(), fromCol, fromRow);
    }

    private void promotePawn(Move m) {

        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(b);
        PromoteGUI dialog = new PromoteGUI(frame, b.getTileSize());
        PromoteGUI.Choice choice = dialog.showDialog();
        boolean white = m.getPiece().isWhite();

        b.capture(m);

        Piece newPiece = switch (choice) {
            case QUEEN -> new Queen(b, m.getNewCol(), m.getNewRow(), white);
            case ROOK -> new Rook(b, m.getNewCol(), m.getNewRow(), white);
            case BISHOP -> new Bishop(b, m.getNewCol(), m.getNewRow(), white);
            case KNIGHT -> new Knight(b, m.getNewCol(), m.getNewRow(), white);
        };

        b.removePiece(m.getPiece());
        b.addPiece(newPiece);

        m.setPromotionChoice(switch (choice) {
            case ROOK -> "R";
            case BISHOP -> "B";
            case KNIGHT -> "N";
            default -> "Q";
        });
    }

    private void castle(King king, int newCol) {

        int row = king.getRow();

        if (newCol == 6) { // kingside
            Piece rook = b.getPiece(7, row);
            rook.setCol(5);
            rook.setxPos(5 * b.getTileSize());
            rook.setFirstMove(false);
            b.moveOnGrid(rook, 7, row);
        }

        if (newCol == 2) { // queenside
            Piece rook = b.getPiece(0, row);
            rook.setCol(3);
            rook.setxPos(3 * b.getTileSize());
            rook.setFirstMove(false);
            b.moveOnGrid(rook, 0, row);
        }
    }

    private boolean isValidCastle(Move m) {
        King king = (King) m.getPiece();
        int step = m.getNewCol() > king.getCol() ? 1 : -1;

        if (cs.isKingInCheckRN(king.isWhite())) return false;

        Move middle = new Move(b, king, king.getCol() + step, king.getRow());
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
        for (Piece p : new ArrayList<>(b.getPieces())) {
            if (p.isWhite() != teamColorWhite) continue;
            for (int row = 0; row < 8; row++)
                for (int col = 0; col < 8; col++)
                    if (isValidMove(new Move(b, p, col, row))) return true;
        }
        return false;
    }

    private void endGame(String result, String displayMessage) {
        gameOver = true;
        b.stopClocks();
        GameRecord record = new GameRecord(config, result,
                new ArrayList<>(moveLog), new ArrayList<>(fenHistory));
        if (gameEndListener != null) {
            gameEndListener.onGameEnd(record, displayMessage);
        }
    }

    // GETTER

    public boolean isTurnOfWhite() {
        return turnOfWhite;
    }

    public ArrayList<String> getMoveLog() {
        return moveLog;
    }

    // SETTER

    public void setGameEndListener(GameEndListener l) {
        this.gameEndListener = l;
    }

    public void setMoveLogPanel(MoveLogPanel panel) {
        this.moveLogPanel = panel;
    }
}