/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.Jpa;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.junit.jupiter.api.Test;

@Jpa(annotatedClasses = {JoinColumnDefinitionTest.Entity1.class, JoinColumnDefinitionTest.Entity2.class})
@RequiresDialect(H2Dialect.class)
class JoinColumnDefinitionTest {

	@Test @JiraKey("HHH-19118")
	void hhh19118test(EntityManagerFactoryScope scope) {
		scope.inTransaction( entityManager -> {
			entityManager.persist(new Entity1());
			entityManager.persist(new Entity2());
		} );
	}

	@Entity
	public static class Entity1 {

		@Id
		@Column(columnDefinition = "bigint not null generated by default as identity")
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		Long id;

		@JoinColumn(columnDefinition = "bigint comment 'entity2 ID'")
		@ManyToOne
		Entity2 entity2;
	}

	@Entity
	public static class Entity2 {

		@Id
		@Column(columnDefinition = "bigint not null generated by default as identity")
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		Long id;

		String name;
	}
}
