package org.hibernate.temporal;

import jakarta.persistence.Entity;
import jakarta.persistence.ExcludedFromVersioning;
import jakarta.persistence.Id;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Temporal;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hibernate.cfg.FlushSettings.FLUSH_QUEUE_TYPE;
import static org.hibernate.cfg.StateManagementSettings.CHANGESET_ID_SUPPLIER;
import static org.hibernate.cfg.StateManagementSettings.TEMPORAL_TABLE_STRATEGY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@JiraKey("HHH-21285")
@SessionFactory(useCollectingStatementObserver = true)
@DomainModel(annotatedClasses = { TemporalVersionHistoryTest.Item.class, TemporalVersionHistoryTest.DynamicItem.class })
@ServiceRegistry(settings = {
		@Setting(name = TEMPORAL_TABLE_STRATEGY, value = "HISTORY_TABLE"),
		@Setting(name = FLUSH_QUEUE_TYPE, value = "graph"),
		@Setting(name = CHANGESET_ID_SUPPLIER, value = TemporalGeneratedPropertyHistoryTest.SUPPLIER)
})
class TemporalVersionHistoryTest {
	@Entity(name = "VersionHistoryItem")
	@Table(name = "version_item")
	@Temporal
	static class Item {
		@Id int id = 1;
		@Version Integer version;
		String name = "first";
		@ExcludedFromVersioning int counter;
		@Generated @ColumnDefault("42") Integer token;
	}

	@Entity(name = "DynamicVersionHistoryItem")
	@DynamicUpdate
	@Temporal
	static class DynamicItem {
		@Id int id = 1;
		@Version Integer version;
		String name;
		@ExcludedFromVersioning int counter;
		@Generated @ColumnDefault("42") Integer token;
	}

	@Test
	void emulatedUpsertUsesTheInsertDefaultAndTheUpdateVersion(SessionFactoryScope scope) {
		var first = new DynamicItem();
		first.name = "first";
		scope.inStatelessTransaction( session -> session.upsert( first ) );
		var loaded = scope.fromSession( session -> session.find( DynamicItem.class, 1 ) );
		assertEquals( 42, loaded.token );
		loaded.counter++;
		scope.inStatelessTransaction( session -> session.upsert( loaded ) );
		assertEquals( 0, loaded.version );
		loaded.token = 23;
		scope.inStatelessTransaction( session -> session.upsert( loaded ) );
		assertEquals( 1, loaded.version );
		scope.inSession( session -> assertEquals( 23, session.find( DynamicItem.class, 1 ).token ) );
	}

	@AfterEach
	void cleanUp(SessionFactoryScope scope) {
		scope.dropData();
	}

	@Test
	void upsertInsertsThenChecksTheStoredVersion(SessionFactoryScope scope) {
		var item = new Item();
		var factory = scope.getSessionFactory();
		var observer = scope.getCollectingStatementObserver();
		var first = factory.fromStatelessTransaction( session -> {
			observer.clear();
			session.upsert( item );
			if ( factory.getJdbcServices().getDialect() instanceof H2Dialect ) {
				observer.assertQueries().hasSize( 3 );
				observer.assertQuery( 0 ).containsIgnoringCase( "final table (merge into" );
			}
			assertEquals( 0, item.version );
			return session.unwrap( SharedSessionContractImplementor.class ).getCurrentChangesetIdentifier();
		} );
		var detached = scope.fromSession( session -> session.find( Item.class, 1 ) );
		assertEquals( 42, detached.token );
		var second = factory.fromStatelessTransaction( session -> {
			detached.counter++;
			session.upsert( detached );
			assertEquals( 0, detached.version );
			detached.counter++;
			session.upsert( detached );
			assertEquals( 0, detached.version );
			return session.unwrap( SharedSessionContractImplementor.class ).getCurrentChangesetIdentifier();
		} );
		var third = factory.fromStatelessTransaction( session -> {
			detached.name = "next";
			session.upsert( detached );
			assertEquals( 1, detached.version );
			return session.unwrap( SharedSessionContractImplementor.class ).getCurrentChangesetIdentifier();
		} );
		assertThrows( OptimisticLockException.class, () -> factory.inStatelessTransaction( session -> session.upsert( item ) ) );
		assertHistory( scope, new Object[]{ first, second, 0, 0, "first", 42 },
				new Object[]{ second, third, 0, 2, "first", 42 }, new Object[]{ third, null, 1, 2, "next", 42 } );
	}

	@Test
	void upsertCanImportAMissingRowWithAnAssignedVersion(SessionFactoryScope scope) {
		var item = new Item();
		item.version = 7;
		scope.inStatelessTransaction( session -> session.upsert( item ) );
		assertEquals( 8, item.version );
		scope.inSession( session -> assertEquals( 8, session.find( Item.class, 1 ).version ) );
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void updateCanCreateTwoRevisionsWithTheSameVersion(boolean stateless, SessionFactoryScope scope) {
		var item = new Item();
		var first = scope.fromTransaction( session -> {
			session.persist( item );
			return session.getCurrentChangesetIdentifier();
		} );
		var second = stateless ? scope.getSessionFactory().fromStatelessTransaction( session -> {
			item.counter++;
			session.update( item );
			assertEquals( 0, item.version );
			return session.unwrap( SharedSessionContractImplementor.class ).getCurrentChangesetIdentifier();
		} ) : scope.fromTransaction( session -> {
			session.find( Item.class, 1 ).counter++;
			return session.getCurrentChangesetIdentifier();
		} );
		assertHistory( scope, new Object[]{ first, second, 0, 0, "first", 42 },
				new Object[]{ second, null, 0, 1, "first", 42 } );
	}

	private static void assertHistory(SessionFactoryScope scope, Object[]... expected) {
		var rows = scope.fromSession( session -> session.createNativeQuery(
				"select effective,superseded,version,counter,name,token from version_item_history order by effective",
				Object[].class ).getResultList() );
		assertEquals( expected.length, rows.size() );
		for ( int i = 0; i < rows.size(); i++ ) {
			assertArrayEquals( expected[i], rows.get( i ) );
			try ( var at = scope.getSessionFactory().withOptions().atChangeset( expected[i][0] ).openSession() ) {
				var item = at.find( Item.class, 1 );
				assertEquals( expected[i][2], item.version );
				assertEquals( expected[i][3], item.counter );
			}
		}
	}
}
