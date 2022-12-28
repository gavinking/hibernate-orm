/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.query;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import org.hibernate.FlushMode;
import org.hibernate.ForcedFlushMode;
import org.hibernate.Session;
import org.hibernate.annotations.NamedNativeQuery;
import org.hibernate.annotations.NamedQuery;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Yoann Rodiere
 */
@TestForIssue(jiraKey = "HHH-12795")
@Jpa(annotatedClasses = {
		NamedQueryForcedFlushModeTest.TestEntity.class
})
public class NamedQueryForcedFlushModeTest {

	@Test
	public void testNamedQueryWithFlushModeManual(EntityManagerFactoryScope scope) {
		String queryName = "NamedQueryFlushModeManual";
		scope.inEntityManager(
				entityManager -> {
					Session s = entityManager.unwrap( Session.class );
					Query<?> query = s.getNamedQuery( queryName );
					assertEquals( FlushMode.MANUAL, query.getHibernateFlushMode() );
					// JPA flush mode is an approximation
					assertEquals( jakarta.persistence.FlushModeType.COMMIT, query.getFlushMode() );
				}
		);
	}

	@Test
	public void testNamedQueryWithFlushModeCommit(EntityManagerFactoryScope scope) {
		String queryName = "NamedQueryFlushModeCommit";
		scope.inEntityManager(
				entityManager -> {
					Session s = entityManager.unwrap( Session.class );
					Query<?> query = s.getNamedQuery( queryName );
					assertEquals( FlushMode.MANUAL, query.getHibernateFlushMode() );
					assertEquals( jakarta.persistence.FlushModeType.COMMIT, query.getFlushMode() );
				}
		);
	}

	@Test
	public void testNamedQueryWithFlushModeAuto(EntityManagerFactoryScope scope) {
		String queryName = "NamedQueryFlushModeAuto";
		scope.inEntityManager(
				entityManager -> {
					Session s = entityManager.unwrap( Session.class );
					Query<?> query = s.getNamedQuery( queryName );
					assertEquals( FlushMode.AUTO, query.getHibernateFlushMode() );
					assertEquals( jakarta.persistence.FlushModeType.AUTO, query.getFlushMode() );
				}
		);
	}

	@Test
	public void testNamedQueryWithFlushModeAlways(EntityManagerFactoryScope scope) {
		String queryName = "NamedQueryFlushModeAlways";
		scope.inEntityManager(
				entityManager -> {
					Session s = entityManager.unwrap( Session.class );
					Query<?> query = s.getNamedQuery( queryName );
					assertEquals( FlushMode.ALWAYS, query.getHibernateFlushMode() );
					// JPA flush mode is an approximation
					assertEquals( jakarta.persistence.FlushModeType.AUTO, query.getFlushMode() );
				}
		);
	}

	@Test
	public void testNamedQueryWithFlushModePersistenceContext(EntityManagerFactoryScope scope) {
		String queryName = "NamedQueryFlushModePersistenceContext";
		scope.inEntityManager(
				entityManager -> {
					Session s = entityManager.unwrap( Session.class );
					Query<?> query;

					// A null Hibernate flush mode means we will use whatever mode is set on the session
					// JPA doesn't allow null flush modes, so we expect some approximation of the flush mode to be returned

					s.setHibernateFlushMode( FlushMode.MANUAL );
					query = s.getNamedQuery( queryName );
					assertEquals( FlushMode.MANUAL, query.getHibernateFlushMode() );
					assertEquals( jakarta.persistence.FlushModeType.COMMIT, query.getFlushMode() );
					assertEquals( ForcedFlushMode.NO_FORCING, query.getForcedFlushMode() );

					s.setHibernateFlushMode( FlushMode.COMMIT );
					query = s.getNamedQuery( queryName );
					assertEquals( FlushMode.COMMIT, query.getHibernateFlushMode() );
					assertEquals( jakarta.persistence.FlushModeType.COMMIT, query.getFlushMode() );
					assertEquals( ForcedFlushMode.NO_FORCING, query.getForcedFlushMode() );

					s.setHibernateFlushMode( FlushMode.AUTO );
					query = s.getNamedQuery( queryName );
					assertEquals( FlushMode.AUTO, query.getHibernateFlushMode() );
					assertEquals( jakarta.persistence.FlushModeType.AUTO, query.getFlushMode() );
					assertEquals( ForcedFlushMode.NO_FORCING, query.getForcedFlushMode() );

					s.setHibernateFlushMode( FlushMode.ALWAYS );
					query = s.getNamedQuery( queryName );
					assertEquals( FlushMode.ALWAYS, query.getHibernateFlushMode() );
					assertEquals( jakarta.persistence.FlushModeType.AUTO, query.getFlushMode() );
					assertEquals( ForcedFlushMode.NO_FORCING, query.getForcedFlushMode() );
				}
		);
	}

	@Test
	public void testNamedNativeQueryWithFlushModeManual(EntityManagerFactoryScope scope) {
		String queryName = "NamedNativeQueryFlushModeManual";
		scope.inEntityManager(
				entityManager -> {
					Session s = entityManager.unwrap( Session.class );
					NativeQuery<?> query = s.getNamedNativeQuery( queryName );
					assertEquals( FlushMode.MANUAL, query.getHibernateFlushMode() );
					assertEquals( ForcedFlushMode.FORCE_NO_FLUSH, query.getForcedFlushMode() );
				}
		);
	}

	@Test
	public void testNamedNativeQueryWithFlushModeCommit(EntityManagerFactoryScope scope) {
		String queryName = "NamedNativeQueryFlushModeCommit";
		scope.inEntityManager(
				entityManager -> {
					Session s = entityManager.unwrap( Session.class );
					NativeQuery<?> query = s.getNamedNativeQuery( queryName );
					assertEquals( FlushMode.MANUAL, query.getHibernateFlushMode() );
					assertEquals( ForcedFlushMode.FORCE_NO_FLUSH, query.getForcedFlushMode() );
				}
		);
	}

	@Test
	public void testNamedNativeQueryWithFlushModeAuto(EntityManagerFactoryScope scope) {
		String queryName = "NamedNativeQueryFlushModeAuto";
		scope.inEntityManager(
				entityManager -> {
					Session s = entityManager.unwrap( Session.class );
					NativeQuery<?> query = s.getNamedNativeQuery( queryName );
					assertEquals( FlushMode.AUTO, query.getHibernateFlushMode() );
					assertEquals( ForcedFlushMode.NO_FORCING, query.getForcedFlushMode() );
				}
		);
	}

	@Test
	public void testNamedNativeQueryWithFlushModeAlways(EntityManagerFactoryScope scope) {
		String queryName = "NamedNativeQueryFlushModeAlways";
		scope.inEntityManager(
				entityManager -> {
					Session s = entityManager.unwrap( Session.class );
					NativeQuery<?> query = s.getNamedNativeQuery( queryName );
					assertEquals( FlushMode.ALWAYS, query.getHibernateFlushMode() );
					assertEquals( ForcedFlushMode.FORCE_FLUSH, query.getForcedFlushMode() );
				}
		);
	}

	@Test
	public void testNamedNativeQueryWithFlushModePersistenceContext(EntityManagerFactoryScope scope) {
		String queryName = "NamedNativeQueryFlushModePersistenceContext";
		scope.inEntityManager(
				entityManager -> {
					Session s = entityManager.unwrap( Session.class );
					NativeQuery<?> query;

					// A null Hibernate flush mode means we will use whatever mode is set on the session
					// JPA doesn't allow null flush modes, so we expect some approximation of the flush mode to be returned

					s.setHibernateFlushMode( FlushMode.MANUAL );
					query = s.getNamedNativeQuery( queryName );
					assertEquals( FlushMode.MANUAL, query.getHibernateFlushMode() );
					assertEquals( jakarta.persistence.FlushModeType.COMMIT, query.getFlushMode() );

					s.setHibernateFlushMode( FlushMode.COMMIT );
					query = s.getNamedNativeQuery( queryName );
					assertEquals( FlushMode.COMMIT, query.getHibernateFlushMode() );
					assertEquals( jakarta.persistence.FlushModeType.COMMIT, query.getFlushMode() );

					s.setHibernateFlushMode( FlushMode.AUTO );
					query = s.getNamedNativeQuery( queryName );
					assertEquals( FlushMode.AUTO, query.getHibernateFlushMode() );
					assertEquals( jakarta.persistence.FlushModeType.AUTO, query.getFlushMode() );

					s.setHibernateFlushMode( FlushMode.ALWAYS );
					query = s.getNamedNativeQuery( queryName );
					assertEquals( FlushMode.ALWAYS, query.getHibernateFlushMode() );
					assertEquals( jakarta.persistence.FlushModeType.AUTO, query.getFlushMode() );
				}
		);
	}

	@Entity(name = "TestEntity")
	@NamedQuery(
			name = "NamedQueryFlushModeManual",
			query = "select e from TestEntity e where e.text = :text",
			flush = ForcedFlushMode.FORCE_NO_FLUSH
	)
	@NamedQuery(
			name = "NamedQueryFlushModeCommit",
			query = "select e from TestEntity e where e.text = :text",
			flush = ForcedFlushMode.FORCE_NO_FLUSH
	)
	@NamedQuery(
			name = "NamedQueryFlushModeAuto",
			query = "select e from TestEntity e where e.text = :text",
			flush = ForcedFlushMode.NO_FORCING
	)
	@NamedQuery(
			name = "NamedQueryFlushModeAlways",
			query = "select e from TestEntity e where e.text = :text",
			flush = ForcedFlushMode.FORCE_FLUSH
	)
	@NamedQuery(
			name = "NamedQueryFlushModePersistenceContext",
			query = "select e from TestEntity e where e.text = :text",
			flush = ForcedFlushMode.NO_FORCING
	)
	@NamedNativeQuery(
			name = "NamedNativeQueryFlushModeManual",
			query = "select * from TestEntity e where e.text = :text",
			resultClass = TestEntity.class,
			flush = ForcedFlushMode.FORCE_NO_FLUSH
	)
	@NamedNativeQuery(
			name = "NamedNativeQueryFlushModeCommit",
			query = "select * from TestEntity e where e.text = :text",
			resultClass = TestEntity.class,
			flush = ForcedFlushMode.FORCE_NO_FLUSH
	)
	@NamedNativeQuery(
			name = "NamedNativeQueryFlushModeAuto",
			query = "select * from TestEntity e where e.text = :text",
			resultClass = TestEntity.class,
			flush = ForcedFlushMode.NO_FORCING
	)
	@NamedNativeQuery(
			name = "NamedNativeQueryFlushModeAlways",
			query = "select * from TestEntity e where e.text = :text",
			resultClass = TestEntity.class,
			flush = ForcedFlushMode.FORCE_FLUSH
	)
	@NamedNativeQuery(
			name = "NamedNativeQueryFlushModePersistenceContext",
			query = "select * from TestEntity e where e.text = :text",
			resultClass = TestEntity.class,
			flush = ForcedFlushMode.NO_FORCING
	)

	public static class TestEntity {
		@Id
		@GeneratedValue
		private Long id;

		private String text;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}
	}
}
