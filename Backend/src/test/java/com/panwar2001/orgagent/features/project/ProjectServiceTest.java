package com.panwar2001.orgagent.features.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.panwar2001.orgagent.core.exception.ConflictException;
import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.features.organization.Organization;
import com.panwar2001.orgagent.features.organization.OrganizationService;
import com.panwar2001.orgagent.features.project.dto.CreateProjectRequest;
import com.panwar2001.orgagent.features.project.dto.ProjectResponse;
import com.panwar2001.orgagent.features.project.dto.UpdateProjectRequest;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ProjectServiceTest {

	private final ProjectRepository repository = mock(ProjectRepository.class);

	private final OrganizationService organizationService = mock(OrganizationService.class);

	private final ProjectService service = new ProjectService(this.repository, this.organizationService);

	private final UUID organizationId = UUID.randomUUID();

	@Test
	void createsAProjectInsideAnExistingOrganization() {
		givenOrganizationExists();
		when(this.repository.existsByOrganizationIdAndSlug(this.organizationId, "hr-policies")).thenReturn(false);
		when(this.repository.save(any(Project.class))).thenAnswer(call -> call.getArgument(0));

		ProjectResponse created = this.service
			.create(this.organizationId, new CreateProjectRequest("HR Policies", null, "handbook"));

		assertThat(created.organizationId()).isEqualTo(this.organizationId);
		assertThat(created.slug()).isEqualTo("hr-policies");
		assertThat(created.description()).isEqualTo("handbook");
		assertThat(created.status()).isEqualTo(ProjectStatus.ACTIVE);
	}

	@Test
	void refusesToCreateAProjectForAnOrganizationThatDoesNotExist() {
		when(this.organizationService.require(this.organizationId))
			.thenThrow(ResourceNotFoundException.of(ErrorCode.ORGANIZATION_NOT_FOUND, this.organizationId));

		assertThatThrownBy(
				() -> this.service.create(this.organizationId, new CreateProjectRequest("HR", null, null)))
			.isInstanceOf(ResourceNotFoundException.class);

		verify(this.repository, never()).save(any());
	}

	@Test
	void refusesADuplicateSlugInsideTheSameOrganization() {
		givenOrganizationExists();
		when(this.repository.existsByOrganizationIdAndSlug(this.organizationId, "hr")).thenReturn(true);

		assertThatThrownBy(() -> this.service.create(this.organizationId, new CreateProjectRequest("HR", "hr", null)))
			.isInstanceOf(ConflictException.class)
			.satisfies(ex -> assertThat(((ConflictException) ex).code()).isEqualTo(ErrorCode.PROJECT_ALREADY_EXISTS));

		verify(this.repository, never()).save(any());
	}

	@Test
	void scopesLookupsByOrganizationSoAnotherTenantsProjectLooksMissing() {
		UUID projectId = UUID.randomUUID();
		when(this.repository.findByIdAndOrganizationId(projectId, this.organizationId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.get(this.organizationId, projectId))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessageContaining(projectId.toString());
	}

	@Test
	void returnsAProjectOfTheOrganization() {
		UUID projectId = UUID.randomUUID();
		when(this.repository.findByIdAndOrganizationId(projectId, this.organizationId))
			.thenReturn(Optional.of(project(projectId)));

		assertThat(this.service.get(this.organizationId, projectId).id()).isEqualTo(projectId);
	}

	@Test
	void listsTheProjectsOfAnOrganization() {
		Pageable pageable = PageRequest.of(0, 20);
		givenOrganizationExists();
		when(this.repository.findByOrganizationId(this.organizationId, pageable))
			.thenReturn(new PageImpl<>(List.of(project(UUID.randomUUID())), pageable, 1));

		PageResponse<ProjectResponse> page = this.service.list(this.organizationId, pageable);

		assertThat(page.content()).singleElement().satisfies(project -> assertThat(project.slug()).isEqualTo("hr"));
	}

	@Test
	void updatesOnlyTheFieldsThatWereSent() {
		UUID projectId = UUID.randomUUID();
		Project project = project(projectId);
		when(this.repository.findByIdAndOrganizationId(projectId, this.organizationId))
			.thenReturn(Optional.of(project));

		ProjectResponse updated = this.service.update(this.organizationId, projectId,
				new UpdateProjectRequest("People Ops", null));

		assertThat(updated.name()).isEqualTo("People Ops");
		assertThat(updated.description()).isEqualTo("original description");
	}

	@Test
	void updatesTheDescription() {
		UUID projectId = UUID.randomUUID();
		Project project = project(projectId);
		when(this.repository.findByIdAndOrganizationId(projectId, this.organizationId))
			.thenReturn(Optional.of(project));

		ProjectResponse updated = this.service.update(this.organizationId, projectId,
				new UpdateProjectRequest(null, "all about people"));

		assertThat(updated.name()).isEqualTo("HR");
		assertThat(updated.description()).isEqualTo("all about people");
	}

	@Test
	void archivesAndReactivatesAProject() {
		UUID projectId = UUID.randomUUID();
		Project project = project(projectId);
		when(this.repository.findByIdAndOrganizationId(projectId, this.organizationId))
			.thenReturn(Optional.of(project));

		assertThat(this.service.archive(this.organizationId, projectId).status()).isEqualTo(ProjectStatus.ARCHIVED);
		assertThat(this.service.activate(this.organizationId, projectId).status()).isEqualTo(ProjectStatus.ACTIVE);
	}

	@Test
	void deletesAProject() {
		UUID projectId = UUID.randomUUID();
		Project project = project(projectId);
		when(this.repository.findByIdAndOrganizationId(projectId, this.organizationId))
			.thenReturn(Optional.of(project));

		this.service.delete(this.organizationId, projectId);

		verify(this.repository).delete(project);
	}

	@Test
	void countsTheProjectsOfAnOrganization() {
		when(this.repository.countByOrganizationId(this.organizationId)).thenReturn(3L);

		assertThat(this.service.count(this.organizationId)).isEqualTo(3L);
	}

	private void givenOrganizationExists() {
		when(this.organizationService.require(this.organizationId))
			.thenReturn(Organization.of(this.organizationId, "Acme", "acme",
					com.panwar2001.orgagent.features.organization.OrganizationStatus.ACTIVE));
	}

	private Project project(UUID id) {
		return Project.of(id, this.organizationId, "HR", "hr", "original description", ProjectStatus.ACTIVE);
	}

}
