package com.github.sqlinjection.autoconfigure;

import com.github.sqlinjection.autoconfigure.properties.SqlInjectionProperties;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.SimpleExecutor;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Timing harness for the production prepare path: a {@link RoutingStatementHandler}
 * wrapped by {@link SqlInjectionPluginInterceptor}.
 *
 * <p>Run: {@code mvn -pl sqlinjection-spring-boot-autoconfigure -q test-compile} then
 * {@code java -cp ... com.github.sqlinjection.autoconfigure.SqlCheckPathBenchmark}.
 */
public class SqlCheckPathBenchmark {

    private static final int WARMUP = 3000;
    private static final int MEASURED = 30000;
    static final String SAFE_SQL = "select id, name from users where id = 1";
    static final String VIOLATING_SQL = "select id, name from users where id = 1 or 1 = 1";

    public static void main(String[] args) throws Exception {
        report("safe-sql", SAFE_SQL, false);
        report("violating-sql", VIOLATING_SQL, true);
    }

    private static void report(String label, String sql, boolean expectViolation) throws Exception {
        Harness harness = Harness.create(sql, false);
        time(harness, WARMUP, expectViolation);
        double nanos = time(harness, MEASURED, expectViolation);
        System.out.printf("RESULT %s iterations=%d nsPerCall=%.1f usPerCall=%.3f proceedCount=%d%n",
                label, MEASURED, nanos, nanos / 1000.0, harness.handler.proceedCount);
    }

    private static double time(Harness harness, int iterations, boolean expectViolation) throws Exception {
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try {
                harness.wrapped.prepare(null, 1);
                if (expectViolation) {
                    throw new IllegalStateException("expected a violation");
                }
            } catch (SQLException ex) {
                if (!expectViolation) {
                    throw ex;
                }
            }
        }
        return (System.nanoTime() - start) / (double) iterations;
    }

    static final class Harness {
        final StatementHandler wrapped;
        final NonPreparingRoutingHandler handler;

        private Harness(StatementHandler wrapped, NonPreparingRoutingHandler handler) {
            this.wrapped = wrapped;
            this.handler = handler;
        }

        static Harness create(String sql, boolean enableWarningOnly) throws Exception {
            SqlInjectionProperties properties = new SqlInjectionProperties();
            properties.setEnableWarningOnly(enableWarningOnly);
            SqlInjectionPluginInterceptor interceptor =
                    new SqlInjectionPluginInterceptor(properties, new PermitAndDenyCustomizer());
            NonPreparingRoutingHandler handler = NonPreparingRoutingHandler.create(sql, "com.example.BenchMapper.select");
            StatementHandler wrapped = (StatementHandler) interceptor.plugin(handler);
            try {
                wrapped.prepare(null, 1);
            } catch (SQLException ignored) {
                // A violating statement rejects before proceed and still initializes the provider.
            }
            handler.proceedCount = 0;
            return new Harness(wrapped, handler);
        }
    }

    static final class NonPreparingRoutingHandler extends RoutingStatementHandler {
        int proceedCount;

        static NonPreparingRoutingHandler create(String sql, String statementId) {
            return create(sql, statementId, StatementType.PREPARED);
        }

        static NonPreparingRoutingHandler create(String sql, String statementId, StatementType statementType) {
            Configuration configuration = configuration();
            MappedStatement mappedStatement = mappedStatement(configuration, sql, statementId, statementType);
            return new NonPreparingRoutingHandler(configuration, mappedStatement);
        }

        private NonPreparingRoutingHandler(Configuration configuration, MappedStatement mappedStatement) {
            super(executor(configuration), mappedStatement, null, RowBounds.DEFAULT, null, null);
        }

        @Override
        public Statement prepare(Connection connection, Integer transactionTimeout) {
            proceedCount++;
            return null;
        }
    }

    static Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment("bench", new JdbcTransactionFactory(), new MysqlUrlDataSource()));
        return configuration;
    }

    static MappedStatement mappedStatement(Configuration configuration, String sql, String statementId, StatementType statementType) {
        SqlSource sqlSource = parameterObject -> new BoundSql(configuration, sql, Collections.emptyList(), parameterObject);
        return new MappedStatement.Builder(configuration, statementId, sqlSource, SqlCommandType.SELECT)
                .statementType(statementType)
                .build();
    }

    private static Executor executor(Configuration configuration) {
        return new SimpleExecutor(configuration, new Transaction() {
            @Override
            public Connection getConnection() {
                return null;
            }

            @Override
            public void commit() {
            }

            @Override
            public void rollback() {
            }

            @Override
            public void close() {
            }

            @Override
            public Integer getTimeout() {
                return null;
            }
        });
    }

    static final class MysqlUrlDataSource implements DataSource {
        @Override
        public Connection getConnection() {
            return connection();
        }

        @Override
        public Connection getConnection(String username, String password) {
            return connection();
        }

        private Connection connection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getMetaData".equals(name)) {
                            return meta();
                        }
                        if ("close".equals(name)) {
                            return null;
                        }
                        if ("isClosed".equals(name)) {
                            return Boolean.FALSE;
                        }
                        if ("toString".equals(name)) {
                            return "MysqlUrlConnection";
                        }
                        if ("hashCode".equals(name)) {
                            return Integer.valueOf(System.identityHashCode(proxy));
                        }
                        if ("equals".equals(name)) {
                            return Boolean.valueOf(proxy == args[0]);
                        }
                        Class<?> returnType = method.getReturnType();
                        if (returnType.equals(Boolean.TYPE)) {
                            return Boolean.FALSE;
                        }
                        if (returnType.equals(Integer.TYPE)) {
                            return Integer.valueOf(0);
                        }
                        if (returnType.equals(Long.TYPE)) {
                            return Long.valueOf(0L);
                        }
                        return null;
                    });
        }

        private java.sql.DatabaseMetaData meta() {
            return (java.sql.DatabaseMetaData) Proxy.newProxyInstance(
                    java.sql.DatabaseMetaData.class.getClassLoader(),
                    new Class<?>[]{java.sql.DatabaseMetaData.class},
                    (proxy, method, args) -> {
                        if ("getURL".equals(method.getName())) {
                            return "jdbc:mysql://127.0.0.1:3306/bench";
                        }
                        if ("toString".equals(method.getName())) {
                            return "meta";
                        }
                        if ("hashCode".equals(method.getName())) {
                            return Integer.valueOf(System.identityHashCode(proxy));
                        }
                        Class<?> returnType = method.getReturnType();
                        if (returnType.equals(Boolean.TYPE)) {
                            return Boolean.FALSE;
                        }
                        if (returnType.equals(Integer.TYPE)) {
                            return Integer.valueOf(0);
                        }
                        return null;
                    });
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
