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
import com.alibaba.druid.wall.Violation;
import com.alibaba.druid.wall.WallCheckResult;
import com.alibaba.druid.wall.WallProvider;
import com.alibaba.druid.wall.violation.SyntaxErrorViolation;
import com.github.sqlinjection.autoconfigure.properties.SqlInjectionProperties;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final AtomicInteger currentErrorCount = new AtomicInteger(0);
    private SqlInjectionProperties properties;
    private PermitAndDenyCustomizer customizer;
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


        return false;
    }

}
