package com.panwar2001.orgagent.core.config;

import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * Flyway is the only thing allowed to change the schema; Hibernate runs with
 * {@code ddl-auto=validate}.
 *
 * <p>Migration outcomes are logged explicitly because "the app failed to start because a migration
 * was pending" is otherwise the least obvious thing in the logs.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class FlywayConfig {

	@Bean
	FlywayMigrationStrategy migrateAndReport() {
		return flyway -> {
			MigrateResult result = flyway.migrate();
			if (result.success) {
				log.info("Flyway migrated schema '{}' from {} to {} ({} migration(s) applied)",
						result.schemaName, result.initialSchemaVersion, result.targetSchemaVersion,
						result.migrationsExecuted);
			}
			else {
				log.error("Flyway migration reported failure on schema '{}': {}", result.schemaName, result.warnings);
			}
		};
	}

	/**
	 * {@code flyway.clean()} would drop every table; it must never be reachable from a running
	 * service, so it is disabled in code rather than left to per-environment configuration.
	 */
	@Bean
	FlywayConfigurationCustomizer disableClean() {
		return configuration -> configuration.cleanDisabled(true);
	}

}
