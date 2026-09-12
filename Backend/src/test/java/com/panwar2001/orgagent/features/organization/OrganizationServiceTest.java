package com.panwar2001.orgagent.features.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.panwar2001.orgagent.core.exception.BadRequestException;
import com.panwar2001.orgagent.core.exception.ConflictException;
import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.features.organization.dto.CreateOrganizationRequest;
import com.panwar2001.orgagent.features.organization.dto.OrganizationResponse;
import com.panwar2001.orgagent.features.organization.dto.UpdateOrganizationRequest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class OrganizationServiceTest {

	private final OrganizationRepository repository = mock(OrganizationRepository.class);

	private final OrganizationService service = new OrganizationService(this.repository);

	@Test
	void derivesTheSlugFromTheNameWhenNoneIsGiven() {
		when(this.repository.existsBySlug("acme-ltd")).thenReturn(false);
		when(this.repository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));

		OrganizationResponse created = this.service.create(new CreateOrganizationRequest("Acme Ltd.", null));

		assertThat(created.slug()).isEqualTo("acme-ltd");
		assertThat(created.name()).isEqualTo("Acme Ltd.");
		assertThat(created.status()).isEqualTo(OrganizationStatus.ACTIVE);
	}

	@Test
	void slugifiesAnExplicitSlugTooSoTheStoredValueIsAlwaysCanonical() {
		when(this.repository.existsBySlug("my-org")).thenReturn(false);
		when(this.repository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));

		OrganizationResponse created = this.service.create(new CreateOrganizationRequest("Whatever", "My Org"));

		assertThat(created.slug()).isEqualTo("my-org");
	}

	@Test
	void treatsABlankSlugAsAbsent() {
		when(this.repository.existsBySlug("acme")).thenReturn(false);
		when(this.repository.save(any(Organization.class))).thenAnswer(call -> call.getArgument(0));

		OrganizationResponse created = this.service.create(new CreateOrganizationRequest("Acme", "   "));

		assertThat(created.slug()).isEqualTo("acme");
	}

	@Test
	void rejectsNamesThatCannotProduceASlug() {
		when(this.repository.existsBySlug(any())).thenReturn(false);

		assertThatThrownBy(() -> this.service.create(new CreateOrganizationRequest("!!! ???", null)))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void pagesThroughAnEmptyAccountList() {
		Pageable pageable = PageRequest.of(0, 20);
		when(this.repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

		PageResponse<OrganizationResponse> page = this.service.list(pageable);

		assertThat(page.content()).isEmpty();
		assertThat(page.totalElements()).isZero();
	}

	@Test
	void refusesADuplicateSlugBeforeWriting() {
		when(this.repository.existsBySlug("acme")).thenReturn(true);

		assertThatThrownBy(() -> this.service.create(new CreateOrganizationRequest("Acme", "acme")))
			.isInstanceOf(ConflictException.class)
			.hasMessageContaining("acme")
			.satisfies(ex -> assertThat(((ConflictException) ex).code())
				.isEqualTo(ErrorCode.ORGANIZATION_ALREADY_EXISTS));

		verify(this.repository, never()).save(any());
	}

	@Test
	void returnsOneOrganization() {
		UUID id = UUID.randomUUID();
		when(this.repository.findById(id)).thenReturn(Optional.of(organization(id, "Acme", "acme")));

		assertThat(this.service.get(id).id()).isEqualTo(id);
	}

	@Test
	void failsWith404WhenTheOrganizationIsUnknown() {
		UUID id = UUID.randomUUID();
		when(this.repository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.get(id)).isInstanceOf(ResourceNotFoundException.class)
			.hasMessageContaining(id.toString())
			.satisfies(ex -> assertThat(((ResourceNotFoundException) ex).code())
				.isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND));
	}

	@Test
	void pagesThroughOrganizations() {
		Pageable pageable = PageRequest.of(0, 20);
		when(this.repository.findAll(pageable))
			.thenReturn(new PageImpl<>(List.of(organization(UUID.randomUUID(), "Acme", "acme")), pageable, 1));

		PageResponse<OrganizationResponse> page = this.service.list(pageable);

		assertThat(page.content()).singleElement().satisfies(org -> assertThat(org.name()).isEqualTo("Acme"));
		assertThat(page.totalElements()).isEqualTo(1);
	}

	@Test
	void renamesAnOrganization() {
		UUID id = UUID.randomUUID();
		when(this.repository.findById(id)).thenReturn(Optional.of(organization(id, "Acme", "acme")));

		OrganizationResponse renamed = this.service.rename(id, new UpdateOrganizationRequest("Acme Holdings"));

		assertThat(renamed.name()).isEqualTo("Acme Holdings");
	}

	@Test
	void suspendsAndReactivatesAnOrganization() {
		UUID id = UUID.randomUUID();
		when(this.repository.findById(id)).thenReturn(Optional.of(organization(id, "Acme", "acme")));

		assertThat(this.service.suspend(id).status()).isEqualTo(OrganizationStatus.SUSPENDED);
		assertThat(this.service.activate(id).status()).isEqualTo(OrganizationStatus.ACTIVE);
	}

	@Test
	void deletesAnOrganization() {
		UUID id = UUID.randomUUID();
		Organization organization = organization(id, "Acme", "acme");
		when(this.repository.findById(id)).thenReturn(Optional.of(organization));

		this.service.delete(id);

		ArgumentCaptor<Organization> deleted = ArgumentCaptor.forClass(Organization.class);
		verify(this.repository).delete(deleted.capture());
		assertThat(deleted.getValue().getId()).isEqualTo(id);
	}

	@Test
	void doesNotDeleteSomethingThatIsNotThere() {
		UUID id = UUID.randomUUID();
		when(this.repository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
		verify(this.repository, never()).delete(any());
	}

	private Organization organization(UUID id, String name, String slug) {
		Organization organization = Organization.of(id, name, slug, OrganizationStatus.ACTIVE);
		// Simulate what JPA does on insert so responses carry timestamps.
		organization.onInsert();
		return organization;
	}

	@Test
	void keepsTimestampsOutOfTheRequestAndInTheResponse() {
		UUID id = UUID.randomUUID();
		Organization organization = organization(id, "Acme", "acme");
		when(this.repository.findById(id)).thenReturn(Optional.of(organization));

		Instant createdAt = this.service.get(id).createdAt();

		assertThat(createdAt).isNotNull();
	}

}
