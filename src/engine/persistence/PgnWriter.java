package engine.persistence;

/*
 * Purpose: PgnWriter turns a finished game into the PGN text other chess programs expect. The writer
 * this replaces put the seven tags and then every move with the position after it in a comment, all
 * on one endless line, which only this program could read back. This one writes the seven tag roster,
 * the time control and the reason the game ended, marks a game that began from a set up position with
 * the tags that say so, and wraps the moves at the eighty columns the standard asks for. The result
 * opens in any chess program rather than only in mine.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.Fen;
import engine.core.Pieces;
import engine.core.Position;
import engine.model.GameRecord;

import java.util.ArrayList;
import java.util.List;

public final class PgnWriter {

    // PGN asks for movetext lines of at most eighty columns
    private static final int MAX_LINE_LENGTH = 80;

    // the backslash, written as its code point so this file holds no escape sequence of its own.
    // An escape that gets doubled by a tool turns every saved name into nonsense, and nothing about
    // a lone backslash in a string literal makes that visible when reading the code.
    private static final char BACKSLASH = (char) 92;

    private PgnWriter() {
    }

    /**
     * Writes a finished game as PGN text.
     * <p>
     * Saved games have to be readable by other chess programs, which means the tags come first and in
     * the order the standard fixes: Event, Site, Date, Round, White, Black and Result. Behind those I
     * write the time control and, when the game is over and the reason is known, the Termination tag.
     * A game that did not begin from the usual position carries SetUp and FEN, because its moves make
     * no sense without them. Then comes a blank line and the movetext, with a move number in front of
     * every move of White, the result as the closing token and no line longer than eighty columns.
     * <p>
     * Time complexity: O(m + c) for m moves and c characters in the tag values.
     * Space complexity: O(m + c) for the text that is built.
     *
     * @param pRecord the game to write, with non-null names, date, result and moves; never null
     * @return the complete PGN text, ending with a line break; never null
     * @throws NullPointerException if pRecord or one of its required values is null
     */
    public static String write(GameRecord pRecord) {
        StringBuilder pgn = new StringBuilder();

        // the seven tag roster comes first and in exactly this order
        appendTag(pgn, "Event", "Casual Game");
        appendTag(pgn, "Site", "Local");
        appendTag(pgn, "Date", pRecord.date);
        appendTag(pgn, "Round", "-");
        appendTag(pgn, "White", pRecord.whiteName);
        appendTag(pgn, "Black", pRecord.blackName);
        appendTag(pgn, "Result", pRecord.result);

        // what the game was played at, and why it ended when anybody recorded that
        appendTag(pgn, "TimeControl", pRecord.pgnTimeControl == null ? GameRecord.UNKNOWN : pRecord.pgnTimeControl);
        if (pRecord.termination != null) {
            appendTag(pgn, "Termination", pRecord.termination);
        }
        // without these two tags the moves of a set up game would be read against the wrong position
        if (pRecord.startFen != null) {
            appendTag(pgn, "SetUp", "1");
            appendTag(pgn, "FEN", pRecord.startFen);
        }

        // a blank line separates the tags from the moves
        pgn.append('\n');
        // TODO [PBR208]: Write the time left after each move as a {[%clk 0:04:59]} comment once the
        // record carries the clock of every ply.
        appendWrapped(pgn, movetextTokens(pRecord));
        return pgn.toString();
    }

    /**
     * Appends one tag pair with a properly escaped value.
     * <p>
     * A tag value is a quoted string, so a player called Magnus "The Hammer" would end the string
     * early and make the whole file unreadable. The standard asks for a backslash in front of a quote
     * and in front of a backslash itself, and the backslashes have to come first, or the one that
     * protects a quote would be protected again.
     * <p>
     * Time complexity: O(n) in the length of the value. Space complexity: O(n) for the escaped copy.
     *
     * @param pBuilder the PGN text being built, never null
     * @param pName    the tag name such as "White", never null
     * @param pValue   the raw value, may contain quotes and backslashes; never null
     * @throws NullPointerException if pValue is null
     */
    private static void appendTag(StringBuilder pBuilder, String pName, String pValue) {
        pBuilder.append('[').append(pName).append(' ').append('"');
        for (char symbol : pValue.toCharArray()) {
            // a quote and a backslash are the two characters a value has to protect
            if (symbol == '"' || symbol == BACKSLASH) {
                pBuilder.append(BACKSLASH);
            }
            pBuilder.append(symbol);
        }
        pBuilder.append('"').append(']').append('\n');
    }

    /**
     * Builds the movetext of a game as the list of words it consists of.
     * <p>
     * Every move of White is announced by its number, and a game that was set up with Black to move
     * starts with the dots that stand for the move White did not play. The numbering of such a game
     * continues from the full move number of its starting position rather than from one. The result
     * closes the movetext, which the standard requires even for a game that was never finished.
     * <p>
     * Time complexity: O(m) for the m moves. Space complexity: O(m) for the words.
     *
     * @param pRecord the game to write, never null
     * @return the movetext as single words, never null
     */
    private static List<String> movetextTokens(GameRecord pRecord) {
        int firstNumber = 1;
        boolean blackMovesFirst = false;
        if (pRecord.startFen != null) {
            try {
                Position start = Fen.parse(pRecord.startFen);
                firstNumber = start.fullmoveNumber();
                blackMovesFirst = start.sideToMove() == Pieces.BLACK;
            } catch (IllegalArgumentException e) {
                // a starting position I cannot read is numbered like an ordinary game
                firstNumber = 1;
            }
        }

        List<String> tokens = new ArrayList<>();
        for (int ply = 0; ply < pRecord.moves.size(); ply++) {
            // with Black to move first every move sits half a move later in the numbering
            int offset = ply + (blackMovesFirst ? 1 : 0);
            int number = firstNumber + offset / 2;
            if (offset % 2 == 0) {
                tokens.add(number + ".");
            } else if (ply == 0) {
                // the dots stand for the move of White this game did not start with
                tokens.add(number + "...");
            }
            tokens.add(pRecord.moves.get(ply));
        }

        tokens.add(pRecord.result);
        return tokens;
    }

    /**
     * Appends words to the text, breaking the line before it grows past eighty columns.
     * <p>
     * The standard asks that movetext lines stay within eighty columns, and a word may never be split
     * across a break, because half a move is not a move. So the line breaks in front of the word that
     * would not fit any more.
     * <p>
     * Time complexity: O(t) for t words. Space complexity: O(1) beyond the text being built.
     *
     * @param pBuilder the PGN text being built, never null
     * @param pTokens  the words of the movetext, never null
     */
    private static void appendWrapped(StringBuilder pBuilder, List<String> pTokens) {
        int lineLength = 0;
        for (String token : pTokens) {
            if (lineLength > 0 && lineLength + 1 + token.length() > MAX_LINE_LENGTH) {
                // the word does not fit, so the line ends in front of it
                pBuilder.append('\n');
                lineLength = 0;
            } else if (lineLength > 0) {
                pBuilder.append(' ');
                lineLength++;
            }
            pBuilder.append(token);
            lineLength += token.length();
        }
        pBuilder.append('\n');
    }
}
