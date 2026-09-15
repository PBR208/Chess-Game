/*
 * Purpose: Build is the zero-dependency build script for the chess game. It compiles the game and
 * its tests with the JDK's own compiler and copies the resources next to the compiled classes, so
 * the sprite sheet is always found on the classpath. I wrote it as one Java file that runs with
 * "java build/Build.java", which means the same command works on Windows, macOS and Linux without
 * Maven, Gradle or shell scripts. Everything is compiled for Java 17 with UTF-8 source encoding,
 * no matter which newer JDK runs the script.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Build {

    // every class file targets Java 17, whatever JDK runs this script
    private static final String RELEASE = "17";

    // project layout, relative to the repository root
    private static final Path SOURCE_ROOT = Paths.get("src");
    private static final Path TEST_SOURCES = SOURCE_ROOT.resolve("test");
    private static final Path RESOURCES = SOURCE_ROOT.resolve("resources");
    private static final Path OUT = Paths.get("out");
    private static final Path MAIN_CLASSES = OUT.resolve("classes");
    private static final Path TEST_CLASSES = OUT.resolve("test-classes");

    /**
     * Runs the requested build targets in the order they were given.
     * <p>
     * This is the entry point for "java build/Build.java target...". I first make sure the script
     * runs from the repository root, because every path is relative to it, then execute each target
     * and stop with a non-zero exit code on the first problem. Without arguments the script
     * compiles everything.
     * <p>
     * Time complexity: O(t) for t targets, each dominated by the size of the source tree it handles.
     * Space complexity: O(t) for the target list.
     *
     * @param pArgs target names such as clean or compile; may be empty but never null
     * @throws IOException if reading sources or writing build output fails
     */
    public static void main(String[] pArgs) throws IOException {
        // all paths are relative, so the working directory has to be the repository root
        if (!Files.isDirectory(SOURCE_ROOT)) {
            fail("run the script from the repository root, for example: java build/Build.java compile");
        }
        // compiling is the useful default
        List<String> targets = pArgs.length == 0 ? List.of("compile") : List.of(pArgs);
        for (String target : targets) {
            switch (target) {
                case "clean" -> clean();
                case "compile" -> compile();
                default -> fail("unknown target '" + target + "', expected clean or compile");
            }
        }
    }

    /**
     * Deletes the output this script produced.
     * <p>
     * A fresh build must not pick up classes of sources that were deleted in the meantime. I only
     * remove the folders this script owns inside out, so the IDE's own out/production folder is
     * left untouched.
     * <p>
     * Time complexity: O(f) where f is the number of files deleted.
     * Space complexity: O(f) for the directory listing.
     *
     * @throws IOException if a file or folder cannot be deleted
     */
    private static void clean() throws IOException {
        deleteRecursively(MAIN_CLASSES);
        deleteRecursively(TEST_CLASSES);
        System.out.println("cleaned " + MAIN_CLASSES + " and " + TEST_CLASSES);
    }

    /**
     * Compiles the game and the tests and copies the resources.
     * <p>
     * A plain javac call neither copies resources nor separates game and test classes, which is why
     * the documented command used to produce a game that crashed on its first piece. I start from
     * empty output folders, compile everything under src except the tests into out/classes, copy the
     * resources folder next to those classes, and then compile the tests into out/test-classes
     * against the game classes.
     * <p>
     * Time complexity: O(n + r) where n is the total size of the sources and r the size of the
     * resources. Space complexity: O(k) for the list of k source paths.
     *
     * @throws IOException if sources cannot be listed or output cannot be written
     */
    private static void compile() throws IOException {
        // start empty so classes of deleted sources can't linger
        deleteRecursively(MAIN_CLASSES);
        deleteRecursively(TEST_CLASSES);

        // the game first, the tests are compiled against it
        List<Path> mainSources = javaSources(SOURCE_ROOT, TEST_SOURCES);
        runJavac(mainSources, MAIN_CLASSES, null);
        copyResources();

        List<Path> testSources = javaSources(TEST_SOURCES, null);
        runJavac(testSources, TEST_CLASSES, MAIN_CLASSES);

        System.out.println("compiled " + mainSources.size() + " game sources and "
                + testSources.size() + " test sources for Java " + RELEASE);
    }

    /**
     * Lists all Java source files below a folder, optionally leaving out one subfolder.
     * <p>
     * The game and the tests share one source root, so the game build has to skip the test folder.
     * I walk the folder tree, keep files ending in .java that are not inside the excluded folder and
     * sort them so the compiler always sees the same order.
     * <p>
     * Time complexity: O(f log f) where f is the number of files below the folder.
     * Space complexity: O(f) for the collected paths.
     *
     * @param pRoot     folder to search, must exist; never null
     * @param pExcluded subfolder to leave out, or null to keep everything
     * @return sorted source file paths, never null, possibly empty
     * @throws IOException if the folder tree cannot be read
     */
    private static List<Path> javaSources(Path pRoot, Path pExcluded) throws IOException {
        try (Stream<Path> paths = Files.walk(pRoot)) {
            return paths
                    // only Java sources
                    .filter(path -> path.toString().endsWith(".java"))
                    // skip the excluded subtree, if any
                    .filter(path -> pExcluded == null || !path.startsWith(pExcluded))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /**
     * Compiles a set of sources for Java 17 with the in-process JDK compiler.
     * <p>
     * Running javac through javax.tools avoids starting a separate process and works the same on
     * every OS. I check that a compiler is available at all, create the output folder, pass
     * --release 17 and UTF-8 encoding so the class files and string literals are identical on every
     * machine, add the classpath when one is given and stop the build if javac reports errors. The
     * compiler prints its own diagnostics.
     * <p>
     * Time complexity: O(n) in the total size of the sources, as far as this method is concerned.
     * Space complexity: O(k) for the argument list of k sources.
     *
     * @param pSources   source files to compile, never null and never empty
     * @param pOutput    folder that receives the class files, created if missing; never null
     * @param pClasspath folder with already compiled classes to compile against, or null for none
     * @throws IOException if the output folder cannot be created
     */
    private static void runJavac(List<Path> pSources, Path pOutput, Path pClasspath) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        // a plain JRE ships without a compiler
        if (javac == null) {
            fail("no Java compiler available, run the script with a JDK instead of a JRE");
        }
        Files.createDirectories(pOutput);

        // same bytecode level and source encoding on every machine
        List<String> args = new ArrayList<>(List.of("--release", RELEASE, "-encoding", "UTF-8", "-d", pOutput.toString()));
        if (pClasspath != null) {
            args.add("-classpath");
            args.add(pClasspath.toString());
        }
        for (Path source : pSources) {
            args.add(source.toString());
        }

        // javac prints diagnostics itself, non-zero means compile errors
        int result = javac.run(null, null, null, args.toArray(new String[0]));
        if (result != 0) {
            fail("javac reported errors while compiling into " + pOutput);
        }
    }

    /**
     * Copies the resources folder next to the compiled game classes.
     * <p>
     * The game loads its sprite sheet from the classpath path /resources/pieces.png. I copy every
     * file below src/resources into out/classes/resources while keeping the relative paths, so that
     * lookup works from the class folder and later from the jar.
     * <p>
     * Time complexity: O(r) in the total size of the copied files.
     * Space complexity: O(f) for the list of f resource files.
     *
     * @throws IOException if a resource cannot be copied
     */
    private static void copyResources() throws IOException {
        // keep the folder name, the game looks resources up under /resources
        Path target = MAIN_CLASSES.resolve(RESOURCES.getFileName().toString());
        try (Stream<Path> paths = Files.walk(RESOURCES)) {
            for (Path source : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
                Path destination = target.resolve(RESOURCES.relativize(source).toString());
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Deletes a folder with everything inside it, if it exists.
     * <p>
     * Output folders are rebuilt from scratch on every compile. I walk the tree and delete the
     * deepest paths first, so every folder is already empty when its turn comes.
     * <p>
     * Time complexity: O(f log f) where f is the number of paths below the folder.
     * Space complexity: O(f) for the sorted path list.
     *
     * @param pDirectory folder to delete; may not exist, never null
     * @throws IOException if a path cannot be deleted
     */
    private static void deleteRecursively(Path pDirectory) throws IOException {
        // nothing to do for a folder that was never created
        if (!Files.exists(pDirectory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(pDirectory)) {
            // children sort after their parents, so reverse order deletes children first
            for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.delete(path);
            }
        }
    }

    /**
     * Stops the build with an error message and exit code 1.
     * <p>
     * Scripts and CI jobs rely on the exit code to notice a broken build. I print the reason to
     * standard error and exit right away.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pMessage reason the build failed, never null
     */
    private static void fail(String pMessage) {
        System.err.println("build failed: " + pMessage);
        System.exit(1);
    }
}
