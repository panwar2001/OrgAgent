package com.panwar2001.orgagent.features.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DocumentTest {

	private final UUID organizationId = UUID.randomUUID();

	private final UUID projectId = UUID.randomUUID();

	@Test
	void startsPendingWithEverythingThePipelineNeeds() {
		Document document = Document.pending(this.organizationId, this.projectId, "HR Policies", "hr.pdf",
				"application/pdf", 1024L, "abc123");

		assertThat(document.getId()).isNotNull();
		assertThat(document.getOrganizationId()).isEqualTo(this.organizationId);
		assertThat(document.getProjectId()).isEqualTo(this.projectId);
		assertThat(document.getTitle()).isEqualTo("HR Policies");
		assertThat(document.getFileName()).isEqualTo("hr.pdf");
		assertThat(document.getContentType()).isEqualTo("application/pdf");
		assertThat(document.getSizeBytes()).isEqualTo(1024L);
		assertThat(document.getContentHash()).isEqualTo("abc123");
		assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
		assertThat(document.getChunkCount()).isZero();
		assertThat(document.isIndexed()).isFalse();
	}

	@Test
	void becomesSearchableWhenItsChunksAreIndexed() {
		Document document = pending();

		document.markIndexed(7);

		assertThat(document.getStatus()).isEqualTo(DocumentStatus.INDEXED);
		assertThat(document.getChunkCount()).isEqualTo(7);
		assertThat(document.getErrorMessage()).isNull();
		assertThat(document.isIndexed()).isTrue();
	}

	@Test
	void refusesToBeIndexedWithoutAnyChunk() {
		Document document = pending();

		assertThatThrownBy(() -> document.markIndexed(0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void recordsTheReasonWhenIngestionFails() {
		Document document = pending();

		document.markFailed("the model provider timed out");

		assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
		assertThat(document.getErrorMessage()).isEqualTo("the model provider timed out");
	}

	@Test
	void truncatesAVeryLongFailureReason() {
		Document document = pending();

		document.markFailed("x".repeat(5000));

		assertThat(document.getErrorMessage()).hasSize(2000);
	}

	@Test
	void clearsAPreviousFailureWhenItIsIndexedLater() {
		Document document = pending();
		document.markFailed("transient");

		document.markIndexed(2);

		assertThat(document.getErrorMessage()).isNull();
		assertThat(document.isIndexed()).isTrue();
	}

	@Test
	void knowsWhichTenantAndProjectItBelongsTo() {
		Document document = pending();

		assertThat(document.belongsTo(this.organizationId, this.projectId)).isTrue();
		assertThat(document.belongsTo(UUID.randomUUID(), this.projectId)).isFalse();
		assertThat(document.belongsTo(this.organizationId, UUID.randomUUID())).isFalse();
	}

	@Test
	void stampsTimestampsWhenItIsPersisted() {
		Document document = pending();

		document.onInsert();

		assertThat(document.getCreatedAt()).isNotNull();
		assertThat(document.getUpdatedAt()).isEqualTo(document.getCreatedAt());
	}

	private Document pending() {
		return Document.pending(this.organizationId, this.projectId, "HR Policies", "hr.txt", "text/plain", 10L, "hash");
	}

}
