package engine.imports;

import engine.pieces.PieceType;

public interface PromotionChooser {
    PieceType choose(boolean whitePromoting);
}