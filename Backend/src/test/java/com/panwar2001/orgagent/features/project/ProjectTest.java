package com.panwar2001.orgagent.features.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProjectTest {

	private final UUID organizationId = UUID.randomUUID();

	@Test
	void createsAnActiveProjectOwnedByItsOrganization() {
		Project project = Project.create(this.organizationId, "  HR Policies ", "hr-policies", "  internal handbook ");

		assertThat(project.getId()).isNotNull();
		assertThat(project.getOrganizationId()).isEqualTo(this.organizationId);
		assertThat(project.getName()).isEqualTo("HR Policies");
		assertThat(project.getSlug()).isEqualTo("hr-policies");
		assertThat(project.getDescription()).isEqualTo("internal handbook");
		assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
	}

	@Test
	void treatsABlankDescriptionAsNoDescription() {
		assertThat(Project.create(this.organizationId, "HR", "hr", "   ").getDescription()).isNull();
		assertThat(Project.create(this.organizationId, "HR", "hr", null).getDescription()).isNull();
	}

	@Test
	void knowsWhichOrganizationOwnsIt() {
		Project project = Project.create(this.organizationId, "HR", "hr", null);

		assertThat(project.belongsTo(this.organizationId)).isTrue();
		assertThat(project.belongsTo(UUID.randomUUID())).isFalse();
	}

	@Test
	void canBeRenamedAndRedescribed() {
		Project project = Project.create(this.organizationId, "HR", "hr", null);

		project.rename("People Ops");
		project.describe("everything about people");

		assertThat(project.getName()).isEqualTo("People Ops");
		assertThat(project.getDescription()).isEqualTo("everything about people");
	}

	@Test
	void archivesAndReactivates() {
		Project project = Project.create(this.organizationId, "HR", "hr", null);

		project.archive();
		assertThat(project.isActive()).isFalse();
		assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);

		project.activate();
		assertThat(project.isActive()).isTrue();
	}

	@Test
	void stampsTimestampsWhenItIsPersisted() {
		Project project = Project.create(this.organizationId, "HR", "hr", null);

		project.onInsert();

		assertThat(project.getCreatedAt()).isNotNull();
		assertThat(project.getUpdatedAt()).isEqualTo(project.getCreatedAt());
	}

	@Test
	void refusesToExistWithoutAnOrganization() {
		assertThatThrownBy(() -> Project.create(null, "HR", "hr", null)).isInstanceOf(NullPointerException.class);
	}

}
