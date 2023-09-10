# mybatis-sql-injection-plugin

A mybatis interceptor to prevent sql injection

## Background

Alibaba's Druid connection pool offers a valuable SQL firewall with comprehensive defenses against SQL injection. However, its low-level design makes user configuration cumbersome, and it doesn't handle multiple data sources effectively. Many applications currently use the HikariCP connection pool and, as a result, cannot benefit from Druid's exceptional SQL firewall. To address this, our project provides an encapsulation of the Druid connection pool at the MyBatis interceptor level. This way, various connection pools, including HikariCP and Tomcat, can leverage the powerful SQL firewall capabilities of the Druid connection pool.