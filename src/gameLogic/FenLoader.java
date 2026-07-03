package gameLogic;

public class FenLoader {

    public static char[][] parse(String fen) {
        char[][] grid = new char[8][8];
        String placement = fen.split(" ")[0]; // piece-placement is the first field

        String[] ranks = placement.split("/");
        for (int row = 0; row < 8 && row < ranks.length; row++) {
            int col = 0;
            for (char c : ranks[row].toCharArray()) {
                if (Character.isDigit(c)) {
                    col += c - '0';
                } else {
                    if (col < 8) grid[row][col++] = c;
                }
            }
        }
        return grid;
    }

    //Whose turn is reflected in this FEN — 'w' or 'b'
    public static boolean isWhiteTurn(String fen) {
        String[] fields = fen.split(" ");
        return fields.length < 2 || fields[1].equals("w");
    }
}