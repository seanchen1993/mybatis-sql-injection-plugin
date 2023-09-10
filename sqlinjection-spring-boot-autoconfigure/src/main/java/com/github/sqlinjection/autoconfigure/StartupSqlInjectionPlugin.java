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

import com.github.sqlinjection.autoconfigure.properties.SqlInjectionProperties;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;

/**
 * @author sean chen
 * @date 2023/9/11 12:00 AM
 */
public class StartupSqlInjectionPlugin {

    public StartupSqlInjectionPlugin(List<SqlSessionFactory> sqlSessionFactories, SqlInjectionProperties properties, PermitAndDenyCustomizer customizer) {
        start(sqlSessionFactories, properties, customizer);
    }

    private void start(List<SqlSessionFactory> sqlSessionFactories, SqlInjectionProperties properties, PermitAndDenyCustomizer customizer) {
        for (SqlSessionFactory sqlSessionFactory : sqlSessionFactories) {
            try {
                Configuration configuration = sqlSessionFactory.getConfiguration();

                SqlInjectionPluginInterceptor interceptor = new SqlInjectionPluginInterceptor(properties, customizer);

                if (containsMybatisInterceptor(configuration, interceptor)) {
                    configuration.addInterceptor(interceptor);
                }

            } catch (Exception ignored) {
            }
        }
    }

    private boolean containsMybatisInterceptor(Configuration configuration, Interceptor interceptor) {
        try {
            return configuration.getInterceptors().stream()
                    .anyMatch(config -> interceptor.getClass().isAssignableFrom(config.getClass()));
        } catch (Exception ex) {
            return false;
        }
    }

}
