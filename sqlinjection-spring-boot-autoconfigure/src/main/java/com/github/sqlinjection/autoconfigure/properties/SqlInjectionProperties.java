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
package com.github.sqlinjection.autoconfigure.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.github.sqlinjection.autoconfigure.properties.SqlInjectionProperties.SQL_INJECTION_PREFIX;

/**
 * @author sean chen
 * @date 2023/9/10 10:17 PM
 */

@SuppressWarnings("unused")
@ConfigurationProperties(SQL_INJECTION_PREFIX)
public class SqlInjectionProperties {

    public static final String SQL_INJECTION_PREFIX = "mybatis.plugin.sqlinjection.wall";

    public static final int DEFAULT_CACHE_MAX_ENTRIES = 1024;

    public static final int DEFAULT_CACHE_MAX_SQL_LENGTH = 4096;

    /**
     * just enable warning，default is false
     */
    private boolean enableWarningOnly = false;

    /**
     * Whether to process stored procedures, the default is true
     */
    private boolean supportCallable = true;

    /**
     * Which sql needs to be skipped, that is, sql whitelist
     */
    private Set<String> ignoredStatements = new LinkedHashSet<>();

    /**
     * Reuse a Wall decision for the same SQL text. Default is true.
     * Set to false to call Druid on every prepare. Non-positive cache limits are rejected while this stays true.
     */
    private boolean cacheEnabled = true;

    /**
     * Maximum distinct SQL texts stored in the decision cache.
     */
    private int cacheMaxEntries = DEFAULT_CACHE_MAX_ENTRIES;

    /**
     * SQL text longer than this is checked every time and is not stored.
     */
    private int cacheMaxSqlLength = DEFAULT_CACHE_MAX_SQL_LENGTH;


    public boolean isEnableWarningOnly() {
        return enableWarningOnly;
    }

    public void setEnableWarningOnly(boolean enableWarningOnly) {
        this.enableWarningOnly = enableWarningOnly;
    }

    public boolean isSupportCallable() {
        return supportCallable;
    }

    public void setSupportCallable(boolean supportCallable) {
        this.supportCallable = supportCallable;
    }

    public Set<String> getIgnoredStatements() {
        return ignoredStatements;
    }

    public void setIgnoredStatements(Set<String> ignoredStatements) {
        this.ignoredStatements = ignoredStatements;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(int cacheMaxEntries) {
        this.cacheMaxEntries = cacheMaxEntries;
    }

    public int getCacheMaxSqlLength() {
        return cacheMaxSqlLength;
    }

    public void setCacheMaxSqlLength(int cacheMaxSqlLength) {
        this.cacheMaxSqlLength = cacheMaxSqlLength;
    }
}
