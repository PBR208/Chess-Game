package engine.model;

/*
 * Purpose: GameRecord holds one finished game in the shape the library screen and the PGN files both
 * need. Next to the players, the date, the result and the moves it carries the three things a
 * standards compliant PGN cannot be written without: why the game ended, the time control in the
 * seconds and increment form other programs expect, and the position the game started from when that
 * was not the usual one. I keep the label of the time control apart from the PGN one, because
 * "Blitz 5+0" is written for players while "300+0" is written for other chess programs.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 2.0
 */

import engine.core.Termination;

import java.time.LocalDate;
import java.util.List;

public class GameRecord {

    /** The value PGN uses for a game that was played without any clock. */
    public static final String NO_TIME_CONTROL = "-";

    /** The value PGN uses when something is not known, for example in a file another program wrote. */
    public static final String UNKNOWN = "?";

    /** The Termination value PGN uses for a game that ended by the rules of chess. */
    public static final String TERMINATION_NORMAL = "Normal";

    /** The Termination value PGN uses when a player ran out of time. */
    public static final String TERMINATION_TIME_FORFEIT = "Time forfeit";

    // the library separates the parts of a title with an em dash. I build it from its code point
    // rather than writing an escape, so the source stays plain ASCII and no tool can double the
    // backslash and turn the separator into the text of its own escape.
    private static final String TITLE_SEPARATOR = "  " + (char) 0x2014 + "  ";

    public final String whiteName;
    public final String blackName;
    public final String result;        // "1-0", "0-1", "1/2-1/2" or "*"
    public final String date;          // "2026.07.03"
    public final String timeControl;   // the label players read, e.g. "Blitz 5+0"
    public final List<String> moves;
    public final List<String> fenHistory;

    // the time control the way PGN spells it, "300+5", "-" without a clock or "?" when unknown
    public final String pgnTimeControl;
    // the Termination tag value, or null when nobody recorded why the game ended
    public final String termination;
    // the position the game began from, or null when it began the usual way
    public final String startFen;

    /**
     * Builds the record of a game that was just played.
     * <p>
     * A finished game has to reach both the library and a PGN file, and the file needs more than the
     * moves. I take the names and the clock from the configuration the game ran with, stamp today's
     * date in the dotted form PGN uses, translate the reason the game ended into the value the
     * Termination tag allows and keep the starting position when it was not the usual one. The moves
     * and positions are copied, so a later move in the running game cannot change a saved record.
     * <p>
     * Time complexity: O(m) for the m moves and positions copied.
     * Space complexity: O(m) for the copies.
     *
     * @param pConfig      names, clock and increment the game was played with, never null
     * @param pResult      result token, "1-0", "0-1", "1/2-1/2" or "*"; never null
     * @param pTermination why the game ended, or null when it is still running
     * @param pMoves       the moves in standard algebraic notation, never null
     * @param pFenHistory  the position after each move, same order and length as the moves; never null
     * @param pStartFen    position the game started from, or null for the standard one
     * @throws NullPointerException if pConfig, pResult, pMoves or pFenHistory is null
     */
    public GameRecord(GameConfig pConfig, String pResult, Termination pTermination,
                      List<String> pMoves, List<String> pFenHistory, String pStartFen) {
        this(pConfig.whiteName(), pConfig.blackName(), pResult,
                LocalDate.now().toString().replace("-", "."),
                pConfig.timeLabel(), pgnTimeControl(pConfig), pgnTermination(pTermination),
                pStartFen, pMoves, pFenHistory);
    }

    /**
     * Builds the record of a game that was just played, without a recorded reason for the end.
     * <p>
     * Time complexity: O(m) for the m moves copied. Space complexity: O(m) for the copies.
     *
     * @param pConfig     names, clock and increment the game was played with, never null
     * @param pResult     result token, never null
     * @param pMoves      the moves in standard algebraic notation, never null
     * @param pFenHistory the position after each move, never null
     * @throws NullPointerException if any argument is null
     */
    public GameRecord(GameConfig pConfig, String pResult, List<String> pMoves, List<String> pFenHistory) {
        this(pConfig, pResult, null, pMoves, pFenHistory, null);
    }

    /**
     * Builds a record from a PGN file that only carries the older set of tags.
     * <p>
     * Games saved by earlier versions have no Termination tag and no PGN time control, and neither
     * has a game somebody wrote by hand. I fill those in as unknown rather than inventing values.
     * <p>
     * Time complexity: O(m) for the m moves copied. Space complexity: O(m) for the copies.
     *
     * @param pWhite       White's name, never null
     * @param pBlack       Black's name, never null
     * @param pResult      result token, never null
     * @param pDate        date in the dotted PGN form, never null
     * @param pTimeControl the label players read, never null
     * @param pMoves       the moves in standard algebraic notation, never null
     * @param pFenHistory  the position after each move, never null
     * @throws NullPointerException if any argument is null
     */
    public GameRecord(String pWhite, String pBlack, String pResult, String pDate, String pTimeControl,
                      List<String> pMoves, List<String> pFenHistory) {
        this(pWhite, pBlack, pResult, pDate, pTimeControl, UNKNOWN, null, null, pMoves, pFenHistory);
    }

    /**
     * Builds a record with every field a PGN file can carry.
     * <p>
     * This is what the PGN reader fills, because a file may hold all of these or none of them. I copy
     * the moves and positions so the record cannot change afterwards, and leave the optional values
     * exactly as they arrived, including null, so writing the game out again says no more than the
     * file said.
     * <p>
     * Time complexity: O(m) for the m moves and positions copied.
     * Space complexity: O(m) for the copies.
     *
     * @param pWhite          White's name, never null
     * @param pBlack          Black's name, never null
     * @param pResult         result token, "1-0", "0-1", "1/2-1/2" or "*"; never null
     * @param pDate           date in the dotted PGN form, never null
     * @param pTimeControl    the label players read, never null
     * @param pPgnTimeControl the time control as PGN spells it, never null
     * @param pTermination    Termination tag value, or null when the file named none
     * @param pStartFen       position the game started from, or null for the standard one
     * @param pMoves          the moves in standard algebraic notation, never null
     * @param pFenHistory     the position after each move, never null
     * @throws NullPointerException if any argument but pTermination and pStartFen is null
     */
    public GameRecord(String pWhite, String pBlack, String pResult, String pDate, String pTimeControl,
                      String pPgnTimeControl, String pTermination, String pStartFen,
                      List<String> pMoves, List<String> pFenHistory) {
        this.whiteName = pWhite;
        this.blackName = pBlack;
        this.result = pResult;
        this.date = pDate;
        this.timeControl = pTimeControl;
        this.pgnTimeControl = pPgnTimeControl;
        this.termination = pTermination;
        this.startFen = pStartFen;
        // copies, so a running game can never change a record that was already saved
        this.moves = List.copyOf(pMoves);
        this.fenHistory = List.copyOf(pFenHistory);
    }

    /**
     * Translates the reason a game ended into the value the PGN Termination tag allows.
     * <p>
     * PGN does not have a tag value per drawing rule. It only distinguishes a game that ended by the
     * rules of chess from one that ended because a clock ran out, so a threefold repetition and a
     * mate are both "Normal" while either kind of flag fall is "Time forfeit". The detailed reason
     * stays in the engine, where the end screen reads it.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pTermination why the game ended, or null when nobody recorded it
     * @return "Normal", "Time forfeit", or null when pTermination is null
     */
    public static String pgnTermination(Termination pTermination) {
        // a game still running, or one loaded from a file that named no reason, claims nothing
        if (pTermination == null) {
            return null;
        }
        // both kinds of flag fall are a forfeit on time, everything else ended by the rules
        return pTermination == Termination.TIME_OUT || pTermination == Termination.TIME_OUT_WITHOUT_MATING_MATERIAL
                ? TERMINATION_TIME_FORFEIT
                : TERMINATION_NORMAL;
    }

    /**
     * Writes a configured clock the way the PGN TimeControl tag spells it.
     * <p>
     * Other programs read this tag to know what was played, and they expect seconds rather than the
     * milliseconds the clocks run on, in the form "300+5" for five minutes with a five second
     * increment. A game without a clock is a dash, which is what the standard uses for no time
     * control at all. I take White's time, because both sides start with the same clock today.
     * <p>
     * Time complexity: O(1). Space complexity: O(1) for the short text.
     *
     * @param pConfig the clock the game was played with, never null
     * @return "300+5", or "-" when the game had no clock; never null
     * @throws NullPointerException if pConfig is null
     */
    public static String pgnTimeControl(GameConfig pConfig) {
        // an unlimited game is played without any time control at all
        if (pConfig.whiteTimeMs() <= 0) {
            return NO_TIME_CONTROL;
        }
        // the tag counts in whole seconds, the clocks in milliseconds
        return (pConfig.whiteTimeMs() / 1000) + "+" + (pConfig.incrementMs() / 1000);
    }

    /**
     * Returns the one line the library list shows for this game.
     * <p>
     * Time complexity: O(n) in the length of the names. Space complexity: O(n) for the line.
     *
     * @return both players, the result, the date and the time control, never null
     */
    public String getDisplayTitle() {
        return whiteName + " vs " + blackName
                + TITLE_SEPARATOR + result
                + TITLE_SEPARATOR + date
                + TITLE_SEPARATOR + timeControl;
    }
}
