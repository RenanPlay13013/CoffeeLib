package net.loyalnetwork.coffeelib.database.hibernate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.loyalnetwork.coffeelib.api.database.Database;
import net.loyalnetwork.coffeelib.api.database.DatabaseException;
import net.loyalnetwork.coffeelib.api.database.DatabaseSettings;
import net.loyalnetwork.coffeelib.api.database.DatabaseType;
import net.loyalnetwork.coffeelib.database.DefaultDatabaseManager;
import net.loyalnetwork.coffeelib.database.OpenDatabases;
import net.loyalnetwork.coffeelib.database.hibernate.CoffeeHibernateTest.Home;
import net.loyalnetwork.coffeelib.database.hibernate.CoffeeHibernateTest.Owner;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HibernateRepository's CRUD, on SQLite and H2 files. */
class HibernateRepositoryTest {

    /** An entity with a NOT NULL column, for transactions that must fail part way. */
    @Entity(name = "Crate")
    public static class Crate {
        @Id
        public String id;
        @Column(nullable = false)
        public String kind;

        protected Crate() {
        }

        Crate(String id, String kind) {
            this.id = id;
            this.kind = kind;
        }
    }

    static final class OwnerRepository extends HibernateRepository<Owner, String> {
        OwnerRepository(SessionFactory sessions, Database database) {
            super(sessions, database, Owner.class);
        }

        CompletableFuture<Optional<Owner>> findByName(String name) {
            return read(session -> session
                    .createSelectionQuery("from " + entityName() + " where name = :name", Owner.class)
                    .setParameter("name", name)
                    .uniqueResultOptional());
        }

        CompletableFuture<String> threadName() {
            return read(session -> Thread.currentThread().getName());
        }
    }

    static final class HomeRepository extends HibernateRepository<Home, Long> {
        HomeRepository(SessionFactory sessions, Database database) {
            super(sessions, database, Home.class);
        }
    }

    static final class CrateRepository extends HibernateRepository<Crate, String> {
        CrateRepository(SessionFactory sessions, Database database) {
            super(sessions, database, Crate.class);
        }
    }

    @TempDir
    Path directory;

    private Database db;
    private SessionFactory sessions;

    @AfterEach
    void closeAll() {
        OpenDatabases.closeOwner("repomod");
    }

    private void open(DatabaseType type) {
        db = new DefaultDatabaseManager("repomod", () -> null, getClass().getClassLoader(), () -> directory)
                .open(DatabaseSettings.builder(type).file("repo-" + type).build());
        sessions = CoffeeHibernate.sessionFactory(db, Owner.class, Home.class, Crate.class);
    }

    private static DatabaseException failure(CompletableFuture<?> future) {
        CompletionException failed = assertThrows(CompletionException.class, future::join);
        return assertInstanceOf(DatabaseException.class, failed.getCause());
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"SQLITE", "H2"})
    void saveInsertsThenUpdatesTheSameRow(DatabaseType type) {
        open(type);
        OwnerRepository owners = new OwnerRepository(sessions, db);

        owners.save(new Owner("p1", "Steve")).join();
        Owner renamed = owners.save(new Owner("p1", "Alex")).join();

        assertEquals(1L, owners.count().join(), "the same id updates, it does not duplicate");
        assertEquals("Alex", renamed.getName());
        assertEquals("Alex", owners.findById("p1").join().orElseThrow().getName());
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"SQLITE", "H2"})
    void saveReturnsTheManagedCopyWithTheGeneratedId(DatabaseType type) {
        open(type);
        Owner owner = new OwnerRepository(sessions, db).save(new Owner("p1", "Steve")).join();
        Home given = new Home(owner, "base", 1);

        Home saved = new HomeRepository(sessions, db).save(given).join();

        assertNotNull(saved.id, "the generated id is on the returned copy");
        assertEquals(null, given.id, "not on the object passed in: merge does not manage it");
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"SQLITE", "H2"})
    void findingCountingAndCheckingExistence(DatabaseType type) {
        open(type);
        OwnerRepository owners = new OwnerRepository(sessions, db);
        owners.saveAll(List.of(new Owner("a", "Ann"), new Owner("b", "Bob"), new Owner("c", "Cid"))).join();

        assertEquals(Optional.empty(), owners.findById("nobody").join());
        assertEquals(3, owners.findAll().join().size());
        assertEquals(3L, owners.count().join());
        assertTrue(owners.existsById("b").join());
        assertFalse(owners.existsById("nobody").join());
        List<String> found = owners.findAllById(List.of("a", "missing", "c")).join().stream()
                .map(owner -> owner.uuid).sorted().toList();
        assertEquals(List.of("a", "c"), found, "missing ids are left out");
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"SQLITE", "H2"})
    void deletingDetachedEntitiesAndById(DatabaseType type) {
        open(type);
        OwnerRepository owners = new OwnerRepository(sessions, db);
        owners.saveAll(List.of(new Owner("a", "Ann"), new Owner("b", "Bob"), new Owner("c", "Cid"))).join();

        Owner detached = owners.findById("a").join().orElseThrow();
        owners.delete(detached).join();
        assertFalse(owners.existsById("a").join(), "a detached entity can be deleted");

        assertTrue(owners.deleteById("b").join());
        assertFalse(owners.deleteById("b").join(), "nothing left to delete");

        assertEquals(1, owners.deleteAll().join());
        assertEquals(0L, owners.count().join());
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"SQLITE", "H2"})
    void saveAllIsAllOrNothing(DatabaseType type) {
        open(type);
        CrateRepository crates = new CrateRepository(sessions, db);

        failure(crates.saveAll(List.of(new Crate("c1", "vote"), new Crate("c2", null), new Crate("c3", "rare"))));

        assertEquals(0L, crates.count().join(), "the valid ones were rolled back with the invalid one");
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"SQLITE", "H2"})
    void aConstraintViolationFailsWithADatabaseException(DatabaseType type) {
        open(type);
        DatabaseException failed = failure(new CrateRepository(sessions, db).save(new Crate("c1", null)));

        assertNotNull(failed.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = DatabaseType.class, names = {"SQLITE", "H2"})
    void subclassesQueryThroughReadOffTheCallingThread(DatabaseType type) {
        open(type);
        OwnerRepository owners = new OwnerRepository(sessions, db);
        owners.save(new Owner("p1", "Steve")).join();

        assertEquals("p1", owners.findByName("Steve").join().orElseThrow().uuid);
        assertEquals(Optional.empty(), owners.findByName("Nobody").join());
        assertTrue(owners.threadName().join().startsWith("CoffeeLib-DB-"), "ran on the database's threads");
    }

    @Test
    void aClassMissingFromTheFactoryIsRefusedAtConstruction() {
        db = new DefaultDatabaseManager("repomod", () -> null, getClass().getClassLoader(), () -> directory)
                .open(DatabaseSettings.builder(DatabaseType.H2).file("narrow").build());
        SessionFactory ownersOnly = CoffeeHibernate.sessionFactory(db, Owner.class);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new CrateRepository(ownersOnly, db));
        assertTrue(refused.getMessage().contains("is not an entity of this SessionFactory"), refused.getMessage());
    }

    @Test
    void aClosedDatabaseFailsTheFutureInsteadOfQueueing() {
        open(DatabaseType.H2);
        OwnerRepository owners = new OwnerRepository(sessions, db);
        db.close();

        assertEquals("Database is closed", failure(owners.count()).getMessage());
    }
}
