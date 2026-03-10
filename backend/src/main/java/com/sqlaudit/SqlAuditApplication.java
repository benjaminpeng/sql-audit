package com.sqlaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class SqlAuditApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SqlAuditApplication.class, args);
        boolean cliEnabled = context.getEnvironment().getProperty("sql-audit.cli.enabled", Boolean.class, false);
        if (cliEnabled) {
            int exitCode = SpringApplication.exit(context);
            System.exit(exitCode);
        }
    }
}
