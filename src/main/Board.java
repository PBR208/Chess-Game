package main;

import pieces.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Board extends JPanel{

    private int tileSize = 85;

    private int rows = 8;
    private int cols = 8;

    private ArrayList<Piece> pieces = new ArrayList<>();

    private Piece selectedPiece;
    private int enPassantTile = -1;

    private Input input = new Input(this);

    private CheckScanner cs = new CheckScanner(this);


    public Board(){
        JFrame board = new JFrame();
        this.setPreferredSize(new Dimension(cols * tileSize, rows * tileSize));

        this.addMouseListener(input);
        this.addMouseMotionListener(input);

        addPieces();
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
        for (Piece p : pieces){
            p.paint(g2d);
        }
    }

    // SECTION for making MOVES

    public void makeMove(Move m){

        if (m.getPiece() instanceof King && Math.abs(m.getNewCol() - m.getPiece().getCol()) == 2) {
            castle((King)m.getPiece(), m.getNewCol());
        }

        if (m.getPiece().getName().equals("Pawn")){
            movePawn(m);
        } else {
            m.getPiece().setCol(m.getNewCol());
            m.getPiece().setRow(m.getNewRow());
            m.getPiece().setxPos(m.getNewCol() * tileSize);
            m.getPiece().setyPos(m.getNewRow() * tileSize);

            m.getPiece().setFirstmove(false);

            capture(m);
        }

        checkGameEnd(m);
    }

    public void movePawn(Move m){

        // en passent
        int colorIndex = m.getPiece().isWhite() ? 1 : -1;

        if (getTileNum(m.getNewCol(), m.getNewRow()) == enPassantTile){
            m.setCapture(getPiece(m.getNewCol(), m.getNewRow() + colorIndex));
        }

        if (Math.abs(m.getPiece().getRow() - m.getNewRow()) == 2){
            enPassantTile = getTileNum(m.getNewCol(), m.getNewRow() + colorIndex);
        } else {
            enPassantTile = -1;
        }

        //promotion

        colorIndex = m.getPiece().isWhite() ? 0 : 7;
        if (m.getNewRow() == colorIndex){
            promotePawn(m);
        }

        m.getPiece().setCol(m.getNewCol());
        m.getPiece().setRow(m.getNewRow());
        m.getPiece().setxPos(m.getNewCol() * tileSize);
        m.getPiece().setyPos(m.getNewRow() * tileSize);

        m.getPiece().setFirstmove(false);

        capture(m);
    }

    private void promotePawn(Move m){

            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);

            PromoteGUI dialog = new PromoteGUI(frame);
            PromoteGUI.Choice choice = dialog.showDialog();

            Piece newPiece;

            boolean white = m.getPiece().isWhite();

            switch (choice) {
                case ROOK:
                    newPiece = new Rook(this, m.getNewCol(), m.getNewRow(), white);
                    break;

                case BISHOP:
                    newPiece = new Bishop(this, m.getNewCol(), m.getNewRow(), white);
                    break;

                case KNIGHT:
                    newPiece = new Knight(this, m.getNewCol(), m.getNewRow(), white);
                    break;

                default:
                    newPiece = new Queen(this, m.getNewCol(), m.getNewRow(), white);
                    break;
            }

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

    private void checkGameEnd(Move m){

        boolean nextPlayer = !m.getPiece().isWhite();
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(this);

        if(cs.isCheckmate(nextPlayer)){
            String winner = m.getPiece().isWhite() ? "White wins!" : "Black wins!";

            EndScreen screen = new EndScreen(parent, winner);
            screen.setVisible(true);

            //restart game

        } else if(cs.isStalemate(nextPlayer)) {
            EndScreen screen = new EndScreen(parent, "Stalemate - Draw");
            screen.setVisible(true);

            //restart game
        }
    }

    public void capture(Move m){
        pieces.remove(m.getCapture());
    }

    public boolean isValidMove(Move m){

        if (!isSameTeam(m.getPiece(), m.getCapture())) {
            if (m.getPiece().isValidMovement(m.getNewCol(), m.getNewRow())) {
                if (!m.getPiece().isValidCollide(m.getNewCol(), m.getNewRow())) {
                    if (!cs.isKingLeftInCheck(m)) {
                        return true;
                    }
                }
            }
        }

        return false;
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



    // GETTER

    public Piece getPiece(int col, int row){
        for (Piece p : pieces){
            if (p.getCol() == col && p.getRow() == row){
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

    public int getTileNum(int col, int row){
        return row * rows + col * cols;
    }

    public int getEnPassantTile() {
        return enPassantTile;
    }

    public List<Piece> getPieces(){

        return pieces;
    }

    public CheckScanner getCs() {
        return cs;
    }

    // SETTER

    public void setSelectedPiece(Piece selectedPiece) {
        this.selectedPiece = selectedPiece;
    }

    public void removePiece(Piece p){
        pieces.remove(p);
    }


}
