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
            System.err.println("PgnManager: failed to save — " + e.getMessage());
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
                    if (r != null) records.add(r);
                } catch (Exception e) {
                    System.err.println("PgnManager: skipping " + file.getFileName()
                            + " — " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("PgnManager: failed to list games — " + e.getMessage());
        }
        return records;
    }

    private static String buildPgn(GameRecord r) {
        StringBuilder sb = new StringBuilder();

        sb.append("[Event \"Casual Game\"]\n");
        sb.append("[Site \"Local\"]\n");
        sb.append("[Date \"").append(r.date).append("\"]\n");
        sb.append("[White \"").append(r.whiteName).append("\"]\n");
        sb.append("[Black \"").append(r.blackName).append("\"]\n");
        sb.append("[Result \"").append(r.result).append("\"]\n");
        sb.append("[TimeControl \"").append(r.timeControl).append("\"]\n");
        sb.append("\n");

        for (int i = 0; i < r.moves.size(); i++) {
            if (i % 2 == 0) sb.append(i / 2 + 1).append(". ");
            sb.append(r.moves.get(i));
            if (i < r.fenHistory.size()) {
                sb.append(" {").append(r.fenHistory.get(i)).append("}");
            }
            sb.append(" ");
        }

        sb.append(r.result).append("\n");
        return sb.toString();
    }

    private static GameRecord parse(String pgn) {
        String white = tag(pgn, "White");
        String black = tag(pgn, "Black");
        String result = tag(pgn, "Result");
        String date = tag(pgn, "Date");
        String timeControl = tag(pgn, "TimeControl");

        if (white == null || black == null || result == null) return null;

        String moveSection = pgn.replaceAll("\\[.*?\\]\n?", "").trim();

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

    private static String tag(String pgn, String name) {
        Matcher m = Pattern.compile("\\[" + name + " \"([^\"]+)\"\\]").matcher(pgn);
        return m.find() ? m.group(1) : null;
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}