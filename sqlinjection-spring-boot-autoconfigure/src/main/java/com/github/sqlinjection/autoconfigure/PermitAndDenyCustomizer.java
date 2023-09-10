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

import com.alibaba.druid.wall.WallConfig;

import java.util.*;

/**
 * @author sean chen
 * @date 2023/9/10 11:27 PM
 */
@SuppressWarnings("unused")
public class PermitAndDenyCustomizer extends WallConfig {

    /**
     * MySQL prohibits the extractvalue and updatexml methods by default, which is quite dangerous.
     */
    public static final Map<String, Set<String>> DEFAULT_MYSQL_DENY_FUNCTIONS = new LinkedHashMap<>(
            Collections.singletonMap("mysql", new HashSet<>(Arrays.asList("extractvalue", "updatexml"))));

    private Map<String, Set<String>> database2DenyFunctions = new LinkedHashMap<>();

    private Map<String, Set<String>> database2DenyTables = new LinkedHashMap<>();

    private Map<String, Set<String>> database2DenySchemas = new LinkedHashMap<>();

    private Map<String, Set<String>> database2DenyVariants = new LinkedHashMap<>();

    private Map<String, Set<String>> database2DenyObjects = new LinkedHashMap<>();

    private Map<String, Set<String>> database2PermitFunctions = new LinkedHashMap<>();

    private Map<String, Set<String>> database2PermitTables = new LinkedHashMap<>();

    private Map<String, Set<String>> database2PermitSchemas = new LinkedHashMap<>();

    private Map<String, Set<String>> database2PermitVariants = new LinkedHashMap<>();

    private Map<String, Set<String>> database2ReadOnlyObjects = new LinkedHashMap<>();


    public Map<String, Set<String>> getDatabase2DenyFunctions() {
        return database2DenyFunctions;
    }

    public void setDatabase2DenyFunctions(Map<String, Set<String>> database2DenyFunctions) {
        this.database2DenyFunctions = database2DenyFunctions;
    }

    public Map<String, Set<String>> getDatabase2DenyTables() {
        return database2DenyTables;
    }

    public void setDatabase2DenyTables(Map<String, Set<String>> database2DenyTables) {
        this.database2DenyTables = database2DenyTables;
    }

    public Map<String, Set<String>> getDatabase2DenySchemas() {
        return database2DenySchemas;
    }

    public void setDatabase2DenySchemas(Map<String, Set<String>> database2DenySchemas) {
        this.database2DenySchemas = database2DenySchemas;
    }

    public Map<String, Set<String>> getDatabase2DenyVariants() {
        return database2DenyVariants;
    }

    public void setDatabase2DenyVariants(Map<String, Set<String>> database2DenyVariants) {
        this.database2DenyVariants = database2DenyVariants;
    }

    public Map<String, Set<String>> getDatabase2DenyObjects() {
        return database2DenyObjects;
    }

    public void setDatabase2DenyObjects(Map<String, Set<String>> database2DenyObjects) {
        this.database2DenyObjects = database2DenyObjects;
    }

    public Map<String, Set<String>> getDatabase2PermitFunctions() {
        return database2PermitFunctions;
    }

    public void setDatabase2PermitFunctions(Map<String, Set<String>> database2PermitFunctions) {
        this.database2PermitFunctions = database2PermitFunctions;
    }

    public Map<String, Set<String>> getDatabase2PermitTables() {
        return database2PermitTables;
    }

    public void setDatabase2PermitTables(Map<String, Set<String>> database2PermitTables) {
        this.database2PermitTables = database2PermitTables;
    }

    public Map<String, Set<String>> getDatabase2PermitSchemas() {
        return database2PermitSchemas;
    }

    public void setDatabase2PermitSchemas(Map<String, Set<String>> database2PermitSchemas) {
        this.database2PermitSchemas = database2PermitSchemas;
    }

    public Map<String, Set<String>> getDatabase2PermitVariants() {
        return database2PermitVariants;
    }

    public void setDatabase2PermitVariants(Map<String, Set<String>> database2PermitVariants) {
        this.database2PermitVariants = database2PermitVariants;
    }

    public Map<String, Set<String>> getDatabase2ReadOnlyObjects() {
        return database2ReadOnlyObjects;
    }

    public void setDatabase2ReadOnlyObjects(Map<String, Set<String>> database2ReadOnlyObjects) {
        this.database2ReadOnlyObjects = database2ReadOnlyObjects;
    }
}
