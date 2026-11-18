package net.loyalnetwork.coffeelib.database.hibernate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import net.loyalnetwork.coffeelib.api.database.Database;
import net.loyalnetwork.coffeelib.api.database.DatabaseSettings;
import net.loyalnetwork.coffeelib.api.database.DatabaseType;
import net.loyalnetwork.coffeelib.database.DefaultDatabaseManager;
import net.loyalnetwork.coffeelib.database.OpenDatabases;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hibernate on a CoffeeLib pool, over an embedded H2 file. */
class CoffeeHibernateTest {

    @Entity(name = "Owner")
    public static class Owner {
        @Id
        public String uuid;
        public String name;

        protected Owner() {
        }

        Owner(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public String getName() { // a lazy proxy loads on a method call, never on a field read
            return name;
        }
    }

    @Entity(name = "Home")
    public static class Home {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;
        public String name;
        public double x;
        @ManyToOne(fetch = FetchType.LAZY)
        public Owner owner;

        protected Home() {
        }

        Home(Owner owner, String name, double x) {
            this.owner = owner;
            this.name = name;
            this.x = x;
        }
    }

    @TempDir
    Path directory;

    @AfterEach
    void closeAll() {
        OpenDatabases.closeOwner("hibernatemod");
    }

    private Database open(DatabaseType type) {
        return new DefaultDatabaseManager("hibernatemod", () -> null, getClass().getClassLoader(), () -> directory)
                .open(DatabaseSettings.builder(type).file("homes-" + type).build());
    }

    private Database open() {
        return open(DatabaseType.H2);
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"H2", "SQLITE"})
    void entitiesArePersistedQueriedAndLazilyLoadedThroughThePool(DatabaseType type) {
        Database db = open(type);
        SessionFactory sessions = CoffeeHibernate.sessionFactory(db, Owner.class, Home.class);

        CompletableFuture.runAsync(() -> sessions.inTransaction(session -> {
            Owner owner = new Owner("p1", "Steve");
            session.persist(owner);
            session.persist(new Home(owner, "base", 10.5));
            session.persist(new Home(owner, "mine", -40));
        }), db.executor()).join();

        List<String> names = CompletableFuture.supplyAsync(() -> sessions.fromSession(session -> session
                .createSelectionQuery("select h.name from Home h where h.owner.uuid = :p order by h.name", String.class)
                .setParameter("p", "p1").getResultList()), db.executor()).join();
        assertEquals(List.of("base", "mine"), names);

        // Updates and deletes through HQL, and a rollback that leaves nothing behind.
        CompletableFuture.runAsync(() -> sessions.inTransaction(session -> session
                .createMutationQuery("update Home set x = x + 1 where name = 'base'").executeUpdate()), db.executor()).join();
        assertThrows(RuntimeException.class, () -> CompletableFuture.runAsync(() -> sessions.inTransaction(session -> {
            session.persist(new Home(session.getReference(Owner.class, "p1"), "ghost", 0));
            throw new IllegalStateException("roll back");
        }), db.executor()).join());

        // A lazy association is a generated proxy (ByteBuddy) until touched.
        CompletableFuture.runAsync(() -> sessions.inSession(session -> {
            Home home = session.createSelectionQuery("from Home where name = 'base'", Home.class).getSingleResult();
            assertEquals(11.5, home.x);
            assertFalse(Hibernate.isInitialized(home.owner), "the owner is a lazy proxy");
            assertEquals("Steve", home.owner.getName());
            assertTrue(Hibernate.isInitialized(home.owner));
        }), db.executor()).join();

        // The same tables, read with CoffeeLib's own helpers: one pool, one database.
        assertEquals(2, db.queryFirst("SELECT COUNT(*) FROM Home", rs -> rs.getInt(1)).join().orElseThrow(),
                "the rolled-back home is not there");
        String dialect = sessions.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class)
                .getJdbcServices().getDialect().getClass().getSimpleName();
        assertEquals(type == DatabaseType.SQLITE ? "SQLiteDialect" : "H2Dialect", dialect);
    }

    @Test
    void theSessionFactoryClosesWithTheDatabase() {
        Database db = open();
        SessionFactory sessions = CoffeeHibernate.sessionFactory(db, Owner.class, Home.class);

        db.close();

        assertTrue(sessions.isClosed());
    }

}
