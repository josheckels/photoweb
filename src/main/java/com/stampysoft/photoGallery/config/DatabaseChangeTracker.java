package com.stampysoft.photoGallery.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Whether anything has been written to the database since it was last published to production, which is the whole
 * question behind the prompt on quit - see PUBLISHING.md.
 * <p>
 * It counts <em>statements</em> rather than hooking the places that make changes. Writes arrive from too many
 * directions to enumerate honestly: the admin UI, the face tools, a scan, a comment posted on the public site, and
 * {@link com.stampysoft.photoGallery.Photo#ensureAllResized} recording resolutions on the read path. Missing any
 * one of them would mean quitting without a prompt and silently leaving production stale, which is exactly the
 * failure this is meant to prevent. So it sits under all of them instead, as a Hibernate
 * {@link StatementInspector}, and sees every insert, update and delete Hibernate issues - JPQL, criteria and
 * native SQL alike. Anything reaching Postgres by another route (psql, a migration run by hand) is invisible to it,
 * which is the one gap and an acceptable one, since those aren't things done from inside the running app.
 * <p>
 * The counter is static because it has to be readable from the Swing UI, which has no bean to inject it into.
 * Statics also mean the count starts at zero when the JVM does, which is what "since startup" wants.
 * <p>
 * The inspector is installed by {@link com.stampysoft.photoGallery.HibernateConfig}, and this class checks at
 * startup that it really was. It cannot install itself as a {@code HibernatePropertiesCustomizer}: that mechanism
 * belongs to Spring Boot's JPA auto-configuration, which backs off entirely because the application declares its
 * own {@code LocalContainerEntityManagerFactoryBean}. Registering it that way looks completely correct, is silently
 * ignored, and leaves the app quitting without ever offering to publish - which is why the check below is an error
 * rather than a comment.
 */
@Component
public class DatabaseChangeTracker
{
    private static final Logger log = LoggerFactory.getLogger(DatabaseChangeTracker.class);

    /** Mutating statements seen since startup. Monotonic. */
    private static final AtomicLong WRITES = new AtomicLong();

    /** The value {@link #WRITES} had when production last received a copy of the database. */
    private static final AtomicLong PUBLISHED = new AtomicLong();

    private static volatile Instant LAST_WRITE;

    private final EntityManagerFactory _entityManagerFactory;

    public DatabaseChangeTracker(EntityManagerFactory entityManagerFactory)
    {
        _entityManagerFactory = entityManagerFactory;
    }

    /** What HibernateConfig registers. */
    public static StatementInspector newInspector()
    {
        return new Inspector();
    }

    /**
     * Complains loudly if the inspector didn't get installed, because everything downstream of it fails quietly:
     * no counting, no "changes not yet published" in the status bar, and no prompt on the way out.
     */
    @PostConstruct
    public void verifyInstalled()
    {
        try
        {
            StatementInspector inspector = _entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                    .getSessionFactoryOptions()
                    .getStatementInspector();
            if (inspector instanceof Inspector)
            {
                log.debug("Database change tracking is active");
            }
            else
            {
                log.error("Database change tracking is NOT active - the statement inspector is {}. Nothing will be "
                        + "counted as a change, so quitting will not offer to publish. See HibernateConfig.",
                        inspector == null ? "not set" : inspector.getClass().getName());
            }
        }
        catch (Exception e)
        {
            log.warn("Could not confirm that database change tracking is active: {}", e.toString());
        }
    }

    public static long currentWriteCount()
    {
        return WRITES.get();
    }

    /**
     * @param writeCount the write count as it stood when the dump was taken, not when the publish finished. Writes
     * that landed while the dump was running aren't in it, so counting them as published would lose them.
     */
    public static void markPublished(long writeCount)
    {
        PUBLISHED.updateAndGet(published -> Math.max(published, writeCount));
    }

    public static boolean hasUnpublishedChanges()
    {
        return WRITES.get() > PUBLISHED.get();
    }

    public static long getUnpublishedWriteCount()
    {
        return Math.max(0, WRITES.get() - PUBLISHED.get());
    }

    /** When the database was last written to, or null if it hasn't been since startup. */
    public static Instant getLastWriteTime()
    {
        return LAST_WRITE;
    }

    private static void recordWrite()
    {
        WRITES.incrementAndGet();
        LAST_WRITE = Instant.now();
    }

    /**
     * Bumps the counter for anything that changes rows. Deliberately not {@code create}/{@code select}: the only
     * statements SequenceSynchronizer runs at startup are {@code CREATE SEQUENCE IF NOT EXISTS} and
     * {@code SELECT setval(...)}, and a freshly started app that counted those would offer to publish before the
     * user had touched anything.
     */
    static final class Inspector implements StatementInspector
    {
        private static final String[] MUTATING = {"insert", "update", "delete", "merge"};

        @Override
        public String inspect(String sql)
        {
            if (sql != null && isMutating(sql))
            {
                recordWrite();
            }
            return sql;
        }

        private static boolean isMutating(String sql)
        {
            int i = skipLeadingNoise(sql);
            for (String keyword : MUTATING)
            {
                if (sql.regionMatches(true, i, keyword, 0, keyword.length()))
                {
                    return true;
                }
            }
            return false;
        }

        /** Steps over whitespace and any leading block comments, which is what hibernate.use_sql_comments emits. */
        private static int skipLeadingNoise(String sql)
        {
            int i = 0;
            while (i < sql.length())
            {
                char c = sql.charAt(i);
                if (Character.isWhitespace(c))
                {
                    i++;
                }
                else if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*')
                {
                    int end = sql.indexOf("*/", i + 2);
                    if (end < 0)
                    {
                        return sql.length();
                    }
                    i = end + 2;
                }
                else
                {
                    break;
                }
            }
            return i;
        }
    }
}
