package com.panwar2001.orgagent.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Flyway is the only thing that creates the schema, so a malformed or duplicated migration file
 * would only be discovered when the application refuses to start against a real database. These
 * checks catch that class of mistake in the ordinary unit-test run.
 */
class FlywayMigrationsTest {

	private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration");

	private static final Pattern MIGRATION_NAME = Pattern.compile("^V(\\d+)__([a-z0-9_]+)\\.sql$");

	@Test
	void everyMigrationFollowsTheFlywayNamingConvention() {
		assertThat(migrationFiles()).isNotEmpty().allSatisfy(file -> assertThat(file.getFileName().toString())
			.matches(MIGRATION_NAME));
	}

	@Test
	void migrationVersionsAreUnique() {
		List<String> versions = new ArrayList<>();
		for (Path file : migrationFiles()) {
			Matcher matcher = MIGRATION_NAME.matcher(file.getFileName().toString());
			assertThat(matcher.matches()).isTrue();
			versions.add(matcher.group(1));
		}

		assertThat(versions).doesNotHaveDuplicates();
	}

	@Test
	void migrationsAreNotSkippingVersionsInASurprisingWay() {
		List<Integer> versions = migrationFiles().stream()
			.map(file -> Integer.valueOf(MIGRATION_NAME.matcher(file.getFileName().toString()).replaceFirst("$1")))
			.toList();

		assertThat(versions).isSorted();
	}

	@Test
	void theFirstMigrationCreatesTheTenancyTables() {
		String sql = read(migration("V1__organizations_and_projects.sql"));

		assertThat(sql).contains("CREATE TABLE organizations").contains("CREATE TABLE projects");
		assertThat(sql).contains("REFERENCES organizations (id)").contains("ON DELETE CASCADE");
		assertThat(sql).contains("CONSTRAINT uq_organizations_slug UNIQUE (slug)");
		assertThat(sql).contains("CONSTRAINT uq_projects_organization_slug UNIQUE (organization_id, slug)");
	}

	@Test
	void theSchemaUsesApplicationGeneratedIdentifiersAndOptimisticLocking() {
		String sql = read(migration("V1__organizations_and_projects.sql"));

		assertThat(sql).contains("gen_random_uuid()");
		assertThat(sql).contains("version     bigint       NOT NULL DEFAULT 0");
	}

	@Test
	void everyEntityTableDeclaresTimestampsInUtc() {
		String sql = read(migration("V1__organizations_and_projects.sql"));

		assertThat(sql).contains("created_at  timestamptz").contains("updated_at  timestamptz");
	}

	@Test
	void theSecondMigrationCreatesDocumentsAndTheVectorTable() {
		String sql = read(migration("V2__documents_and_embeddings.sql"));

		assertThat(sql).contains("CREATE EXTENSION IF NOT EXISTS vector");
		assertThat(sql).contains("CREATE TABLE documents");
		assertThat(sql).contains("CONSTRAINT uq_documents_project_content_hash UNIQUE (project_id, content_hash)");
		assertThat(sql).contains("CREATE TABLE document_embeddings");
	}

	@Test
	void theVectorTableMatchesWhatThePgVectorStoreExpects() {
		String sql = read(migration("V2__documents_and_embeddings.sql"));

		// Columns and their types are dictated by Spring AI's PgVectorStore, which is configured
		// with initialize-schema=false and table-name=document_embeddings.
		assertThat(sql).contains("id         uuid PRIMARY KEY");
		assertThat(sql).contains("content    text");
		assertThat(sql).contains("metadata   json");
		assertThat(sql).contains("embedding  vector(768)");
		assertThat(sql).contains("USING hnsw (embedding vector_cosine_ops)");
	}

	@Test
	void embeddedChunksAreDeletedWithTheirProject() {
		String sql = read(migration("V2__documents_and_embeddings.sql"));

		assertThat(sql).contains("REFERENCES projects (id) ON DELETE CASCADE");
	}

	@Test
	void theThirdMigrationCreatesConversationsTheLogAndTheSemanticCache() {
		String sql = read(migration("V3__chat.sql"));

		assertThat(sql).contains("CREATE TABLE chat_conversations");
		assertThat(sql).contains("CREATE TABLE chat_messages");
		assertThat(sql).contains("CREATE TABLE chat_semantic_cache");
		assertThat(sql).contains("REFERENCES chat_conversations (id) ON DELETE CASCADE");
	}

	@Test
	void theSemanticCacheKeepsTheAnswerAndWhyItWasServed() {
		String sql = read(migration("V3__chat.sql"));

		assertThat(sql).contains("served_from_cache").contains("latency_ms").contains("model");
		assertThat(sql).contains("embedding  vector(768)");
		assertThat(sql).contains("USING hnsw (embedding vector_cosine_ops)");
	}

	private List<Path> migrationFiles() {
		try (Stream<Path> files = Files.list(MIGRATION_DIRECTORY)) {
			return files.filter(path -> path.getFileName().toString().endsWith(".sql")).sorted().toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private Path migration(String fileName) {
		Path path = MIGRATION_DIRECTORY.resolve(fileName);
		assertThat(path).exists();
		return path;
	}

	private String read(Path path) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
