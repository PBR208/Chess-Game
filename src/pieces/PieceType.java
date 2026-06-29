package pieces;

public enum PieceType {
    KING("King"),
    QUEEN("Queen"),
    ROOK("Rook"),
    BISHOP("Bishop"),
    KNIGHT("Knight"),
    PAWN("Pawn");

    private final String displayName;

    PieceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
