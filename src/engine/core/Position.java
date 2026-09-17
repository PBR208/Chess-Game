package engine.core;

/*
 * Purpose: Position is the new home of everything that makes up a chess position: twelve bitboards
 * with one bit per occupied square, a plain array that says which piece stands on a square, the side
 * to move, the castling rights, the en passant square, both move counters and a Zobrist key. It can
 * play a move and take it back again, which the old model could not do at all, and it does that
 * without allocating anything, so a search can walk millions of moves through one object. Castling
 * rights are stored explicitly instead of being guessed from a first-move flag, which also fixes
 * positions that were loaded rather than played from the start.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

public final class Position {

    // castling rights as one bit each, the four bits together are the rights mask
    public static final int WHITE_KINGSIDE = 1;
    public static final int WHITE_QUEENSIDE = 2;
    public static final int BLACK_KINGSIDE = 4;
    public static final int BLACK_QUEENSIDE = 8;
    public static final int ALL_CASTLING_RIGHTS = 15;

    // stands for "no en passant square in this position"
    public static final int NO_EN_PASSANT = -1;

    // deepest line the undo stack can hold, far beyond any real game or search
    public static final int MAX_PLY = 1024;

    // squares the rights depend on, so one table can clear them for any move
    private static final int[] CASTLING_MASK = new int[Bitboards.SQUARE_COUNT];

    static {
        // a move that neither starts nor ends on one of these squares changes no rights
        for (int square = 0; square < Bitboards.SQUARE_COUNT; square++) {
            CASTLING_MASK[square] = ALL_CASTLING_RIGHTS;
        }
        // a king that moves loses both of its rights
        CASTLING_MASK[Bitboards.squareOf("e1")] = ALL_CASTLING_RIGHTS & ~(WHITE_KINGSIDE | WHITE_QUEENSIDE);
        CASTLING_MASK[Bitboards.squareOf("e8")] = ALL_CASTLING_RIGHTS & ~(BLACK_KINGSIDE | BLACK_QUEENSIDE);
        // a rook that moves or is captured loses the right on its own side
        CASTLING_MASK[Bitboards.squareOf("h1")] = ALL_CASTLING_RIGHTS & ~WHITE_KINGSIDE;
        CASTLING_MASK[Bitboards.squareOf("a1")] = ALL_CASTLING_RIGHTS & ~WHITE_QUEENSIDE;
        CASTLING_MASK[Bitboards.squareOf("h8")] = ALL_CASTLING_RIGHTS & ~BLACK_KINGSIDE;
        CASTLING_MASK[Bitboards.squareOf("a8")] = ALL_CASTLING_RIGHTS & ~BLACK_QUEENSIDE;
    }

    // one bitboard per piece code, bit n means that piece stands on square n
    private final long[] piecesByCode = new long[Pieces.COUNT];
    // every square a side occupies, indexed by colour
    private final long[] occupancyByColour = new long[2];
    // every occupied square
    private long occupancy;
    // which piece stands on a square, Pieces.NONE for an empty one
    private final int[] board = new int[Bitboards.SQUARE_COUNT];

    private int sideToMove = Pieces.WHITE;
    private int castlingRights;
    private int epSquare = NO_EN_PASSANT;
    private int halfmoveClock;
    private int fullmoveNumber = 1;
    private long key;

    // preallocated undo stack, so playing a move allocates nothing at all
    private final int[] undoCaptured = new int[MAX_PLY];
    private final int[] undoCastling = new int[MAX_PLY];
    private final int[] undoEpSquare = new int[MAX_PLY];
    private final int[] undoHalfmove = new int[MAX_PLY];
    private final long[] undoKey = new long[MAX_PLY];
    // how many moves are currently on the stack
    private int ply;

    private Position() {
        // an empty board still has a key, made of the rights and the side to move
        java.util.Arrays.fill(board, Pieces.NONE);
        key = computeKey();
    }

    /**
     * Creates a position without any pieces on it.
     * <p>
     * Tests and, later, the FEN reader and a position editor need a board they can fill square by
     * square. I hand back an empty board with White to move, no castling rights, no en passant
     * square and both counters at their starting values.
     * <p>
     * Time complexity: O(1), the 64 squares are a fixed size. Space complexity: O(1).
     *
     * @return an empty position, never null
     */
    public static Position empty() {
        return new Position();
    }

    /**
     * Creates the standard starting position.
     * <p>
     * Every new game and most tests begin here. I place both back ranks and both pawn ranks, hand
     * all four castling rights out and leave White to move with both counters at their start.
     * <p>
     * Time complexity: O(p) for the 32 pieces placed. Space complexity: O(1) beyond the position.
     *
     * @return the position after no moves have been played, never null
     */
    public static Position startPosition() {
        Position position = new Position();
        // the back ranks mirror each other, file by file
        int[] backRank = {Pieces.ROOK, Pieces.KNIGHT, Pieces.BISHOP, Pieces.QUEEN,
                Pieces.KING, Pieces.BISHOP, Pieces.KNIGHT, Pieces.ROOK};
        for (int file = 0; file < 8; file++) {
            position.put(Pieces.make(Pieces.WHITE, backRank[file]), Bitboards.square(file, 0));
            position.put(Pieces.WHITE_PAWN, Bitboards.square(file, 1));
            position.put(Pieces.BLACK_PAWN, Bitboards.square(file, 6));
            position.put(Pieces.make(Pieces.BLACK, backRank[file]), Bitboards.square(file, 7));
        }
        position.setCastlingRights(ALL_CASTLING_RIGHTS);
        return position;
    }

    /**
     * Puts a piece on an empty square while setting a position up.
     * <p>
     * Building a position by hand is what the starting position, the tests and later the FEN reader
     * do. I add the piece to its bitboard, to both occupancy sets and to the square array, and xor
     * its number into the key so the key stays correct without a full recount.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPiece  piece code, Pieces.WHITE_PAWN up to Pieces.BLACK_KING
     * @param pSquare square to put it on, 0 to 63, must be empty
     * @throws IllegalArgumentException if the square is already occupied
     */
    public void put(int pPiece, int pSquare) {
        // two pieces on one square would corrupt every bitboard that follows
        if (board[pSquare] != Pieces.NONE) {
            throw new IllegalArgumentException("square " + Bitboards.nameOf(pSquare) + " is already occupied");
        }
        addPiece(pPiece, pSquare);
    }

    /**
     * Takes the piece off a square while setting a position up.
     * <p>
     * A position editor and some tests need to clear a square again. I remove the piece from its
     * bitboard, both occupancy sets and the square array, and xor its number back out of the key.
     * An empty square is left alone rather than treated as an error, so clearing twice is harmless.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare square to clear, 0 to 63
     */
    public void clear(int pSquare) {
        if (board[pSquare] != Pieces.NONE) {
            removePiece(pSquare);
        }
    }

    /**
     * Plays a move and remembers everything needed to take it back.
     * <p>
     * This is the one place a position changes during a game or a search. I push the captured piece,
     * the castling rights, the en passant square, the fifty move counter and the key onto the undo
     * stack, then clear the old en passant square, remove a captured piece, which for en passant does
     * not stand on the target square, move the piece itself, turn a promoting pawn into its new
     * piece, move the rook along when castling, and set a new en passant square after a double push,
     * but only when an enemy pawn stands next to the arriving pawn and could really capture it. Then
     * I update the castling rights from the squares the move touched, reset or raise the fifty move
     * counter, hand the turn over and count a full move once Black has moved. Nothing here allocates.
     * <p>
     * Time complexity: O(1), every step touches a fixed number of squares.
     * Space complexity: O(1), the undo stack was allocated once.
     *
     * @param pMove packed move as produced by the Moves class, legal in this position
     * @throws IllegalStateException if more than MAX_PLY moves are on the undo stack
     */
    public void makeMove(int pMove) {
        // a search deeper than the stack would silently corrupt the undo history
        if (ply >= MAX_PLY) {
            throw new IllegalStateException("the undo stack holds at most " + MAX_PLY + " moves");
        }

        int from = Moves.from(pMove);
        int to = Moves.to(pMove);
        int flag = Moves.flag(pMove);
        int us = sideToMove;
        int them = 1 - us;
        int moved = board[from];

        // the square a captured pawn stands on differs from the target square for en passant
        int captureSquare = flag == Moves.FLAG_EN_PASSANT ? (us == Pieces.WHITE ? to - 8 : to + 8) : to;
        int captured = board[captureSquare];

        undoCaptured[ply] = captured;
        undoCastling[ply] = castlingRights;
        undoEpSquare[ply] = epSquare;
        undoHalfmove[ply] = halfmoveClock;
        undoKey[ply] = key;
        ply++;

        // the old en passant option is gone as soon as any move is played
        setEpSquare(NO_EN_PASSANT);

        // a capture or a pawn move starts the fifty move count over
        if (captured != Pieces.NONE) {
            removePiece(captureSquare);
            halfmoveClock = 0;
        } else {
            halfmoveClock++;
        }
        if (Pieces.typeOf(moved) == Pieces.PAWN) {
            halfmoveClock = 0;
        }

        movePiece(from, to);

        if (flag == Moves.FLAG_PROMOTION) {
            // the pawn is gone, its square now holds the piece the player chose
            removePiece(to);
            addPiece(Pieces.make(us, Moves.promotionType(pMove)), to);
        } else if (flag == Moves.FLAG_CASTLING) {
            // the king has moved already, the rook follows to the square it jumps over
            moveCastlingRook(to, false);
        } else if (Pieces.typeOf(moved) == Pieces.PAWN && Math.abs(to - from) == 16) {
            rememberEnPassantSquare(from, to, them);
        }

        // a king or rook that moved, and a rook that was captured, lose their rights
        setCastlingRights(castlingRights & CASTLING_MASK[from] & CASTLING_MASK[to]);

        sideToMove = them;
        key ^= Zobrist.sideToMove();
        // a full move is complete once Black has moved
        if (sideToMove == Pieces.WHITE) {
            fullmoveNumber++;
        }
    }

    /**
     * Takes back the move that was played last.
     * <p>
     * A search walks down a line and has to come back up again, so every change of makeMove has to be
     * reversible exactly. I hand the turn back first, then undo the move itself: a promoted piece
     * turns into the pawn it came from, the rook of a castling move goes back to its corner, the
     * piece returns to the square it came from and a captured piece reappears on the square it stood
     * on, which for en passant is behind the target square. The rights, the en passant square, the
     * fifty move counter and the key come back from the undo stack, so they are restored and not
     * recomputed.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMove the same packed move that was passed to makeMove
     * @throws IllegalStateException if no move is on the undo stack
     */
    public void unmakeMove(int pMove) {
        // taking back more moves than were played means the caller lost track
        if (ply <= 0) {
            throw new IllegalStateException("there is no move left to take back");
        }
        ply--;

        int from = Moves.from(pMove);
        int to = Moves.to(pMove);
        int flag = Moves.flag(pMove);

        // the side that made the move is to move again
        if (sideToMove == Pieces.WHITE) {
            fullmoveNumber--;
        }
        sideToMove = 1 - sideToMove;
        int us = sideToMove;

        if (flag == Moves.FLAG_PROMOTION) {
            // the new piece disappears and the pawn comes back to where it started
            removePiece(to);
            addPiece(Pieces.make(us, Pieces.PAWN), from);
        } else {
            movePiece(to, from);
            if (flag == Moves.FLAG_CASTLING) {
                moveCastlingRook(to, true);
            }
        }

        int captured = undoCaptured[ply];
        if (captured != Pieces.NONE) {
            // the pawn taken en passant stood behind the square the capturing pawn moved to
            int captureSquare = flag == Moves.FLAG_EN_PASSANT ? (us == Pieces.WHITE ? to - 8 : to + 8) : to;
            addPiece(captured, captureSquare);
        }

        castlingRights = undoCastling[ply];
        epSquare = undoEpSquare[ply];
        halfmoveClock = undoHalfmove[ply];
        // restoring the saved key is exact and cannot drift over a long search
        key = undoKey[ply];
    }

    /**
     * Recomputes the position key from scratch.
     * <p>
     * Every move keeps the key up to date by xoring only what it changes, and a mistake there would
     * stay unnoticed for a long time. This walks the whole board instead, so a test can compare the
     * cheap key against the honest one, and the constructor uses it for its starting value.
     * <p>
     * Time complexity: O(64) for the squares of the board. Space complexity: O(1).
     *
     * @return the key this position should have
     */
    public long computeKey() {
        long computed = 0L;
        for (int square = 0; square < Bitboards.SQUARE_COUNT; square++) {
            int piece = board[square];
            if (piece != Pieces.NONE) {
                computed ^= Zobrist.piece(piece, square);
            }
        }
        computed ^= Zobrist.castling(castlingRights);
        // only a real en passant square is part of the key, and only by its file
        if (epSquare != NO_EN_PASSANT) {
            computed ^= Zobrist.enPassantFile(Bitboards.fileOf(epSquare));
        }
        if (sideToMove == Pieces.BLACK) {
            computed ^= Zobrist.sideToMove();
        }
        return computed;
    }

    /**
     * Sets the side to move while a position is being built.
     * <p>
     * A position read from FEN can have either side to move. I keep the key in step by xoring the
     * side number in or out whenever the side really changes.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pColour Pieces.WHITE or Pieces.BLACK
     */
    public void setSideToMove(int pColour) {
        if (pColour != sideToMove) {
            sideToMove = pColour;
            key ^= Zobrist.sideToMove();
        }
    }

    /**
     * Sets the castling rights.
     * <p>
     * The rights are part of the position, so the key has to follow them. I xor the old combination
     * out and the new one in, which is also what every move does when it takes a right away.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pCastlingRights four bit mask built from WHITE_KINGSIDE, WHITE_QUEENSIDE,
     *                        BLACK_KINGSIDE and BLACK_QUEENSIDE, 0 to 15
     */
    public void setCastlingRights(int pCastlingRights) {
        if (pCastlingRights != castlingRights) {
            key ^= Zobrist.castling(castlingRights) ^ Zobrist.castling(pCastlingRights);
            castlingRights = pCastlingRights;
        }
    }

    /**
     * Sets the square a pawn could be captured on en passant.
     * <p>
     * Only the file of that square is part of the key, since its rank follows from the side to move.
     * I xor the old file out and the new one in, and NO_EN_PASSANT simply contributes nothing.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare the en passant square, or NO_EN_PASSANT when there is none
     */
    public void setEpSquare(int pSquare) {
        if (pSquare == epSquare) {
            return;
        }
        if (epSquare != NO_EN_PASSANT) {
            key ^= Zobrist.enPassantFile(Bitboards.fileOf(epSquare));
        }
        epSquare = pSquare;
        if (epSquare != NO_EN_PASSANT) {
            key ^= Zobrist.enPassantFile(Bitboards.fileOf(epSquare));
        }
    }

    /**
     * Sets the number of half moves since the last capture or pawn move.
     * <p>
     * A position loaded from FEN brings its own counter, which decides how close the fifty move rule
     * already is. The counter is not part of the key, because two positions with different counters
     * are still the same position for the repetition rule.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pHalfmoveClock half moves since the last capture or pawn move, 0 or more
     */
    public void setHalfmoveClock(int pHalfmoveClock) {
        this.halfmoveClock = pHalfmoveClock;
    }

    /**
     * Sets the number of the full move that is about to be played.
     * <p>
     * FEN counts full moves starting at one and raises the number after every black move. Like the
     * fifty move counter it is not part of the key.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pFullmoveNumber full move number, 1 or more
     */
    public void setFullmoveNumber(int pFullmoveNumber) {
        this.fullmoveNumber = pFullmoveNumber;
    }

    /**
     * Returns the piece standing on a square.
     * <p>
     * Make and unmake, move generation and the FEN writer all ask this constantly, so the position
     * keeps a plain array beside the bitboards and answers without searching.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare square number, 0 to 63
     * @return the piece code, or Pieces.NONE for an empty square
     */
    public int pieceAt(int pSquare) {
        return board[pSquare];
    }

    /**
     * Returns every square one kind of piece stands on.
     * <p>
     * Move generation works one piece kind at a time, so it needs the bitboard of that kind.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPiece piece code, Pieces.WHITE_PAWN up to Pieces.BLACK_KING
     * @return the bitboard of that piece
     */
    public long pieces(int pPiece) {
        return piecesByCode[pPiece];
    }

    /**
     * Returns every occupied square.
     * <p>
     * Sliding pieces stop at the first occupied square, so their move generation needs all pieces at
     * once, whatever their colour.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the bitboard of all pieces
     */
    public long occupancy() {
        return occupancy;
    }

    /**
     * Returns every square one side occupies.
     * <p>
     * A move may not end on a square of its own side and a capture must end on one of the other, so
     * both sets are kept ready.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pColour Pieces.WHITE or Pieces.BLACK
     * @return the bitboard of that side's pieces
     */
    public long occupancy(int pColour) {
        return occupancyByColour[pColour];
    }

    /**
     * Returns the square a king stands on.
     * <p>
     * Check detection starts at the king, so its square is asked for on every legality test. A side
     * always has exactly one king in a legal position, so the lowest square of that bitboard is it.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pColour Pieces.WHITE or Pieces.BLACK
     * @return the king's square, or 64 if that side has no king on the board
     */
    public int kingSquare(int pColour) {
        return Bitboards.lowestSquare(piecesByCode[Pieces.make(pColour, Pieces.KING)]);
    }

    public int sideToMove() {
        return sideToMove;
    }

    public int castlingRights() {
        return castlingRights;
    }

    public int epSquare() {
        return epSquare;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public long key() {
        return key;
    }

    /**
     * Returns how many moves can still be taken back.
     * <p>
     * Tests and, later, the search use this to check that make and unmake stay in balance.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the number of moves on the undo stack, 0 or more
     */
    public int ply() {
        return ply;
    }

    /**
     * Draws the position as eight lines of text.
     * <p>
     * A failing test has to show what the board looked like, and a bitboard alone is unreadable. I
     * print rank eight first with the FEN letter of every piece and a dot for an empty square.
     * <p>
     * Time complexity: O(64). Space complexity: O(1) for the fixed size text.
     *
     * @return the board as text, never null
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            for (int file = 0; file < 8; file++) {
                int piece = board[Bitboards.square(file, rank)];
                text.append(piece == Pieces.NONE ? '.' : Pieces.fenCharOf(piece));
            }
            text.append('\n');
        }
        return text.toString();
    }

    /**
     * Moves the rook that belongs to a castling king move.
     * <p>
     * Castling is stored as the king's move alone, so the rook has to be derived from the square the
     * king lands on. I move it from its corner to the square the king crossed, or back again when a
     * castling move is taken off the board.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pKingTarget square the king castles to, g1, c1, g8 or c8
     * @param pUndo       false while playing the move, true while taking it back
     * @throws IllegalArgumentException if the king target is not a castling square
     */
    private void moveCastlingRook(int pKingTarget, boolean pUndo) {
        int rookFrom;
        int rookTo;
        // the corner and the crossed square follow from the king's target square
        switch (pKingTarget) {
            case 6 -> { rookFrom = 7; rookTo = 5; }
            case 2 -> { rookFrom = 0; rookTo = 3; }
            case 62 -> { rookFrom = 63; rookTo = 61; }
            case 58 -> { rookFrom = 56; rookTo = 59; }
            default -> throw new IllegalArgumentException(
                    "a king cannot castle to " + Bitboards.nameOf(pKingTarget));
        }
        if (pUndo) {
            movePiece(rookTo, rookFrom);
        } else {
            movePiece(rookFrom, rookTo);
        }
    }

    /**
     * Sets the en passant square after a double pawn push, if it can be used at all.
     * <p>
     * FEN records the skipped square after every double push, but a square no pawn can capture on
     * makes two otherwise identical positions look different, which breaks repetition detection and
     * pollutes the key. I only remember the square when an enemy pawn stands directly beside the
     * pawn that just arrived.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pFrom square the pawn started on
     * @param pTo   square the pawn arrived on
     * @param pThem colour of the side that could capture en passant
     */
    private void rememberEnPassantSquare(int pFrom, int pTo, int pThem) {
        long enemyPawns = piecesByCode[Pieces.make(pThem, Pieces.PAWN)];
        int file = Bitboards.fileOf(pTo);
        long neighbours = 0L;
        // a pawn on the a-file or h-file only has one neighbour
        if (file > 0) {
            neighbours |= Bitboards.bit(pTo - 1);
        }
        if (file < 7) {
            neighbours |= Bitboards.bit(pTo + 1);
        }
        if ((enemyPawns & neighbours) != 0L) {
            // the skipped square lies exactly between start and target
            setEpSquare((pFrom + pTo) / 2);
        }
    }

    /**
     * Puts a piece on a square and keeps the bitboards, the array and the key in step.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pPiece  piece code to add
     * @param pSquare square it goes on, must be empty
     */
    private void addPiece(int pPiece, int pSquare) {
        long bit = Bitboards.bit(pSquare);
        piecesByCode[pPiece] |= bit;
        occupancyByColour[Pieces.colourOf(pPiece)] |= bit;
        occupancy |= bit;
        board[pSquare] = pPiece;
        key ^= Zobrist.piece(pPiece, pSquare);
    }

    /**
     * Takes the piece off a square and keeps the bitboards, the array and the key in step.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pSquare square to clear, must hold a piece
     */
    private void removePiece(int pSquare) {
        int piece = board[pSquare];
        long bit = Bitboards.bit(pSquare);
        piecesByCode[piece] &= ~bit;
        occupancyByColour[Pieces.colourOf(piece)] &= ~bit;
        occupancy &= ~bit;
        board[pSquare] = Pieces.NONE;
        key ^= Zobrist.piece(piece, pSquare);
    }

    /**
     * Moves a piece from one square to another, both of which it owns.
     * <p>
     * Doing this in one step instead of a remove and an add keeps the key updates to the two squares
     * that really change.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pFrom square the piece stands on
     * @param pTo   empty square it moves to
     */
    private void movePiece(int pFrom, int pTo) {
        int piece = board[pFrom];
        long move = Bitboards.bit(pFrom) | Bitboards.bit(pTo);
        piecesByCode[piece] ^= move;
        occupancyByColour[Pieces.colourOf(piece)] ^= move;
        occupancy ^= move;
        board[pFrom] = Pieces.NONE;
        board[pTo] = piece;
        key ^= Zobrist.piece(piece, pFrom) ^ Zobrist.piece(piece, pTo);
    }
}
