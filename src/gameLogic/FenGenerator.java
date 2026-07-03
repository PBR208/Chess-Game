package gameLogic;

import gui.Board;
import pieces.*;

public class FenGenerator {

    // Spritesheet/type order confirmed from piece constructors:
    // King, Queen, Bishop, Knight, Rook, Pawn

    private final Board b;

    public FenGenerator(Board b) {
        this.b = b;
    }

    public String generate(boolean isWhiteTurn, int halfMoveClock, int fullMove) {
        StringBuilder fen = new StringBuilder();

        // 1. Piece placement — rank 8 → rank 1, file a → h
        for (int row = 0; row < 8; row++) {
            int empty = 0;
            for (int col = 0; col < 8; col++) {
                Piece p = b.getPiece(col, row);
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

        // 2. Active colour
        fen.append(isWhiteTurn ? " w " : " b ");

        // 3. Castling rights
        String castling = buildCastling();
        fen.append(castling.isEmpty() ? "-" : castling).append(' ');

        // 4. En-passant target square
        int epTile = b.getEnPassantTile();
        if (epTile == -1) {
            fen.append('-');
        } else {
            int epCol = epTile % 8;
            int epRow = epTile / 8;
            fen.append((char) ('a' + epCol)).append(8 - epRow);
        }

        // 5 & 6. Clocks
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

        Piece wKing = b.getPiece(4, 7);
        Piece wKsRook = b.getPiece(7, 7);
        Piece wQsRook = b.getPiece(0, 7);
        Piece bKing = b.getPiece(4, 0);
        Piece bKsRook = b.getPiece(7, 0);
        Piece bQsRook = b.getPiece(0, 0);

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