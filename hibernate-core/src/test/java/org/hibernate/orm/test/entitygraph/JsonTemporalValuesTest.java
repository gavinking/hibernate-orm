package org.hibernate.orm.test.entitygraph;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.FetchMethod;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.assertj.core.api.Assertions.assertThat;

@DomainModel(annotatedClasses = JsonTemporalValuesTest.Payload.class)
@SessionFactory
@RequiresDialect(H2Dialect.class)
@ResourceLock(Resources.TIME_ZONE)
class JsonTemporalValuesTest {
	@Test
	void existingJsonAndMemberAccess(SessionFactoryScope scope) {
		final var previous = TimeZone.getDefault();
		TimeZone.setDefault( TimeZone.getTimeZone( "Europe/Madrid" ) );
		try {
			final var time = LocalTime.parse( "12:34:56.789" );
			final var stamp = LocalDateTime.parse( "2024-05-17T10:15:30.123456" );
			scope.inTransaction( session -> {
				session.createNativeMutationQuery( """
						insert into Payload(id,data) values
						(1, JSON '{"tod":"12:34:56.789","stamp":"2024-05-17T08:15:30.123456"}')
						""" ).executeUpdate();
				final var payload = new Payload();
				payload.id = 2;
				payload.data = new Stamp();
				payload.data.tod = time;
				payload.data.stamp = stamp;
				session.persist( payload );
			} );
			scope.inTransaction( session -> {
				final var existing = session.find( Payload.class, 1L ).data;
				assertThat( existing.tod ).isEqualTo( time );
				assertThat( existing.stamp ).isEqualTo( stamp );
				assertThat( session.find( Payload.class, 2L ).data.tod ).isEqualTo( time );
				assertThat( session.createSelectionQuery(
						"select p.data.stamp from Payload p where p.id = 2", LocalDateTime.class )
						.getSingleResult() ).isEqualTo( stamp );
				session.createMutationQuery( "update Payload p set p.data.stamp = :stamp where p.id = 2" )
						.setParameter( "stamp", stamp.plusDays( 1 ) ).executeUpdate();
			} );
			scope.inTransaction( session -> assertThat( session.find( Payload.class, 2L ).data.stamp )
					.isEqualTo( stamp.plusDays( 1 ) ) );
		}
		finally {
			TimeZone.setDefault( previous );
		}
	}

	@Test
	void nestedTimestampInJvmDaylightSavingGap(SessionFactoryScope scope) {
		final var previous = TimeZone.getDefault();
		TimeZone.setDefault( TimeZone.getTimeZone( "Europe/Madrid" ) );
		try {
			final var value = Timestamp.from( Instant.parse( "2024-03-31T02:30:00.123456Z" ) );
			final var clock = LocalTime.of( 12, 34, 56, 789_000_000 );
			final var sessions = scope.getSessionFactory().withOptions().jdbcTimeZone( TimeZone.getTimeZone( "UTC" ) );
			try ( var session = sessions.openSession() ) {
				session.inTransaction( transaction -> {
					final var payload = new Payload();
					payload.id = 3;
					payload.times.add( value );
					payload.clocks.add( clock );
					session.persist( payload );
				} );
			}
			for ( var method : List.of( FetchMethod.BY_ID, FetchMethod.NESTED ) ) {
				try ( var session = sessions.openSession() ) {
					final var graph = session.createEntityGraph( Payload.class );
					graph.addAttributeNode( "times" ).addOption( method );
					graph.addAttributeNode( "clocks" ).addOption( method );
					final var payload = session.find( graph, 3L );
					assertThat( payload.times ).containsExactly( value );
					assertThat( payload.clocks ).containsExactly( clock );
				}
			}
		}
		finally {
			TimeZone.setDefault( previous );
		}
	}

	@Entity(name = "Payload")
	static class Payload {
		@Id long id;
		@JdbcTypeCode(SqlTypes.JSON) Stamp data;
		@ElementCollection List<Timestamp> times = new ArrayList<>();
		@ElementCollection @JdbcTypeCode(SqlTypes.TIME)
		@Column(columnDefinition = "time(3)") List<LocalTime> clocks = new ArrayList<>();
	}

	@Embeddable
	static class Stamp {
		@JdbcTypeCode(SqlTypes.TIME) LocalTime tod;
		@JdbcTypeCode(SqlTypes.TIMESTAMP) LocalDateTime stamp;
	}
}
