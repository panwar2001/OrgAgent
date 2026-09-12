package com.panwar2001.orgagent.features.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class OrganizationTest {

	@Test
	void createsAnActiveOrganizationWithItsOwnIdentity() {
		Organization organization = Organization.create("  Acme Ltd.  ", "acme");

		assertThat(organization.getId()).isNotNull();
		assertThat(organization.getName()).isEqualTo("Acme Ltd.");
		assertThat(organization.getSlug()).isEqualTo("acme");
		assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
		assertThat(organization.isActive()).isTrue();
	}

	@Test
	void givesTwoOrganizationsDifferentIds() {
		assertThat(Organization.create("Acme", "acme").getId())
			.isNotEqualTo(Organization.create("Acme", "acme").getId());
	}

	@Test
	void stampsTimestampsWhenItIsPersisted() {
		Organization organization = Organization.create("Acme", "acme");

		organization.onInsert();

		assertThat(organization.getCreatedAt()).isNotNull();
		assertThat(organization.getUpdatedAt()).isEqualTo(organization.getCreatedAt());
	}

	@Test
	void refreshesOnlyTheUpdateTimestampOnLaterWrites() {
		Organization organization = Organization.create("Acme", "acme");
		organization.onInsert();
		var created = organization.getCreatedAt();

		organization.rename("Acme Holdings");
		organization.onUpdate();

		assertThat(organization.getCreatedAt()).isEqualTo(created);
		assertThat(organization.getUpdatedAt()).isAfterOrEqualTo(created);
		assertThat(organization.getName()).isEqualTo("Acme Holdings");
	}

	@Test
	void movesBetweenLifecycleStates() {
		Organization organization = Organization.create("Acme", "acme");

		organization.suspend();
		assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
		assertThat(organization.isActive()).isFalse();

		organization.activate();
		assertThat(organization.isActive()).isTrue();

		organization.archive();
		assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.ARCHIVED);
	}

	@Test
	void refusesBlankNames() {
		assertThatThrownBy(() -> Organization.create("   ", "acme")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("name");
	}

	@Test
	void rehydratesAKnownOrganization() {
		UUID id = UUID.randomUUID();

		Organization organization = Organization.of(id, "Acme", "acme", OrganizationStatus.SUSPENDED);

		assertThat(organization.getId()).isEqualTo(id);
		assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
	}

}
