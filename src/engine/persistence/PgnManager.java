package engine.persistence;

/*
 * Purpose: PgnManager stores finished games as PGN text files and reads them back for the Past
 * Games library. I chose plain PGN files so games stay readable in any text editor and can be
 * opened by other chess tools. The class hides where the library lives and how a GameRecord maps
 * to PGN, so the UI only ever deals with records. The storage folder can be redirected with the
 * chess.gamesDir system property, which keeps test runs away from the real saved games.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import engine.model.GameRecord;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class PgnManager {

    // system property that redirects saved games, used by tests and portable setups
    public static final String GAMES_DIR_PROPERTY = "chess.gamesDir";

    /**
     * Resolves the directory where finished games are stored as PGN files.
     * <p>
     * Saved games need a predictable home, and tests need a way to keep their files out of the
     * real library. I first read the chess.gamesDir system property and use it when it holds a
     * non-blank value. Otherwise I fall back to a games folder in the current working directory,
     * which is how the app has always behaved.
     * <p>
     * Time complexity: O(p) where p is the length of the path string.
     * Space complexity: O(p) for the resulting Path.
     *
     * @return the games directory, never null; the directory itself may not exist yet
     * @throws InvalidPathException if the configured property is not a valid path on this OS
     */
    public static Path getGamesDirectory() {
        // an explicit location always wins
        String override = System.getProperty(GAMES_DIR_PROPERTY);
        if (override != null && !override.isBlank()) return Paths.get(override.trim());
        // default is a games folder next to where the app was started
        return Paths.get(System.getProperty("user.dir"), "games");
    }

    /**
     * Saves a finished game as a new PGN file.
     * <p>
     * Every finished game should show up in the Past Games library without overwriting an older
     * one. I make sure the games directory exists, build a file name from the date and both player
     * names, append a counter when that name is taken and write the PGN text. I/O failures are
     * only reported on standard error so a full or read-only disk never crashes the game screen.
     * <p>
     * Time complexity: O(m + f) where m is the number of moves written and f is the number of
     * existing files sharing the same base name. Space complexity: O(m) for the PGN text.
     *
     * @param pRecord finished game to persist, never null
     * @throws NullPointerException if pRecord is null
     * @throws InvalidPathException if the configured games directory is not a valid path
     */
    public static void save(GameRecord pRecord) {
        try {
            // create the library folder on first use
            Path dir = getGamesDirectory();
            Files.createDirectories(dir);

            // date and player names make the file easy to recognise
            String base = pRecord.date + "_"
                    + sanitize(pRecord.whiteName) + "_vs_"
                    + sanitize(pRecord.blackName);
            Path file = uniquePath(dir, base, ".pgn");

            Files.writeString(file, buildPgn(pRecord));
        } catch (IOException e) {
            System.err.println("PgnManager: failed to save: " + e.getMessage());
        }
    }

    private static Path uniquePath(Path dir, String base, String ext) {
        Path p = dir.resolve(base + ext);
        int n = 1;
        while (Files.exists(p)) {
            p = dir.resolve(base + "_" + n++ + ext);
        }
        return p;
    }

    /**
     * Loads every saved game from the games directory, newest first.
     * <p>
     * The Past Games screen lists the whole library. I return an empty list when the directory does
     * not exist yet. Otherwise I collect all .pgn files, sort them by file name in reverse so the
     * latest dates come first, and parse each file. A file that fails to parse is skipped with a
     * message on standard error so one broken file never hides the rest of the library.
     * <p>
     * Time complexity: O(f log f + c) where f is the number of files and c is the total number of
     * characters parsed. Space complexity: O(c) for the loaded records.
     *
     * @return all readable games, newest first; never null, possibly empty
     * @throws InvalidPathException if the configured games directory is not a valid path
     */
    public static List<GameRecord> loadAll() {
        List<GameRecord> records = new ArrayList<>();
        // no folder yet simply means no saved games yet
        Path dir = getGamesDirectory();
        if (!Files.exists(dir)) return records;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.pgn")) {
            List<Path> files = new ArrayList<>();
            stream.forEach(files::add);
            files.sort(Comparator.comparing(Path::getFileName).reversed());

            for (Path file : files) {
                try {
                    GameRecord r = parse(Files.readString(file));
                    if (r != null) {
                        records.add(r);
                    } else {
                        // a file without players or result can't be listed, so say which one it was
                        // TODO [PBR208]: Show skipped files in the Past Games screen, not only on standard error.
                        System.err.println("PgnManager: skipping " + file.getFileName()
                                + ": missing White, Black or Result tag");
                    }
                } catch (Exception e) {
                    System.err.println("PgnManager: skipping " + file.getFileName()
                            + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("PgnManager: failed to list games: " + e.getMessage());
        }
        return records;
    }

    /**
     * Turns a game record into PGN text.
     * <p>
     * Saved games must follow the PGN standard so the library and other chess programs can read them.
     * I write the seven tag roster first and in its required order, Event, Site, Date, Round, White,
     * Black and Result, followed by the time control, with every value escaped. Then come the moves
     * with a move number before each White move, the FEN after each move as a comment for the replay
     * viewer, and the result as the final token.
     * <p>
     * Time complexity: O(m + c) for m moves and c characters in the tag values.
     * Space complexity: O(m + c) for the PGN text.
     *
     * @param pRecord game to write, with non-null names, date, result and time control; never null
     * @return the complete PGN text ending with a line break, never null
     * @throws NullPointerException if pRecord or one of its tag values is null
     */
    private static String buildPgn(GameRecord pRecord) {
        StringBuilder sb = new StringBuilder();

        // the seven tag roster comes first and in exactly this order
        appendTag(sb, "Event", "Casual Game");
        appendTag(sb, "Site", "Local");
        appendTag(sb, "Date", pRecord.date);
        appendTag(sb, "Round", "-");
        appendTag(sb, "White", pRecord.whiteName);
        appendTag(sb, "Black", pRecord.blackName);
        appendTag(sb, "Result", pRecord.result);
        appendTag(sb, "TimeControl", pRecord.timeControl);
        sb.append("\n");

        for (int i = 0; i < pRecord.moves.size(); i++) {
            // move numbers only stand before White's moves
            if (i % 2 == 0) sb.append(i / 2 + 1).append(". ");
            sb.append(pRecord.moves.get(i));
            // the FEN after each move is kept in a comment for the replay viewer
            if (i < pRecord.fenHistory.size()) {
                sb.append(" {").append(pRecord.fenHistory.get(i)).append("}");
            }
            sb.append(" ");
        }

        sb.append(pRecord.result).append("\n");
        return sb.toString();
    }

    /**
     * Appends one PGN tag pair with a correctly escaped value.
     * <p>
     * Tag values are quoted strings, so a player name containing a quote used to end the string early
     * and made the whole file unreadable. As the PGN standard asks, I escape backslashes first and
     * quotes second, then write the tag on its own line.
     * <p>
     * Time complexity: O(n) in the length of the value. Space complexity: O(n) for the escaped copy.
     *
     * @param pBuilder PGN text being built, never null
     * @param pName    tag name such as "White", never null
     * @param pValue   raw tag value, may contain quotes and backslashes; never null
     * @throws NullPointerException if pValue is null
     */
    private static void appendTag(StringBuilder pBuilder, String pName, String pValue) {
        // backslashes first, otherwise the escape of a quote would be escaped again
        String escaped = pValue.replace("\\", "\\\\").replace("\"", "\\\"");
        pBuilder.append('[').append(pName).append(" \"").append(escaped).append("\"]\n");
    }

    /**
     * Reads a game record back from PGN text.
     * <p>
     * The Past Games library is built from the saved files. I read the player, result, date and time
     * control tags, whose values may contain escaped quotes, and give up on files without players or
     * result. Then I remove the complete tag lines, so brackets inside a quoted value can't cut a tag
     * short, collect the FEN comments for the replay viewer and split what is left into moves,
     * skipping move numbers and the result token.
     * <p>
     * Time complexity: O(c) in the length of the text. Space complexity: O(c) for the intermediate
     * strings and the record.
     *
     * @param pPgn complete PGN text of one game, never null
     * @return the parsed record, or null when the White, Black or Result tag is missing
     * @throws NullPointerException if pPgn is null
     */
    private static GameRecord parse(String pPgn) {
        String white = tag(pPgn, "White");
        String black = tag(pPgn, "Black");
        String result = tag(pPgn, "Result");
        String date = tag(pPgn, "Date");
        String timeControl = tag(pPgn, "TimeControl");

        if (white == null || black == null || result == null) return null;

        // only whole tag lines go, a bracket inside a quoted value stays part of its tag
        String moveSection = pPgn.replaceAll("(?m)^\\s*\\[\\w+\\s+" + QUOTED_VALUE + "\\]\\s*$", "").trim();

        List<String> fens = new ArrayList<>();
        Matcher fenMatcher = Pattern.compile("\\{([^}]+)\\}").matcher(moveSection);
        while (fenMatcher.find()) fens.add(fenMatcher.group(1).trim());

        String clean = moveSection
                .replaceAll("\\{[^}]*\\}", "")
                .replaceAll("\\d+\\.", "")
                .replaceAll(Pattern.quote(result), "")
                .trim();

        List<String> moves = new ArrayList<>();
        for (String token : clean.split("\\s+")) {
            String t = token.trim();
            if (!t.isEmpty()) moves.add(t);
        }

        return new GameRecord(
                white, black, result,
                date != null ? date : "?",
                timeControl != null ? timeControl : "?",
                moves, fens);
    }

    // a tag value is a quoted string in which a backslash escapes quotes and backslashes
    private static final String QUOTED_VALUE = "\"((?:[^\"\\\\]|\\\\.)*)\"";

    /**
     * Reads the value of one PGN tag.
     * <p>
     * Tag values are quoted strings that may contain escaped quotes and backslashes, and they may also
     * be empty. The old pattern stopped at the first quote and rejected empty values, which made whole
     * games disappear from the library. I match the value as a proper quoted string and then undo the
     * escaping, where a backslash always protects the character after it.
     * <p>
     * Time complexity: O(c) in the length of the text. Space complexity: O(n) for the value.
     *
     * @param pPgn  complete PGN text, never null
     * @param pName tag name such as "White", never null
     * @return the unescaped tag value, possibly empty, or null when the tag is missing
     * @throws NullPointerException if pPgn or pName is null
     */
    private static String tag(String pPgn, String pName) {
        Matcher m = Pattern.compile("\\[" + Pattern.quote(pName) + "\\s+" + QUOTED_VALUE + "\\]").matcher(pPgn);
        // a backslash always protects the next character
        return m.find() ? m.group(1).replaceAll("\\\\(.)", "$1") : null;
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}