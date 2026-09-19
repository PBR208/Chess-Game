package engine.persistence;

/*
 * Purpose: PgnReader reads PGN text that other people and other programs wrote, not only the files
 * this game saves itself. Real PGN carries comments in braces and after a semicolon, side lines in
 * brackets, numeric annotation glyphs, move numbers in front of either colour and several games in
 * one file, and the old reader here treated all of that as moves. This one walks the text properly
 * and then resolves every move against the legal moves of the position, so a move is only accepted
 * when it really can be played, and the positions of the game are rebuilt while doing it.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.core.Fen;
import engine.core.MoveGen;
import engine.core.Position;
import engine.core.San;
import engine.model.GameRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PgnReader {

    // the four tokens that end a game in the movetext
    private static final List<String> RESULT_TOKENS = List.of("1-0", "0-1", "1/2-1/2", "*");

    private PgnReader() {
    }

    /**
     * Reads every game in a PGN text.
     * <p>
     * A PGN file may hold one game or a whole tournament, and the library wants all of them. I walk
     * the text once, start a new game whenever a tag pair follows movetext, and turn each collected
     * game into a record. A game without the players or the result is left out rather than listed
     * with empty names, and a game whose moves cannot be replayed still comes back with its tags and
     * its move text, because a file nobody can replay is still worth seeing in the library.
     * <p>
     * Time complexity: O(c + g * m * l) for c characters, g games, m moves each and l legal moves per
     * position, because every move is matched against the notation of the legal moves.
     * Space complexity: O(c) for the collected games.
     *
     * @param pPgnText the complete text of a PGN file, never null
     * @return every readable game in the order the file lists them, never null and possibly empty
     * @throws NullPointerException if pPgnText is null
     */
    public static List<GameRecord> readAll(String pPgnText) {
        List<ParsedGame> parsed = split(pPgnText);
        List<GameRecord> records = new ArrayList<>();
        for (ParsedGame game : parsed) {
            GameRecord record = toRecord(game);
            // a game without players or a result cannot be listed sensibly
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    /**
     * Reads the first game of a PGN text.
     * <p>
     * Saving writes one game per file, so loading one usually means loading exactly one game. I read
     * the whole text and hand back the first game it holds.
     * <p>
     * Time complexity: O(c + m * l) as in readAll. Space complexity: O(c).
     *
     * @param pPgnText the complete text of a PGN file, never null
     * @return the first readable game, or null when the text holds none
     * @throws NullPointerException if pPgnText is null
     */
    public static GameRecord read(String pPgnText) {
        List<GameRecord> games = readAll(pPgnText);
        return games.isEmpty() ? null : games.get(0);
    }

    /**
     * Splits PGN text into its games, separating tags, moves and comments.
     * <p>
     * This is the part that has to understand the format rather than guess at it. I read the text
     * character by character: a bracket starts a tag pair, a brace or a semicolon starts a comment,
     * a parenthesis starts a side line that is skipped with everything nested inside it, a dollar
     * sign starts an annotation glyph, and anything else is a word. A word is either a result, a
     * move number, which may be stuck to the move behind it, or a move. A tag pair that follows
     * movetext begins the next game of the file.
     * <p>
     * Time complexity: O(c) in the number of characters, each read once.
     * Space complexity: O(c) for the collected games.
     *
     * @param pText the complete text of a PGN file, never null
     * @return the games in the order the file lists them, never null
     */
    private static List<ParsedGame> split(String pText) {
        List<ParsedGame> games = new ArrayList<>();
        ParsedGame current = new ParsedGame();
        int index = 0;

        while (index < pText.length()) {
            char symbol = pText.charAt(index);

            if (Character.isWhitespace(symbol)) {
                index++;
            } else if (symbol == '[') {
                // a tag pair after movetext means the previous game is finished
                if (current.hasMovetext()) {
                    games.add(current);
                    current = new ParsedGame();
                }
                index = readTag(pText, index, current);
            } else if (symbol == '{') {
                index = readBraceComment(pText, index, current);
            } else if (symbol == ';') {
                // a semicolon comments out the rest of the line
                index = endOfLine(pText, index);
            } else if (symbol == '(') {
                index = skipVariation(pText, index);
            } else if (symbol == '$') {
                // a numeric annotation glyph says how good a move was, which changes no position
                index = endOfWord(pText, index);
            } else {
                int end = endOfWord(pText, index);
                readWord(pText.substring(index, end), current);
                index = end;
            }
        }

        // the last game of the file has no tag pair behind it to close it
        if (current.hasMovetext() || !current.tags.isEmpty()) {
            games.add(current);
        }
        return games;
    }

    /**
     * Reads one tag pair into the game.
     * <p>
     * A tag pair is a name and a quoted value in brackets, and the value may contain quotes and
     * backslashes that are protected by a backslash. I read the name up to the first quote, then the
     * value up to the closing quote while honouring those escapes, and undo the escaping.
     * <p>
     * Time complexity: O(n) in the length of the tag pair. Space complexity: O(n) for name and value.
     *
     * @param pText  the complete text, never null
     * @param pStart index of the opening bracket
     * @param pGame  game that receives the tag, never null
     * @return the index just behind the tag pair
     */
    private static int readTag(String pText, int pStart, ParsedGame pGame) {
        int index = pStart + 1;
        StringBuilder name = new StringBuilder();
        // the name runs up to the first space or quote
        while (index < pText.length() && !Character.isWhitespace(pText.charAt(index)) && pText.charAt(index) != '"') {
            name.append(pText.charAt(index));
            index++;
        }
        while (index < pText.length() && pText.charAt(index) != '"' && pText.charAt(index) != ']') {
            index++;
        }

        StringBuilder value = new StringBuilder();
        if (index < pText.length() && pText.charAt(index) == '"') {
            index++;
            while (index < pText.length() && pText.charAt(index) != '"') {
                // a backslash protects the character behind it, a quote as well as a backslash
                if (pText.charAt(index) == '\\' && index + 1 < pText.length()) {
                    index++;
                }
                value.append(pText.charAt(index));
                index++;
            }
            index++;
        }
        // skip whatever is left before the closing bracket
        while (index < pText.length() && pText.charAt(index) != ']') {
            index++;
        }

        if (name.length() > 0) {
            pGame.tags.put(name.toString(), value.toString());
        }
        return Math.min(index + 1, pText.length());
    }

    /**
     * Reads a comment in braces and keeps its text.
     * <p>
     * Comments carry no moves, but the games this program saved before this change wrote the position
     * after every move into one, and those files still have to open. I keep the text so the reader can
     * fall back on it when the moves themselves cannot be replayed.
     * <p>
     * Time complexity: O(n) in the length of the comment. Space complexity: O(n) for its text.
     *
     * @param pText  the complete text, never null
     * @param pStart index of the opening brace
     * @param pGame  game that receives the comment, never null
     * @return the index just behind the closing brace
     */
    private static int readBraceComment(String pText, int pStart, ParsedGame pGame) {
        int end = pText.indexOf('}', pStart + 1);
        // a comment nobody closed runs to the end of the file
        if (end < 0) {
            pGame.comments.add(pText.substring(pStart + 1).trim());
            return pText.length();
        }
        pGame.comments.add(pText.substring(pStart + 1, end).trim());
        return end + 1;
    }

    /**
     * Skips a side line and everything nested inside it.
     * <p>
     * A variation shows moves that were not played, so none of it belongs in the game. Variations
     * nest, and they may contain comments with brackets of their own, so I count the parentheses
     * and step over braces rather than searching for the next closing one.
     * <p>
     * Time complexity: O(n) in the length of the variation. Space complexity: O(1).
     *
     * @param pText  the complete text, never null
     * @param pStart index of the opening parenthesis
     * @return the index just behind the matching closing parenthesis
     */
    private static int skipVariation(String pText, int pStart) {
        int depth = 0;
        int index = pStart;
        while (index < pText.length()) {
            char symbol = pText.charAt(index);
            if (symbol == '{') {
                int end = pText.indexOf('}', index + 1);
                index = end < 0 ? pText.length() : end + 1;
                continue;
            }
            if (symbol == '(') {
                depth++;
            } else if (symbol == ')') {
                depth--;
                if (depth == 0) {
                    return index + 1;
                }
            }
            index++;
        }
        return index;
    }

    /**
     * Sorts one word of the movetext into a result, a move number or a move.
     * <p>
     * A word can be the result that ends the game, a move number such as 12. or 12... which says
     * nothing the position does not already know, or a move. Move numbers are often stuck to the
     * move behind them, so I strip the digits and dots from the front and keep whatever is left.
     * <p>
     * Time complexity: O(n) in the length of the word. Space complexity: O(n).
     *
     * @param pWord the word as it stands in the file, never null and never empty
     * @param pGame game that receives the move or the result, never null
     */
    private static void readWord(String pWord, ParsedGame pGame) {
        if (RESULT_TOKENS.contains(pWord)) {
            pGame.result = pWord;
            return;
        }

        // a move number is digits followed by dots, and the move may follow without a space
        int index = 0;
        while (index < pWord.length() && Character.isDigit(pWord.charAt(index))) {
            index++;
        }
        int digits = index;
        while (index < pWord.length() && pWord.charAt(index) == '.') {
            index++;
        }
        // only a real move number is stripped, so a result like 1-0 keeps its digits
        String move = digits > 0 && index > digits ? pWord.substring(index) : pWord;

        if (!move.isEmpty() && !RESULT_TOKENS.contains(move)) {
            pGame.moveTokens.add(move);
        } else if (RESULT_TOKENS.contains(move)) {
            pGame.result = move;
        }
    }

    /**
     * Turns one collected game into a record and rebuilds its positions.
     * <p>
     * The library needs players, a result and one position per move. I read the tags, refuse a game
     * without White, Black or a result, start from the position of the FEN tag when there is one and
     * replay the moves through the rules, which is also what proves each move is real. When the moves
     * cannot be replayed but the file carries one position comment per move, which is how this
     * program used to save its games, those positions are used instead.
     * <p>
     * Time complexity: O(m * l) for m moves and l legal moves per position.
     * Space complexity: O(m) for the moves and positions.
     *
     * @param pGame the collected game, never null
     * @return the record, or null when the game has no players or no result
     */
    private static GameRecord toRecord(ParsedGame pGame) {
        String white = pGame.tags.get("White");
        String black = pGame.tags.get("Black");
        String result = pGame.tags.getOrDefault("Result", pGame.result);
        // a game nobody can name or score does not belong in the library
        if (white == null || black == null || result == null) {
            return null;
        }

        String startFen = pGame.tags.get("FEN");
        List<String> moves = new ArrayList<>();
        List<String> fens = new ArrayList<>();
        replay(pGame.moveTokens, startFen, moves, fens);

        // games saved before this change kept the position after every move in a comment
        if (fens.size() < moves.size()) {
            List<String> comments = fenComments(pGame.comments);
            if (comments.size() == moves.size()) {
                fens = comments;
            }
        }

        return new GameRecord(white, black, result,
                pGame.tags.getOrDefault("Date", GameRecord.UNKNOWN),
                pGame.tags.getOrDefault("TimeControl", GameRecord.UNKNOWN),
                pGame.tags.getOrDefault("TimeControl", GameRecord.UNKNOWN),
                pGame.tags.get("Termination"),
                startFen,
                moves, fens);
    }

    /**
     * Replays the moves of a game and records the position after each of them.
     * <p>
     * A move in a file is text, and text can be wrong, ambiguous or written in a style this program
     * does not use. Rather than parsing the notation I generate the legal moves of the position and
     * compare their notation with the word from the file, so a move is accepted exactly when it can
     * be played. That handles castling with zeros, annotation marks and the e.p. suffix along the
     * way. The first move that cannot be matched stops the replay, and everything up to it is kept.
     * <p>
     * Time complexity: O(m * l) for m moves and l legal moves per position, since the notation of
     * every legal move is written once per move.
     * Space complexity: O(m) for the results plus one move buffer.
     *
     * @param pTokens   the moves as the file writes them, never null
     * @param pStartFen position to start from, or null for the usual one
     * @param pMoves    receives the moves in this program's notation, never null
     * @param pFens     receives the position after each move, never null
     */
    private static void replay(List<String> pTokens, String pStartFen, List<String> pMoves, List<String> pFens) {
        Position position;
        try {
            position = pStartFen == null ? Position.startPosition() : Fen.parse(pStartFen);
        } catch (IllegalArgumentException e) {
            // a starting position that is not a position leaves the moves as plain text
            pMoves.addAll(pTokens);
            return;
        }

        int[] legal = new int[MoveGen.MAX_MOVES];
        for (String token : pTokens) {
            int move = resolve(position, token, legal);
            if (move == NO_MOVE) {
                // the rest cannot be trusted once one move does not fit the position
                pMoves.add(token);
                continue;
            }
            pMoves.add(San.of(position, move));
            position.makeMove(move);
            pFens.add(Fen.write(position));
        }
    }

    // says that no legal move of the position is written the way the file writes it
    private static final int NO_MOVE = -1;

    /**
     * Finds the legal move a word of the movetext names.
     * <p>
     * I write every legal move of the position in algebraic notation and compare it with the word
     * from the file, both stripped of the parts that say nothing about which move it is, such as the
     * check mark, annotation marks and the e.p. suffix, and with castling written with zeros treated
     * as castling. That way the reader and the writer can never disagree about notation.
     * <p>
     * Time complexity: O(l * l) for l legal moves, because writing one move looks at all of them.
     * Space complexity: O(1) beyond the buffer the caller owns.
     *
     * @param pPosition position the move is played in, never null and unchanged afterwards
     * @param pToken    the move as the file writes it, never null
     * @param pBuffer   buffer for the legal moves, at least MoveGen.MAX_MOVES long
     * @return the packed move, or NO_MOVE when no legal move is written that way
     */
    private static int resolve(Position pPosition, String pToken, int[] pBuffer) {
        String wanted = normalise(pToken);
        int count = MoveGen.generateLegal(pPosition, pBuffer, 0);
        for (int index = 0; index < count; index++) {
            if (normalise(San.of(pPosition, pBuffer[index])).equals(wanted)) {
                return pBuffer[index];
            }
        }
        return NO_MOVE;
    }

    /**
     * Strips everything from a move that does not say which move it is.
     * <p>
     * Files differ in how they decorate a move: a check may be marked or left out, a good move may
     * carry exclamation marks, an en passant capture may say so, and castling is written with the
     * letter O by the standard but with zeros by many programs. None of that changes which move is
     * meant, so it all goes before two moves are compared.
     * <p>
     * Time complexity: O(n) in the length of the move. Space complexity: O(n) for the stripped copy.
     *
     * @param pMove a move in algebraic notation, never null
     * @return the bare move, never null
     */
    private static String normalise(String pMove) {
        // an en passant capture is already named by the squares it uses
        String text = pMove.replace("e.p.", "").replace("0", "O");
        StringBuilder bare = new StringBuilder();
        for (char symbol : text.toCharArray()) {
            // check and mate marks and the annotation marks say nothing about the move itself
            if (symbol != '+' && symbol != '#' && symbol != '!' && symbol != '?') {
                bare.append(symbol);
            }
        }
        return bare.toString();
    }

    /**
     * Picks the comments that are positions out of all comments of a game.
     * <p>
     * Games this program saved before this change wrote the position after every move into a comment,
     * and those files have to keep opening. A position comment has the shape of a FEN, so I keep the
     * comments that start like a piece placement and leave anything a player wrote alone.
     * <p>
     * Time complexity: O(k * n) for k comments of length n. Space complexity: O(k) for the result.
     *
     * @param pComments every comment of the game in the order they appear, never null
     * @return the comments that look like positions, never null
     */
    private static List<String> fenComments(List<String> pComments) {
        List<String> fens = new ArrayList<>();
        for (String comment : pComments) {
            // a placement has eight ranks separated by slashes and a side to move behind them
            if (comment.chars().filter(symbol -> symbol == '/').count() == 7 && comment.contains(" ")) {
                fens.add(comment);
            }
        }
        return fens;
    }

    /**
     * Returns the index just behind the current word.
     * <p>
     * Time complexity: O(n) in the length of the word. Space complexity: O(1).
     *
     * @param pText  the complete text, never null
     * @param pStart index of the first character of the word
     * @return the index of the first character that does not belong to the word
     */
    private static int endOfWord(String pText, int pStart) {
        int index = pStart;
        while (index < pText.length()) {
            char symbol = pText.charAt(index);
            // a word ends at a space or wherever something else begins
            if (Character.isWhitespace(symbol) || symbol == '{' || symbol == '(' || symbol == ')' || symbol == ';') {
                break;
            }
            index++;
        }
        return index;
    }

    /**
     * Returns the index just behind the current line.
     * <p>
     * Time complexity: O(n) in the length of the line. Space complexity: O(1).
     *
     * @param pText  the complete text, never null
     * @param pStart index to start looking from
     * @return the index behind the next line break, or the end of the text
     */
    private static int endOfLine(String pText, int pStart) {
        int end = pText.indexOf('\n', pStart);
        return end < 0 ? pText.length() : end + 1;
    }

    /** One game as the text describes it, before the moves are checked against the rules. */
    private static final class ParsedGame {
        private final Map<String, String> tags = new LinkedHashMap<>();
        private final List<String> moveTokens = new ArrayList<>();
        private final List<String> comments = new ArrayList<>();
        private String result;

        /** Tells whether this game already has something behind its tags. */
        private boolean hasMovetext() {
            return !moveTokens.isEmpty() || result != null;
        }
    }
}
