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
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
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
    private static final Path JAR = OUT.resolve("Chess-Game.jar");

    // the finished application image and the runtime it carries
    private static final Path PACKAGE_DIR = OUT.resolve("package");
    // jpackage wants a folder holding nothing but the jar, so the jar is copied into this one
    private static final Path PACKAGE_INPUT = OUT.resolve("package-input");
    // the linked runtime is built outside the image folder on purpose. jpackage copies it into the
    // image, so leaving it in there would mean shipping the whole runtime twice in every upload
    private static final Path PACKAGE_RUNTIME = OUT.resolve("package-runtime");
    // the only modules the game uses, which is what keeps the bundled runtime small
    private static final String RUNTIME_MODULES = "java.base,java.desktop,java.logging";
    // name of the packaged application, used for the folder and for the launcher inside it
    private static final String APP_NAME = "Chess-Game";

    // entry point of the game, written into the jar manifest
    private static final String MAIN_CLASS = "app.Main";

    // release version for the manifest, pass -Dchess.version=1.2.2 when building a release
    private static final String VERSION = System.getProperty("chess.version", "dev");


    // the class file major version is the Java release plus 44, so 61 for Java 17
    private static final int EXPECTED_CLASS_VERSION = Integer.parseInt(RELEASE) + 44;

    // set once this run has compiled, so later targets can reuse the classes
    private static boolean compiled = false;

    // package prefixes the rules engine must never reach for, checked by the verify target
    private static final List<String> FORBIDDEN_ENGINE_REFERENCES = List.of("ui.", "java.awt", "javax.");

    /**
     * Runs the requested build targets in the order they were given.
     * <p>
     * This is the entry point for "java build/Build.java target...". I first make sure the script
     * runs from the repository root, because every path is relative to it, then execute each target
     * and stop with a non-zero exit code on the first problem. Without arguments the script
     * verifies the engine, compiles, runs the headless tests and packages the jar.
     * <p>
     * Time complexity: O(t) for t targets, each dominated by the size of the source tree it handles.
     * Space complexity: O(t) for the target list.
     *
     * @param pArgs target names clean, compile, verify, test, test-gui, jar or run; may be empty but
     *              never null
     * @throws IOException          if reading sources, writing build output or starting a JVM fails
     * @throws InterruptedException if the script is interrupted while waiting for the tests
     */
    public static void main(String[] pArgs) throws IOException, InterruptedException {
        // all paths are relative, so the working directory has to be the repository root
        if (!Files.isDirectory(SOURCE_ROOT)) {
            fail("run the script from the repository root, for example: java build/Build.java compile");
        }
        // the full build is the useful default
        List<String> targets = pArgs.length == 0 ? List.of("verify", "compile", "test", "jar") : List.of(pArgs);
        for (String target : targets) {
            switch (target) {
                case "clean" -> clean();
                case "compile" -> compile();
                case "verify" -> verify();
                case "test" -> test(false);
                case "test-gui" -> test(true);
                case "jar" -> jar();
                case "run" -> run();
                case "package" -> packageApp();
                default -> fail("unknown target '" + target
                        + "', expected clean, compile, verify, test, test-gui, jar, run or package");
            }
        }
    }

    /**
     * Deletes the output this script produced.
     * <p>
     * A fresh build must not pick up classes of sources that were deleted in the meantime. I only
     * remove the folders and the jar this script owns inside out, so the IDE's own out/production
     * folder is left untouched.
     * <p>
     * Time complexity: O(f) where f is the number of files deleted.
     * Space complexity: O(f) for the directory listing.
     *
     * @throws IOException if a file or folder cannot be deleted
     */
    private static void clean() throws IOException {
        deleteRecursively(MAIN_CLASSES);
        deleteRecursively(TEST_CLASSES);
        Files.deleteIfExists(JAR);
        System.out.println("cleaned " + MAIN_CLASSES + ", " + TEST_CLASSES + " and " + JAR);
    }

    /**
     * Compiles the game and the tests and copies the resources.
     * <p>
     * A plain javac call neither copies resources nor separates game and test classes, which is why
     * the documented command used to produce a game that crashed on its first piece. I start from
     * empty output folders and make sure every source file is plain ASCII. Then I compile everything
     * under src except the tests into out/classes, copy the resources folder next to those classes,
     * and compile the tests into out/test-classes against the game classes.
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

        List<Path> mainSources = javaSources(SOURCE_ROOT, TEST_SOURCES);
        List<Path> testSources = javaSources(TEST_SOURCES, null);
        // ASCII sources compile the same way whatever encoding a compiler assumes
        verifyAsciiSources(mainSources);
        verifyAsciiSources(testSources);

        // the game first, the tests are compiled against it
        runJavac(mainSources, MAIN_CLASSES, null);
        copyResources();

        runJavac(testSources, TEST_CLASSES, MAIN_CLASSES);
        // later targets in the same run can use these classes
        compiled = true;

        System.out.println("compiled " + mainSources.size() + " game sources and "
                + testSources.size() + " test sources for Java " + RELEASE);
    }

    /**
     * Stops the build when the rules engine reaches into the user interface.
     * <p>
     * The engine used to import ui.board.Board and AWT, which meant the rules could not run without
     * a window and a sprite sheet. Nothing but a rule stops that from creeping back in, so this is
     * that rule: I read every source below src/engine and fail on the first line outside a comment
     * that mentions the ui package, java.awt or javax. Comments may still name those classes, for
     * example to explain why something moved, because a comment creates no dependency.
     * <p>
     * Time complexity: O(n) in the total number of characters of the engine sources.
     * Space complexity: O(l) for the lines of the largest source file.
     *
     * @throws IOException if an engine source cannot be read
     */
    private static void verify() throws IOException {
        List<Path> engineSources = javaSources(SOURCE_ROOT.resolve("engine"), null);
        for (Path source : engineSources) {
            int line = 0;
            for (String text : Files.readAllLines(source)) {
                line++;
                String trimmed = text.trim();
                // a comment may name a class without depending on it
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    continue;
                }
                for (String forbidden : FORBIDDEN_ENGINE_REFERENCES) {
                    // the rules have to stay usable in a JVM that has no display at all
                    if (trimmed.contains(forbidden)) {
                        fail(source + " line " + line + " uses " + forbidden + ", the rules engine"
                                + " must not depend on the user interface, AWT or Swing");
                    }
                }
            }
        }
        System.out.println("verified " + engineSources.size()
                + " engine sources are free of ui, AWT and Swing references");
    }

    /**
     * Stops the build when a source file contains anything but ASCII.
     * <p>
     * A JDK 17 compiler on Windows reads sources as windows-1252 unless it is told otherwise, and
     * with characters like arrows or dashes it either garbles the text or refuses to compile at all.
     * Sources that only contain ASCII compile the same with every encoding, so I read every file as
     * bytes and fail with the file and line of the first byte above 127. Special characters belong in
     * Unicode escapes inside strings.
     * <p>
     * Time complexity: O(b) in the total number of source bytes.
     * Space complexity: O(f) for the bytes of the largest file.
     *
     * @param pSources source files to check, never null
     * @throws IOException if a source file cannot be read
     */
    private static void verifyAsciiSources(List<Path> pSources) throws IOException {
        for (Path source : pSources) {
            int line = 1;
            for (byte value : Files.readAllBytes(source)) {
                // bytes of non-ASCII characters are negative as signed Java bytes
                if (value < 0) {
                    fail(source + " line " + line + " contains a non-ASCII character,"
                            + " use a Unicode escape in strings and plain ASCII in comments");
                }
                if (value == '\n') {
                    line++;
                }
            }
        }
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
     * Compiles if needed and runs the test suite in a separate JVM.
     * <p>
     * The suite ends with System.exit, so it needs its own JVM to report a status code without
     * taking the build script down with it. I compile first when this run has not compiled yet,
     * start test.GameTest with the game and test classes on the classpath, force headless mode
     * unless the window tests should run too, and fail the build when the suite reports failures.
     * <p>
     * Time complexity: O(n) for compiling plus the runtime of the suite.
     * Space complexity: O(1) apart from the child process.
     *
     * @param pWithDisplay true to also run the dialog and frame tests on a real display, false to
     *                     run headless so the target works on build machines
     * @throws IOException          if compiling fails or the test JVM cannot be started
     * @throws InterruptedException if the script is interrupted while waiting for the tests
     */
    private static void test(boolean pWithDisplay) throws IOException, InterruptedException {
        // reuse classes compiled earlier in the same run
        if (!compiled) {
            compile();
        }

        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        // headless unless the window tests should run as well
        if (!pWithDisplay) {
            command.add("-Djava.awt.headless=true");
        }
        command.add("-cp");
        // the separator is ';' on Windows and ':' everywhere else
        command.add(MAIN_CLASSES + File.pathSeparator + TEST_CLASSES);
        command.add("test.GameTest");

        int exitCode = runProcess(command);
        if (exitCode != 0) {
            fail("tests failed with exit code " + exitCode);
        }
    }

    /**
     * Compiles if needed and packages the game classes and resources into a runnable jar.
     * <p>
     * A release should be one file that starts with java -jar on any OS. I compile first when this
     * run has not compiled yet and check that every class targets Java 17. Then I write a manifest
     * with app.Main as the main class plus the title, version, Java level and the JDK that built
     * it, and add every file below out/classes with forward slash entry names. Test classes never
     * end up in the jar, because they are compiled into their own folder.
     * <p>
     * Time complexity: O(b) in the total size of the packaged files.
     * Space complexity: O(f) for the list of f packaged files.
     *
     * @throws IOException if compiling fails or the jar cannot be written
     */
    private static void jar() throws IOException {
        // reuse classes compiled earlier in the same run
        if (!compiled) {
            compile();
        }

        // a release must never contain classes that need a newer Java than 17
        verifyClassFileVersions();

        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        // without a manifest version every other attribute is silently ignored
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, MAIN_CLASS);
        // shows which version and Java level a jar was built for
        attributes.put(Attributes.Name.IMPLEMENTATION_TITLE, "Chess-Game");
        attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, VERSION);
        attributes.put(new Attributes.Name("Build-Jdk-Spec"), RELEASE);
        attributes.put(new Attributes.Name("Created-By"),
                System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");

        Files.createDirectories(OUT);
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(JAR), manifest);
             Stream<Path> paths = Files.walk(MAIN_CLASSES)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                // jar entry names always use forward slashes, also on Windows
                String entryName = MAIN_CLASSES.relativize(file).toString().replace(File.separatorChar, '/');
                jar.putNextEntry(new JarEntry(entryName));
                Files.copy(file, jar);
                jar.closeEntry();
            }
        }
        System.out.println("packaged " + JAR);
    }

    /**
     * Makes sure every compiled game class targets exactly Java 17.
     * <p>
     * My last published jar was built for Java 25 and refused to start on anything older, although
     * the project promises Java 17. I read the header of every class file below out/classes and stop
     * the build when a file is not a class file or its major version differs from the one Java 17
     * uses. That way a changed compiler setting or stray classes from another build can never slip
     * into a release.
     * <p>
     * Time complexity: O(c) for c class files, each read only up to its first eight bytes.
     * Space complexity: O(c) for the list of class file paths.
     *
     * @throws IOException if a class file cannot be read
     */
    private static void verifyClassFileVersions() throws IOException {
        try (Stream<Path> paths = Files.walk(MAIN_CLASSES)) {
            for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).collect(Collectors.toList())) {
                try (DataInputStream in = new DataInputStream(Files.newInputStream(classFile))) {
                    // every class file starts with CAFEBABE, then minor and major version
                    int magic = in.readInt();
                    in.readUnsignedShort();
                    int major = in.readUnsignedShort();
                    if (magic != 0xCAFEBABE) {
                        fail(classFile + " is not a valid class file");
                    }
                    // anything else would not start on the Java version the project promises
                    if (major != EXPECTED_CLASS_VERSION) {
                        fail(classFile + " has class file version " + major + ", expected "
                                + EXPECTED_CLASS_VERSION + " for Java " + RELEASE);
                    }
                }
            }
        }
    }

    /**
     * Compiles if needed and starts the game from the compiled classes.
     * <p>
     * During development I want to start the game with one command and the current sources. I
     * compile first when this run has not compiled yet and launch app.Main in a child JVM from the
     * same JDK, with the game classes and copied resources on the classpath. A non-zero exit code of
     * the game fails the build.
     * <p>
     * Time complexity: O(n) for compiling plus the time the game stays open.
     * Space complexity: O(1) apart from the child process.
     *
     * @throws IOException          if compiling fails or the game JVM cannot be started
     * @throws InterruptedException if the script is interrupted while the game is running
     */
    private static void run() throws IOException, InterruptedException {
        // reuse classes compiled earlier in the same run
        if (!compiled) {
            compile();
        }
        // the class folder already contains the resources
        int exitCode = runProcess(List.of(javaExecutable(), "-cp", MAIN_CLASSES.toString(), MAIN_CLASS));
        if (exitCode != 0) {
            fail("the game exited with code " + exitCode);
        }
    }

    /**
     * Builds a self contained application image with a Java runtime inside it.
     * <p>
     * The commonest way for this game to fail has nothing to do with chess: somebody downloads the
     * jar, has no Java or the wrong one, and never gets to the board. An application image carries
     * its own runtime, so there is nothing to install and nothing to match. I build the jar first,
     * then link a runtime out of only the three modules the game actually uses, which is a fraction
     * of a whole JDK, and hand that runtime to jpackage together with a folder holding nothing but
     * the jar. The output folder is cleared first, because both tools refuse to write into one that
     * already exists.
     * <p>
     * jpackage cannot build for another operating system, so this produces an image for the machine
     * it runs on and each platform has to build its own. An app image needs no extra tooling, while
     * the installer formats do: an msi or exe needs WiX on Windows, and a signed and notarized macOS
     * app needs an Apple developer account, which is a manual step rather than something CI can do.
     * <p>
     * Time complexity: O(n + r) for n bytes of application and r bytes of runtime that get copied.
     * Space complexity: O(n + r) on disk for the finished image.
     *
     * @throws IOException          if the output cannot be written or the jar cannot be copied
     * @throws InterruptedException if the script is interrupted while jlink or jpackage runs
     */
    private static void packageApp() throws IOException, InterruptedException {
        // the image is built out of the jar, so the jar has to exist and be current
        jar();

        deleteRecursively(PACKAGE_DIR);
        deleteRecursively(PACKAGE_INPUT);
        deleteRecursively(PACKAGE_RUNTIME);
        Files.createDirectories(PACKAGE_INPUT);
        // jpackage copies everything it finds in the input folder, so it gets the jar alone
        Files.copy(JAR, PACKAGE_INPUT.resolve(JAR.getFileName()), StandardCopyOption.REPLACE_EXISTING);

        Path runtime = PACKAGE_RUNTIME;
        int linked = runProcess(List.of(toolExecutable("jlink"),
                "--add-modules", RUNTIME_MODULES,
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--output", runtime.toString()));
        if (linked != 0) {
            fail("jlink could not build the runtime");
        }

        int packaged = runProcess(List.of(toolExecutable("jpackage"),
                "--type", "app-image",
                "--name", APP_NAME,
                "--app-version", packageVersion(),
                "--input", PACKAGE_INPUT.toString(),
                "--main-jar", JAR.getFileName().toString(),
                "--main-class", MAIN_CLASS,
                "--runtime-image", runtime.toString(),
                "--dest", PACKAGE_DIR.toString()));
        if (packaged != 0) {
            fail("jpackage could not build the application image");
        }

        System.out.println("packaged the application image into " + PACKAGE_DIR
                + " for " + System.getProperty("os.name"));
    }

    /**
     * Returns a version jpackage is willing to accept.
     * <p>
     * jpackage only takes a version made of numbers and dots, and this script defaults to "dev" when
     * nobody passes one, which would fail the whole packaging run over a label. A development build
     * gets a stand in version instead, and a release passes its real one with -Dchess.version.
     * <p>
     * Time complexity: O(n) in the length of the version. Space complexity: O(1).
     *
     * @return the version to stamp on the application image, never null
     */
    private static String packageVersion() {
        // anything that is not a number or a dot is a name rather than a version
        for (char symbol : VERSION.toCharArray()) {
            if (!Character.isDigit(symbol) && symbol != '.') {
                return "1.0.0";
            }
        }
        return VERSION.isEmpty() ? "1.0.0" : VERSION;
    }

    /**
     * Returns one of the JDK's own tools, from the same installation that runs this script.
     * <p>
     * jlink and jpackage have to come from the JDK the build uses, not from whichever one happens to
     * be first on the PATH, or the runtime inside the image would not be the one the jar was built
     * for. I build the path from the java.home property. The name needs no .exe on Windows, because
     * process creation adds it there.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @param pTool name of the tool, such as jlink or jpackage; never null
     * @return the path of that tool, never null
     */
    private static String toolExecutable(String pTool) {
        return Paths.get(System.getProperty("java.home"), "bin", pTool).toString();
    }

    /**
     * Returns the java launcher of the JDK that runs this script.
     * <p>
     * Child JVMs should use the same Java installation as the build, not whichever java comes first
     * on the PATH. I build the path from the java.home system property. The name works without the
     * .exe suffix on Windows, because process creation adds it there.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return path of the java launcher, never null
     */
    private static String javaExecutable() {
        return Paths.get(System.getProperty("java.home"), "bin", "java").toString();
    }

    /**
     * Runs a command as a child process on the same console and waits for it to finish.
     * <p>
     * Test output should appear live next to the build output. I start the process with inherited
     * standard streams and return its exit code once it ends.
     * <p>
     * Time complexity: O(1) apart from the runtime of the child process.
     * Space complexity: O(k) for the k command arguments held by the process builder.
     *
     * @param pCommand program followed by its arguments, never null and never empty
     * @return exit code of the finished process
     * @throws IOException          if the process cannot be started
     * @throws InterruptedException if waiting for the process is interrupted
     */
    private static int runProcess(List<String> pCommand) throws IOException, InterruptedException {
        // share stdout and stderr so progress shows up right away
        Process process = new ProcessBuilder(pCommand).inheritIO().start();
        return process.waitFor();
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
                // jpackage leaves its launcher read only, and Windows refuses to delete a read only
                // file, which made a second packaging run fail with an access denied error every
                // time. Clearing the flag first costs nothing anywhere else.
                path.toFile().setWritable(true);
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
