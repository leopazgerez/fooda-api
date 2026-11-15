import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Renombra la identidad del template (paquete, groupId, artifactId, base de datos, clase principal). Una sola vez.
 * Uso, desde la ra\u00edz del repo: java scripts/Rename.java com.miempresa.tienda
 */
public class Rename {

    private static final String OLD_PACKAGE = "com.example.app";
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "_");

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("uso: java scripts/Rename.java <paquete>   (por ejemplo com.miempresa.tienda)");
        }
        String pkg = args[0];
        if (!pkg.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")) {
            throw new IllegalArgumentException("'" + pkg + "' no es un paquete v\u00e1lido: min\u00fasculas, al menos dos partes, por ejemplo com.miempresa.tienda");
        }
        if (Arrays.stream(pkg.split("\\.")).anyMatch(JAVA_KEYWORDS::contains)) {
            throw new IllegalArgumentException("'" + pkg + "' usa una palabra reservada de Java");
        }
        if (pkg.equals(OLD_PACKAGE) || pkg.startsWith(OLD_PACKAGE + ".")) {
            throw new IllegalArgumentException("el paquete nuevo no puede ser " + OLD_PACKAGE + " ni estar adentro");
        }
        Path oldMain = Path.of("src", "main", "java", "com", "example", "app");
        if (!Files.isRegularFile(Path.of("pom.xml"))) {
            throw new IllegalStateException("corr\u00e9 el script desde la ra\u00edz del repo");
        }
        if (!Files.isRegularFile(oldMain.resolve("App.java"))) {
            throw new IllegalStateException("el proyecto ya fue renombrado (no existe " + oldMain.resolve("App.java") + ")");
        }

        String groupId = pkg.substring(0, pkg.lastIndexOf('.'));
        String name = pkg.substring(pkg.lastIndexOf('.') + 1);
        String mainClass = Arrays.stream(name.split("_")).filter(s -> !s.isEmpty())
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1)).collect(Collectors.joining()) + "App";

        // 1. Compute every change in memory: a missing pattern aborts before anything is written.
        Map<Path, String> edits = new LinkedHashMap<>();
        List<Path> roots = List.of(Path.of("src", "main", "java"), Path.of("src", "test", "java"));
        for (Path root : roots) {
            Path old = root.resolve("com/example/app");
            if (!Files.isDirectory(old)) {
                continue;
            }
            for (Path f : list(old)) {
                if (f.toString().endsWith(".java")) {
                    String src = read(f);
                    if (src.contains(OLD_PACKAGE)) {
                        edits.put(f, src.replace(OLD_PACKAGE, pkg));
                    }
                }
            }
        }
        Path app = oldMain.resolve("App.java");
        edits.put(app, require(require(edits.getOrDefault(app, read(app)), app, "class App ", "class " + mainClass + " "),
                app, "App.class", mainClass + ".class"));

        Path pom = Path.of("pom.xml");
        String pomSrc = read(pom);
        int parentEnd = pomSrc.indexOf("</parent>");
        if (parentEnd < 0) {
            throw new IllegalStateException("no encontr\u00e9 </parent> en pom.xml");
        }
        String pomTail = pomSrc.substring(parentEnd);
        pomTail = first(pomTail, pom, "<groupId>com.example</groupId>", "<groupId>" + groupId + "</groupId>");
        pomTail = first(pomTail, pom, "<artifactId>app</artifactId>", "<artifactId>" + name + "</artifactId>");
        pomTail = first(pomTail, pom, "<name>app</name>", "<name>" + name + "</name>");
        edits.put(pom, pomSrc.substring(0, parentEnd) + pomTail);

        Path props = Path.of("src", "main", "resources", "application.properties");
        String p = read(props);
        p = require(p, props, "spring.application.name=app\n", "spring.application.name=" + name + "\n");
        p = require(p, props, "localhost:5432/app}", "localhost:5432/" + name + "}");
        p = require(p, props, "${DB_USERNAME:app}", "${DB_USERNAME:" + name + "}");
        p = require(p, props, "${DB_PASSWORD:app}", "${DB_PASSWORD:" + name + "}");
        edits.put(props, p);

        Path compose = Path.of("docker-compose.yaml");
        String c = read(compose);
        c = require(c, compose, "POSTGRES_DB: app\n", "POSTGRES_DB: " + name + "\n");
        c = require(c, compose, "POSTGRES_USER: app\n", "POSTGRES_USER: " + name + "\n");
        c = require(c, compose, "POSTGRES_PASSWORD: app\n", "POSTGRES_PASSWORD: " + name + "\n");
        edits.put(compose, c);

        Path env = Path.of(".env.example");
        String e = read(env);
        e = require(e, env, "localhost:5432/app\n", "localhost:5432/" + name + "\n");
        e = require(e, env, "DB_USERNAME=app\n", "DB_USERNAME=" + name + "\n");
        e = require(e, env, "DB_PASSWORD=app\n", "DB_PASSWORD=" + name + "\n");
        edits.put(env, e);

        Path readme = Path.of("README.md");
        String r = read(readme);
        int start = r.indexOf("<!-- rename:start -->");
        int end = r.indexOf("<!-- rename:end -->");
        if (start < 0 || end < start) {
            throw new IllegalStateException("no encontr\u00e9 la secci\u00f3n <!-- rename:start --> ... <!-- rename:end --> en README.md");
        }
        r = r.substring(0, start) + r.substring(end + "<!-- rename:end -->".length()).replaceFirst("^\\R+", "");
        r = require(r, readme, "localhost:5432/app", "localhost:5432/" + name);
        r = require(r, readme, "`app` / `app`", "`" + name + "` / `" + name + "`");
        edits.put(readme, r.replace(OLD_PACKAGE, pkg));

        // 2. Write contents, then move the package trees.
        for (Map.Entry<Path, String> edit : edits.entrySet()) {
            String text = CRLF.contains(edit.getKey()) ? edit.getValue().replace("\n", "\r\n") : edit.getValue();
            Files.writeString(edit.getKey(), text, StandardCharsets.UTF_8);
            System.out.println("editado " + edit.getKey());
        }
        for (Path root : roots) {
            Path old = root.resolve("com/example/app");
            if (!Files.isDirectory(old)) {
                continue;
            }
            Path target = root.resolve(pkg.replace('.', '/'));
            for (Path f : list(old)) {
                Path to = target.resolve(old.relativize(f).toString());
                if (f.equals(app)) {
                    to = to.resolveSibling(mainClass + ".java");
                }
                Files.createDirectories(to.getParent());
                Files.move(f, to);
            }
            deleteEmptyDirs(old, root);
            System.out.println("movido  " + old + " -> " + target);
        }

        System.out.println();
        System.out.println("Listo: " + pkg + " (groupId " + groupId + ", nombre " + name + ", clase " + mainClass + ").");
        System.out.println("La base ahora se llama " + name + ": si ya ten\u00edas el contenedor, docker compose down -v && docker compose up -d, y actualiz\u00e1 tu .env.");
        System.out.println("Siguiente: ./mvnw clean test   (en Windows: mvnw.cmd clean test)");
    }

    /** Replaces every occurrence; at least one has to exist. */
    private static String require(String content, Path file, String from, String to) {
        if (!content.contains(from)) {
            throw new IllegalStateException("no encontr\u00e9 '" + from.strip() + "' en " + file + "; no se cambi\u00f3 nada");
        }
        return content.replace(from, to);
    }

    private static String first(String content, Path file, String from, String to) {
        int i = content.indexOf(from);
        if (i < 0) {
            throw new IllegalStateException("no encontr\u00e9 '" + from + "' en " + file + "; no se cambi\u00f3 nada");
        }
        return content.substring(0, i) + to + content.substring(i + from.length());
    }

    private static List<Path> list(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    /** Removes the emptied old tree and its empty parents (com/example), never the source root. */
    private static void deleteEmptyDirs(Path old, Path root) throws IOException {
        List<Path> dirs;
        try (Stream<Path> paths = Files.walk(old)) {
            dirs = paths.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        }
        for (Path d : dirs) {
            deleteIfEmpty(d);
        }
        for (Path d = old.getParent(); d != null && !d.equals(root); d = d.getParent()) {
            if (!deleteIfEmpty(d)) {
                break;
            }
        }
    }

    private static boolean deleteIfEmpty(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            if (entries.findAny().isPresent()) {
                return false;
            }
        }
        Files.delete(dir);
        return true;
    }

    /** Files checked out with CRLF (Windows): edited as LF, written back as CRLF. */
    private static final Set<Path> CRLF = new java.util.HashSet<>();

    private static String read(Path p) throws IOException {
        String s = Files.readString(p, StandardCharsets.UTF_8);
        if (s.contains("\r\n")) {
            CRLF.add(p);
        }
        return s.replace("\r\n", "\n");
    }
}
