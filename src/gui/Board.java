package gui;

import gameLogic.GameController;
import gameLogic.Input;
import gameLogic.Move;
import pieces.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class Board extends JPanel {

    private final int tileSize = 85;
    private final int rows = 8;
    private final int cols = 8;
    private final int clockHeight = tileSize; // each clock panel is one tile tall

    private ArrayList<Piece> pieces = new ArrayList<>();
    private Piece selectedPiece;
    private int enPassantTile = -1;
    private HashSet<Integer> legalMoveTiles = new HashSet<>();

    private final GameController gc = new GameController(this);

    private final ChessClock whiteClock = new ChessClock(true, this::repaint, this::onTimeExpired);
    private final ChessClock blackClock = new ChessClock(false, this::repaint, this::onTimeExpired);

    private final Color LIGHT_TILE = new Color(232, 235, 239);
    private final Color DARK_TILE = new Color(125, 135, 150);
    private final Color HINT_COLOR = new Color(81, 168, 0, 200);

    public Board() {
        this.setPreferredSize(new Dimension(cols * tileSize, rows * tileSize + clockHeight * 2));

        Input input = new Input(this, gc);
        this.addMouseListener(input);
        this.addMouseMotionListener(input);

        pieces = addPieces();
        whiteClock.start();
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
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        boolean whiteAtBottom = gc.isTurnOfWhite();
        int boardWidth = cols * tileSize;
        int bottomY = clockHeight + rows * tileSize;

        if (whiteAtBottom) {
            blackClock.draw(g2d, 0, boardWidth, clockHeight);
        } else {
            whiteClock.draw(g2d, 0, boardWidth, clockHeight);
        }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                g2d.setColor((c + r) % 2 == 0
                        ? LIGHT_TILE
                        : DARK_TILE);
                g2d.fillRect(toVisualX(c), toVisualY(r), tileSize, tileSize);
            }
        }

        if (selectedPiece != null) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (legalMoveTiles.contains(getTileNum(c, r))) {
                        g2d.setColor(HINT_COLOR);
                        g2d.fillRect(toVisualX(c), toVisualY(r), tileSize, tileSize);
                    }
                }
            }
        }

        for (Piece p : pieces) {
            if (p == selectedPiece) {
                p.paint(g2d, p.getxPos(), p.getyPos()); // follows the mouse cursor
            } else {
                p.paint(g2d, toVisualX(p.getCol()), toVisualY(p.getRow()));
            }
        }

        if (whiteAtBottom) {
            whiteClock.draw(g2d, bottomY, boardWidth, clockHeight);
        } else {
            blackClock.draw(g2d, bottomY, boardWidth, clockHeight);
        }
    }

    public void switchClocks() {
        if (gc.isTurnOfWhite()) {
            blackClock.stop();
            whiteClock.start();
        } else {
            whiteClock.stop();
            blackClock.start();
        }
    }

    public void stopClocks() {
        whiteClock.stop();
        blackClock.stop();
    }

    public void resetClocks() {
        whiteClock.reset();
        blackClock.reset();
        whiteClock.start();
    }

    // logical col/row -> pixel X/Y for drawing
    public int toVisualX(int col) {
        return (gc.isTurnOfWhite() ? col : 7 - col) * tileSize;
    }

    public int toVisualY(int row) {
        return clockHeight + (gc.isTurnOfWhite() ? row : 7 - row) * tileSize;
    }

    // pixel X/Y from a mouse click -> logical col/row
    public int toLogicalCol(int x) {
        int c = x / tileSize;
        return gc.isTurnOfWhite() ? c : 7 - c;
    }

    public int toLogicalRow(int y) {
        int r = (y - clockHeight) / tileSize;
        return gc.isTurnOfWhite() ? r : 7 - r;
    }

    private void onTimeExpired(boolean isWhiteExpired) {
        stopClocks();
        String winner = isWhiteExpired ? "Black wins on time!" : "White wins on time!";
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(this);
        EndScreen screen = new EndScreen(parent, winner, tileSize);
        screen.setVisible(true);
        gc.restartGame();
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

    public GameController getGameController() {
        return gc;
    }

    // SETTER

    public void setSelectedPiece(Piece selectedPiece) {
        this.selectedPiece = selectedPiece;
        legalMoveTiles.clear();

        if (selectedPiece != null) {
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (gc.isValidMove(new Move(this, selectedPiece, c, r))) {
                        legalMoveTiles.add(getTileNum(c, r));
                    }
                }
            }
        }
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
