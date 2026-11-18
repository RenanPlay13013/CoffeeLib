package net.loyalnetwork.coffeelib.database.hibernate;

import jakarta.persistence.metamodel.EntityType;
import net.loyalnetwork.coffeelib.api.database.Database;
import net.loyalnetwork.coffeelib.api.database.DatabaseException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;

/**
 * The usual CRUD for one entity type, on a Hibernate {@link SessionFactory}
 * built by {@link CoffeeHibernate}. A mod extends it and adds its own queries
 * through {@link #read} and {@link #write}:
 * <pre>{@code
 * public class PlayerRepository extends HibernateRepository<PlayerEntity, String> {
 *     public PlayerRepository(SessionFactory sessions, Database database) {
 *         super(sessions, database, PlayerEntity.class);
 *     }
 *
 *     public CompletableFuture<Optional<PlayerEntity>> findByName(String name) {
 *         return read(session -> session
 *                 .createSelectionQuery("from PlayerEntity where name = :name", PlayerEntity.class)
 *                 .setParameter("name", name)
 *                 .uniqueResultOptional());
 *     }
 * }
 * }</pre>
 * Things to know:
 * <ul>
 *   <li>Every method runs on {@link Database#executor()} and returns a future,
 *       which <b>completes on a database thread</b>, not the server's: get back
 *       to the server thread with the platform's scheduler before touching the
 *       world ({@code server.execute(...)} on Forge/NeoForge, Bukkit's
 *       scheduler on Paper).</li>
 *   <li><b>Returned entities are detached</b>: their session is closed by the
 *       time the future completes, so reading a {@code LAZY} association then
 *       throws {@code LazyInitializationException}. Fetch what you need inside
 *       {@link #read}, with {@code join fetch}.</li>
 *   <li><b>{@link #save} merges</b>: the object passed in does not become
 *       managed - use the one returned, which also carries a generated id.</li>
 *   <li><b>One call is one transaction.</b> {@code findById} then {@code save}
 *       are two; put both in one {@link #write} to make them atomic.</li>
 *   <li>There are no queries derived from method names ({@code findByName}
 *       written for you): write them with {@link #read}.</li>
 * </ul>
 * Failures complete the future with a {@link DatabaseException} carrying
 * Hibernate's message, as the {@link Database} helpers do.
 *
 * @param <T>  the entity type
 * @param <ID> its id type
 */
public abstract class HibernateRepository<T, ID> {

    private final SessionFactory sessions;
    private final Database database;
    private final Class<T> type;
    private final String entityName;

    /**
     * @throws IllegalArgumentException when {@code type} is not an entity of
     *                                  {@code sessions} - left out of
     *                                  {@code CoffeeHibernate.sessionFactory(...)}
     */
    protected HibernateRepository(SessionFactory sessions, Database database, Class<T> type) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.database = Objects.requireNonNull(database, "database");
        this.type = Objects.requireNonNull(type, "type");
        EntityType<T> entity;
        try {
            entity = sessions.getMetamodel().entity(type);
        } catch (IllegalArgumentException e) {
            entity = null;
        }
        if (entity == null) {
            throw new IllegalArgumentException(type.getName() + " is not an entity of this SessionFactory - "
                    + "pass it to CoffeeHibernate.sessionFactory(...)");
        }
        // The entity name, not the class name: HQL uses it, and @Entity(name = ...) changes it.
        this.entityName = entity.getName();
    }

    /** Inserts {@code entity}, or updates the row with its id; the future holds the managed copy (with any generated id). */
    public CompletableFuture<T> save(T entity) {
        Objects.requireNonNull(entity, "entity");
        return write(session -> session.merge(entity));
    }

    /** {@link #save} for every entity, in one transaction: all of them are saved, or none. */
    public CompletableFuture<List<T>> saveAll(Collection<? extends T> entities) {
        List<T> copy = List.copyOf(entities);
        return write(session -> {
            List<T> saved = new ArrayList<>(copy.size());
            for (T entity : copy) {
                saved.add(session.merge(entity));
            }
            return saved;
        });
    }

    public CompletableFuture<Optional<T>> findById(ID id) {
        Objects.requireNonNull(id, "id");
        return read(session -> Optional.ofNullable(session.find(type, id)));
    }

    /** The entities with these ids, in one query; ids with no row are left out. */
    public CompletableFuture<List<T>> findAllById(Collection<? extends ID> ids) {
        List<ID> copy = List.copyOf(ids);
        return read(session -> {
            List<T> found = new ArrayList<>();
            for (T entity : session.byMultipleIds(type).multiLoad(copy)) {
                if (entity != null) {
                    found.add(entity);
                }
            }
            return found;
        });
    }

    public CompletableFuture<List<T>> findAll() {
        return read(session -> session.createSelectionQuery("from " + entityName, type).getResultList());
    }

    public CompletableFuture<Boolean> existsById(ID id) {
        Objects.requireNonNull(id, "id");
        return read(session -> session
                .createSelectionQuery("select count(*) from " + entityName + " e where id(e) = :id", Long.class)
                .setParameter("id", id)
                .getSingleResult() > 0);
    }

    public CompletableFuture<Long> count() {
        return read(session -> session.createSelectionQuery("select count(*) from " + entityName, Long.class)
                .getSingleResult());
    }

    /** Deletes the entity with {@code id}; the future says whether there was one. */
    public CompletableFuture<Boolean> deleteById(ID id) {
        Objects.requireNonNull(id, "id");
        return write(session -> {
            T entity = session.find(type, id);
            if (entity == null) {
                return false;
            }
            session.remove(entity);
            return true;
        });
    }

    /** Deletes {@code entity} - detached ones too, such as those this repository returns. */
    public CompletableFuture<Void> delete(T entity) {
        Objects.requireNonNull(entity, "entity");
        return write(session -> {
            session.remove(session.contains(entity) ? entity : session.merge(entity));
            return null;
        });
    }

    /**
     * Deletes every row of this entity in one bulk statement; the future holds
     * how many. Being bulk, it skips JPA callbacks and cascades - rows
     * referencing these are not removed for you.
     */
    public CompletableFuture<Integer> deleteAll() {
        return write(session -> session.createMutationQuery("delete from " + entityName).executeUpdate());
    }

    /** Runs {@code work} on a session without a transaction, on the database's threads. */
    protected <R> CompletableFuture<R> read(Function<Session, R> work) {
        return run(() -> sessions.fromSession(work::apply));
    }

    /** Runs {@code work} in a transaction - committed when it returns, rolled back when it throws. */
    protected <R> CompletableFuture<R> write(Function<Session, R> work) {
        return run(() -> sessions.fromTransaction(work::apply));
    }

    protected Class<T> type() {
        return type;
    }

    /** The entity's name in HQL ({@code @Entity(name = ...)}, or the simple class name). */
    protected String entityName() {
        return entityName;
    }

    protected SessionFactory sessions() {
        return sessions;
    }

    private <R> CompletableFuture<R> run(java.util.function.Supplier<R> work) {
        if (database.isClosed()) {
            return CompletableFuture.failedFuture(new DatabaseException("Database is closed"));
        }
        CompletableFuture<R> future = new CompletableFuture<>();
        try {
            database.executor().execute(() -> {
                try {
                    future.complete(work.get());
                } catch (DatabaseException e) {
                    future.completeExceptionally(e);
                } catch (RuntimeException e) {
                    future.completeExceptionally(new DatabaseException(describe(e), e));
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(new DatabaseException("Database is closed", e));
        }
        return future;
    }

    /** Hibernate's message, and the database's own reason when there is one beneath it. */
    private static String describe(RuntimeException e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root != e && root.getMessage() != null && !message.contains(root.getMessage())) {
            message += ": " + root.getMessage();
        }
        return message;
    }
}
