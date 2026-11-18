package net.loyalnetwork.coffeelib.database.hibernate;

import net.loyalnetwork.coffeelib.api.database.Database;
import net.loyalnetwork.coffeelib.api.database.DatabaseException;
import net.loyalnetwork.coffeelib.api.database.DatabaseType;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.community.dialect.SQLiteDialect;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A Hibernate {@link SessionFactory} on a CoffeeLib {@link Database}: its pool
 * as Hibernate's connections, closed along with the database.
 * <pre>{@code
 * Database db = CoffeeLib.database(container).open();
 * SessionFactory sessions = CoffeeHibernate.sessionFactory(db, Home.class);
 *
 * db.executor().execute(() -> sessions.inTransaction(session -> session.persist(home)));
 * }</pre>
 * Hibernate itself ships with CoffeeLib - nested in the Forge and NeoForge
 * jars, a library of the CoffeeLib plugin on Paper - so a mod only compiles
 * against it ({@code compileOnly "org.hibernate.orm:hibernate-core"}).
 * <p>
 * Every {@link DatabaseType} works: SQLite through Hibernate's community
 * {@code SQLiteDialect}, chosen here; the others through the dialect Hibernate
 * detects from the connection.
 * <p>
 * Use sessions off the server thread: {@link Database#executor()} is there
 * for it.
 */
public final class CoffeeHibernate {

    private CoffeeHibernate() {
    }

    /**
     * A session factory for {@code entities}, with tables created or updated to
     * match them ({@code hibernate.hbm2ddl.auto=update}) - the usual choice for
     * a mod's own tables. Use the other overload to change that, or anything else.
     */
    public static SessionFactory sessionFactory(Database database, Class<?>... entities) {
        return sessionFactory(database, configuration ->
                configuration.setProperty(AvailableSettings.HBM2DDL_AUTO, "update"), entities);
    }

    /**
     * A session factory for {@code entities}, after {@code customize} has had
     * its say on the configuration - schema handling, caching, naming, extra
     * mappings.
     *
     * @throws DatabaseException when Hibernate cannot be set up - mappings that
     *                           do not match the tables, say
     */
    public static SessionFactory sessionFactory(Database database, Consumer<Configuration> customize,
                                                Class<?>... entities) {
        Objects.requireNonNull(database, "database");
        if (entities.length == 0) {
            throw new IllegalArgumentException("No entity classes given");
        }

        Configuration configuration = new Configuration();
        for (Class<?> entity : entities) {
            configuration.addAnnotatedClass(entity);
        }
        // CoffeeLib's pool is the connection provider; Hibernate opens none of its own.
        configuration.getProperties().put(AvailableSettings.DATASOURCE, database.dataSource());
        // The entities' loaders, so Hibernate finds them (and what they reference by name)
        // whatever layer or plugin loader they live in.
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        for (Class<?> entity : entities) {
            loaders.add(entity.getClassLoader());
        }
        configuration.getProperties().put(AvailableSettings.CLASSLOADERS, List.copyOf(loaders));
        if (database.type() == DatabaseType.SQLITE) {
            // Hibernate detects every other type from the connection, but SQLite's dialect
            // lives in hibernate-community-dialects (shipped with CoffeeLib), which detection
            // does not look at. A class literal, so it is checked at compile time.
            configuration.setProperty(AvailableSettings.DIALECT, SQLiteDialect.class.getName());
        }
        customize.accept(configuration);

        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(entities[0].getClassLoader());
        SessionFactory sessions;
        try {
            sessions = configuration.buildSessionFactory();
        } catch (RuntimeException e) {
            throw new DatabaseException("Could not set up Hibernate on " + database.type() + ": " + e.getMessage(), e);
        } finally {
            thread.setContextClassLoader(previous);
        }
        database.closeWith(sessions);
        return sessions;
    }
}
