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

public class PgnManager {

    // system property that redirects saved games, used by tests and portable setups
    public static final String GAMES_DIR_PROPERTY = "chess.gamesDir";

    // marker in the new folder that records that the old games were already copied
    private static final String MIGRATION_MARKER = ".migrated-from-working-directory";

    // the old games folder only has to be looked at once per run
    private static boolean legacyMigrationChecked = false;

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
     * latest dates come first, and read each file. One file may hold several games, and every one of
     * them is listed. A file that fails to parse is skipped with a message on standard error so one
     * broken file never hides the rest of the library.
     * <p>
     * Time complexity: O(f log f + c) where f is the number of files and c is the total number of
     * characters parsed. Space complexity: O(c) for the loaded records.
     *
     * @return all readable games, newest first; never null, possibly empty
     * @throws InvalidPathException if the configured games directory is not a valid path
     */
    public static List<GameRecord> loadAll() {
        List<GameRecord> records = new ArrayList<>();
        // games from the old default folder come along the first time
        migrateLegacyGamesOnce();
        // no folder yet simply means no saved games yet
        Path dir = getGamesDirectory();
        if (!Files.exists(dir)) return records;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.pgn")) {
            List<Path> files = new ArrayList<>();
            stream.forEach(files::add);
            files.sort(Comparator.comparing(Path::getFileName).reversed());

            for (Path file : files) {
                try {
                    // one file may hold a whole tournament, and every game in it belongs in the library
                    List<GameRecord> loaded = PgnReader.readAll(Files.readString(file));
                    if (!loaded.isEmpty()) {
                        records.addAll(loaded);
                    } else {
                        // a file without players or result can't be listed, so say which one it was
                        // TODO [PBR208]: Show skipped files in the Past Games screen, not only on standard error.
                        System.err.println("PgnManager: skipping " + file.getFileName()
                                + ": no game with White, Black and Result tags");
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

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}