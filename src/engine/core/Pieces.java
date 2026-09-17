package engine.core;

/*
 * Purpose: Pieces gives every kind of piece a small number, so a position can store pieces in plain
 * arrays and bitboards instead of objects. White pieces are 0 to 5 and black pieces 6 to 11, in the
 * order pawn, knight, bishop, rook, queen, king, which makes the colour a single comparison and the
 * piece type a remainder. I keep the FEN letters here as well, because the mapping from letter to
 * piece belongs to the piece codes and not to whoever happens to read a FEN string.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public final class Pieces {

    public static final int WHITE_PAWN = 0;
    public static final int WHITE_KNIGHT = 1;
    public static final int WHITE_BISHOP = 2;
    public static final int WHITE_ROOK = 3;
    public static final int WHITE_QUEEN = 4;
    public static final int WHITE_KING = 5;

    public static final int BLACK_PAWN = 6;
    public static final int BLACK_KNIGHT = 7;
    public static final int BLACK_BISHOP = 8;
    public static final int BLACK_ROOK = 9;
    public static final int BLACK_QUEEN = 10;
    public static final int BLACK_KING = 11;

    // how many piece codes exist, which is also the number of bitboards a position needs
    public static final int COUNT = 12;
    // stands for an empty square in the piece array
    public static final int NONE = 12;

    // the two colours, used as an index as well
    public static final int WHITE = 0;
    public static final int BLACK = 1;

    // piece types without their colour
    public static final int PAWN = 0;
    public static final int KNIGHT = 1;
    public static final int BISHOP = 2;
    public static final int ROOK = 3;
    public static final int QUEEN = 4;
    public static final int KING = 5;
    // how many types exist, pawn up to king
    public static final int TYPE_COUNT = 6;

    // FEN letters in piece code order, white in upper case as the standard requires
    private static final char[] FEN_CHARS = {'P', 'N', 'B', 'R', 'Q', 'K', 'p', 'n', 'b', 'r', 'q', 'k'};

    private Pieces() {
    }

    /**
     * Builds the piece code of a colour and a type.
     * <p>
     * Promotion and move generation know a colour and a type and need the piece code that belongs to
     * them. The six white codes come first, so a black piece is its type plus six.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pColour WHITE or BLACK
     * @param pType   piece type, PAWN up to KING
     * @return the piece code, WHITE_PAWN up to BLACK_KING
     */
    public static int make(int pColour, int pType) {
        return pColour * TYPE_COUNT + pType;
    }

    /**
     * Returns the colour of a piece.
     * <p>
     * Whose piece stands on a square decides almost every rule, so this is asked constantly. Codes
     * below six are White's, the rest are Black's.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPiece piece code, WHITE_PAWN up to BLACK_KING
     * @return WHITE or BLACK
     */
    public static int colourOf(int pPiece) {
        return pPiece < TYPE_COUNT ? WHITE : BLACK;
    }

    /**
     * Returns the type of a piece without its colour.
     * <p>
     * Move generation treats a white and a black knight the same way once the colour is known, so it
     * needs the type alone. The remainder of the code by six is that type.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPiece piece code, WHITE_PAWN up to BLACK_KING
     * @return the type, PAWN up to KING
     */
    public static int typeOf(int pPiece) {
        return pPiece % TYPE_COUNT;
    }

    /**
     * Tells whether a piece belongs to White.
     * <p>
     * Reads better than comparing the colour at every call site, and it is the most frequent
     * question about a piece.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPiece piece code, WHITE_PAWN up to BLACK_KING
     * @return true for a white piece
     */
    public static boolean isWhite(int pPiece) {
        return pPiece < TYPE_COUNT;
    }

    /**
     * Returns the FEN letter of a piece.
     * <p>
     * FEN writes white pieces in upper case and black pieces in lower case. I look the letter up in
     * the table that is ordered exactly like the piece codes.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPiece piece code, WHITE_PAWN up to BLACK_KING
     * @return the FEN letter, such as 'N' for a white knight
     * @throws IllegalArgumentException if pPiece is not a piece code
     */
    public static char fenCharOf(int pPiece) {
        // an empty square has no letter of its own in FEN, it is counted instead
        if (pPiece < 0 || pPiece >= COUNT) {
            throw new IllegalArgumentException("piece code " + pPiece + " has no FEN letter");
        }
        return FEN_CHARS[pPiece];
    }

    /**
     * Returns the piece a FEN letter stands for.
     * <p>
     * Reading a position from FEN turns letters into pieces, and an unknown letter has to be an
     * error rather than a silently wrong piece. I search the letter table and fail when it is not a
     * piece letter.
     * <p>
     * Time complexity: O(1), the table has twelve entries. Space complexity: O(1).
     *
     * @param pFenChar FEN letter such as 'q' or 'K'
     * @return the piece code, WHITE_PAWN up to BLACK_KING
     * @throws IllegalArgumentException if the letter is not a FEN piece letter
     */
    public static int fromFenChar(char pFenChar) {
        for (int piece = 0; piece < COUNT; piece++) {
            if (FEN_CHARS[piece] == pFenChar) {
                return piece;
            }
        }
        throw new IllegalArgumentException("'" + pFenChar + "' is not a FEN piece letter");
    }
}
