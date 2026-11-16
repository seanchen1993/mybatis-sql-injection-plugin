package com.github.sqlinjection.autoconfigure;

import com.alibaba.druid.wall.Violation;
import com.alibaba.druid.wall.WallCheckResult;
import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.wall.violation.SyntaxErrorViolation;
import com.github.sqlinjection.autoconfigure.properties.SqlInjectionProperties;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Exact-SQL cache of Druid Wall decisions.
 *
 * <p>The key is the SQL text passed to {@code WallProvider.check}. A stored decision is only valid
 * for the {@link WallConfig} that produced it, so the interceptor keeps one cache per provider and
 * does not use it when the config can vary by thread (privileged checks, tenant callbacks,
 * tenant table patterns, or update handlers). A tenant callback or tenant table pattern bypasses
 * this cache entirely. Entries are capped by count and by SQL length. Puts take a lock; hits do not.
 */
final class SqlCheckDecisionCache {

    static final int DEFAULT_MAX_ENTRIES = SqlInjectionProperties.DEFAULT_CACHE_MAX_ENTRIES;
    static final int DEFAULT_MAX_SQL_LENGTH = SqlInjectionProperties.DEFAULT_CACHE_MAX_SQL_LENGTH;

    private final int maxEntries;
    private final int maxSqlLength;
    private final ConcurrentMap<String, Decision> decisions = new ConcurrentHashMap<String, Decision>();
    private final Object putLock = new Object();

    SqlCheckDecisionCache() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_SQL_LENGTH);
    }

    SqlCheckDecisionCache(int maxEntries, int maxSqlLength) {
        this.maxEntries = maxEntries;
        this.maxSqlLength = maxSqlLength;
    }

    Decision getOrLoad(String sql, Function<String, Decision> loader) {
        if (sql == null || sql.length() > maxSqlLength) {
            return loader.apply(sql);
        }
        Decision cached = decisions.get(sql);
        if (cached != null) {
            return cached;
        }
        Decision loaded = loader.apply(sql);
        if (loaded == null) {
            return null;
        }
        synchronized (putLock) {
            Decision existing = decisions.get(sql);
            if (existing != null) {
                return existing;
            }
            if (decisions.size() >= maxEntries) {
                return loaded;
            }
            decisions.put(sql, loaded);
            return loaded;
        }
    }

    static boolean isRepeatable(WallConfig config) {
        if (config == null) {
            return false;
        }
        if (config.isDoPrivilegedAllow() || config.getUpdateCheckHandler() != null || config.getTenantCallBack() != null) {
            return false;
        }
        String tenantTablePattern = config.getTenantTablePattern();
        return tenantTablePattern == null || tenantTablePattern.isEmpty();
    }

    static final class Decision {
        static final Decision ALLOW = new Decision(false, false, null, null);

        final boolean violation;
        final boolean syntaxError;
        final String message;
        final Throwable cause;

        private Decision(boolean violation, boolean syntaxError, String message, Throwable cause) {
            this.violation = violation;
            this.syntaxError = syntaxError;
            this.message = message;
            this.cause = cause;
        }

        static Decision allow() {
            return ALLOW;
        }

        static Decision violation(String message, boolean syntaxError, Throwable cause) {
            return new Decision(true, syntaxError, message, cause);
        }

        static Decision from(WallCheckResult result) {
            List<Violation> violations = result.getViolations();
            if (violations == null || violations.isEmpty()) {
                return ALLOW;
            }
            Violation first = violations.get(0);
            if (first instanceof SyntaxErrorViolation) {
                return new Decision(true, true, first.getMessage(), ((SyntaxErrorViolation) first).getException());
            }
            return new Decision(true, false, first.getMessage(), null);
        }
    }
}
