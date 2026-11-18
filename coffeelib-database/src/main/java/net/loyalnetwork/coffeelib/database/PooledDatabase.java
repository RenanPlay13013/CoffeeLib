package net.loyalnetwork.coffeelib.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import net.loyalnetwork.coffeelib.api.database.Database;
import net.loyalnetwork.coffeelib.api.database.DatabaseException;
import net.loyalnetwork.coffeelib.api.database.DatabaseSettings;
import net.loyalnetwork.coffeelib.api.database.DatabaseType;
import net.loyalnetwork.coffeelib.api.database.SqlFunction;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link Database} over a pool and an executor of the same size, so a
 * helper's task always finds a connection instead of queueing for one while
 * holding a thread.
 */
final class PooledDatabase implements Database {

    private final String ownerId;
    private final DatabaseType type;
    private final DataSource dataSource;
    private final AutoCloseable pool;
    private final ExecutorService executor;
    private final java.util.Deque<AutoCloseable> dependents = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private volatile boolean closed;

    private PooledDatabase(String ownerId, ClassLoader ownerLoader, DatabaseType type, DataSource dataSource,
                           AutoCloseable pool, int threads) {
        this.ownerId = ownerId;
        this.type = type;
        this.dataSource = dataSource;
        this.pool = pool;
        this.executor = Executors.newFixedThreadPool(threads, threads(ownerId, ownerLoader));
    }

    /**
     * Connects with Hikari. The pool's first connection is opened here, so bad
     * settings fail now, with the reason, rather than on the first query.
     *
     * @param ownerLoader the owner's class loader, made the context loader of
     *                    the executor's threads - so a library the owner runs
     *                    there (Hibernate finding its entities) sees the
     *                    owner's classes
     */
    static PooledDatabase connect(String ownerId, ClassLoader ownerLoader, DatabaseSettings settings) {
        if (settings.type().isEmbedded() && settings.jdbcUrl() == null) {
            createParent(ownerId, settings.file());
        }
        if (settings.type() == DatabaseType.SQLITE && !sqliteAvailable(ownerLoader)) {
            throw new DatabaseException("[" + ownerId + "] SQLite's driver (org.sqlite.JDBC) is not on the classpath. "
                    + "CoffeeLib's Forge and NeoForge jars carry it; on Paper it comes with the server");
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("CoffeeLib-" + ownerId);
        config.setDriverClassName(JdbcUrls.driverClass(settings.type()));
        config.setJdbcUrl(JdbcUrls.url(settings));
        if (!settings.type().isEmbedded()) {
            config.setUsername(settings.username());
            config.setPassword(settings.password());
        }
        // SQLite takes one writer at a time; a second pooled connection would only meet
        // SQLITE_BUSY. One connection, waiting politely when the file is busy.
        int poolSize = settings.type() == DatabaseType.SQLITE ? 1 : settings.poolSize();
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.min(2, poolSize));
        if (settings.type() == DatabaseType.SQLITE) {
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("busy_timeout", "5000");
        }
        config.setConnectionTimeout(settings.connectionTimeoutMillis());
        settings.properties().forEach(config::addDataSourceProperty);
        if (needsPublicKeyRetrieval(settings)) {
            // MySQL 8's default authentication (caching_sha2_password) sends the password
            // either over TLS or encrypted with the server's RSA key. Without TLS, the driver
            // must be allowed to ask for that key, or every connection fails with "RSA public
            // key is not available client side". Configuring sslMode, or the option itself,
            // leaves the choice to the operator.
            config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
        }

        // Hikari looks a driver up in its own loader, then in the thread's. The bundled
        // drivers are found in its own; SQLite is not bundled - nested in the mod jar on Forge
        // and NeoForge, the server's on Paper - and the owner's loader is one that sees it.
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(ownerLoader);
        try {
            HikariDataSource pool = new HikariDataSource(config);
            return new PooledDatabase(ownerId, ownerLoader, settings.type(), pool, pool, poolSize);
        } catch (HikariPool.PoolInitializationException e) {
            Throwable reason = e.getCause() != null ? e.getCause() : e;
            throw new DatabaseException("[" + ownerId + "] Could not connect to " + settings + ": " + reason.getMessage(), e);
        } catch (RuntimeException e) {
            throw new DatabaseException("[" + ownerId + "] Could not set up a pool for " + settings + ": " + e.getMessage(), e);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static void createParent(String ownerId, String file) {
        java.nio.file.Path parent = java.nio.file.Path.of(file).toAbsolutePath().getParent();
        try {
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
        } catch (java.io.IOException e) {
            throw new DatabaseException("[" + ownerId + "] Could not create " + parent + " for the database file", e);
        }
    }

    private static boolean sqliteAvailable(ClassLoader ownerLoader) {
        for (ClassLoader loader : new ClassLoader[]{PooledDatabase.class.getClassLoader(), ownerLoader}) {
            if (loader == null) {
                continue;
            }
            try {
                Class.forName("org.sqlite.JDBC", false, loader);
                return true;
            } catch (ClassNotFoundException | LinkageError ignored) {
                // try the next loader
            }
        }
        return false;
    }

    static boolean needsPublicKeyRetrieval(DatabaseSettings settings) {
        if (settings.type() != DatabaseType.MYSQL) {
            return false;
        }
        String url = settings.jdbcUrl() == null ? "" : settings.jdbcUrl();
        for (String option : new String[]{"allowPublicKeyRetrieval", "sslMode", "serverRsaPublicKeyFile"}) {
            if (settings.properties().containsKey(option) || url.contains(option + "=")) {
                return false;
            }
        }
        return true;
    }

    /** Over an existing data source - for tests, which have no server to pool. */
    static PooledDatabase over(String ownerId, DatabaseType type, DataSource dataSource, int threads) {
        return new PooledDatabase(ownerId, PooledDatabase.class.getClassLoader(), type, dataSource, () -> { }, threads);
    }

    @Override
    public DatabaseType type() {
        return type;
    }

    @Override
    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public ExecutorService executor() {
        return executor;
    }

    @Override
    public <T> CompletableFuture<T> query(String sql, SqlFunction<ResultSet, T> reader, Object... parameters) {
        return withConnection(connection -> {
            try (PreparedStatement statement = prepare(connection, sql, parameters);
                 ResultSet results = statement.executeQuery()) {
                return reader.apply(results);
            }
        });
    }

    @Override
    public <T> CompletableFuture<List<T>> queryList(String sql, SqlFunction<ResultSet, T> mapper, Object... parameters) {
        return query(sql, results -> {
            List<T> rows = new ArrayList<>();
            while (results.next()) {
                rows.add(mapper.apply(results));
            }
            return rows;
        }, parameters);
    }

    @Override
    public <T> CompletableFuture<Optional<T>> queryFirst(String sql, SqlFunction<ResultSet, T> mapper,
                                                         Object... parameters) {
        return query(sql, results -> results.next() ? Optional.ofNullable(mapper.apply(results)) : Optional.empty(),
                parameters);
    }

    @Override
    public CompletableFuture<Integer> update(String sql, Object... parameters) {
        return withConnection(connection -> {
            try (PreparedStatement statement = prepare(connection, sql, parameters)) {
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> withConnection(SqlFunction<Connection, T> work) {
        return submit(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return work.apply(connection);
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> transaction(SqlFunction<Connection, T> work) {
        return submit(() -> {
            try (Connection connection = dataSource.getConnection()) {
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    T result = work.apply(connection);
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException | Error e) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailed) {
                        e.addSuppressed(rollbackFailed);
                    }
                    throw e;
                } finally {
                    connection.setAutoCommit(autoCommit);
                }
            }
        });
    }

    @Override
    public void closeWith(AutoCloseable resource) {
        dependents.push(java.util.Objects.requireNonNull(resource, "resource"));
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        for (AutoCloseable dependent; (dependent = dependents.poll()) != null; ) {
            try {
                dependent.close();
            } catch (Exception e) {
                java.util.logging.Logger.getLogger(PooledDatabase.class.getName()).log(java.util.logging.Level.WARNING,
                        "[CoffeeLib] [" + ownerId + "] Could not close " + dependent + " before its database", e);
            }
        }
        try {
            pool.close();
        } catch (Exception e) {
            throw new DatabaseException("[" + ownerId + "] Could not close the connection pool", e);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run() throws SQLException;
    }

    private <T> CompletableFuture<T> submit(SqlWork<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (closed) {
            future.completeExceptionally(new DatabaseException("[" + ownerId + "] The database is closed"));
            return future;
        }
        try {
            executor.execute(() -> {
                try {
                    future.complete(work.run());
                } catch (SQLException e) {
                    future.completeExceptionally(new DatabaseException("[" + ownerId + "] " + e.getMessage(), e));
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(new DatabaseException("[" + ownerId + "] The database is closed", e));
        }
        return future;
    }

    static PreparedStatement prepare(Connection connection, String sql, Object... parameters) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            return statement;
        } catch (SQLException | RuntimeException e) {
            statement.close();
            throw e;
        }
    }

    private static ThreadFactory threads(String ownerId, ClassLoader ownerLoader) {
        AtomicInteger count = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "CoffeeLib-DB-" + ownerId + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            // Code running here (a mapper, Hibernate) loads classes the way the owner's code would.
            thread.setContextClassLoader(ownerLoader);
            return thread;
        };
    }
}
