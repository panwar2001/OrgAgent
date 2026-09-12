package com.panwar2001.orgagent.features.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.features.organization.dto.OrganizationResponse;

import org.junit.jupiter.api.Test;

/**
 * The console signs a user into exactly one organization, keyed by a slug it derives from their
 * identity, so looking that organization up has to be reliable and has to fail clearly.
 */
class OrganizationSlugLookupTest {

	private final OrganizationRepository repository = mock(OrganizationRepository.class);

	private final OrganizationService service = new OrganizationService(this.repository);

	@Test
	void returnsTheOrganizationUsingThatSlug() {
		UUID id = UUID.randomUUID();
		when(this.repository.findBySlug("user-abc")).thenReturn(Optional.of(organization(id)));

		OrganizationResponse found = this.service.getBySlug("user-abc");

		assertThat(found.id()).isEqualTo(id);
	}

	@Test
	void reportsAMissingSlugAsNotFoundSoTheConsoleCanOfferSetup() {
		when(this.repository.findBySlug("user-abc")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.getBySlug("user-abc")).isInstanceOf(ResourceNotFoundException.class)
			.hasMessageContaining("user-abc")
			.satisfies(failure -> assertThat(((ResourceNotFoundException) failure).code())
				.isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND));
	}

	private Organization organization(UUID id) {
		Organization organization = Organization.of(id, "Acme", "user-abc", OrganizationStatus.ACTIVE);
		organization.onInsert();
		return organization;
	}

}
