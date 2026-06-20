package main;

import pieces.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Board extends JPanel {

    private final int tileSize = 85;

    private final int rows = 8;
    private final int cols = 8;

    private ArrayList<Piece> pieces = new ArrayList<>();

    private Piece selectedPiece;
    private int enPassantTile = -1;

    private final CheckScanner cs = new CheckScanner(this);


    public Board() {
        this.setPreferredSize(new Dimension(cols * tileSize, rows * tileSize));

        Input input = new Input(this);
        this.addMouseListener(input);
        this.addMouseMotionListener(input);

        pieces = addPieces();
    }

    public ArrayList<Piece> addPieces() {

        ArrayList<Piece> newGame = new ArrayList<>();

        // Black Pieces

        newGame.add(new Rook(this, 0, 0, false));
        newGame.add(new Rook(this, 7, 0, false));
        newGame.add(new Knight(this, 1, 0, false));
        newGame.add(new Knight(this, 6, 0, false));
        newGame.add(new Bishop(this, 2, 0, false));
        newGame.add(new Bishop(this, 5, 0, false));
        newGame.add(new Queen(this, 3, 0, false));
        newGame.add(new King(this, 4, 0, false));

        // White Pieces

        newGame.add(new Rook(this, 0, 7, true));
        newGame.add(new Rook(this, 7, 7, true));
        newGame.add(new Knight(this, 1, 7, true));
        newGame.add(new Knight(this, 6, 7, true));
        newGame.add(new Bishop(this, 2, 7, true));
        newGame.add(new Bishop(this, 5, 7, true));
        newGame.add(new Queen(this, 3, 7, true));
        newGame.add(new King(this, 4, 7, true));

        // Pawns

        for (int i = 0; i <= 7; i++) {
            newGame.add(new Pawn(this, i, 1, false));
            newGame.add(new Pawn(this, i, 6, true));
        }
        return newGame;
    }

    public void restartGame() {
        pieces = addPieces();
    }

    public void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                g2d.setColor((c + r) % 2 == 0 ? new Color(232, 235, 239) : new Color(125, 135, 150));
                g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
            }

        }

        if (selectedPiece != null) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {

                    if (isValidMove(new Move(this, selectedPiece, c, r))) {

                        g2d.setColor(new Color(81, 168, 0, 200));
                        g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
                    }

                }
            }
        }
        for (Piece p : pieces) {
            p.paint(g2d);
        }
    }

    // SECTION for making MOVES

    public void makeMove(Move m) {

        if (m.getPiece() instanceof King && Math.abs(m.getNewCol() - m.getPiece().getCol()) == 2) {
            castle((King) m.getPiece(), m.getNewCol());
        }

        if (m.getPiece().getName().equals("Pawn")) {
            movePawn(m);
        } else {
            m.getPiece().setCol(m.getNewCol());
            m.getPiece().setRow(m.getNewRow());
            m.getPiece().setxPos(m.getNewCol() * tileSize);
            m.getPiece().setyPos(m.getNewRow() * tileSize);

            m.getPiece().setFirstMove(false);

            capture(m);
        }

        checkGameEnd(m);
    }

    public void movePawn(Move m) {

        // en passent
        int colorIndex = m.getPiece().isWhite() ? 1 : -1;

        if (getTileNum(m.getNewCol(), m.getNewRow()) == enPassantTile) {
            m.setCapture(getPiece(m.getNewCol(), m.getNewRow() + colorIndex));
        }

        if (Math.abs(m.getPiece().getRow() - m.getNewRow()) == 2) {
            enPassantTile = getTileNum(m.getNewCol(), m.getNewRow() + colorIndex);
        } else {
            enPassantTile = -1;
        }

        //promotion

        colorIndex = m.getPiece().isWhite() ? 0 : 7;
        if (m.getNewRow() == colorIndex) {
            promotePawn(m);
        }

        m.getPiece().setCol(m.getNewCol());
        m.getPiece().setRow(m.getNewRow());
        m.getPiece().setxPos(m.getNewCol() * tileSize);
        m.getPiece().setyPos(m.getNewRow() * tileSize);

        m.getPiece().setFirstMove(false);

        capture(m);
    }

    private void promotePawn(Move m) {

        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);

        PromoteGUI dialog = new PromoteGUI(frame);
        PromoteGUI.Choice choice = dialog.showDialog();

        Piece newPiece;

        boolean white = m.getPiece().isWhite();

        newPiece = switch (choice) {
            case ROOK -> new Rook(this, m.getNewCol(), m.getNewRow(), white);
            case BISHOP -> new Bishop(this, m.getNewCol(), m.getNewRow(), white);
            case KNIGHT -> new Knight(this, m.getNewCol(), m.getNewRow(), white);
            default -> new Queen(this, m.getNewCol(), m.getNewRow(), white);
        };

        pieces.remove(m.getPiece());
        pieces.add(newPiece);
    }

    private void castle(King king, int newCol) {

        int row = king.getRow();

        if (newCol == 6) { // kingside
            Piece rook = getPiece(7, row);
            rook.setCol(5);
            rook.setxPos(5 * getTileSize());
        }

        if (newCol == 2) { // queenside
            Piece rook = getPiece(0, row);
            rook.setCol(3);
            rook.setxPos(3 * getTileSize());
        }
    }

    private void checkGameEnd(Move m) {

        boolean nextPlayer = !m.getPiece().isWhite();
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(this);

        if (cs.isCheckmate(nextPlayer)) {
            String winner = m.getPiece().isWhite() ? "White wins!" : "Black wins!";

            EndScreen screen = new EndScreen(parent, winner);
            screen.setVisible(true);

            restartGame();

        } else if (cs.isStalemate(nextPlayer)) {
            EndScreen screen = new EndScreen(parent, "Stalemate - Draw");
            screen.setVisible(true);

            restartGame();
        }
    }

    public void capture(Move m) {
        pieces.remove(m.getCapture());
    }

    public boolean isValidMove(Move m) {

        if (!isSameTeam(m.getPiece(), m.getCapture())) {
            if (m.getPiece().isValidMovement(m.getNewCol(), m.getNewRow())) {
                if (!m.getPiece().isValidCollide(m.getNewCol(), m.getNewRow())) {
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


    // GETTER

    public Piece getPiece(int col, int row) {
        for (Piece p : pieces) {
            if (p.getCol() == col && p.getRow() == row) {
                return p;
            }
        }
        return null;
    }

    public int getTileSize() {
        return tileSize;
    }

    public Piece getSelectedPiece() {
        return selectedPiece;
    }

    public int getTileNum(int col, int row) {
        return row * rows + col * cols;
    }

    public int getEnPassantTile() {
        return enPassantTile;
    }

    public List<Piece> getPieces() {

        return pieces;
    }

    public CheckScanner getCs() {
        return cs;
    }

    // SETTER

    public void setSelectedPiece(Piece selectedPiece) {
        this.selectedPiece = selectedPiece;
    }

    public void removePiece(Piece p) {
        pieces.remove(p);
    }


}
