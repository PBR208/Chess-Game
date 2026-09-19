package engine.search;

/*
 * Purpose: Evaluator puts a number on a position, in hundredths of a pawn, from the point of view of
 * the side to move. It counts material, adds a piece square table that says where each kind of piece
 * belongs, and then mobility, king safety and passed pawns. Every term is kept twice, once for a
 * board full of pieces and once for an endgame, and the two are mixed according to how much material
 * is left, because a king that belongs behind a wall of pawns in the opening belongs in the centre
 * once the queens are gone. The tables are plain Java constants, so this depends on nothing at all.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.Bitboards;
import engine.core.MoveGen;
import engine.core.Pieces;
import engine.core.Position;

public final class Evaluator {

    // what each piece type is worth with a full board, in hundredths of a pawn
    private static final int[] MIDDLEGAME_VALUE = {82, 337, 365, 477, 1025, 0};

    // and what it is worth in an endgame, where pawns matter more and knights less
    private static final int[] ENDGAME_VALUE = {94, 281, 297, 512, 936, 0};

    // how much each piece holds the game back from being an endgame
    private static final int[] PHASE_WEIGHT = {0, 1, 1, 2, 4, 0};

    // the phase of the starting position, four minors and two rooks and a queen a side
    private static final int TOTAL_PHASE = 24;

    // bonus for a passed pawn by the rank it stands on, from its own side's view
    private static final int[] PASSED_PAWN_MIDDLEGAME = {0, 5, 10, 20, 35, 60, 100, 0};
    private static final int[] PASSED_PAWN_ENDGAME = {0, 10, 20, 40, 70, 120, 180, 0};

    // what one square a piece can move to is worth, per piece type
    private static final int[] MOBILITY_MIDDLEGAME = {0, 4, 5, 2, 1, 0};
    private static final int[] MOBILITY_ENDGAME = {0, 4, 5, 4, 2, 0};

    // what each pawn still standing in front of the king is worth in the middlegame
    private static final int KING_SHIELD_BONUS = 12;

    // what an open file next to the king costs in the middlegame
    private static final int KING_OPEN_FILE_PENALTY = 18;

    // Piece square tables, written from White's point of view with a1 first and h8 last, so the
    // first row of each table is rank one. Black reads the same table with the rank turned round.

    private static final int[] PAWN_MIDDLEGAME = {
              0,   0,   0,   0,   0,   0,   0,   0,
            -35,  -1, -20, -23, -15,  24,  38, -22,
            -26,  -4,  -4, -10,   3,   3,  33, -12,
            -27,  -2,  -5,  12,  17,   6,  10, -25,
            -14,  13,   6,  21,  23,  12,  17, -23,
             -6,   7,  26,  31,  65,  56,  25, -20,
             98, 134,  61,  95,  68, 126,  34, -11,
              0,   0,   0,   0,   0,   0,   0,   0,
    };

    private static final int[] PAWN_ENDGAME = {
              0,   0,   0,   0,   0,   0,   0,   0,
             13,   8,   8,  10,  13,   0,   2,  -7,
              4,   7,  -6,   1,   0,  -5,  -1,  -8,
             13,   9,  -3,  -7,  -7,  -8,   3,  -1,
             32,  24,  13,   5,  -2,   4,  17,  17,
             94, 100,  85,  67,  56,  53,  82,  84,
            178, 173, 158, 134, 147, 132, 165, 187,
              0,   0,   0,   0,   0,   0,   0,   0,
    };

    private static final int[] KNIGHT_MIDDLEGAME = {
           -105, -21, -58, -33, -17, -28, -19, -23,
            -29, -53, -12,  -3,  -1,  18, -14, -19,
            -23,  -9,  12,  10,  19,  17,  25, -16,
            -13,   4,  16,  13,  28,  19,  21,  -8,
             -9,  17,  19,  53,  37,  69,  18,  22,
            -47,  60,  37,  65,  84, 129,  73,  44,
            -73, -41,  72,  36,  23,  62,   7, -17,
           -167, -89, -34, -49,  61, -97, -15, -107,
    };

    private static final int[] KNIGHT_ENDGAME = {
            -29, -51, -23, -15, -22, -18, -50, -64,
            -42, -20, -10,  -5,  -2, -20, -23, -44,
            -23,  -3,  -1,  15,  10,  -3, -20, -22,
            -18,  -6,  16,  25,  16,  17,   4, -18,
            -17,   3,  22,  22,  22,  11,   8, -18,
            -24, -20,  10,   9,  -1,  -9, -19, -41,
            -25,  -8, -25,  -2,  -9, -25, -24, -52,
            -58, -38, -13, -28, -31, -27, -63, -99,
    };

    private static final int[] BISHOP_MIDDLEGAME = {
            -33,  -3, -14, -21, -13, -12, -39, -21,
              4,  15,  16,   0,   7,  21,  33,   1,
              0,  15,  15,  15,  14,  27,  18,  10,
             -6,  13,  13,  26,  34,  12,  10,   4,
             -4,   5,  19,  50,  37,  37,   7,  -2,
            -16,  37,  43,  40,  35,  50,  37,  -2,
            -26,  16, -18, -13,  30,  59,  18, -47,
            -29,   4, -82, -37, -25, -42,   7,  -8,
    };

    private static final int[] BISHOP_ENDGAME = {
            -23,  -9, -23,  -5,  -9, -16,  -5, -17,
            -14, -18,  -7,  -1,   4,  -9, -15, -27,
            -12,  -3,   8,  10,  13,   3,  -7, -15,
             -6,   3,  13,  19,   7,  10,  -3,  -9,
             -3,   9,  12,   9,  14,  10,   3,   2,
              2,  -8,   0,  -1,  -2,   6,   0,   4,
             -8,  -4,   7, -12,  -3, -13,  -4, -14,
            -14, -21, -11,  -8,  -7,  -9, -17, -24,
    };

    private static final int[] ROOK_MIDDLEGAME = {
            -19, -13,   1,  17,  16,   7, -37, -26,
            -44, -16, -20,  -9,  -1,  11,  -6, -71,
            -45, -25, -16, -17,   3,   0,  -5, -33,
            -36, -26, -12,  -1,   9,  -7,   6, -23,
            -24, -11,   7,  26,  24,  35,  -8, -20,
             -5,  19,  26,  36,  17,  45,  61,  16,
             27,  32,  58,  62,  80,  67,  26,  44,
             32,  42,  32,  51,  63,   9,  31,  43,
    };

    private static final int[] ROOK_ENDGAME = {
             -9,   2,   3,  -1,  -5, -13,   4, -20,
             -6,  -6,   0,   2,  -9,  -9, -11,  -3,
             -4,   0,  -5,  -1,  -7, -12,  -8, -16,
              3,   5,   8,   4,  -5,  -6,  -8, -11,
              4,   3,  13,   1,   2,   1,  -1,   2,
              7,   7,   7,   5,   4,  -3,  -5,  -3,
             11,  13,  13,  11,  -3,   3,   8,   3,
             13,  10,  18,  15,  12,  12,   8,   5,
    };

    private static final int[] QUEEN_MIDDLEGAME = {
             -1, -18,  -9,  10, -15, -25, -31, -50,
            -35,  -8,  11,   2,   8,  15,  -3,   1,
            -14,   2, -11,  -2,  -5,   2,  14,   5,
             -9, -26,  -9, -10,  -2,  -4,   3,  -3,
            -27, -27, -16, -16,  -1,  17,  -2,   1,
            -13, -17,   7,   8,  29,  56,  47,  57,
            -24, -39,  -5,   1, -16,  57,  28,  54,
            -28,   0,  29,  12,  59,  44,  43,  45,
    };

    private static final int[] QUEEN_ENDGAME = {
            -33, -28, -22, -43,  -5, -32, -20, -41,
            -22, -23, -30, -16, -16, -23, -36, -32,
            -16, -27,  15,   6,   9,  17,  10,   5,
            -18,  28,  19,  47,  31,  34,  39,  23,
              3,  22,  24,  45,  57,  40,  57,  36,
            -20,   6,   9,  49,  47,  35,  19,   9,
            -17,  20,  32,  41,  58,  25,  30,   0,
             -9,  22,  22,  27,  27,  19,  10,  20,
    };

    private static final int[] KING_MIDDLEGAME = {
            -15,  36,  12, -54,   8, -28,  24,  14,
              1,   7,  -8, -64, -43, -16,   9,   8,
            -14, -14, -22, -46, -44, -30, -15, -27,
            -49,  -1, -27, -39, -46, -44, -33, -51,
            -17, -20, -12, -27, -30, -25, -14, -36,
             -9,  24,   2, -16, -20,   6,  22, -22,
             29,  -1, -20,  -7,  -8,  -4, -38, -29,
            -65,  23,  16, -15, -56, -34,   2,  13,
    };

    private static final int[] KING_ENDGAME = {
            -53, -34, -21, -11, -28, -14, -24, -43,
            -27, -11,   4,  13,  14,   4,  -5, -17,
            -19,  -3,  11,  21,  23,  16,   7,  -9,
            -18,  -4,  21,  24,  27,  23,   9, -11,
             -8,  22,  24,  27,  26,  33,  26,   3,
             10,  17,  23,  15,  20,  45,  44,  13,
            -12,  17,  14,  17,  17,  38,  23,  11,
            -74, -35, -18, -18, -11,  15,   4, -17,
    };

    // the tables gathered by piece type, so a loop can reach them without a switch
    private static final int[][] MIDDLEGAME_TABLE = {
            PAWN_MIDDLEGAME, KNIGHT_MIDDLEGAME, BISHOP_MIDDLEGAME,
            ROOK_MIDDLEGAME, QUEEN_MIDDLEGAME, KING_MIDDLEGAME,
    };

    private static final int[][] ENDGAME_TABLE = {
            PAWN_ENDGAME, KNIGHT_ENDGAME, BISHOP_ENDGAME,
            ROOK_ENDGAME, QUEEN_ENDGAME, KING_ENDGAME,
    };

    // every square in front of a pawn on its own file and the two beside it, by colour and square.
    // A pawn with none of the other side's pawns in that area has nobody left to stop it.
    private static final long[][] PASSED_PAWN_MASK = new long[2][Bitboards.SQUARE_COUNT];

    // the file a square stands on and the files either side of it
    private static final long[] NEIGHBOUR_FILES = new long[Bitboards.SQUARE_COUNT];

    static {
        buildPawnMasks();
    }

    private Evaluator() {
    }

    /**
     * Works out how good a position is for the side to move.
     * <p>
     * A search needs one number to compare positions by, and it has to be from the point of view of
     * whoever is to move, because that is what negamax turns round at every level. I add up what each
     * side has and where it stands, twice, once as if the board were full and once as if it were an
     * endgame, and mix the two by how much material is left. A score of zero is a level game and a
     * hundred is worth about a pawn.
     * <p>
     * Time complexity: O(p) for the p pieces on the board, each looked at a fixed number of times.
     * Space complexity: O(1), every table exists from the start.
     *
     * @param pPosition position to judge, never null and unchanged when this returns
     * @return the score in hundredths of a pawn, positive when the side to move stands better
     * @throws NullPointerException if pPosition is null
     */
    public static int evaluate(Position pPosition) {
        int middlegame = 0;
        int endgame = 0;
        int phase = 0;

        for (int colour = Pieces.WHITE; colour <= Pieces.BLACK; colour++) {
            // White counts upwards and Black downwards, so one loop can serve both
            int sign = colour == Pieces.WHITE ? 1 : -1;
            for (int type = Pieces.PAWN; type < Pieces.TYPE_COUNT; type++) {
                long pieces = pPosition.pieces(Pieces.make(colour, type));
                while (pieces != 0L) {
                    int square = Bitboards.lowestSquare(pieces);
                    pieces = Bitboards.clearLowestSquare(pieces);

                    // a black piece reads the table with the board turned round
                    int tableSquare = colour == Pieces.WHITE ? square : square ^ 56;
                    middlegame += sign * (MIDDLEGAME_VALUE[type] + MIDDLEGAME_TABLE[type][tableSquare]);
                    endgame += sign * (ENDGAME_VALUE[type] + ENDGAME_TABLE[type][tableSquare]);
                    phase += PHASE_WEIGHT[type];

                    int mobility = mobilityOf(pPosition, type, square, colour);
                    middlegame += sign * mobility * MOBILITY_MIDDLEGAME[type];
                    endgame += sign * mobility * MOBILITY_ENDGAME[type];

                    if (type == Pieces.PAWN && isPassed(pPosition, square, colour)) {
                        int rank = colour == Pieces.WHITE
                                ? Bitboards.rankOf(square)
                                : 7 - Bitboards.rankOf(square);
                        middlegame += sign * PASSED_PAWN_MIDDLEGAME[rank];
                        endgame += sign * PASSED_PAWN_ENDGAME[rank];
                    }
                }
            }
            // a king wants walls in the middlegame and open space in the endgame, so this is
            // deliberately part of the middlegame score alone
            middlegame += sign * kingSafety(pPosition, colour);
        }

        int score = taper(middlegame, endgame, phase);
        // negamax asks every position how good it is for whoever is about to move
        return pPosition.sideToMove() == Pieces.WHITE ? score : -score;
    }

    /**
     * Mixes the two scores according to how much material is still on the board.
     * <p>
     * A position is rarely purely one thing or the other, and a score that jumped the moment the last
     * queen came off would make the search chase that moment. I weigh the middlegame score by how
     * much of the starting material is left and the endgame score by the rest, which slides smoothly
     * from one to the other. More material than the starting position, which promotions can produce,
     * simply counts as a full middlegame.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMiddlegame the score as if the board were full
     * @param pEndgame    the score as if only kings and pawns were left
     * @param pPhase      the material left, 0 for a bare endgame up to TOTAL_PHASE for a full board
     * @return the mixed score
     */
    private static int taper(int pMiddlegame, int pEndgame, int pPhase) {
        int phase = Math.min(pPhase, TOTAL_PHASE);
        return (pMiddlegame * phase + pEndgame * (TOTAL_PHASE - phase)) / TOTAL_PHASE;
    }

    /**
     * Counts the squares a piece could move to.
     * <p>
     * A piece hemmed in by its own pieces is worth less than the same piece with the board in front
     * of it, which material alone cannot say. I count the squares it attacks that are not occupied by
     * its own side. Pawns and kings are left out: a pawn's worth is in where it stands rather than in
     * how many squares it attacks, and a king that can move in every direction is usually a king in
     * trouble rather than a king doing well.
     * <p>
     * Time complexity: O(1), one attack lookup and a bit count. Space complexity: O(1).
     *
     * @param pPosition position being judged, never null
     * @param pType     piece type, Pieces.PAWN up to Pieces.KING
     * @param pSquare   square the piece stands on, 0 to 63
     * @param pColour   side the piece belongs to
     * @return how many squares it could move to
     */
    private static int mobilityOf(Position pPosition, int pType, int pSquare, int pColour) {
        long occupancy = pPosition.occupancy();
        long attacks = switch (pType) {
            case Pieces.KNIGHT -> MoveGen.knightAttacks(pSquare);
            case Pieces.BISHOP -> MoveGen.bishopAttacks(pSquare, occupancy);
            case Pieces.ROOK -> MoveGen.rookAttacks(pSquare, occupancy);
            case Pieces.QUEEN -> MoveGen.queenAttacks(pSquare, occupancy);
            default -> 0L;
        };
        // a square one of its own pieces stands on is not somewhere it can go
        return Bitboards.count(attacks & ~pPosition.occupancy(pColour));
    }

    /**
     * Tells whether a pawn has nothing left to stop it.
     * <p>
     * A pawn is passed when no enemy pawn stands on its own file ahead of it or on either file
     * beside it, because those are the only pawns that could ever block it or capture it on its way.
     * Such a pawn is a long term threat that material counting says nothing about.
     * <p>
     * Time complexity: O(1), one mask and one test. Space complexity: O(1).
     *
     * @param pPosition position being judged, never null
     * @param pSquare   square the pawn stands on, 0 to 63
     * @param pColour   side the pawn belongs to
     * @return true if no enemy pawn can stop it
     */
    private static boolean isPassed(Position pPosition, int pSquare, int pColour) {
        long enemyPawns = pPosition.pieces(Pieces.make(1 - pColour, Pieces.PAWN));
        return (PASSED_PAWN_MASK[pColour][pSquare] & enemyPawns) == 0L;
    }

    /**
     * Scores how well a king is sheltered.
     * <p>
     * With pieces still on the board a king lives behind its own pawns, and the holes in that wall
     * are where an attack arrives. I count the friendly pawns on the king's file and the two beside
     * it, and charge for each of those three files that has no friendly pawn on it at all. The result
     * only ever reaches the middlegame score, because in an endgame the same wall is a cage.
     * <p>
     * Time complexity: O(1), a handful of bitboard tests. Space complexity: O(1).
     *
     * @param pPosition position being judged, never null
     * @param pColour   side whose king is looked at
     * @return the middlegame score for that king's shelter, never positive by much
     */
    private static int kingSafety(Position pPosition, int pColour) {
        int kingSquare = pPosition.kingSquare(pColour);
        long ownPawns = pPosition.pieces(Pieces.make(pColour, Pieces.PAWN));

        // the pawns still standing between the king and the other side
        long shield = PASSED_PAWN_MASK[pColour][kingSquare] & ownPawns & NEIGHBOUR_FILES[kingSquare];
        int score = Bitboards.count(shield) * KING_SHIELD_BONUS;

        // a file beside the king with none of its own pawns on it is a road in
        long neighbourFiles = NEIGHBOUR_FILES[kingSquare];
        for (int file = Math.max(0, Bitboards.fileOf(kingSquare) - 1);
             file <= Math.min(7, Bitboards.fileOf(kingSquare) + 1); file++) {
            long fileMask = Bitboards.FILE_A << file;
            if ((fileMask & neighbourFiles & ownPawns) == 0L) {
                score -= KING_OPEN_FILE_PENALTY;
            }
        }
        return score;
    }

    /**
     * Fills in the pawn masks the evaluation reads.
     * <p>
     * Both the passed pawn test and the king shelter ask the same question, which squares lie ahead
     * of a square on its own file and the two beside it. Working that out once per square at class
     * load keeps it out of the evaluation, which runs millions of times.
     * <p>
     * Time complexity: O(64 * 8) once. Space complexity: O(64) longs per colour.
     */
    private static void buildPawnMasks() {
        for (int square = 0; square < Bitboards.SQUARE_COUNT; square++) {
            int file = Bitboards.fileOf(square);
            int rank = Bitboards.rankOf(square);

            long files = Bitboards.FILE_A << file;
            if (file > 0) {
                files |= Bitboards.FILE_A << (file - 1);
            }
            if (file < 7) {
                files |= Bitboards.FILE_A << (file + 1);
            }
            NEIGHBOUR_FILES[square] = files;

            long ahead = 0L;
            for (int above = rank + 1; above <= 7; above++) {
                ahead |= Bitboards.RANK_1 << (above * 8);
            }
            long below = 0L;
            for (int under = rank - 1; under >= 0; under--) {
                below |= Bitboards.RANK_1 << (under * 8);
            }

            // ahead means up the board for White and down it for Black
            PASSED_PAWN_MASK[Pieces.WHITE][square] = files & ahead;
            PASSED_PAWN_MASK[Pieces.BLACK][square] = files & below;
        }
    }
}
