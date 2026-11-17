package net.loyalnetwork.coffeelib.api.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A pooled connection to one database.
 * <p>
 * Two ways to use it, freely mixed:
 * <ul>
 *   <li>{@link #dataSource()} - the pool itself, as a plain JDBC
 *       {@link DataSource}, for whatever sits on top: Hibernate, jOOQ, JDBI,
 *       or your own JDBC code;</li>
 *   <li>the helpers below, for plain SQL without the boilerplate. Each runs on
 *       {@link #executor()} - never on the calling thread, so never on the
 *       server thread - and returns a future. Parameters bind in order to the
 *       statement's {@code ?} placeholders, with {@code null} as SQL NULL.</li>
 * </ul>
 * Opened databases are closed when their owner shuts down (the plugin is
 * disabled on Paper, the game exits on Forge and NeoForge); {@link #close()}
 * closes one earlier.
 */
public interface Database extends AutoCloseable {

    /** Which server this connects to. */
    DatabaseType type();

    /** The connection pool. Borrowed connections must be closed, which returns them to it. */
    DataSource dataSource();

    /**
     * Where the helpers run their work, one thread per pooled connection.
     * Usable for your own blocking database code too.
     */
    Executor executor();

    /** Runs {@code sql} and hands its result set to {@code reader}, whose return value completes the future. */
    <T> CompletableFuture<T> query(String sql, SqlFunction<ResultSet, T> reader, Object... parameters);

    /** Runs {@code sql} and maps each row with {@code mapper} (called with the cursor on that row). */
    <T> CompletableFuture<List<T>> queryList(String sql, SqlFunction<ResultSet, T> mapper, Object... parameters);

    /** The first row mapped with {@code mapper}, or empty when there is none. */
    <T> CompletableFuture<Optional<T>> queryFirst(String sql, SqlFunction<ResultSet, T> mapper, Object... parameters);

    /** Runs an {@code INSERT}, {@code UPDATE}, {@code DELETE} or DDL statement; the future holds the update count. */
    CompletableFuture<Integer> update(String sql, Object... parameters);

    /** Runs {@code work} with a connection from the pool, closed (returned) afterwards. */
    <T> CompletableFuture<T> withConnection(SqlFunction<Connection, T> work);

    /**
     * Runs {@code work} in a transaction: committed when it returns, rolled
     * back when it throws. Auto-commit is restored before the connection goes
     * back to the pool.
     */
    <T> CompletableFuture<T> transaction(SqlFunction<Connection, T> work);

    boolean isClosed();

    /** Closes the pool and stops the executor. Work already queued fails with a {@link DatabaseException}. */
    @Override
    void close();
}
