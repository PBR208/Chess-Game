package engine.core;

/*
 * Purpose: Moves packs a chess move into a single int, so the search can keep move lists in plain
 * arrays without allocating an object per move. Six bits hold the square a move starts on, six the
 * square it ends on, two the piece a pawn promotes to and two a flag that marks promotions, en
 * passant captures and castling. Those four kinds are exactly the moves that change the position in
 * a way the from and to squares alone do not describe, which is what make and unmake need to know.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public final class Moves {

    // an ordinary move, including a normal capture and a double pawn push
    public static final int FLAG_NORMAL = 0;
    // a pawn reaching the last rank, the new piece is in the promotion bits
    public static final int FLAG_PROMOTION = 1;
    // a pawn capturing a pawn that just passed it, the captured pawn is not on the target square
    public static final int FLAG_EN_PASSANT = 2;
    // a king moving two squares, the rook moves along with it
    public static final int FLAG_CASTLING = 3;

    // promotion pieces in the order the two promotion bits count
    public static final int PROMOTION_KNIGHT = 0;
    public static final int PROMOTION_BISHOP = 1;
    public static final int PROMOTION_ROOK = 2;
    public static final int PROMOTION_QUEEN = 3;

    // no legal move has all bits zero, so this can stand for "no move at all"
    public static final int NONE = 0;

    private Moves() {
    }

    /**
     * Packs an ordinary move.
     * <p>
     * Most moves are fully described by where they start and where they end, including captures and
     * the double pawn push. I put the starting square into the lowest six bits and the target square
     * into the next six.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pFrom square the move starts on, 0 to 63
     * @param pTo   square the move ends on, 0 to 63
     * @return the packed move
     */
    public static int encode(int pFrom, int pTo) {
        return pFrom | (pTo << 6);
    }

    /**
     * Packs a pawn promotion.
     * <p>
     * A promotion needs the piece the pawn becomes, otherwise the same from and to squares would
     * describe four different moves. I add the promotion piece and the promotion flag to the two
     * squares.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pFrom           square the pawn starts on, 0 to 63
     * @param pTo             square on the last rank, 0 to 63
     * @param pPromotionPiece PROMOTION_KNIGHT, PROMOTION_BISHOP, PROMOTION_ROOK or PROMOTION_QUEEN
     * @return the packed move
     */
    public static int encodePromotion(int pFrom, int pTo, int pPromotionPiece) {
        return encode(pFrom, pTo) | (pPromotionPiece << 12) | (FLAG_PROMOTION << 14);
    }

    /**
     * Packs an en passant capture.
     * <p>
     * The captured pawn does not stand on the target square, so unmake could not put it back from
     * the squares alone. The flag says that the pawn behind the target square has to be removed and
     * restored.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pFrom square the capturing pawn starts on, 0 to 63
     * @param pTo   en passant square the pawn ends on, 0 to 63
     * @return the packed move
     */
    public static int encodeEnPassant(int pFrom, int pTo) {
        return encode(pFrom, pTo) | (FLAG_EN_PASSANT << 14);
    }

    /**
     * Packs a castling move.
     * <p>
     * Castling moves two pieces at once, so the rook move has to be derived from the king move. The
     * flag marks the king move as castling, and the side is clear from the target square.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pFrom square the king starts on, e1 or e8
     * @param pTo   square the king ends on, g1, c1, g8 or c8
     * @return the packed move
     */
    public static int encodeCastling(int pFrom, int pTo) {
        return encode(pFrom, pTo) | (FLAG_CASTLING << 14);
    }

    /**
     * Returns the square a move starts on.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove packed move
     * @return the starting square, 0 to 63
     */
    public static int from(int pMove) {
        return pMove & 63;
    }

    /**
     * Returns the square a move ends on.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove packed move
     * @return the target square, 0 to 63
     */
    public static int to(int pMove) {
        return (pMove >>> 6) & 63;
    }

    /**
     * Returns the kind of a move.
     * <p>
     * Make and unmake branch on this to decide whether a rook moves along, a pawn turns into another
     * piece or a captured pawn sits somewhere other than the target square.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove packed move
     * @return FLAG_NORMAL, FLAG_PROMOTION, FLAG_EN_PASSANT or FLAG_CASTLING
     */
    public static int flag(int pMove) {
        return (pMove >>> 14) & 3;
    }

    /**
     * Returns the piece a promoting pawn becomes.
     * <p>
     * Only meaningful for a move whose flag is FLAG_PROMOTION, since the bits are unused otherwise.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove packed move
     * @return PROMOTION_KNIGHT, PROMOTION_BISHOP, PROMOTION_ROOK or PROMOTION_QUEEN
     */
    public static int promotionPiece(int pMove) {
        return (pMove >>> 12) & 3;
    }

    /**
     * Tells whether a move promotes a pawn.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove packed move
     * @return true if the move is a promotion
     */
    public static boolean isPromotion(int pMove) {
        return flag(pMove) == FLAG_PROMOTION;
    }

    /**
     * Tells whether a move captures en passant.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove packed move
     * @return true if the move is an en passant capture
     */
    public static boolean isEnPassant(int pMove) {
        return flag(pMove) == FLAG_EN_PASSANT;
    }

    /**
     * Tells whether a move castles.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove packed move
     * @return true if the move is castling
     */
    public static boolean isCastling(int pMove) {
        return flag(pMove) == FLAG_CASTLING;
    }

    /**
     * Returns the piece type a promotion produces.
     * <p>
     * The two promotion bits count from knight to queen, while the rest of the core uses the piece
     * types of the Pieces class. I shift the promotion piece into that range, where knight is one.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove packed promotion move
     * @return the piece type, Pieces.KNIGHT up to Pieces.QUEEN
     */
    public static int promotionType(int pMove) {
        // knight is 0 here and 1 there, so the ranges differ by exactly one
        return promotionPiece(pMove) + Pieces.KNIGHT;
    }

    /**
     * Writes a move the way the UCI protocol spells it.
     * <p>
     * A UCI move is the starting square, the target square and, for a promotion, the new piece as a
     * lower case letter, for example e2e4, e1g1 for castling and e7e8q. I put those parts together,
     * which is the exact text a chess GUI expects later on.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) for the short text.
     *
     * @param pMove packed move
     * @return the move in long algebraic notation, never null
     */
    public static String toUci(int pMove) {
        String text = Bitboards.nameOf(from(pMove)) + Bitboards.nameOf(to(pMove));
        // only a promotion carries a piece letter
        if (isPromotion(pMove)) {
            text += Character.toLowerCase(Pieces.fenCharOf(Pieces.make(Pieces.WHITE, promotionType(pMove))));
        }
        return text;
    }
}
