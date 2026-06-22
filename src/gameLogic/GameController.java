package gameLogic;

import gui.Board;
import gui.PromoteGUI;
import gui.EndScreen;
import pieces.*;

import javax.swing.*;
import java.util.ArrayList;

public class GameController {

    Board b;
    CheckScanner cs;

    private boolean turnOfWhite = true;

    public GameController(Board b) {
        this.b = b;
        this.cs = new CheckScanner(b);
    }

    public void restartGame() {
        b.setPieces(b.addPieces());
        turnOfWhite = true;
        b.setEnPassantTile(-1);
    }

    private void checkGameEnd(Move m) {

        boolean nextPlayer = !m.getPiece().isWhite();
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(b);

        if (isCheckmate(nextPlayer)) {
            String winner = m.getPiece().isWhite() ? "White wins!" : "Black wins!";

            EndScreen screen = new EndScreen(parent, winner);
            screen.setVisible(true);

            restartGame();

        } else if (isStalemate(nextPlayer)) {
            EndScreen screen = new EndScreen(parent, "Stalemate - Draw");
            screen.setVisible(true);

            restartGame();
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

        if (m.getPiece() instanceof King && Math.abs(m.getNewCol() - m.getPiece().getCol()) == 2) {
            castle((King) m.getPiece(), m.getNewCol());
        }

        if (m.getPiece() instanceof Pawn) {
            movePawn(m);
        } else {
            m.getPiece().setCol(m.getNewCol());
            m.getPiece().setRow(m.getNewRow());
            m.getPiece().setxPos(m.getNewCol() * b.getTileSize());
            m.getPiece().setyPos(m.getNewRow() * b.getTileSize());

            m.getPiece().setFirstMove(false);

            b.capture(m);
        }

        turnOfWhite = !turnOfWhite;
        checkGameEnd(m);
        b.flip();
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

        PromoteGUI dialog = new PromoteGUI(frame);
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
}
