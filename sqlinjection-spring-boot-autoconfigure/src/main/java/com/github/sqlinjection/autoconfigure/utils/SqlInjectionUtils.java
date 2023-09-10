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
package com.github.sqlinjection.autoconfigure.utils;

import java.util.Objects;
import java.util.regex.Pattern;



/**
 * @author sean chen
 * @date 2023/9/3 10:22 PM
 */
@SuppressWarnings("unused")
public class SqlInjectionUtils {

    private static final String SQL_INJECTION_REGEX = "\\b(and|or)\\b.{1,6}?(=|>|<|\\bin\\b|\\blike\\b)"
            + "|/\\*.+?\\*/"
            + "|<\\s*script\\b"
            + "|\\bEXEC\\b"
            + "|UNION.+?SELECT"
            + "|UPDATE.+?SET"
            + "|INSERT\\s+INTO.+?VALUES"
            + "|(SELECT|DELETE).+?FROM|(CREATE|ALTER|DROP|TRUNCATE)\\s+(TABLE|DATABASE)"
            + "|\\b(alert\\(|confirm\\(|expression\\(|prompt\\(|benchmark\\s*?\\(.*\\)|sleep\\s*?\\(.*\\)|load_file\\s*?\\()"
            + "|\\b(updatexml\\s*?\\(.*\\)|extractvalue\\s*?\\(.*\\)|floor\\s*?\\(.*\\))";
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(SQL_INJECTION_REGEX, Pattern.CASE_INSENSITIVE);

    private SqlInjectionUtils() {
    }

    /**
     * Check if the given sql has sql injection risk
     * @param sql The input sql
     * @return {@code true} if the sql is safe
     */
    public static boolean isValidSql(String sql) {
        Objects.requireNonNull(sql, "Sql must not be null.");
        String sqlToCheck = sql.toLowerCase();
        return !SQL_INJECTION_PATTERN.matcher(sqlToCheck).find();
    }
}
