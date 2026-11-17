package net.loyalnetwork.coffeelib.database;

import net.loyalnetwork.coffeelib.api.database.Database;
import net.loyalnetwork.coffeelib.api.database.DatabaseException;
import net.loyalnetwork.coffeelib.api.database.DatabaseType;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The JDBC helpers, over an in-memory H2 database standing in for a server. */
class DatabaseHelpersTest {

    private Database database;

    @BeforeEach
    void open() {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        database = PooledDatabase.over("testmod", DatabaseType.POSTGRESQL, h2, 2);
        database.update("CREATE TABLE crates (id INT PRIMARY KEY, name VARCHAR(32), opened INT)").join();
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void parametersBindInOrderAndRowsMapBack() {
        database.update("INSERT INTO crates VALUES (?, ?, ?)", 1, "vote", 3).join();
        database.update("INSERT INTO crates VALUES (?, ?, ?)", 2, "rare", null).join();

        List<String> names = database.queryList("SELECT name FROM crates ORDER BY id", rs -> rs.getString(1)).join();
        Optional<Integer> opened = database.queryFirst("SELECT opened FROM crates WHERE name = ?",
                rs -> rs.getInt(1), "vote").join();
        Optional<String> none = database.queryFirst("SELECT name FROM crates WHERE id = ?", rs -> rs.getString(1), 99)
                .join();
        int updated = database.update("UPDATE crates SET opened = 0").join();

        assertEquals(List.of("vote", "rare"), names);
        assertEquals(Optional.of(3), opened);
        assertEquals(Optional.empty(), none);
        assertEquals(2, updated);
    }

    @Test
    void helpersRunOffTheCallingThread() {
        AtomicReference<String> thread = new AtomicReference<>();
        database.withConnection(connection -> {
            thread.set(Thread.currentThread().getName());
            return null;
        }).join();

        assertTrue(thread.get().startsWith("CoffeeLib-DB-testmod-"), thread.get());
    }

    @Test
    void aTransactionCommitsWhenItReturnsAndRollsBackWhenItThrows() {
        database.transaction(connection -> {
            connection.createStatement().executeUpdate("INSERT INTO crates VALUES (1, 'vote', 0)");
            return null;
        }).join();

        CompletionException failed = assertThrows(CompletionException.class, () -> database.transaction(connection -> {
            connection.createStatement().executeUpdate("INSERT INTO crates VALUES (2, 'rare', 0)");
            throw new SQLException("something went wrong halfway");
        }).join());

        assertInstanceOf(DatabaseException.class, failed.getCause());
        assertInstanceOf(SQLException.class, failed.getCause().getCause());
        assertEquals(List.of(1), database.queryList("SELECT id FROM crates", rs -> rs.getInt(1)).join(),
                "the second insert was rolled back");
        assertTrue(database.withConnection(connection -> connection.getAutoCommit()).join(),
                "auto-commit is back on for the next borrower");
    }

    @Test
    void aBadStatementFailsTheFutureWithTheDriversMessage() {
        CompletionException failed = assertThrows(CompletionException.class,
                () -> database.update("INSERT INTO nowhere VALUES (1)").join());

        assertInstanceOf(DatabaseException.class, failed.getCause());
        assertTrue(failed.getCause().getMessage().startsWith("[testmod] "), failed.getCause().getMessage());
    }

    @Test
    void aClosedDatabaseRefusesWorkInsteadOfHanging() {
        database.close();

        assertTrue(database.isClosed());
        CompletionException failed = assertThrows(CompletionException.class,
                () -> database.update("DELETE FROM crates").join());
        assertInstanceOf(DatabaseException.class, failed.getCause());
        database.close(); // twice is fine
    }
}
