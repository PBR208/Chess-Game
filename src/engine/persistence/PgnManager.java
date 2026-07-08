package engine.persistence;

import engine.model.GameRecord;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class PgnManager {

    private static final String GAMES_DIR =
            System.getProperty("user.dir") + File.separator + "games";

    public static void save(GameRecord record) {
        try {
            Path dir = Paths.get(GAMES_DIR);
            Files.createDirectories(dir);

            String base = record.date + "_"
                    + sanitize(record.whiteName) + "_vs_"
                    + sanitize(record.blackName);
            Path file = uniquePath(dir, base, ".pgn");

            Files.writeString(file, buildPgn(record));
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

    // Returns all saved games, newest first
    public static List<GameRecord> loadAll() {
        List<GameRecord> records = new ArrayList<>();
        Path dir = Paths.get(GAMES_DIR);
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