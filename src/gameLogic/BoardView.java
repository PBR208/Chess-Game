package gameLogic;

import pieces.Piece;
import pieces.PieceType;

import java.util.ArrayList;

public interface BoardView {
    void repaint();

    void switchClocks();

    void stopClocks();

    void resetClocks();

    ArrayList<Piece> freshSetup();

    Piece createPiece(PieceType type, int col, int row, boolean isWhite);
}