package gameLogic;

import gui.Board;
import pieces.Piece;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class Input extends MouseAdapter {

    private final Board b;
    private final GameController gc;

    public Input(Board b, GameController gc) {
        this.b = b;
        this.gc = gc;
    }

    @Override
    public void mousePressed(MouseEvent e) {

        int col = e.getX() / b.getTileSize();
        int row = e.getY() / b.getTileSize();

        Piece pAtLocation = b.getPiece(col, row);
        if (pAtLocation != null) {
            b.setSelectedPiece(pAtLocation);
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {

        if (b.getSelectedPiece() != null) {
            b.getSelectedPiece().setxPos(e.getX() - b.getTileSize() / 2); // /2 for centering on the tile
            b.getSelectedPiece().setyPos(e.getY() - b.getTileSize() / 2);

            b.repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {

        int col = e.getX() / b.getTileSize();
        int row = e.getY() / b.getTileSize();

        if (b.getSelectedPiece() != null) {
            Move m = new Move(b, b.getSelectedPiece(), col, row);

            if (gc.isValidMove(m)) {
                gc.makeMove(m);
            } else {
                b.getSelectedPiece().setxPos(b.getSelectedPiece().getCol() * b.getTileSize());
                b.getSelectedPiece().setyPos(b.getSelectedPiece().getRow() * b.getTileSize());
            }
        }

        b.setSelectedPiece(null);
        b.repaint();
    }
}
