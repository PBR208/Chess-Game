package engine.imports;

import engine.pieces.Piece;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BoardState {

    private final int cols = 8;

    private ArrayList<Piece> pieces = new ArrayList<>();
    private final Piece[][] grid = new Piece[8][8];
    private int enPassantTile = -1;

    public Piece getPiece(int col, int row) {
        return grid[row][col];
    }

    public List<Piece> getPieces() {
        return Collections.unmodifiableList(pieces);
    }

    public int getTileNum(int col, int row) {
        return row * cols + col;
    }

    public int getEnPassantTile() {
        return enPassantTile;
    }

    public void setEnPassantTile(int enPassantTile) {
        this.enPassantTile = enPassantTile;
    }

    public void addPiece(Piece p) {
        pieces.add(p);
        grid[p.getRow()][p.getCol()] = p;
    }

    public void removePiece(Piece p) {
        pieces.remove(p);
        grid[p.getRow()][p.getCol()] = null;
    }

    public void setPieces(ArrayList<Piece> pieces) {
        this.pieces = pieces;

        for (Piece[] row : grid) Arrays.fill(row, null);
        for (Piece p : pieces) {
            grid[p.getRow()][p.getCol()] = p;
        }
    }

    public void capture(Move m) {
        Piece cap = m.getCapture();
        if (cap != null) {
            pieces.remove(cap);
            grid[cap.getRow()][cap.getCol()] = null;
        }
    }

    public void moveOnGrid(Piece p, int fromCol, int fromRow) {
        grid[fromRow][fromCol] = null;
        grid[p.getRow()][p.getCol()] = p;
    }
}