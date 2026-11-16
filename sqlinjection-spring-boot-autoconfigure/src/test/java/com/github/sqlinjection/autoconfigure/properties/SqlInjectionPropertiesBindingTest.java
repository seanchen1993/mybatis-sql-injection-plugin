package com.github.sqlinjection.autoconfigure.properties;

import org.junit.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SqlInjectionPropertiesBindingTest {

    @Test
    public void defaultsMatchTheDecisionCacheBounds() {
        SqlInjectionProperties properties = new SqlInjectionProperties();
        assertTrue(properties.isCacheEnabled());
        assertEquals(1024, properties.getCacheMaxEntries());
        assertEquals(SqlInjectionProperties.DEFAULT_CACHE_MAX_ENTRIES, properties.getCacheMaxEntries());
        assertEquals(4096, properties.getCacheMaxSqlLength());
        assertEquals(SqlInjectionProperties.DEFAULT_CACHE_MAX_SQL_LENGTH, properties.getCacheMaxSqlLength());
    }

    @Test
    public void bindsCacheSettingsUnderTheWallPrefix() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("mybatis.plugin.sqlinjection.wall.cache-enabled", "false");
        values.put("mybatis.plugin.sqlinjection.wall.cache-max-entries", "32");
        values.put("mybatis.plugin.sqlinjection.wall.cache-max-sql-length", "128");
        values.put("mybatis.plugin.sqlinjection.wall.enable-warning-only", "true");

        SqlInjectionProperties properties = new SqlInjectionProperties();
        new Binder(new MapConfigurationPropertySource(values))
                .bind(SqlInjectionProperties.SQL_INJECTION_PREFIX, Bindable.ofInstance(properties));

        assertFalse(properties.isCacheEnabled());
        assertEquals(32, properties.getCacheMaxEntries());
        assertEquals(128, properties.getCacheMaxSqlLength());
        assertTrue(properties.isEnableWarningOnly());
        assertTrue(properties.isSupportCallable());
    }
}
