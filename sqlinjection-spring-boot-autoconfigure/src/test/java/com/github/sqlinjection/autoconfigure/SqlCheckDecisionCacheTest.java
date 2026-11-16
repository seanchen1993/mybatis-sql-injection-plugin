package com.github.sqlinjection.autoconfigure;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SqlCheckDecisionCacheTest {

    @Test
    public void repeatedSqlLoadsOnce() {
        SqlCheckDecisionCache cache = new SqlCheckDecisionCache(8, 100);
        AtomicInteger loads = new AtomicInteger();
        SqlCheckDecisionCache.Decision first = cache.getOrLoad("select 1", sql -> {
            loads.incrementAndGet();
            return SqlCheckDecisionCache.Decision.allow();
        });
        SqlCheckDecisionCache.Decision second = cache.getOrLoad("select 1", sql -> {
            loads.incrementAndGet();
            return SqlCheckDecisionCache.Decision.allow();
        });
        assertEquals(1, loads.get());
        assertSame(first, second);
        assertFalse(first.violation);
    }

    @Test
    public void distinctSqlLoadsAgain() {
        SqlCheckDecisionCache cache = new SqlCheckDecisionCache(8, 100);
        AtomicInteger loads = new AtomicInteger();
        cache.getOrLoad("select 1", sql -> {
            loads.incrementAndGet();
            return SqlCheckDecisionCache.Decision.allow();
        });
        cache.getOrLoad("select 2", sql -> {
            loads.incrementAndGet();
            return SqlCheckDecisionCache.Decision.violation("bad", false, null);
        });
        assertEquals(2, loads.get());
    }

    @Test
    public void overCapacityStillReturnsTheFreshDecision() {
        SqlCheckDecisionCache cache = new SqlCheckDecisionCache(1, 100);
        AtomicInteger loads = new AtomicInteger();
        cache.getOrLoad("cached", sql -> {
            loads.incrementAndGet();
            return SqlCheckDecisionCache.Decision.allow();
        });
        SqlCheckDecisionCache.Decision overflow = cache.getOrLoad("overflow", sql -> {
            loads.incrementAndGet();
            return SqlCheckDecisionCache.Decision.violation("blocked", false, null);
        });
        cache.getOrLoad("overflow", sql -> {
            loads.incrementAndGet();
            return SqlCheckDecisionCache.Decision.violation("blocked", false, null);
        });
        cache.getOrLoad("cached", sql -> {
            loads.incrementAndGet();
            return SqlCheckDecisionCache.Decision.allow();
        });
        assertTrue(overflow.violation);
        assertEquals("blocked", overflow.message);
        // cached hit + two loads of overflow. The first SQL is not loaded again.
        assertEquals(3, loads.get());
    }

    @Test
    public void sqlLongerThanTheLimitIsNotCached() {
        SqlCheckDecisionCache cache = new SqlCheckDecisionCache(8, 4);
        AtomicInteger loads = new AtomicInteger();
        cache.getOrLoad("select", sql -> {
            loads.incrementAndGet();
            return SqlCheckDecisionCache.Decision.violation("syntax", true, new IllegalStateException("eof"));
        });
        SqlCheckDecisionCache.Decision again = cache.getOrLoad("select", sql -> {
            loads.incrementAndGet();
            return SqlCheckDecisionCache.Decision.violation("syntax", true, new IllegalStateException("eof"));
        });
        assertEquals(2, loads.get());
        assertTrue(again.syntaxError);
        assertTrue(again.cause instanceof IllegalStateException);
    }

    @Test
    public void failedLoadIsNotCached() {
        SqlCheckDecisionCache cache = new SqlCheckDecisionCache(8, 100);
        AtomicInteger loads = new AtomicInteger();
        try {
            cache.getOrLoad("select 1", sql -> {
                loads.incrementAndGet();
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            assertEquals("boom", expected.getMessage());
        }
        cache.getOrLoad("select 1", sql -> {
            loads.incrementAndGet();
            return SqlCheckDecisionCache.Decision.allow();
        });
        assertEquals(2, loads.get());
    }

    @Test
    public void concurrentCallersSeeTheSameViolation() throws Exception {
        SqlCheckDecisionCache cache = new SqlCheckDecisionCache(8, 100);
        AtomicInteger loads = new AtomicInteger();
        int threads = 8;
        int perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger mismatches = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int n = 0; n < perThread; n++) {
                        SqlCheckDecisionCache.Decision decision = cache.getOrLoad("select id from users where 1 = 1 or 1 = 1", sql -> {
                            loads.incrementAndGet();
                            return SqlCheckDecisionCache.Decision.violation("part alway true condition not allow", false, null);
                        });
                        if (!decision.violation || !"part alway true condition not allow".equals(decision.message)) {
                            mismatches.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    mismatches.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();
        assertEquals(0, mismatches.get());
        assertTrue(loads.get() >= 1);
        assertTrue(loads.get() < threads * perThread);
    }
}
