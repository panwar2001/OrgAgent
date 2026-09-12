package com.panwar2001.orgagent.core.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

class FlywayConfigTest {

	private final FlywayConfig config = new FlywayConfig();

	@Test
	void runsTheMigrationsWhenTheApplicationStarts() {
		Flyway flyway = mock(Flyway.class);
		when(flyway.migrate()).thenReturn(successfulMigration("public", "1", "2", 1));

		config.migrateAndReport().migrate(flyway);

		verify(flyway).migrate();
	}

	@Test
	void toleratesAFailedMigrationReportWithoutThrowing() {
		Flyway flyway = mock(Flyway.class);
		MigrateResult result = successfulMigration("public", "1", "2", 0);
		result.success = false;
		when(flyway.migrate()).thenReturn(result);

		config.migrateAndReport().migrate(flyway);

		verify(flyway).migrate();
	}

	@Test
	void disablesFlywayCleanInEveryEnvironment() {
		FluentConfiguration configuration = mock(FluentConfiguration.class);

		config.disableClean().customize(configuration);

		verify(configuration).cleanDisabled(true);
	}

	private MigrateResult successfulMigration(String schema, String from, String to, int executed) {
		MigrateResult result = new MigrateResult();
		result.schemaName = schema;
		result.initialSchemaVersion = from;
		result.targetSchemaVersion = to;
		result.migrationsExecuted = executed;
		result.success = true;
		return result;
	}

}
