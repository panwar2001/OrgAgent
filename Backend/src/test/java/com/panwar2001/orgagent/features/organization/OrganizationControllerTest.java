package com.panwar2001.orgagent.features.organization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.panwar2001.orgagent.core.exception.GlobalRestExceptionHandler;
import com.panwar2001.orgagent.core.exception.ResourceNotFoundException;
import com.panwar2001.orgagent.core.exception.ErrorCode;
import com.panwar2001.orgagent.features.organization.dto.OrganizationResponse;
import com.panwar2001.orgagent.features.organization.dto.UpdateOrganizationRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;


/**
 * Drives the controller over real HTTP semantics, including bean validation and the global exception
 * handler, with the service mocked. No Spring context, database or Redis is involved.
 */
class OrganizationControllerTest {

	private final OrganizationService service = mock(OrganizationService.class);

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(new OrganizationController(this.service))
			.setControllerAdvice(new GlobalRestExceptionHandler())
			.setValidator(validator())
			.build();
	}

	@Test
	void createsAnOrganizationAndReturns201() throws Exception {
		when(this.service.create(any())).thenReturn(response(UUID.randomUUID(), "Acme", "acme"));

		this.mockMvc
			.perform(post("/api/v1/organizations").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Acme\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Acme"))
			.andExpect(jsonPath("$.slug").value("acme"));
	}

	@Test
	void rejectsAnOrganizationWithoutANameWith400AndFieldDetails() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/organizations").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"  \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.violations[0].field").value("name"))
			.andExpect(jsonPath("$.path").value("/api/v1/organizations"));
	}

	@Test
	void rejectsAMalformedBodyWith400() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/organizations").contentType(MediaType.APPLICATION_JSON).content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void rejectsANonSlugSlugWith400() throws Exception {
		this.mockMvc
			.perform(post("/api/v1/organizations").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Acme\",\"slug\":\"Not A Slug\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.violations[0].field").value("slug"));
	}

	@Test
	void readsOneOrganization() throws Exception {
		UUID id = UUID.randomUUID();
		when(this.service.get(id)).thenReturn(response(id, "Acme", "acme"));

		this.mockMvc.perform(get("/api/v1/organizations/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id.toString()));
	}

	@Test
	void turnsAnUnknownOrganizationInto404() throws Exception {
		UUID id = UUID.randomUUID();
		when(this.service.get(id))
			.thenThrow(ResourceNotFoundException.of(ErrorCode.ORGANIZATION_NOT_FOUND, id));

		this.mockMvc.perform(get("/api/v1/organizations/{id}", id))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));
	}

	@Test
	void listsOrganizationsWithPageMetadata() throws Exception {
		when(this.service.list(any(Pageable.class))).thenReturn(
				com.panwar2001.orgagent.core.web.PageResponse.from(
						new PageImpl<>(List.of(response(UUID.randomUUID(), "Acme", "acme"))), value -> value));

		this.mockMvc.perform(get("/api/v1/organizations"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].slug").value("acme"))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.page").value(0));
	}

	@Test
	void rejectsAPageSizeBeyondTheMaximum() throws Exception {
		this.mockMvc.perform(get("/api/v1/organizations").param("size", "5000"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("size")));
	}

	@Test
	void rejectsANegativePageIndex() throws Exception {
		this.mockMvc.perform(get("/api/v1/organizations").param("page", "-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void renamesAnOrganization() throws Exception {
		UUID id = UUID.randomUUID();
		when(this.service.rename(eq(id), any(UpdateOrganizationRequest.class)))
			.thenReturn(response(id, "Acme Holdings", "acme"));

		this.mockMvc
			.perform(patch("/api/v1/organizations/{id}", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Acme Holdings\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Acme Holdings"));
	}

	@Test
	void rejectsARenameWithoutAName() throws Exception {
		UUID id = UUID.randomUUID();

		this.mockMvc
			.perform(patch("/api/v1/organizations/{id}", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.violations[0].field").value("name"));
	}

	@Test
	void deletesAnOrganizationAndReturns204() throws Exception {
		UUID id = UUID.randomUUID();

		this.mockMvc.perform(delete("/api/v1/organizations/{id}", id)).andExpect(status().isNoContent());

		verify(this.service).delete(id);
	}

	@Test
	void reportsAnUnsupportedMethodWith405() throws Exception {
		this.mockMvc.perform(put("/api/v1/organizations/{id}", UUID.randomUUID()))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	private static org.springframework.validation.Validator validator() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		return validator;
	}

	private OrganizationResponse response(UUID id, String name, String slug) {
		return new OrganizationResponse(id, name, slug, OrganizationStatus.ACTIVE, Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"));
	}

}
