package com.github.sqlinjection.autoconfigure;

import com.alibaba.druid.wall.WallCheckResult;
import com.github.sqlinjection.autoconfigure.properties.SqlInjectionProperties;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Signature;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SqlInjectionPluginInterceptorTest {

    @Test
    public void safeSqlProceeds() throws Exception {
        CountingInterceptor interceptor = interceptor(properties(false), new PermitAndDenyCustomizer());
        PreparedCall call = prepare(interceptor, SqlCheckPathBenchmark.SAFE_SQL, "com.example.Mapper.safe");
        call.wrapped.prepare(null, 1);
        call.wrapped.prepare(null, 1);
        assertEquals(2, call.handler.proceedCount);
        assertEquals(1, interceptor.wallChecks.get());
    }

    @Test
    public void violatingSqlIsRejectedOnCacheHit() throws Exception {
        CountingInterceptor interceptor = interceptor(properties(false), new PermitAndDenyCustomizer());
        PreparedCall call = prepare(interceptor, SqlCheckPathBenchmark.VIOLATING_SQL, "com.example.Mapper.bad");
        SQLException first = expectSqlException(call);
        SQLException second = expectSqlException(call);
        assertEquals(0, call.handler.proceedCount);
        assertEquals(1, interceptor.wallChecks.get());
        assertTrue(first.getMessage().contains("sql injection violation"));
        assertTrue(first.getMessage().contains(SqlCheckPathBenchmark.VIOLATING_SQL));
        assertEquals(first.getMessage(), second.getMessage());
        assertNull(first.getCause());
    }

    @Test
    public void syntaxErrorKeepsTheParserCause() throws Exception {
        CountingInterceptor interceptor = interceptor(properties(false), new PermitAndDenyCustomizer());
        PreparedCall call = prepare(interceptor, "select", "com.example.Mapper.syntax");
        SQLException first = expectSqlException(call);
        SQLException second = expectSqlException(call);
        assertNotNull(first.getCause());
        assertEquals(first.getCause().getClass(), second.getCause().getClass());
        assertEquals(1, interceptor.wallChecks.get());
    }

    @Test
    public void multiStatementIsRejected() throws Exception {
        CountingInterceptor interceptor = interceptor(properties(false), new PermitAndDenyCustomizer());
        PreparedCall call = prepare(interceptor, "select id from users; drop table users", "com.example.Mapper.multi");
        SQLException exception = expectSqlException(call);
        assertTrue(exception.getMessage().contains("multi-statement"));
        expectSqlException(call);
        assertEquals(1, interceptor.wallChecks.get());
    }

    @Test
    public void warningModeStillProceedsAndBlockingModeStillRejectsTheCachedViolation() throws Exception {
        SqlInjectionProperties properties = properties(true);
        CountingInterceptor interceptor = interceptor(properties, new PermitAndDenyCustomizer());
        PreparedCall call = prepare(interceptor, SqlCheckPathBenchmark.VIOLATING_SQL, "com.example.Mapper.warn");
        call.wrapped.prepare(null, 1);
        assertEquals(1, call.handler.proceedCount);
        properties.setEnableWarningOnly(false);
        expectSqlException(call);
        assertEquals(1, call.handler.proceedCount);
        assertEquals(1, interceptor.wallChecks.get());
    }

    @Test
    public void blockingModeThenWarningModeUsesTheSameCachedViolation() throws Exception {
        SqlInjectionProperties properties = properties(false);
        CountingInterceptor interceptor = interceptor(properties, new PermitAndDenyCustomizer());
        PreparedCall call = prepare(interceptor, SqlCheckPathBenchmark.VIOLATING_SQL, "com.example.Mapper.blockThenWarn");
        expectSqlException(call);
        properties.setEnableWarningOnly(true);
        call.wrapped.prepare(null, 1);
        assertEquals(1, call.handler.proceedCount);
        assertEquals(1, interceptor.wallChecks.get());
    }

    @Test
    public void ignoredStatementSkipsTheCheck() throws Exception {
        SqlInjectionProperties properties = properties(false);
        properties.setIgnoredStatements(Collections.singleton("com.example.Mapper.ignored"));
        CountingInterceptor interceptor = interceptor(properties, new PermitAndDenyCustomizer());
        PreparedCall call = prepare(interceptor, SqlCheckPathBenchmark.VIOLATING_SQL, "com.example.Mapper.ignored");
        call.wrapped.prepare(null, 1);
        assertEquals(1, call.handler.proceedCount);
        assertEquals(0, interceptor.wallChecks.get());
    }

    @Test
    public void disabledCallableCheckSkipsStoredProcedures() throws Exception {
        SqlInjectionProperties properties = properties(false);
        properties.setSupportCallable(false);
        CountingInterceptor interceptor = interceptor(properties, new PermitAndDenyCustomizer());
        PreparedCall call = prepare(interceptor, SqlCheckPathBenchmark.VIOLATING_SQL, "com.example.Mapper.call", StatementType.CALLABLE);
        call.wrapped.prepare(null, 1);
        assertEquals(1, call.handler.proceedCount);
        assertEquals(0, interceptor.wallChecks.get());
    }

    @Test
    public void privilegedWallConfigDoesNotReuseAnotherThreadsDecision() throws Exception {
        PermitAndDenyCustomizer customizer = new PermitAndDenyCustomizer();
        customizer.setDoPrivilegedAllow(true);
        CountingInterceptor interceptor = interceptor(properties(false), customizer);
        PreparedCall call = prepare(interceptor, SqlCheckPathBenchmark.VIOLATING_SQL, "com.example.Mapper.privileged");
        expectSqlException(call);
        expectSqlException(call);
        assertEquals(2, interceptor.wallChecks.get());
    }

    @Test
    public void nestedPluginProxiesStillSeeTheRoutingHandler() throws Exception {
        CountingInterceptor interceptor = interceptor(properties(false), new PermitAndDenyCustomizer());
        SqlCheckPathBenchmark.NonPreparingRoutingHandler handler =
                SqlCheckPathBenchmark.NonPreparingRoutingHandler.create(SqlCheckPathBenchmark.SAFE_SQL, "com.example.Mapper.nested");
        StatementHandler inner = (StatementHandler) interceptor.plugin(handler);
        StatementHandler outer = (StatementHandler) interceptor.plugin(inner);
        outer.prepare(null, 1);
        outer.prepare(null, 1);
        assertEquals(2, handler.proceedCount);
        assertEquals(1, interceptor.wallChecks.get());
    }

    @Test
    public void concurrentViolationsStayRejected() throws Exception {
        CountingInterceptor interceptor = interceptor(properties(false), new PermitAndDenyCustomizer());
        PreparedCall call = prepare(interceptor, SqlCheckPathBenchmark.VIOLATING_SQL, "com.example.Mapper.concurrent");
        int threads = 8;
        int perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int n = 0; n < perThread; n++) {
                        try {
                            call.wrapped.prepare(null, 1);
                            allowed.incrementAndGet();
                        } catch (SQLException expected) {
                            if (expected.getMessage() == null || !expected.getMessage().contains("sql injection violation")) {
                                allowed.incrementAndGet();
                            }
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    allowed.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();
        assertEquals(0, allowed.get());
        assertEquals(0, call.handler.proceedCount);
        assertTrue(interceptor.wallChecks.get() >= 1);
        assertTrue(interceptor.wallChecks.get() < threads * perThread);
    }

    private static SQLException expectSqlException(PreparedCall call) {
        try {
            call.wrapped.prepare(null, 1);
            fail("expected SQLException");
            return null;
        } catch (SQLException ex) {
            return ex;
        }
    }

    private static CountingInterceptor interceptor(SqlInjectionProperties properties, PermitAndDenyCustomizer customizer) {
        return new CountingInterceptor(properties, customizer);
    }

    private static SqlInjectionProperties properties(boolean warningOnly) {
        SqlInjectionProperties properties = new SqlInjectionProperties();
        properties.setEnableWarningOnly(warningOnly);
        return properties;
    }

    private static PreparedCall prepare(CountingInterceptor interceptor, String sql, String statementId) {
        return prepare(interceptor, sql, statementId, StatementType.PREPARED);
    }

    private static PreparedCall prepare(CountingInterceptor interceptor, String sql, String statementId, StatementType statementType) {
        SqlCheckPathBenchmark.NonPreparingRoutingHandler handler =
                SqlCheckPathBenchmark.NonPreparingRoutingHandler.create(sql, statementId, statementType);
        StatementHandler wrapped = (StatementHandler) interceptor.plugin(handler);
        return new PreparedCall(handler, wrapped);
    }

    private static final class PreparedCall {
        final SqlCheckPathBenchmark.NonPreparingRoutingHandler handler;
        final StatementHandler wrapped;

        PreparedCall(SqlCheckPathBenchmark.NonPreparingRoutingHandler handler, StatementHandler wrapped) {
            this.handler = handler;
            this.wrapped = wrapped;
        }
    }

    @Intercepts({
            @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
    })
    static final class CountingInterceptor extends SqlInjectionPluginInterceptor {
        final AtomicInteger wallChecks = new AtomicInteger();

        CountingInterceptor(SqlInjectionProperties properties, PermitAndDenyCustomizer customizer) {
            super(properties, customizer);
        }

        @Override
        protected WallCheckResult invokeWallCheck(String sql) {
            wallChecks.incrementAndGet();
            return super.invokeWallCheck(sql);
        }
    }
}
