import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Genera el esqueleto CRUD de un m\u00f3dulo hexagonal (como user), con auditor\u00eda y borrado l\u00f3gico.
 * Uso, desde la ra\u00edz del repo: java scripts/NewModule.java Product
 */
public class NewModule {

    /** Java keywords plus classes the generated files import, compared against the lowercased entity. */
    private static final Set<String> JAVA_RESERVED = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "var", "record", "yield", "sealed",
            "permits", "true", "false", "null", "object", "string",
            "clock", "instant", "objects", "optional", "sort", "component", "transactional", "entity", "table", "id",
            "column", "version", "jwt", "principals");

    /** PostgreSQL fully reserved keywords, compared against the table name. */
    private static final Set<String> SQL_RESERVED = Set.of(
            "all", "analyse", "analyze", "and", "any", "array", "as", "asc", "asymmetric", "both", "case", "cast",
            "check", "collate", "column", "constraint", "create", "current_catalog", "current_date", "current_role",
            "current_time", "current_timestamp", "current_user", "default", "deferrable", "desc", "distinct", "do",
            "else", "end", "except", "false", "fetch", "for", "foreign", "from", "grant", "group", "having", "in",
            "initially", "intersect", "into", "lateral", "leading", "limit", "localtime", "localtimestamp", "not",
            "null", "offset", "on", "only", "or", "order", "placing", "primary", "references", "returning", "select",
            "session_user", "some", "symmetric", "system_user", "table", "then", "to", "trailing", "true", "union",
            "unique", "user", "using", "variadic", "when", "where", "window", "with");

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
            throw new IllegalArgumentException("uso: java scripts/NewModule.java <Entidad>   (por ejemplo Product)");
        }
        String entity = args[0];
        if (!entity.matches("[A-Z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("'" + entity + "' tiene que estar en PascalCase singular, por ejemplo Product u OrderItem");
        }
        if (JAVA_RESERVED.contains(entity.toLowerCase(Locale.ROOT)) || SQL_RESERVED.contains(words(entity, "_"))) {
            throw new IllegalArgumentException("'" + entity + "' es una palabra reservada de Java o SQL; eleg\u00ed otro nombre");
        }
        Path javaRoot = Path.of("src", "main", "java");
        Path migrations = Path.of("src", "main", "resources", "db", "migration");
        if (!Files.isRegularFile(Path.of("pom.xml")) || !Files.isDirectory(javaRoot) || !Files.isDirectory(migrations)) {
            throw new IllegalStateException("corr\u00e9 el script desde la ra\u00edz del repo");
        }

        String base = basePackage(javaRoot);
        String module = entity.toLowerCase(Locale.ROOT);
        Path moduleDir = javaRoot.resolve(base.replace('.', '/')).resolve(module);
        if (Files.exists(moduleDir)) {
            throw new IllegalStateException("ya existe " + moduleDir + "; no se gener\u00f3 nada");
        }

        String plural = plural(entity);
        String table = words(entity, "_");
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("{{BASE}}", base);
        vars.put("{{Entity}}", entity);
        vars.put("{{entity}}", Character.toLowerCase(entity.charAt(0)) + entity.substring(1));
        vars.put("{{Plural}}", plural);
        vars.put("{{module}}", module);
        vars.put("{{table}}", table);
        vars.put("{{idColumn}}", table + "_id");
        vars.put("{{route}}", "/api/" + words(plural, "-"));

        Map<Path, String> files = new LinkedHashMap<>();
        String e = entity;
        add(files, moduleDir, "domain/" + e + ".java", DOMAIN, vars);
        add(files, moduleDir, "domain/" + e + "Id.java", DOMAIN_ID, vars);
        add(files, moduleDir, "application/port/out/" + e + "Repository.java", PORT_OUT, vars);
        add(files, moduleDir, "application/port/in/Manage" + plural + "UseCase.java", PORT_IN, vars);
        add(files, moduleDir, "application/service/" + e + "Service.java", SERVICE, vars);
        add(files, moduleDir, "config/" + e + "Config.java", CONFIG, vars);
        add(files, moduleDir, "infrastructure/out/persistence/" + e + "JpaEntity.java", JPA_ENTITY, vars);
        add(files, moduleDir, "infrastructure/out/persistence/" + e + "JpaRepository.java", JPA_REPOSITORY, vars);
        add(files, moduleDir, "infrastructure/out/persistence/" + e + "PersistenceAdapter.java", ADAPTER, vars);
        add(files, moduleDir, "infrastructure/in/web/" + plural + "Controller.java", CONTROLLER, vars);
        add(files, moduleDir, "infrastructure/in/web/" + e + "Request.java", REQUEST, vars);
        add(files, moduleDir, "infrastructure/in/web/" + e + "Response.java", RESPONSE, vars);
        add(files, migrations, "V" + nextMigration(migrations) + "__" + table + ".sql", MIGRATION, vars);

        checkClashes(javaRoot, migrations, files.keySet(), table);

        for (Map.Entry<Path, String> f : files.entrySet()) {
            Files.createDirectories(f.getKey().getParent());
            Files.writeString(f.getKey(), f.getValue(), StandardCharsets.UTF_8);
            System.out.println("creado  " + f.getKey());
        }
        System.out.println();
        System.out.println("M\u00f3dulo " + module + " listo en " + vars.get("{{route}}") + ". Faltan los campos de negocio (busc\u00e1 TODO).");
        System.out.println("Siguiente: ./mvnw test   (en Windows: mvnw.cmd test)");
    }

    /** Package of the single @SpringBootApplication class, so this works before and after Rename. */
    private static String basePackage(Path javaRoot) throws IOException {
        Pattern pkg = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
        List<String> found;
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            found = paths.filter(p -> p.toString().endsWith(".java")).map(NewModule::read)
                    .filter(src -> src.contains("@SpringBootApplication"))
                    .map(src -> {
                        Matcher m = pkg.matcher(src);
                        return m.find() ? m.group(1) : "";
                    })
                    .collect(Collectors.toList());
        }
        if (found.size() != 1 || found.get(0).isEmpty()) {
            throw new IllegalStateException("no encontr\u00e9 una \u00fanica clase @SpringBootApplication con package en " + javaRoot);
        }
        return found.get(0);
    }

    /** A generated class or table that already exists breaks startup (duplicate bean names, CREATE TABLE twice). */
    private static void checkClashes(Path javaRoot, Path migrations, Set<Path> generated, String table) throws IOException {
        Set<String> existing;
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            existing = paths.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".java"))
                    .collect(Collectors.toSet());
        }
        for (Path f : generated) {
            if (existing.contains(f.getFileName().toString())) {
                throw new IllegalStateException("ya existe una clase " + f.getFileName() + " en el proyecto; eleg\u00ed otro nombre");
            }
        }
        Pattern create = Pattern.compile("(?i)create\\s+table\\s+(if\\s+not\\s+exists\\s+)?\"?" + table + "\"?\\b");
        try (Stream<Path> paths = Files.list(migrations)) {
            for (Path m : paths.collect(Collectors.toList())) {
                if (create.matcher(read(m)).find()) {
                    throw new IllegalStateException("la tabla " + table + " ya existe (" + m.getFileName() + "); eleg\u00ed otro nombre");
                }
            }
        }
    }

    private static int nextMigration(Path migrations) throws IOException {
        Pattern version = Pattern.compile("^V(\\d+)__.*");
        try (Stream<Path> paths = Files.list(migrations)) {
            return paths.map(p -> version.matcher(p.getFileName().toString()))
                    .filter(Matcher::matches)
                    .mapToInt(m -> Integer.parseInt(m.group(1)))
                    .max().orElse(0) + 1;
        }
    }

    // ponytail: naive English plural; rename the generated classes by hand for irregular words
    static String plural(String word) {
        if (word.matches(".*[^aeiouAEIOU]y")) {
            return word.substring(0, word.length() - 1) + "ies";
        }
        if (word.matches(".*(s|x|z|ch|sh)")) {
            return word + "es";
        }
        return word + "s";
    }

    /** OrderItem -> order_item (sep "_") or order-item (sep "-"). */
    static String words(String pascal, String sep) {
        return pascal.replaceAll("([a-z0-9])([A-Z])", "$1" + sep + "$2").toLowerCase(Locale.ROOT);
    }

    private static void add(Map<Path, String> files, Path dir, String relative, String template, Map<String, String> vars) {
        String content = template;
        for (Map.Entry<String, String> v : vars.entrySet()) {
            content = content.replace(v.getKey(), v.getValue());
        }
        files.put(dir.resolve(relative), content);
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("no pude leer " + p, e);
        }
    }

    private static final String DOMAIN = """
            package {{BASE}}.{{module}}.domain;

            import java.time.Instant;
            import java.util.Objects;

            /** deletedAt != null means soft-deleted; version is the optimistic lock (null until stored). */
            public record {{Entity}}({{Entity}}Id id, long createdBy, Instant createdAt, Instant updatedAt, Instant deletedAt,
                                 Long version) {

                // TODO: campos de negocio. Validalos ac\u00e1 y lanz\u00e1 ValidationException(campo, mensaje) de shared.domain.

                public {{Entity}} {
                    Objects.requireNonNull(createdAt, "createdAt");
                    Objects.requireNonNull(updatedAt, "updatedAt");
                }

                public {{Entity}} touched(Instant now) {
                    return new {{Entity}}(id, createdBy, createdAt, now, deletedAt, version);
                }

                public {{Entity}} deleted(Instant now) {
                    return new {{Entity}}(id, createdBy, createdAt, now, now, version);
                }
            }
            """;

    private static final String DOMAIN_ID = """
            package {{BASE}}.{{module}}.domain;

            public record {{Entity}}Id(long value) {
            }
            """;

    private static final String PORT_OUT = """
            package {{BASE}}.{{module}}.application.port.out;

            import {{BASE}}.shared.application.Page;
            import {{BASE}}.{{module}}.domain.{{Entity}};
            import {{BASE}}.{{module}}.domain.{{Entity}}Id;

            import java.util.Optional;

            /** Soft-deleted rows are invisible to every read. */
            public interface {{Entity}}Repository {

                Optional<{{Entity}}> findById({{Entity}}Id id);

                {{Entity}} save({{Entity}} {{entity}});

                /** Ordered by id; page starts at 0. */
                Page<{{Entity}}> list(int page, int size);
            }
            """;

    private static final String PORT_IN = """
            package {{BASE}}.{{module}}.application.port.in;

            import {{BASE}}.shared.application.Page;
            import {{BASE}}.{{module}}.domain.{{Entity}};
            import {{BASE}}.{{module}}.domain.{{Entity}}Id;

            // TODO: con campos de negocio, create y update reciben un New{{Entity}} / {{Entity}}Changes (como NewUser / UserChanges)
            public interface Manage{{Plural}}UseCase {

                Page<{{Entity}}> list(int page, int size);

                {{Entity}} get({{Entity}}Id id);

                {{Entity}} create(long actor);

                {{Entity}} update({{Entity}}Id id);

                /** Soft delete: the row stays, with deletedAt set. */
                void delete({{Entity}}Id id);
            }
            """;

    private static final String SERVICE = """
            package {{BASE}}.{{module}}.application.service;

            import {{BASE}}.shared.application.Page;
            import {{BASE}}.shared.application.exception.ResourceNotFoundException;
            import {{BASE}}.shared.domain.ValidationException;
            import {{BASE}}.{{module}}.application.port.in.Manage{{Plural}}UseCase;
            import {{BASE}}.{{module}}.application.port.out.{{Entity}}Repository;
            import {{BASE}}.{{module}}.domain.{{Entity}};
            import {{BASE}}.{{module}}.domain.{{Entity}}Id;

            import java.time.Clock;
            import java.time.Instant;

            public class {{Entity}}Service implements Manage{{Plural}}UseCase {

                private final {{Entity}}Repository repository;
                private final Clock clock;

                public {{Entity}}Service({{Entity}}Repository repository, Clock clock) {
                    this.repository = repository;
                    this.clock = clock;
                }

                @Override
                public Page<{{Entity}}> list(int page, int size) {
                    if (page < 0) {
                        throw new ValidationException("page", "La p\u00e1gina debe ser 0 o mayor");
                    }
                    if (size < 1 || size > 100) {
                        throw new ValidationException("size", "El tama\u00f1o debe estar entre 1 y 100");
                    }
                    return repository.list(page, size);
                }

                @Override
                public {{Entity}} get({{Entity}}Id id) {
                    return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("No se encontr\u00f3 el recurso"));
                }

                @Override
                public {{Entity}} create(long actor) {
                    Instant now = clock.instant();
                    return repository.save(new {{Entity}}(null, actor, now, now, null, null));
                }

                @Override
                public {{Entity}} update({{Entity}}Id id) {
                    return repository.save(get(id).touched(clock.instant()));
                }

                @Override
                public void delete({{Entity}}Id id) {
                    repository.save(get(id).deleted(clock.instant()));
                }
            }
            """;

    private static final String CONFIG = """
            package {{BASE}}.{{module}}.config;

            import {{BASE}}.{{module}}.application.port.out.{{Entity}}Repository;
            import {{BASE}}.{{module}}.application.service.{{Entity}}Service;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            import java.time.Clock;

            @Configuration(proxyBeanMethods = false)
            public class {{Entity}}Config {

                @Bean
                {{Entity}}Service {{entity}}Service({{Entity}}Repository repository, Clock clock) {
                    return new {{Entity}}Service(repository, clock);
                }
            }
            """;

    private static final String JPA_ENTITY = """
            package {{BASE}}.{{module}}.infrastructure.out.persistence;

            import jakarta.persistence.Column;
            import jakarta.persistence.Entity;
            import jakarta.persistence.GeneratedValue;
            import jakarta.persistence.GenerationType;
            import jakarta.persistence.Id;
            import jakarta.persistence.Table;
            import jakarta.persistence.Version;

            import java.time.Instant;

            @Entity
            @Table(name = "{{table}}")
            class {{Entity}}JpaEntity {

                @Id
                @GeneratedValue(strategy = GenerationType.IDENTITY)
                @Column(name = "{{idColumn}}")
                Long id;

                // TODO: campos de negocio (y sus columnas en una migraci\u00f3n nueva)

                @Column(name = "created_by", nullable = false, updatable = false)
                long createdBy;

                @Column(name = "created_at", nullable = false, updatable = false)
                Instant createdAt;

                @Column(name = "updated_at", nullable = false)
                Instant updatedAt;

                @Column(name = "deleted_at")
                Instant deletedAt;

                /** Optimistic lock: a save from a stale snapshot fails instead of overwriting newer data. */
                @Version
                @Column(name = "version", nullable = false)
                Long version;
            }
            """;

    private static final String JPA_REPOSITORY = """
            package {{BASE}}.{{module}}.infrastructure.out.persistence;

            import org.springframework.data.domain.Page;
            import org.springframework.data.domain.Pageable;
            import org.springframework.data.jpa.repository.JpaRepository;

            import java.util.Optional;

            interface {{Entity}}JpaRepository extends JpaRepository<{{Entity}}JpaEntity, Long> {

                Optional<{{Entity}}JpaEntity> findByIdAndDeletedAtIsNull(Long id);

                Page<{{Entity}}JpaEntity> findAllByDeletedAtIsNull(Pageable pageable);
            }
            """;

    private static final String ADAPTER = """
            package {{BASE}}.{{module}}.infrastructure.out.persistence;

            import {{BASE}}.shared.application.Page;
            import {{BASE}}.{{module}}.application.port.out.{{Entity}}Repository;
            import {{BASE}}.{{module}}.domain.{{Entity}};
            import {{BASE}}.{{module}}.domain.{{Entity}}Id;
            import org.springframework.data.domain.PageRequest;
            import org.springframework.data.domain.Sort;
            import org.springframework.stereotype.Component;
            import org.springframework.transaction.annotation.Transactional;

            import java.util.Optional;

            @Component
            public class {{Entity}}PersistenceAdapter implements {{Entity}}Repository {

                private final {{Entity}}JpaRepository repository;

                public {{Entity}}PersistenceAdapter({{Entity}}JpaRepository repository) {
                    this.repository = repository;
                }

                @Override
                @Transactional(readOnly = true)
                public Optional<{{Entity}}> findById({{Entity}}Id id) {
                    return repository.findByIdAndDeletedAtIsNull(id.value()).map({{Entity}}PersistenceAdapter::toDomain);
                }

                @Override
                @Transactional
                public {{Entity}} save({{Entity}} {{entity}}) {
                    return toDomain(repository.saveAndFlush(toEntity({{entity}})));
                }

                @Override
                @Transactional(readOnly = true)
                public Page<{{Entity}}> list(int page, int size) {
                    var result = repository.findAllByDeletedAtIsNull(PageRequest.of(page, size, Sort.by("id")));
                    return new Page<>(result.map({{Entity}}PersistenceAdapter::toDomain).getContent(), page, size,
                            result.getTotalElements());
                }

                private static {{Entity}} toDomain({{Entity}}JpaEntity e) {
                    return new {{Entity}}(new {{Entity}}Id(e.id), e.createdBy, e.createdAt, e.updatedAt, e.deletedAt, e.version);
                }

                private static {{Entity}}JpaEntity toEntity({{Entity}} d) {
                    {{Entity}}JpaEntity e = new {{Entity}}JpaEntity();
                    e.id = d.id() == null ? null : d.id().value();
                    e.createdBy = d.createdBy();
                    e.createdAt = d.createdAt();
                    e.updatedAt = d.updatedAt();
                    e.deletedAt = d.deletedAt();
                    e.version = d.version();
                    return e;
                }
            }
            """;

    private static final String CONTROLLER = """
            package {{BASE}}.{{module}}.infrastructure.in.web;

            import {{BASE}}.shared.application.Page;
            import {{BASE}}.shared.infrastructure.in.web.Principals;
            import {{BASE}}.{{module}}.application.port.in.Manage{{Plural}}UseCase;
            import {{BASE}}.{{module}}.domain.{{Entity}}Id;
            import org.springframework.http.ResponseEntity;
            import org.springframework.security.core.annotation.AuthenticationPrincipal;
            import org.springframework.security.oauth2.jwt.Jwt;
            import org.springframework.web.bind.annotation.DeleteMapping;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PatchMapping;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestBody;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RequestParam;
            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

            /** Any authenticated user. To restrict it, see "Roles y permisos" in the README. */
            @RestController
            @RequestMapping("{{route}}")
            public class {{Plural}}Controller {

                private final Manage{{Plural}}UseCase useCase;

                public {{Plural}}Controller(Manage{{Plural}}UseCase useCase) {
                    this.useCase = useCase;
                }

                @GetMapping
                public Page<{{Entity}}Response> list(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
                    return useCase.list(page, size).map({{Entity}}Response::from);
                }

                @GetMapping("/{id}")
                public {{Entity}}Response get(@PathVariable long id) {
                    return {{Entity}}Response.from(useCase.get(new {{Entity}}Id(id)));
                }

                @PostMapping
                public ResponseEntity<{{Entity}}Response> create(@AuthenticationPrincipal Jwt jwt, @RequestBody {{Entity}}Request r) {
                    {{Entity}}Response created = {{Entity}}Response.from(useCase.create(Principals.userId(jwt)));
                    return ResponseEntity.created(
                            ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
                            .body(created);
                }

                @PatchMapping("/{id}")
                public {{Entity}}Response update(@PathVariable long id, @RequestBody {{Entity}}Request r) {
                    return {{Entity}}Response.from(useCase.update(new {{Entity}}Id(id)));
                }

                @DeleteMapping("/{id}")
                public ResponseEntity<Void> delete(@PathVariable long id) {
                    useCase.delete(new {{Entity}}Id(id));
                    return ResponseEntity.noContent().build();
                }
            }
            """;

    private static final String REQUEST = """
            package {{BASE}}.{{module}}.infrastructure.in.web;

            // TODO: campos de negocio que manda el cliente (y pasalos al caso de uso en el controller)
            public record {{Entity}}Request() {
            }
            """;

    private static final String RESPONSE = """
            package {{BASE}}.{{module}}.infrastructure.in.web;

            import {{BASE}}.{{module}}.domain.{{Entity}};

            import java.time.Instant;

            // TODO: campos de negocio que ve el cliente
            public record {{Entity}}Response(long id, long createdBy, Instant createdAt, Instant updatedAt) {

                public static {{Entity}}Response from({{Entity}} d) {
                    return new {{Entity}}Response(d.id().value(), d.createdBy(), d.createdAt(), d.updatedAt());
                }
            }
            """;

    private static final String MIGRATION = """
            CREATE TABLE {{table}} (
                {{idColumn}} BIGSERIAL   PRIMARY KEY,
                created_by   BIGINT      NOT NULL REFERENCES app_user (user_id),
                created_at   TIMESTAMPTZ NOT NULL,
                updated_at   TIMESTAMPTZ NOT NULL,
                deleted_at   TIMESTAMPTZ,
                version      BIGINT      NOT NULL DEFAULT 0
            );
            """;
}
