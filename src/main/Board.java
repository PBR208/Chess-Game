package main;

import pieces.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class Board extends JPanel{

    public int tileSize = 85;

    private int rows = 8;
    private int cols = 8;

    ArrayList<Piece> pieces = new ArrayList<>();

    public Piece selectedPiece;

    Input input = new Input(this);
    public Board(){
        JFrame board = new JFrame();
        this.setPreferredSize(new Dimension(cols * tileSize, rows * tileSize));

        this.addMouseListener(input);
        this.addMouseMotionListener(input);

        addPieces();
    }

    public Piece getPiece(int col, int row){
        for (Piece p : pieces){
            if (p.getCol() == col && p.getRow() == row){
                return p;
            }
        }
        return null;
    }


    public void addPieces(){

        // Black Pieces

        pieces.add(new Rook(this, 0, 0, false));
        pieces.add(new Rook(this, 7, 0, false));
        pieces.add(new Knight(this, 1, 0, false));
        pieces.add(new Knight(this, 6, 0, false));
        pieces.add(new Bishop(this, 2, 0, false));
        pieces.add(new Bishop(this, 5, 0, false));
        pieces.add(new Queen(this, 3, 0, false));
        pieces.add(new King(this, 4, 0, false));

        // White Pieces

        pieces.add(new Rook(this, 0, 7, true));
        pieces.add(new Rook(this, 7, 7, true));
        pieces.add(new Knight(this, 1, 7, true));
        pieces.add(new Knight(this, 6, 7, true));
        pieces.add(new Bishop(this, 2, 7, true));
        pieces.add(new Bishop(this, 5, 7, true));
        pieces.add(new Queen(this, 3, 7, true));
        pieces.add(new King(this, 4, 7, true));

        // Pawns

        for (int i = 0; i <= 7; i++) {
            pieces.add(new Pawn(this, i, 1, false));
            pieces.add(new Pawn(this, i, 6, true));
        }
    }

    public void paintComponent(Graphics g){
        Graphics2D g2d = (Graphics2D) g;

        for (int r = 0; r < rows; r++){
            for (int c = 0; c < cols; c++){
                g2d.setColor((c+r) % 2 == 0 ? new Color( 232, 235, 239) : new Color(125, 135, 150));
                g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
            }

        }

        for (Piece p : pieces){
            p.paint(g2d);
        }
    }

    public void makeMove(Move m){
        m.piece.setCol(m.newCol);
        m.piece.setRow(m.newRow);
        m.piece.setxPos(m.newCol * tileSize);
        m.piece.setyPos(m.newRow * tileSize);

        capture(m);
    }

    public void capture(Move m){
        pieces.remove(m.capture);
    }

    public boolean isValidMove(Move m){

        if (isSameTeam(m.piece, m.capture)){
            return false;
        }

        return true;
    }

    private boolean isSameTeam(Piece p1, Piece p2) {
        if (p1 != null && p2 != null) {
            if (p1.isWhite() == p2.isWhite()) {
                return true;
            }

            return false;
        }
        return false;
    }


}
