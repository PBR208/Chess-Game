package gameLogic;

import pieces.PieceType;

public interface PromotionChooser {
    PieceType choose(boolean whitePromoting);
}