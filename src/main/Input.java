package main;

import pieces.Piece;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class Input extends MouseAdapter {

    Board b;

    public Input(Board b){
        this.b = b;
    }

    @Override
    public void mousePressed(MouseEvent e) {

        int col = e.getX() / b.tileSize;
        int row = e.getY() / b.tileSize;

        Piece pAtLocation = b.getPiece(col, row);
        if (pAtLocation != null){
            b.selectedPiece = pAtLocation;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {

        if (b.selectedPiece != null){
            b.selectedPiece.setxPos(e.getX() - b.tileSize / 2); // /2 for centering on the tile
            b.selectedPiece.setyPos(e.getY() - b.tileSize / 2);

            b.repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {

        int col = e.getX() / b.tileSize;
        int row = e.getY() / b.tileSize;

        if (b.selectedPiece != null){
            Move m = new Move(b, b.selectedPiece, col, row);

            if (b.isValidMove(m)){
                b.makeMove(m);
            } else {
                b.selectedPiece.setxPos(b.selectedPiece.getCol() * b.tileSize);
                b.selectedPiece.setyPos(b.selectedPiece.getRow() * b.tileSize);
            }
        }

        b.selectedPiece = null;
        b.repaint();
    }
}
