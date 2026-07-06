package engine.imports;

import engine.pieces.*;

public class FenGenerator {

    // Spritesheet/type order confirmed from piece constructors:
    // King, Queen, Bishop, Knight, Rook, Pawn

    private final BoardState state;

    public FenGenerator(BoardState state) {
        this.state = state;
    }

    public String generate(boolean isWhiteTurn, int halfMoveClock, int fullMove) {
        StringBuilder fen = new StringBuilder();

        for (int row = 0; row < 8; row++) {
            int empty = 0;
            for (int col = 0; col < 8; col++) {
                Piece p = state.getPiece(col, row);
                if (p == null) {
                    empty++;
                } else {
                    if (empty > 0) {
                        fen.append(empty);
                        empty = 0;
                    }
                    fen.append(fenChar(p));
                }
            }
            if (empty > 0) fen.append(empty);
            if (row < 7) fen.append('/');
        }

        fen.append(isWhiteTurn ? " w " : " b ");

        String castling = buildCastling();
        fen.append(castling.isEmpty() ? "-" : castling).append(' ');

        int epTile = state.getEnPassantTile();
        if (epTile == -1) {
            fen.append('-');
        } else {
            int epCol = epTile % 8;
            int epRow = epTile / 8;
            fen.append((char) ('a' + epCol)).append(8 - epRow);
        }

        fen.append(' ').append(halfMoveClock);
        fen.append(' ').append(fullMove);

        return fen.toString();
    }

    private char fenChar(Piece p) {
        char c = switch (p.getType().getDisplayName()) {
            case "King" -> 'k';
            case "Queen" -> 'q';
            case "Bishop" -> 'b';
            case "Knight" -> 'n';
            case "Rook" -> 'r';
            default -> 'p';
        };
        return p.isWhite() ? Character.toUpperCase(c) : c;
    }

    private String buildCastling() {
        StringBuilder sb = new StringBuilder();

        Piece wKing = state.getPiece(4, 7);
        Piece wKsRook = state.getPiece(7, 7);
        Piece wQsRook = state.getPiece(0, 7);
        Piece bKing = state.getPiece(4, 0);
        Piece bKsRook = state.getPiece(7, 0);
        Piece bQsRook = state.getPiece(0, 0);

        if (isKingUnmoved(wKing, true)) {
            if (isRookUnmoved(wKsRook, true)) sb.append('K');
            if (isRookUnmoved(wQsRook, true)) sb.append('Q');
        }
        if (isKingUnmoved(bKing, false)) {
            if (isRookUnmoved(bKsRook, false)) sb.append('k');
            if (isRookUnmoved(bQsRook, false)) sb.append('q');
        }
        return sb.toString();
    }

    private boolean isKingUnmoved(Piece p, boolean white) {
        return p instanceof King && p.isWhite() == white && p.isFirstMove();
    }

    private boolean isRookUnmoved(Piece p, boolean white) {
        return p instanceof Rook && p.isWhite() == white && p.isFirstMove();
    }
}