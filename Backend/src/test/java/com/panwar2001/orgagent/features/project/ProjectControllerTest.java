package com.panwar2001.orgagent.features.project;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.core.exception.GlobalRestExceptionHandler;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.web.PageResponse;
import com.panwar2001.orgagent.features.project.dto.CreateProjectRequest;
import com.panwar2001.orgagent.features.project.dto.ProjectResponse;
import com.panwar2001.orgagent.features.project.dto.UpdateProjectRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ProjectControllerTest {

	private final ProjectService service = mock(ProjectService.class);

	private final UUID organizationId = UUID.randomUUID();

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(new ProjectController(this.service))
			.setControllerAdvice(new GlobalRestExceptionHandler())
			.setValidator(validator())
			.build();
	}

	@Test
	void createsAProjectUnderItsOrganization() throws Exception {
		when(this.service.create(eq(this.organizationId), any(CreateProjectRequest.class)))
			.thenReturn(response(UUID.randomUUID(), "HR Policies", "hr-policies"));

		this.mockMvc
			.perform(post("/api/v1/organizations/{organizationId}/projects", this.organizationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"HR Policies\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value("hr-policies"))
			.andExpect(jsonPath("$.organizationId").value(this.organizationId.toString()));
	}

	@Test
	void rejectsAProjectWithoutAName() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/organizations/{organizationId}/projects", this.organizationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.violations[0].field").value("name"));
	}

	@Test
	void rejectsANameThatIsTooLong() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/organizations/{organizationId}/projects", this.organizationId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"%s\"}".formatted("x".repeat(201))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.violations[0].field").value("name"));
	}

	@Test
	void listsTheProjectsOfAnOrganization() throws Exception {
		when(this.service.list(eq(this.organizationId), any(Pageable.class))).thenReturn(
				PageResponse.from(new PageImpl<>(List.of(response(UUID.randomUUID(), "HR", "hr"))), value -> value));

		this.mockMvc.perform(get("/api/v1/organizations/{organizationId}/projects", this.organizationId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].slug").value("hr"));
	}

	@Test
	void readsOneProject() throws Exception {
		UUID projectId = UUID.randomUUID();
		when(this.service.get(this.organizationId, projectId)).thenReturn(response(projectId, "HR", "hr"));

		this.mockMvc.perform(get("/api/v1/organizations/{organizationId}/projects/{projectId}", this.organizationId,
				projectId)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(projectId.toString()));
	}

	@Test
	void turnsAnUnknownProjectInto404() throws Exception {
		UUID projectId = UUID.randomUUID();
		when(this.service.get(this.organizationId, projectId))
			.thenThrow(ResourceNotFoundException.of(ErrorCode.PROJECT_NOT_FOUND, projectId));

		this.mockMvc
			.perform(get("/api/v1/organizations/{organizationId}/projects/{projectId}", this.organizationId, projectId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
	}

	@Test
	void updatesAProject() throws Exception {
		UUID projectId = UUID.randomUUID();
		when(this.service.update(eq(this.organizationId), eq(projectId), any(UpdateProjectRequest.class)))
			.thenReturn(response(projectId, "People Ops", "hr"));

		this.mockMvc
			.perform(patch("/api/v1/organizations/{organizationId}/projects/{projectId}", this.organizationId,
					projectId).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"People Ops\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("People Ops"));
	}

	@Test
	void acceptsAnEmptyPatchBecauseEveryFieldIsOptional() throws Exception {
		UUID projectId = UUID.randomUUID();
		when(this.service.update(eq(this.organizationId), eq(projectId), any(UpdateProjectRequest.class)))
			.thenReturn(response(projectId, "HR", "hr"));

		this.mockMvc
			.perform(patch("/api/v1/organizations/{organizationId}/projects/{projectId}", this.organizationId,
					projectId).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
	}

	@Test
	void archivesAProject() throws Exception {
		UUID projectId = UUID.randomUUID();
		when(this.service.archive(this.organizationId, projectId))
			.thenReturn(new ProjectResponse(projectId, this.organizationId, "HR", "hr", null, ProjectStatus.ARCHIVED,
					Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")));

		this.mockMvc
			.perform(post("/api/v1/organizations/{organizationId}/projects/{projectId}/archive", this.organizationId,
					projectId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"));
	}

	@Test
	void deletesAProject() throws Exception {
		UUID projectId = UUID.randomUUID();

		this.mockMvc
			.perform(delete("/api/v1/organizations/{organizationId}/projects/{projectId}", this.organizationId,
					projectId))
			.andExpect(status().isNoContent());

		verify(this.service).delete(this.organizationId, projectId);
	}

	private static org.springframework.validation.Validator validator() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		return validator;
	}

	private ProjectResponse response(UUID id, String name, String slug) {
		return new ProjectResponse(id, this.organizationId, name, slug, null, ProjectStatus.ACTIVE,
				Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
	}

}
