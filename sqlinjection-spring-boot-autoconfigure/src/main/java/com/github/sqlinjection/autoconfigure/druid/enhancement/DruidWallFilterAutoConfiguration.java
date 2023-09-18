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
package com.github.sqlinjection.autoconfigure.druid.enhancement;

import com.alibaba.druid.DbType;
import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.sql.dialect.clickhouse.parser.ClickhouseSelectParser;
import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.wall.WallFilter;
import com.github.sqlinjection.autoconfigure.druid.DruidWallFilterProperties;
import com.github.sqlinjection.autoconfigure.utils.JsonUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.github.sqlinjection.autoconfigure.druid.DruidWallFilterProperties.DRUID_FILTER_WALL_PREFIX;
import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * @author sean chen
 * @date 2023/9/10 10:33 PM
 */

@Configuration
@ConditionalOnProperty(prefix = DRUID_FILTER_WALL_PREFIX, value = "enabled", havingValue = "true")
@ConditionalOnBean(DruidDataSource.class)
@ConditionalOnClass({DbType.class, ClickhouseSelectParser.class})
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnWebApplication(type = SERVLET)
@EnableConfigurationProperties(DruidWallFilterProperties.class)
public class DruidWallFilterAutoConfiguration {


    @Bean
    @ConfigurationProperties(DRUID_FILTER_WALL_PREFIX + ".config")
    @ConditionalOnMissingBean
    public WallConfig wallConfig() {
        return new WallConfig();
    }

    @Bean
    @ConfigurationProperties(DRUID_FILTER_WALL_PREFIX + ".startup")
    @ConditionalOnMissingBean
    public WallFilter wallFilter(WallConfig wallConfig) {
        WallFilter wallFilter = new WallFilter();
        wallFilter.setConfig(wallConfig);
        return wallFilter;
    }

    @Bean
    public DruidWallFilterPostProcessor druidWallFilterPostProcessor(DruidWallFilterProperties properties,
                                                                     WallFilter wallFilter,
                                                                     WallConfig wallConfig) {
        return new DruidWallFilterPostProcessor(properties, wallFilter, wallConfig);
    }


    public static class DruidWallFilterPostProcessor implements BeanPostProcessor {

        private final DruidWallFilterProperties properties;
        private final WallFilter wallFilter;

        private final WallConfig wallConfig;

        public DruidWallFilterPostProcessor(DruidWallFilterProperties properties,
                                            WallFilter wallFilter,
                                            WallConfig wallConfig) {
            this.properties = properties;
            this.wallFilter = wallFilter;
            this.wallConfig = wallConfig;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof DruidDataSource) {
                DruidDataSource dataSource = (DruidDataSource) bean;
                try {
                    List<Filter> proxyFilters = dataSource.getProxyFilters();
                    boolean exists = proxyFilters.stream().anyMatch(filter -> filter instanceof WallFilter);
                    if (!exists) {
                        WallFilter tempWallFilter = new WallFilter();
                        boolean enableWarningOnly = properties.isEnableWarningOnly();
                        if (enableWarningOnly) {
                            tempWallFilter.setLogViolation(true);
                            tempWallFilter.setThrowException(false);
                        } else {
                            tempWallFilter.setLogViolation(wallFilter.isLogViolation());
                            tempWallFilter.setThrowException(wallFilter.isThrowException());
                        }

                        tempWallFilter.init(dataSource);
                        WallConfig originalWallConfig = tempWallFilter.getConfig();
                        WallConfig targetWallConfig = deepCopyWallConfig(originalWallConfig, wallConfig);
                        tempWallFilter.setConfig(targetWallConfig);
                        //init again
                        tempWallFilter.init(dataSource);
                        dataSource.setProxyFilters(Collections.singletonList(tempWallFilter));
                    }
                } catch (Throwable throwable) {
                    //ignored
                }
            }
            return bean;
        }

        private WallConfig deepCopyWallConfig(WallConfig originalWallConfig, WallConfig wallConfig) throws NoSuchFieldException, IllegalAccessException {
            Class<? extends WallConfig> aClass = wallConfig.getClass();
            //deny
            getFunctionsProperty(wallConfig, aClass, "denyFunctions", originalWallConfig.getDenyFunctions());
            getFunctionsProperty(wallConfig, aClass, "denyTables", originalWallConfig.getDenyTables());
            getFunctionsProperty(wallConfig, aClass, "denySchemas", originalWallConfig.getDenySchemas());
            getFunctionsProperty(wallConfig, aClass, "denyVariants", originalWallConfig.getDenyVariants());
            getFunctionsProperty(wallConfig, aClass, "denyObjects", originalWallConfig.getDenyObjects());
            //permit
            getFunctionsProperty(wallConfig, aClass, "permitFunctions", originalWallConfig.getPermitFunctions());
            getFunctionsProperty(wallConfig, aClass, "permitTables", originalWallConfig.getPermitTables());
            getFunctionsProperty(wallConfig, aClass, "permitSchemas", originalWallConfig.getPermitSchemas());
            getFunctionsProperty(wallConfig, aClass, "permitVariants", originalWallConfig.getPermitVariants());
            getFunctionsProperty(wallConfig, aClass, "readOnlyTables", originalWallConfig.getReadOnlyTables());

            wallConfig.setDir(originalWallConfig.getDir());
            wallConfig.setInited(originalWallConfig.isInited());

            return JsonUtils.readValue(wallConfig, WallConfig.class);
        }

        private void getFunctionsProperty(WallConfig wallConfig, Class<? extends WallConfig> aClass, String fieldName, Set<String> values)
                throws NoSuchFieldException, IllegalAccessException {
            Field declaredField = aClass.getDeclaredField(fieldName);
            declaredField.setAccessible(true);
            declaredField.set(wallConfig, values);
        }
    }

}
