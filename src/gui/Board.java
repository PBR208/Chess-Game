package gui;

import gameLogic.GameController;
import gameLogic.Input;
import gameLogic.Move;
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

    private final GameController gc = new GameController(this);

    public Board() {
        this.setPreferredSize(new Dimension(cols * tileSize, rows * tileSize));

        Input input = new Input(this, gc);
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

    public void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                g2d.setColor((c + r) % 2 == 0 ? new Color(232, 235, 239) : new Color(125, 135, 150));
                g2d.fillRect(toVisualX(c), toVisualY(r), tileSize, tileSize);
            }
        }

        if (selectedPiece != null) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (gc.isValidMove(new Move(this, selectedPiece, c, r))) {
                        g2d.setColor(new Color(81, 168, 0, 200));
                        g2d.fillRect(toVisualX(c), toVisualY(r), tileSize, tileSize);
                    }
                }
            }
        }

        for (Piece p : pieces) {
            if (p == selectedPiece) {
                p.paint(g2d, p.getxPos(), p.getyPos());
            } else {
                p.paint(g2d, toVisualX(p.getCol()), toVisualY(p.getRow()));
            }
        }
    }


    // logical col/row -> pixel X/Y for drawing
    public int toVisualX(int col) {
        return (gc.isTurnOfWhite() ? col : 7 - col) * tileSize;
    }

    public int toVisualY(int row) {
        return (gc.isTurnOfWhite() ? row : 7 - row) * tileSize;
    }

    // pixel X/Y from a mouse click -> logical col/row
    public int toLogicalCol(int x) {
        int c = x / tileSize;
        return gc.isTurnOfWhite() ? c : 7 - c;
    }

    public int toLogicalRow(int y) {
        int r = y / tileSize;
        return gc.isTurnOfWhite() ? r : 7 - r;
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
        return row * cols + col;
    }

    public int getEnPassantTile() {
        return enPassantTile;
    }

    public List<Piece> getPieces() {
        return pieces;
    }

    // SETTER

    public void setSelectedPiece(Piece selectedPiece) {
        this.selectedPiece = selectedPiece;
    }

    public void removePiece(Piece p) {
        pieces.remove(p);
    }

    public void setPieces(ArrayList<Piece> pieces) {
        this.pieces = pieces;
    }

    public void setEnPassantTile(int enPassantTile) {
        this.enPassantTile = enPassantTile;
    }

    public void addPiece(Piece p) {
        pieces.add(p);
    }

    // HELPER

    public void capture(Move m) {
        pieces.remove(m.getCapture());
    }
}
