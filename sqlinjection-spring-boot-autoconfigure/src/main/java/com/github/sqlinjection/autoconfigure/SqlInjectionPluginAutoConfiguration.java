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
import com.alibaba.druid.sql.dialect.clickhouse.parser.ClickhouseSelectParser;
import com.github.sqlinjection.autoconfigure.properties.SqlInjectionProperties;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Collections;
import java.util.List;

import static com.github.sqlinjection.autoconfigure.properties.SqlInjectionProperties.SQL_INJECTION_PREFIX;
import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * @author sean chen
 * @date 2023/9/10 10:33 PM
 */

@Configuration
@ConditionalOnProperty(prefix = SQL_INJECTION_PREFIX, value = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(SqlSessionFactory.class)
@ConditionalOnClass({DbType.class, ClickhouseSelectParser.class})
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnWebApplication(type = SERVLET)
@EnableConfigurationProperties(SqlInjectionProperties.class)
public class SqlInjectionPluginAutoConfiguration {


    @Bean
    @ConfigurationProperties(SQL_INJECTION_PREFIX + ".config")
    @ConditionalOnMissingBean
    public PermitAndDenyCustomizer permitAndDenyCustomizer() {
        return new PermitAndDenyCustomizer();
    }

    @Bean
    public StartupSqlInjectionPlugin startupSqlInjectionPlugin(ObjectProvider<List<SqlSessionFactory>> sqlSessionFactories,
                                                               SqlInjectionProperties properties,
                                                               PermitAndDenyCustomizer customizer) {
        return new StartupSqlInjectionPlugin(sqlSessionFactories.getIfAvailable(Collections::emptyList), properties, customizer);
    }
}
