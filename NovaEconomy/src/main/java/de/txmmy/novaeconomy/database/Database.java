package de.txmmy.novaeconomy.database;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class Database implements AutoCloseable {
    @FunctionalInterface
    public interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }

    private final JavaPlugin plugin;
    private final Path databasePath;
    private final int busyTimeoutMs;
    private Connection connection;

    public Database(JavaPlugin plugin, String fileName, int busyTimeoutMs) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.databasePath = plugin.getDataFolder().toPath().resolve(fileName);
        this.busyTimeoutMs = busyTimeoutMs;
    }

    public synchronized void open() {
        try {
            Files.createDirectories(databasePath.getParent());
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
                statement.execute("PRAGMA busy_timeout = " + busyTimeoutMs);
            }
            createMigrationTable();
            applyMigrations();
        } catch (IOException | ClassNotFoundException | SQLException exception) {
            throw new DataAccessException("SQLite-Datenbank konnte nicht initialisiert werden", exception);
        }
    }

    public synchronized <T> T read(SqlWork<T> work) {
        ensureOpen();
        try {
            return work.execute(connection);
        } catch (SQLException exception) {
            throw new DataAccessException("SQLite-Leseoperation fehlgeschlagen", exception);
        }
    }

    public synchronized <T> T transaction(SqlWork<T> work) {
        ensureOpen();
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Throwable throwable) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    throwable.addSuppressed(rollbackFailure);
                }
                if (throwable instanceof SQLException sqlException) {
                    throw sqlException;
                }
                if (throwable instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new SQLException("Transaktion fehlgeschlagen", throwable);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new DataAccessException("SQLite-Transaktion fehlgeschlagen", exception);
        }
    }

    private void createMigrationTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        version INTEGER PRIMARY KEY,
                        description TEXT NOT NULL,
                        applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    private void applyMigrations() throws IOException, SQLException {
        InputStream migrationConfigStream = plugin.getResource("migrations.yml");
        if (migrationConfigStream == null) {
            throw new IOException("migrations.yml fehlt in der Plugin-JAR");
        }

        YamlConfiguration migrationConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(migrationConfigStream, StandardCharsets.UTF_8));
        List<Migration> migrations = new ArrayList<>();
        for (var entry : migrationConfig.getMapList("migrations")) {
            int version = ((Number) entry.get("version")).intValue();
            String resource = String.valueOf(entry.get("resource"));
            Object descriptionValue = entry.containsKey("description") ? entry.get("description") : resource;
            String description = String.valueOf(descriptionValue);
            migrations.add(new Migration(version, resource, description));
        }
        migrations.sort(Comparator.comparingInt(Migration::version));

        for (Migration migration : migrations) {
            if (isMigrationApplied(migration.version())) {
                continue;
            }
            applyMigration(migration);
            plugin.getLogger().info("SQLite-Migration V" + migration.version() + " angewendet: " + migration.description());
        }
    }

    private boolean isMigrationApplied(int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM schema_migrations WHERE version = ?")) {
            statement.setInt(1, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void applyMigration(Migration migration) throws IOException, SQLException {
        InputStream scriptStream = plugin.getResource(migration.resource());
        if (scriptStream == null) {
            throw new IOException("Migrationsressource fehlt: " + migration.resource());
        }
        String script;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(scriptStream, StandardCharsets.UTF_8))) {
            script = reader.lines().reduce("", (left, right) -> left + right + "\n");
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (String statementText : splitSqlStatements(script)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(statementText);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, description) VALUES (?, ?)")) {
                statement.setInt(1, migration.version());
                statement.setString(2, migration.description());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    static List<String> splitSqlStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        String[] lines = script.replace("\r", "").split("\n");
        for (String line : lines) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("--") || trimmed.isBlank()) {
                continue;
            }
            for (int index = 0; index < line.length(); index++) {
                char character = line.charAt(index);
                if (character == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                } else if (character == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                }

                if (character == ';' && !inSingleQuote && !inDoubleQuote) {
                    String statement = current.toString().trim();
                    if (!statement.isEmpty()) {
                        statements.add(statement);
                    }
                    current.setLength(0);
                } else {
                    current.append(character);
                }
            }
            current.append('\n');
        }
        String trailing = current.toString().trim();
        if (!trailing.isEmpty()) {
            statements.add(trailing);
        }
        return statements;
    }

    private void ensureOpen() {
        if (connection == null) {
            throw new IllegalStateException("Datenbank ist nicht geöffnet");
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("SQLite-Verbindung konnte nicht sauber geschlossen werden: " + exception.getMessage());
        } finally {
            connection = null;
        }
    }

    private record Migration(int version, String resource, String description) {
    }
}
