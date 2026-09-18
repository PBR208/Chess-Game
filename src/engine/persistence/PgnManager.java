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

    // marker in the new folder that records that the old games were already copied
    private static final String MIGRATION_MARKER = ".migrated-from-working-directory";

    // the old games folder only has to be looked at once per run
    private static boolean legacyMigrationChecked = false;

    // the Event tag every game is saved with. A game still carrying it was never named by anybody,
    // so the library shows who played instead of repeating this for every entry.
    private static final String DEFAULT_EVENT = "Casual Game";

    /**
     * Purpose: SavedGame is one entry of the library, the parsed game together with the file it was
     * read from and the name a player gave it. The library screen needs the file to delete or rename
     * an entry, and a record on its own has no idea where it came from. Pairing them here rather than
     * handing out two lists means the two can never drift apart, which matters because a file that
     * fails to parse is skipped and would shift every following index.
     *
     * Owner: PBR208 - https://github.com/PBR208/
     * Version: 1.0
     */
    public static final class SavedGame {

        /** the PGN file this game was read from, never null */
        public final Path file;

        /** the parsed game, never null */
        public final GameRecord record;

        /** the name a player gave this game, empty when nobody renamed it; never null */
        public final String name;

        /**
         * Pairs a parsed game with its file and its name.
         * <p>
         * Time complexity: O(1). Space complexity: O(1).
         *
         * @param pFile   file the game was read from, never null
         * @param pRecord parsed game, never null
         * @param pName   name given to the game, empty when it has none; never null
         */
        SavedGame(Path pFile, GameRecord pRecord, String pName) {
            this.file = pFile;
            this.record = pRecord;
            this.name = pName;
        }

        /**
         * Builds the line the library shows for this game.
         * <p>
         * A game nobody renamed is best recognised by who played it, so that stays the whole line. A
         * game with a name of its own leads with that name and keeps the players behind it, because
         * the name alone would lose the result and the date a player searches by.
         * <p>
         * Time complexity: O(n) in the length of the text. Space complexity: O(n) for it.
         *
         * @return the text for this entry, never null
         */
        public String title() {
            return name.isEmpty() ? record.getDisplayTitle() : name + "  |  " + record.getDisplayTitle();
        }
    }

    /**
     * Resolves the directory where finished games are stored as PGN files.
     * <p>
     * Saved games need a predictable home that doesn't depend on where the app was started, and
     * tests need a way to keep their files out of the real library. I first read the chess.gamesDir
     * system property and use it when it holds a non-blank value. Otherwise I use the per-user data
     * folder of the operating system, because the working directory of a double-clicked jar or an
     * installed app is often unwritable or unexpected.
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
        // otherwise the per-user data folder of this operating system
        return defaultGamesDirectory(System.getProperty("os.name", ""), System.getProperty("user.home"),
                System.getenv("APPDATA"), System.getenv("XDG_DATA_HOME"));
    }

    /**
     * Works out the default games folder for an operating system.
     * <p>
     * Every platform has its own place for per-user application data. On Windows I use the roaming
     * application data folder from APPDATA, or AppData\Roaming in the home folder when it isn't set.
     * On macOS I use Library/Application Support in the home folder. Everywhere else I follow the XDG
     * base directory rules, which means XDG_DATA_HOME when it is an absolute path and .local/share in
     * the home folder otherwise. The inputs are parameters, so the rules can be tested for every
     * platform on any machine.
     * <p>
     * Time complexity: O(p) in the length of the paths. Space complexity: O(p) for the result.
     *
     * @param pOsName      value of the os.name property, such as "Windows 11" or "Mac OS X"; never null
     * @param pUserHome    home folder of the user, never null
     * @param pAppData     value of the APPDATA environment variable, may be null or blank
     * @param pXdgDataHome value of the XDG_DATA_HOME environment variable, may be null or blank
     * @return the games folder inside the platform's data folder, never null
     * @throws NullPointerException if pOsName or pUserHome is null
     * @throws InvalidPathException if one of the values is not a valid path on this OS
     */
    public static Path defaultGamesDirectory(String pOsName, String pUserHome, String pAppData, String pXdgDataHome) {
        String os = pOsName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            // roaming application data is where per-user files live on Windows
            Path base = pAppData != null && !pAppData.isBlank()
                    ? Paths.get(pAppData)
                    : Paths.get(pUserHome, "AppData", "Roaming");
            return base.resolve("ChessGame").resolve("games");
        }
        if (os.startsWith("mac")) {
            return Paths.get(pUserHome, "Library", "Application Support", "ChessGame", "games");
        }
        // Linux and other Unix systems, the XDG spec ignores relative values
        Path base = pXdgDataHome != null && !pXdgDataHome.isBlank() && Paths.get(pXdgDataHome).isAbsolute()
                ? Paths.get(pXdgDataHome)
                : Paths.get(pUserHome, ".local", "share");
        return base.resolve("chess-game").resolve("games");
    }

    /**
     * Copies games from the old default folder the first time the new default folder is used.
     * <p>
     * Games used to be saved in a games folder in the working directory, and players shouldn't lose
     * that library now that the default is the per-user data folder. When no explicit folder is
     * configured, I copy the old games over. The marker file limits that to a single time across
     * runs, and a flag makes sure this method only looks once per run.
     * <p>
     * Time complexity: O(f + b) for f old game files with b bytes in total.
     * Space complexity: O(1) apart from the directory stream.
     */
    private static void migrateLegacyGamesOnce() {
        // only the first call in a run needs to look
        if (legacyMigrationChecked) {
            return;
        }
        legacyMigrationChecked = true;
        // an explicitly configured folder is managed by whoever configured it
        String override = System.getProperty(GAMES_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return;
        }
        migrateLegacyGames(Paths.get(System.getProperty("user.dir"), "games"), getGamesDirectory());
    }

    /**
     * Copies the PGN files of an old games folder into a new one, a single time.
     * <p>
     * Moving the default library must not lose games, must not overwrite games and must not bring
     * back games a player deleted later. I do nothing when the old folder doesn't exist, when both
     * folders are the same place, or when the marker file shows that the copy already happened.
     * Otherwise I copy every .pgn file that doesn't exist in the new folder yet, leave the old folder
     * untouched and write the marker. Problems only go to standard error, because the library keeps
     * working without the old games.
     * <p>
     * Time complexity: O(f + b) for f files with b bytes in total.
     * Space complexity: O(1) apart from the directory stream.
     *
     * @param pLegacyDir old games folder, may not exist; never null
     * @param pTargetDir new games folder, created when needed; never null
     * @return how many games were copied, 0 when nothing had to be done
     * @throws NullPointerException if one of the folders is null
     */
    public static int migrateLegacyGames(Path pLegacyDir, Path pTargetDir) {
        // no old folder, or old and new are the same place
        if (!Files.isDirectory(pLegacyDir)
                || pLegacyDir.toAbsolutePath().normalize().equals(pTargetDir.toAbsolutePath().normalize())) {
            return 0;
        }
        Path marker = pTargetDir.resolve(MIGRATION_MARKER);
        // the copy happens once, so games deleted later don't come back
        if (Files.exists(marker)) {
            return 0;
        }
        int copied = 0;
        try {
            Files.createDirectories(pTargetDir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(pLegacyDir, "*.pgn")) {
                for (Path file : stream) {
                    Path target = pTargetDir.resolve(file.getFileName().toString());
                    // never overwrite a game that already exists in the new folder
                    if (!Files.exists(target)) {
                        Files.copy(file, target);
                        copied++;
                    }
                }
            }
            Files.writeString(marker, "copied " + copied + " games from " + pLegacyDir.toAbsolutePath() + "\n");
        } catch (IOException e) {
            System.err.println("PgnManager: could not copy old games from " + pLegacyDir + ": " + e.getMessage());
        }
        return copied;
    }

    /**
     * Saves a finished game as a new PGN file.
     * <p>
     * Every finished game should show up in the Past Games library without overwriting an older
     * one. I bring games from the old default folder along the first time, make sure the games
     * directory exists, build a file name from the date and both player
     * names, append a counter when that name is taken and write the PGN text. I/O failures don't
     * throw, so a full or read-only disk never crashes the game screen, but they are reported on
     * standard error and through the return value, so the caller can tell the player.
     * <p>
     * Time complexity: O(m + f) where m is the number of moves written and f is the number of
     * existing files sharing the same base name. Space complexity: O(m) for the PGN text.
     *
     * @param pRecord finished game to persist, never null
     * @return true if the game was written, false if an I/O error prevented it
     * @throws NullPointerException if pRecord is null
     * @throws InvalidPathException if the configured games directory is not a valid path
     */
    public static boolean save(GameRecord pRecord) {
        try {
            // games from the old default folder come along the first time
            migrateLegacyGamesOnce();
            // create the library folder on first use
            Path dir = getGamesDirectory();
            Files.createDirectories(dir);

            // date and player names make the file easy to recognise
            String base = pRecord.date + "_"
                    + sanitize(pRecord.whiteName) + "_vs_"
                    + sanitize(pRecord.blackName);
            Path file = uniquePath(dir, base, ".pgn");

            Files.writeString(file, buildPgn(pRecord));
            return true;
        } catch (IOException e) {
            System.err.println("PgnManager: failed to save: " + e.getMessage());
            // let the caller tell the player instead of pretending it worked
            return false;
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
     * The Past Games screen lists the whole library. I first bring games from the old default folder
     * along if that hasn't happened yet, and return an empty list when the directory does not exist.
     * Otherwise I collect all .pgn files, sort them by file name in reverse so the
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
        for (SavedGame game : loadLibrary()) {
            records.add(game.record);
        }
        return records;
    }

    /**
     * Loads the whole library, newest first, with the file and the name of every game.
     * <p>
     * The library screen lists every saved game and lets a player delete or rename one, which needs
     * the file each record came from. I bring games from the old default folder along if that hasn't
     * happened yet and return an empty list when the directory does not exist. Otherwise I collect
     * all .pgn files, sort them by file name in reverse so the latest dates come first, and read each
     * one. The name is the Event tag, left empty for a game still carrying the default, so a game
     * nobody renamed is shown by its players. A file that fails to parse is skipped with a message on
     * standard error, so one broken file never hides the rest of the library.
     * <p>
     * Time complexity: O(f log f + c) where f is the number of files and c is the total number of
     * characters parsed. Space complexity: O(c) for the loaded games.
     *
     * @return every readable game with its file, newest first; never null, possibly empty
     * @throws InvalidPathException if the configured games directory is not a valid path
     */
    public static List<SavedGame> loadLibrary() {
        List<SavedGame> games = new ArrayList<>();
        // games from the old default folder come along the first time
        migrateLegacyGamesOnce();
        // no folder yet simply means no saved games yet
        Path dir = getGamesDirectory();
        if (!Files.exists(dir)) return games;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.pgn")) {
            List<Path> files = new ArrayList<>();
            stream.forEach(files::add);
            files.sort(Comparator.comparing(Path::getFileName).reversed());

            for (Path file : files) {
                try {
                    String text = Files.readString(file);
                    GameRecord r = parse(text);
                    if (r != null) {
                        games.add(new SavedGame(file, r, gameName(text)));
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
        return games;
    }

    /**
     * Reads the name a player gave a game.
     * <p>
     * A game is named through its Event tag, which is where the PGN standard puts the name of what
     * was played. Every game this program saves carries the same default there, and a file with no
     * Event tag at all was written by something else, so both count as unnamed.
     * <p>
     * Time complexity: O(c) in the length of the text. Space complexity: O(n) for the name.
     *
     * @param pPgn complete PGN text of one game, never null
     * @return the name, or an empty string when the game has none; never null
     * @throws NullPointerException if pPgn is null
     */
    private static String gameName(String pPgn) {
        String event = tag(pPgn, "Event");
        // no tag, the default every save writes, or a blank value all mean nobody named this game
        if (event == null || event.isBlank() || event.equals(DEFAULT_EVENT)) {
            return "";
        }
        return event;
    }

    /**
     * Deletes one saved game from the library.
     * <p>
     * A library nobody can tidy up only grows, and a player who deletes a game expects it gone from
     * the disk rather than hidden. I delete the file and report whether there was one to delete, so
     * deleting the same entry twice is harmless and answers honestly the second time. An I/O failure
     * doesn't throw, because a read-only disk shouldn't crash the library screen.
     * <p>
     * Time complexity: O(1) apart from what the file system does. Space complexity: O(1).
     *
     * @param pFile the game file to delete, never null
     * @return true if a file was deleted, false if there was none or it could not be deleted
     * @throws NullPointerException if pFile is null
     */
    public static boolean delete(Path pFile) {
        try {
            return Files.deleteIfExists(pFile);
        } catch (IOException e) {
            System.err.println("PgnManager: failed to delete " + pFile.getFileName() + ": " + e.getMessage());
            // let the caller tell the player instead of pretending it worked
            return false;
        }
    }

    /**
     * Gives one saved game a name of its own.
     * <p>
     * "Alice vs Bob" repeated eleven times tells a player nothing, so a game can be named after what
     * made it worth keeping. The name goes into the Event tag, which is where the PGN standard puts
     * it, so other chess programs read it as the name too. I replace the existing tag in place and
     * add one at the top when a file has none, which is possible for an imported game. The value is
     * escaped like any tag value, and I write it by position rather than through a regular expression
     * replacement, so a name containing a backslash or a dollar sign stays exactly what was typed.
     * <p>
     * Time complexity: O(c) in the length of the file. Space complexity: O(c) for the rewritten text.
     *
     * @param pFile the game file to rename, never null
     * @param pName the new name, never null; blank means the game goes back to being unnamed
     * @return true if the file was rewritten, false if an I/O error prevented it
     * @throws NullPointerException if pFile or pName is null
     */
    public static boolean rename(Path pFile, String pName) {
        try {
            String text = Files.readString(pFile);
            // a blank name puts the default back, so the game reads as unnamed again
            String value = pName.isBlank() ? DEFAULT_EVENT : pName.trim();
            // backslashes first, otherwise the escape of a quote would be escaped again
            String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
            String line = "[Event \"" + escaped + "\"]";

            Matcher m = Pattern.compile("(?m)^\\[Event\\s+" + QUOTED_VALUE + "\\]").matcher(text);
            String updated = m.find()
                    ? new StringBuilder(text).replace(m.start(), m.end(), line).toString()
                    : line + "\n" + text;

            Files.writeString(pFile, updated);
            return true;
        } catch (IOException e) {
            System.err.println("PgnManager: failed to rename " + pFile.getFileName() + ": " + e.getMessage());
            // let the caller tell the player instead of pretending it worked
            return false;
        }
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