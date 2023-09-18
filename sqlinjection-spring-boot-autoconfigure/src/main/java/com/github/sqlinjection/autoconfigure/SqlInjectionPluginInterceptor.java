/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2023 seanchen(sean737281994@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.sqlinjection.autoconfigure;

import com.alibaba.druid.DbType;
import com.alibaba.druid.util.JdbcUtils;
import com.alibaba.druid.wall.Violation;
import com.alibaba.druid.wall.WallCheckResult;
import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.wall.WallProvider;
import com.alibaba.druid.wall.spi.*;
import com.alibaba.druid.wall.violation.SyntaxErrorViolation;
import com.github.sqlinjection.autoconfigure.properties.SqlInjectionProperties;
import com.github.sqlinjection.autoconfigure.utils.JsonUtils;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

import static com.github.sqlinjection.autoconfigure.PermitAndDenyCustomizer.DEFAULT_MYSQL_DENY_FUNCTIONS;
import static org.apache.ibatis.mapping.StatementType.CALLABLE;

/**
 * @author sean chen
 * @date 2023/9/11 12:14 AM
 */
@Intercepts(
        {
                @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
        }
)
public class SqlInjectionPluginInterceptor implements Interceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlInjectionPluginInterceptor.class);

    private static final int MAX_ERROR_EXECUTE_COUNT = 10;
    private static final String MYBATIS_PLUGIN_TARGET = "h.target";
    private static final String STATEMENT_HANDLER_DELEGATE_MAPPED_STATEMENT = "delegate.mappedStatement";

    private static final String STATEMENT_HANDLER_DELEGATE_BOUND_SQL = "delegate.boundSql";
    private final Object sqlInjectionPluginMonitor = new Object();
    private final SqlInjectionProperties properties;
    private final PermitAndDenyCustomizer customizer;
    private volatile int currentErrorCount = 0;
    private volatile WallProvider wallProvider;
    private volatile DbType dbType;

    public SqlInjectionPluginInterceptor(SqlInjectionProperties properties, PermitAndDenyCustomizer customizer) {
        this.properties = properties;
        this.customizer = customizer;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        StatementHandler statementHandler = getTarget(invocation.getTarget());

        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);

        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue(STATEMENT_HANDLER_DELEGATE_MAPPED_STATEMENT);

        if (!properties.isSupportCallable() && CALLABLE.equals(mappedStatement.getStatementType())) {
            return invocation.proceed();
        }

        Set<String> ignoredStatements = properties.getIgnoredStatements();
        if (ignoredStatements != null && ignoredStatements.contains(mappedStatement.getId())) {
            return invocation.proceed();
        }

        if (!initDruidWallProvider(mappedStatement)) {
            return invocation.proceed();
        }

        BoundSql boundSql = (BoundSql) metaObject.getValue(STATEMENT_HANDLER_DELEGATE_BOUND_SQL);

        String sqlToCheck = boundSql.getSql();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("The sql checked by sql injection plugin is: {}", sqlToCheck);
        }

        WallCheckResult check = wallProvider.check(sqlToCheck);

        List<Violation> violations = check.getViolations();

        if (violations.size() > 0) {
            Violation firstViolation = violations.get(0);
            if (properties.isEnableWarningOnly()) {

                LOGGER.warn("sql injection violation, dbType {}, {} : {}", dbType, firstViolation.getMessage(), sqlToCheck);
            } else {
                if (firstViolation instanceof SyntaxErrorViolation) {
                    SyntaxErrorViolation violation = (SyntaxErrorViolation) firstViolation;

                    throw new SQLException("sql injection violation, dbType: "
                            + dbType
                            + ", " + firstViolation.getMessage()
                            + ": " + sqlToCheck,
                            violation.getException());
                } else {

                    throw new SQLException("sql injection violation, dbType: "
                            + dbType
                            + ", " + firstViolation.getMessage()
                            + ": " + sqlToCheck);
                }
            }
        }

        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    @Override
    public void setProperties(Properties properties) {
        //do nothing
    }

    @SuppressWarnings("unchecked")
    private <D> D getTarget(Object target) {
        if (Proxy.isProxyClass(target.getClass())) {
            MetaObject metaObject = SystemMetaObject.forObject(target);
            return getTarget(metaObject.getValue(MYBATIS_PLUGIN_TARGET));
        }
        return (D) target;
    }


    private boolean initDruidWallProvider(MappedStatement mappedStatement) {
        if (invalidExecuteTimes()) {
            return false;
        }

        if (wallProvider == null) {
            synchronized (sqlInjectionPluginMonitor) {
                // double check
                if (wallProvider == null) {
                    try {
                        if (invalidExecuteTimes()) {
                            return false;
                        }

                        DataSource dataSource = mappedStatement.getConfiguration().getEnvironment().getDataSource();

                        String url = getUrl(dataSource);

                        String dbTypeName = JdbcUtils.getDbType(url, null);
                        if (dbTypeName == null) {
                            LOGGER.info("The db type is not supported, url: {}", url);
                            currentErrorCount++;
                            return false;
                        }

                        dbType = DbType.of(dbTypeName);

                        //init wall provider
                        wallProvider = createWallProvider(dbType, customizer, url);

                    } catch (Throwable throwable) {
                        LOGGER.error("Init druid wall provider error.", throwable);
                        currentErrorCount++;
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private WallProvider createWallProvider(DbType dbType, PermitAndDenyCustomizer customizer, String url) {
        WallProvider provider;
        WallConfig config;

        switch (dbType) {
            case mysql:
            case oceanbase:
            case drds:
            case mariadb:
            case tidb:
            case h2:
            case presto:
            case trino:
                config = getWallConfig(dbType, customizer, MySqlWallProvider.DEFAULT_CONFIG_DIR);
                provider = new MySqlWallProvider(config);
                break;
            case oracle:
            case ali_oracle:
            case oceanbase_oracle:
                //case dm:
                config = getWallConfig(dbType, customizer, OracleWallProvider.DEFAULT_CONFIG_DIR);
                provider = new OracleWallProvider(config);
                break;
            case sqlserver:
            case jtds:
                config = getWallConfig(dbType, customizer, SQLServerWallProvider.DEFAULT_CONFIG_DIR);
                provider = new SQLServerWallProvider(config);
                break;
            case postgresql:
            case edb:
            case polardb:
            case greenplum:
            case gaussdb:
                config = getWallConfig(dbType, customizer, PGWallProvider.DEFAULT_CONFIG_DIR);
                provider = new PGWallProvider(config);
                break;
            case db2:
                config = getWallConfig(dbType, customizer, DB2WallProvider.DEFAULT_CONFIG_DIR);
                provider = new DB2WallProvider(config);
                break;
            case sqlite:
                config = getWallConfig(dbType, customizer, SQLiteWallProvider.DEFAULT_CONFIG_DIR);
                provider = new SQLiteWallProvider(config);
                break;
            case clickhouse:
                config = getWallConfig(dbType, customizer, ClickhouseWallProvider.DEFAULT_CONFIG_DIR);
                provider = new ClickhouseWallProvider(config);
                break;
            default:
                throw new IllegalStateException("dbType not support : " + dbType + ", url " + url);
        }
        return provider;
    }

    private boolean invalidExecuteTimes() {
        return currentErrorCount > MAX_ERROR_EXECUTE_COUNT;
    }

    private String getUrl(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        }
    }


    private WallConfig getWallConfig(DbType dbType, PermitAndDenyCustomizer customizer, String defaultConfigDir) {
        WallConfig wallConfig = JsonUtils.convertValue(customizer, WallConfig.class);

        clearWallConfig(wallConfig);

        wallConfig.setDir(defaultConfigDir);

        wallConfig.init();

        applyCustomizerFunctions(wallConfig, customizer, dbType);

        return wallConfig;
    }


    private void clearWallConfig(WallConfig config) {
        config.getDenyFunctions().clear();
        config.getDenyTables().clear();
        config.getDenySchemas().clear();
        config.getDenyVariants().clear();
        config.getDenyObjects().clear();
        config.getPermitFunctions().clear();
        config.getPermitTables().clear();
        config.getPermitSchemas().clear();
        config.getPermitVariants().clear();
        config.getReadOnlyTables().clear();
    }


    private void applyCustomizerFunctions(WallConfig wallConfig, PermitAndDenyCustomizer customizer, DbType dbType) {
        applyFunctions(wallConfig, customizer, dbType);
        applyTables(wallConfig, customizer, dbType);
        applySchemas(wallConfig, customizer, dbType);
        applyVariants(wallConfig, customizer, dbType);
        applyDenyObjects(wallConfig, customizer, dbType);
        applyReadOnlyTables(wallConfig, customizer, dbType);
        wallConfig.setTenantCallBack(customizer.getTenantCallBack());
        wallConfig.setUpdateCheckHandler(customizer.getUpdateCheckHandler());
    }


    private void applyFunctions(WallConfig wallConfig, PermitAndDenyCustomizer customizer, DbType dbType) {
        Set<String> denyFunctions = wallConfig.getDenyFunctions();
        Set<String> permitFunctions = wallConfig.getPermitFunctions();
        //default deny functions
        dataOperator(DEFAULT_MYSQL_DENY_FUNCTIONS, dbType, dataConsumer(denyFunctions, permitFunctions));
        //custom permit and deny functions
        dataOperator(customizer.getDatabase2PermitFunctions(), dbType, dataConsumer(permitFunctions, denyFunctions));
        dataOperator(customizer.getDatabase2DenyFunctions(), dbType, dataConsumer(denyFunctions, permitFunctions));
    }

    private void applyTables(WallConfig wallConfig, PermitAndDenyCustomizer customizer, DbType dbType) {
        Set<String> permitTables = wallConfig.getPermitTables();
        Set<String> denyTables = wallConfig.getDenyTables();
        //permit
        dataOperator(customizer.getDatabase2PermitTables(), dbType, dataConsumer(permitTables, denyTables));
        //deny
        dataOperator(customizer.getDatabase2DenyTables(), dbType, dataConsumer(denyTables, permitTables));
    }

    private void applySchemas(WallConfig wallConfig, PermitAndDenyCustomizer customizer, DbType dbType) {
        Set<String> permitSchemas = wallConfig.getPermitSchemas();
        Set<String> denySchemas = wallConfig.getDenySchemas();
        //permit
        dataOperator(customizer.getDatabase2PermitSchemas(), dbType, dataConsumer(permitSchemas, denySchemas));
        //deny
        dataOperator(customizer.getDatabase2DenySchemas(), dbType, dataConsumer(denySchemas, permitSchemas));
    }

    private void applyVariants(WallConfig wallConfig, PermitAndDenyCustomizer customizer, DbType dbType) {
        Set<String> permitVariants = wallConfig.getPermitVariants();
        Set<String> denyVariants = wallConfig.getDenyVariants();
        //permit
        dataOperator(customizer.getDatabase2PermitVariants(), dbType, dataConsumer(permitVariants, denyVariants));
        //deny
        dataOperator(customizer.getDatabase2DenyVariants(), dbType, dataConsumer(denyVariants, permitVariants));
    }

    private void applyDenyObjects(WallConfig wallConfig, PermitAndDenyCustomizer customizer, DbType dbType) {
        Set<String> denyObjects = wallConfig.getDenyObjects();
        //deny objects
        dataOperator(customizer.getDatabase2DenyObjects(), dbType, dataConsumer(denyObjects, null));
    }

    private void applyReadOnlyTables(WallConfig wallConfig, PermitAndDenyCustomizer customizer, DbType dbType) {
        Set<String> readOnlyTables = wallConfig.getReadOnlyTables();
        //read only tables
        dataOperator(customizer.getDatabase2ReadOnlyObjects(), dbType, dataConsumer(readOnlyTables, null));
    }

    private void dataOperator(Map<String, Set<String>> customMap, DbType type, Consumer<Set<String>> consumer) {
        Optional.ofNullable(customMap)
                .map(map -> map.get(type.name()))
                .ifPresent(consumer);
    }

    private Consumer<Set<String>> dataConsumer(Set<String> addAllSet, @Nullable Set<String> removeAllSet) {
        return data -> {
            addAllSet.addAll(data);

            if (removeAllSet != null) {
                removeAllSet.removeAll(data);
            }
        };
    }
}
