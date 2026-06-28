package gameLogic;

import gui.Board;
import gui.FiftyRuleDraw;
import gui.PromoteGUI;
import gui.EndScreen;
import pieces.*;

import javax.swing.*;
import java.util.ArrayList;

public class GameController {

    Board b;
    CheckScanner cs;
    NotationHelper nh = new NotationHelper();

    int passedMoves;

    private boolean turnOfWhite = true;
    private final ArrayList<String> moveLog = new ArrayList<>();

    public GameController(Board b) {
        this.b = b;
        this.cs = new CheckScanner(b);
        passedMoves = 0;
    }

    public void restartGame() {
        b.setPieces(b.addPieces());
        turnOfWhite = true;
        passedMoves = 0;
        b.setEnPassantTile(-1);
        b.resetClocks(); // reset both clocks and start white's
        moveLog.clear();
    }

    private void checkGameEnd(Move m) {

        boolean nextPlayer = !m.getPiece().isWhite();
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(b);

        if (isCheckmate(nextPlayer)) {
            String winner = m.getPiece().isWhite() ? "White wins!" : "Black wins!";

            b.stopClocks();
            EndScreen screen = new EndScreen(parent, winner, b.getTileSize());
            screen.setVisible(true);

            restartGame();

        } else if (isStalemate(nextPlayer)) {
            b.stopClocks();
            EndScreen screen = new EndScreen(parent, "Stalemate - Draw", b.getTileSize());
            screen.setVisible(true);

            restartGame();
        }

        // after 75 moves its declared a draw no matter what
        if (passedMoves >= 150) {
            b.stopClocks();
            FiftyRuleDraw fiftyRuleDraw = new FiftyRuleDraw(parent, b.getTileSize(), true);
            fiftyRuleDraw.setVisible(true);

            restartGame();
        }

        // 50 moves by black AND white = 100 - possible draw
        if (passedMoves >= 100) {
            b.stopClocks();
            FiftyRuleDraw fiftyRuleDraw = new FiftyRuleDraw(parent, b.getTileSize(), false);
            fiftyRuleDraw.setVisible(true);

            if (fiftyRuleDraw.getResult() == FiftyRuleDraw.DrawResult.ACCEPTED) {
                EndScreen screen = new EndScreen(parent, "Draw accepted", b.getTileSize());
                screen.setVisible(true);
                restartGame();
                return;
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

            b.capture(m);
            passedMoves = -1;
        }

        moveLog.add(nh.toNotation(m, fromCol, fromRow));

        passedMoves++;
        turnOfWhite = !turnOfWhite;
        checkGameEnd(m);
        flip();
    }

    public void movePawn(Move m) {

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
        }

        m.getPiece().setCol(m.getNewCol());
        m.getPiece().setRow(m.getNewRow());
        m.getPiece().setxPos(m.getNewCol() * b.getTileSize());
        m.getPiece().setyPos(m.getNewRow() * b.getTileSize());

        m.getPiece().setFirstMove(false);

        b.capture(m);
    }

    private void promotePawn(Move m) {

        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(b);

        PromoteGUI dialog = new PromoteGUI(frame, b.getTileSize());
        PromoteGUI.Choice choice = dialog.showDialog();

        Piece newPiece;

        boolean white = m.getPiece().isWhite();

        newPiece = switch (choice) {
            case ROOK -> new Rook(b, m.getNewCol(), m.getNewRow(), white);
            case BISHOP -> new Bishop(b, m.getNewCol(), m.getNewRow(), white);
            case KNIGHT -> new Knight(b, m.getNewCol(), m.getNewRow(), white);
            default -> new Queen(b, m.getNewCol(), m.getNewRow(), white);
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
        }

        if (newCol == 2) { // queenside
            Piece rook = b.getPiece(0, row);
            rook.setCol(3);
            rook.setxPos(3 * b.getTileSize());
            rook.setFirstMove(false);
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

        if (!cs.isKingInCheckRN(teamColorWhite)) {
            return false;
        }

        return !hasLegalMoves(teamColorWhite);
    }


    public boolean isStalemate(boolean teamColorWhite) {

        if (cs.isKingInCheckRN(teamColorWhite)) {
            return false;
        }

        return !hasLegalMoves(teamColorWhite);
    }

    private void flip() {
        b.switchClocks();
        b.repaint();
    }

    private boolean hasLegalMoves(boolean teamColorWhite) {

        for (Piece p : new ArrayList<Piece>(b.getPieces())) {

            // only check players A or B pieces
            if (p.isWhite() != teamColorWhite) {
                continue;
            }

            // try every square on board
            for (int row = 0; row < 8; row++) {

                for (int col = 0; col < 8; col++) {

                    Move move = new Move(b, p, col, row);

                    if (isValidMove(move)) {
                        return true;
                    }

                }
            }
        }
        return false;
    }

    // GETTER

    public boolean isTurnOfWhite() {
        return turnOfWhite;
    }
}