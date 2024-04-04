/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.orm.test.annotations.xml.ejb3;

import java.util.List;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.boot.internal.Target;
import org.hibernate.models.spi.AnnotationUsage;
import org.hibernate.models.spi.ClassDetails;
import org.hibernate.models.spi.MemberDetails;

import org.hibernate.testing.orm.junit.JiraKey;
import org.junit.Test;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.JoinTable;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.PrimaryKeyJoinColumns;
import jakarta.persistence.UniqueConstraint;

import static org.assertj.core.api.Assertions.assertThat;

@JiraKey("HHH-14529")
public class Ejb3XmlOneToOneTest extends Ejb3XmlTestCase {
	@Test
	public void testNoChildren() {
		final MemberDetails memberDetails = getAttributeMember( Entity1.class, "field1", "one-to-one.orm1.xml" );
		assertThat( memberDetails.hasAnnotationUsage( OneToOne.class ) ).isTrue();

		assertThat( memberDetails.hasAnnotationUsage( MapsId.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( Id.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumn.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumns.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumns.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumn.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinTable.class ) ).isFalse();

		final AnnotationUsage<OneToOne> oneToOneUsage = memberDetails.getAnnotationUsage( OneToOne.class );
		assertThat( oneToOneUsage.getList( "cascade" ) ).isEmpty();
		assertThat( oneToOneUsage.getEnum( "fetch", FetchType.class ) ).isEqualTo( FetchType.EAGER );
		assertThat( oneToOneUsage.getString( "mappedBy" ) ).isEmpty();
		assertThat( oneToOneUsage.getBoolean( "optional" ) ).isTrue();
		assertThat( oneToOneUsage.getBoolean( "orphanRemoval" ) ).isFalse();
		assertThat( oneToOneUsage.getClassDetails( "targetEntity" ) ).isEqualTo( ClassDetails.VOID_CLASS_DETAILS );
	}

	/**
	 * When there's a single primary key join column, we still wrap it with
	 * a PrimaryKeyJoinColumns annotation.
	 */
	@Test
	public void testSinglePrimaryKeyJoinColumn() {
		final MemberDetails memberDetails = getAttributeMember( Entity1.class, "field1", "one-to-one.orm2.xml" );
		assertThat( memberDetails.hasAnnotationUsage( OneToOne.class ) ).isTrue();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumns.class ) ).isTrue();

		assertThat( memberDetails.hasAnnotationUsage( MapsId.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( Id.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumn.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumns.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumn.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinTable.class ) ).isFalse();

		final AnnotationUsage<PrimaryKeyJoinColumns> pkJoinColumnsUsage = memberDetails.getAnnotationUsage( PrimaryKeyJoinColumns.class );
		final List<AnnotationUsage<PrimaryKeyJoinColumn>> pkJoinColumnUsages = pkJoinColumnsUsage.getList( "value" );
		assertThat( pkJoinColumnUsages ).hasSize( 1 );
		final AnnotationUsage<PrimaryKeyJoinColumn> pkJoinColumnUsage = pkJoinColumnUsages.get( 0 );
		assertThat( pkJoinColumnUsage.getString( "name" ) ).isEqualTo( "col1" );
		assertThat( pkJoinColumnUsage.getString( "referencedColumnName" ) ).isEqualTo( "col2" );
		assertThat( pkJoinColumnUsage.getString( "columnDefinition" ) ).isEqualTo( "int" );
	}

	@Test
	public void testMultiplePrimaryKeyJoinColumn() {
		final MemberDetails memberDetails = getAttributeMember( Entity1.class, "field1", "one-to-one.orm3.xml" );
		assertThat( memberDetails.hasAnnotationUsage( OneToOne.class ) ).isTrue();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumns.class ) ).isTrue();

		assertThat( memberDetails.hasAnnotationUsage( MapsId.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( Id.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumn.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumns.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumn.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinTable.class ) ).isFalse();

		final AnnotationUsage<PrimaryKeyJoinColumns> pkJoinColumnsUsage = memberDetails.getAnnotationUsage( PrimaryKeyJoinColumns.class );
		final List<AnnotationUsage<PrimaryKeyJoinColumn>> pkJoinColumnUsages = pkJoinColumnsUsage.getList( "value" );
		assertThat( pkJoinColumnUsages ).hasSize( 2 );

		final AnnotationUsage<PrimaryKeyJoinColumn> pkJoinColumnUsage0 = pkJoinColumnUsages.get( 0 );
		assertThat( pkJoinColumnUsage0.getString( "name" ) ).isEmpty();
		assertThat( pkJoinColumnUsage0.getString( "referencedColumnName" ) ).isEmpty();
		assertThat( pkJoinColumnUsage0.getString( "columnDefinition" ) ).isEmpty();

		final AnnotationUsage<PrimaryKeyJoinColumn> pkJoinColumnUsage1 = pkJoinColumnUsages.get( 1 );
		assertThat( pkJoinColumnUsage1.getString( "name" ) ).isEqualTo( "col1" );
		assertThat( pkJoinColumnUsage1.getString( "referencedColumnName" ) ).isEqualTo( "col2" );
		assertThat( pkJoinColumnUsage1.getString( "columnDefinition" ) ).isEqualTo( "int" );
	}

	/**
	 * When there's a single join column, we still wrap it with a JoinColumns
	 * annotation.
	 */
	@Test
	public void testSingleJoinColumn() {
		final MemberDetails memberDetails = getAttributeMember( Entity1.class, "field1", "one-to-one.orm4.xml" );
		assertThat( memberDetails.hasAnnotationUsage( OneToOne.class ) ).isTrue();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumn.class ) ).isTrue();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumns.class ) ).isFalse();

		assertThat( memberDetails.hasAnnotationUsage( MapsId.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( Id.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumns.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumn.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinTable.class ) ).isFalse();

		final AnnotationUsage<JoinColumn> joinColumnUsage = memberDetails.getAnnotationUsage( JoinColumn.class );
		assertThat( joinColumnUsage.getString( "name" ) ).isEqualTo( "col1" );
		assertThat( joinColumnUsage.getString( "referencedColumnName" ) ).isEqualTo( "col2" );
		assertThat( joinColumnUsage.getString( "table" ) ).isEqualTo( "table1" );
	}

	@Test
	public void testMultipleJoinColumns(){
		final MemberDetails memberDetails = getAttributeMember( Entity1.class, "field1", "one-to-one.orm5.xml" );
		assertThat( memberDetails.hasAnnotationUsage( OneToOne.class ) ).isTrue();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumns.class ) ).isTrue();

		assertThat( memberDetails.hasAnnotationUsage( MapsId.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( Id.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumns.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumn.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinTable.class ) ).isFalse();

		final AnnotationUsage<JoinColumns> joinColumnsUsage = memberDetails.getAnnotationUsage( JoinColumns.class );
		final List<AnnotationUsage<JoinColumn>> joinColumnUsages = joinColumnsUsage.getList( "value" );
		assertThat( joinColumnUsages ).hasSize( 2 );

		final AnnotationUsage<JoinColumn> joinColumnUsage0 = joinColumnUsages.get( 0 );
		assertThat( joinColumnUsage0.getString( "name" ) ).isEmpty();
		assertThat( joinColumnUsage0.getString( "referencedColumnName" ) ).isEmpty();
		assertThat( joinColumnUsage0.getString( "table" ) ).isEmpty();
		assertThat( joinColumnUsage0.getString( "columnDefinition" ) ).isEmpty();
		assertThat( joinColumnUsage0.getBoolean( "insertable" ) ).isTrue();
		assertThat( joinColumnUsage0.getBoolean( "updatable" ) ).isTrue();
		assertThat( joinColumnUsage0.getBoolean( "nullable" ) ).isTrue();
		assertThat( joinColumnUsage0.getBoolean( "unique" ) ).isFalse();

		final AnnotationUsage<JoinColumn> joinColumnUsage1 = joinColumnUsages.get( 1 );
		assertThat( joinColumnUsage1.getString( "name" ) ).isEqualTo( "col1" );
		assertThat( joinColumnUsage1.getString( "referencedColumnName" ) ).isEqualTo( "col2" );
		assertThat( joinColumnUsage1.getString( "table" ) ).isEqualTo( "table1" );
		assertThat( joinColumnUsage1.getString( "columnDefinition" ) ).isEqualTo( "int" );
		assertThat( joinColumnUsage1.getBoolean( "insertable" ) ).isFalse();
		assertThat( joinColumnUsage1.getBoolean( "updatable" ) ).isFalse();
		assertThat( joinColumnUsage1.getBoolean( "nullable" ) ).isFalse();
		assertThat( joinColumnUsage1.getBoolean( "unique" ) ).isTrue();
	}

	@Test
	public void testJoinTableNoChildren() {
		final MemberDetails memberDetails = getAttributeMember( Entity1.class, "field1", "one-to-one.orm6.xml" );
		assertThat( memberDetails.hasAnnotationUsage( OneToOne.class ) ).isTrue();
		assertThat( memberDetails.hasAnnotationUsage( JoinTable.class ) ).isTrue();

		assertThat( memberDetails.hasAnnotationUsage( MapsId.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( Id.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumns.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumn.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumns.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumn.class ) ).isFalse();

		final AnnotationUsage<JoinTable> joinTableUsage = memberDetails.getAnnotationUsage( JoinTable.class );
		assertThat( joinTableUsage.getString( "catalog" ) ).isEmpty();
		assertThat( joinTableUsage.getString( "schema" ) ).isEmpty();
		assertThat( joinTableUsage.getString( "name" ) ).isEmpty();
		assertThat( joinTableUsage.getList( "joinColumns" ) ).isEmpty();
		assertThat( joinTableUsage.getList( "inverseJoinColumns" ) ).isEmpty();
		assertThat( joinTableUsage.getList( "uniqueConstraints" ) ).isEmpty();
	}

	@Test
	public void testJoinTableAllChildren() {
		final MemberDetails memberDetails = getAttributeMember( Entity1.class, "field1", "one-to-one.orm7.xml" );
		assertThat( memberDetails.hasAnnotationUsage( OneToOne.class ) ).isTrue();
		assertThat( memberDetails.hasAnnotationUsage( JoinTable.class ) ).isTrue();

		assertThat( memberDetails.hasAnnotationUsage( MapsId.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( Id.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumns.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( PrimaryKeyJoinColumn.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumns.class ) ).isFalse();
		assertThat( memberDetails.hasAnnotationUsage( JoinColumn.class ) ).isFalse();

		final AnnotationUsage<JoinTable> joinTableUsage = memberDetails.getAnnotationUsage( JoinTable.class );
		assertThat( joinTableUsage.getString( "catalog" ) ).isEqualTo( "cat1" );
		assertThat( joinTableUsage.getString( "schema" ) ).isEqualTo( "schema1" );
		assertThat( joinTableUsage.getString( "name" ) ).isEqualTo( "table1" );

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// JoinColumns
		final List<AnnotationUsage<JoinColumn>> joinColumnUsages = joinTableUsage.getList( "joinColumns" );
		assertThat( joinColumnUsages ).hasSize( 2 );

		final AnnotationUsage<JoinColumn> joinColumnUsage0 = joinColumnUsages.get( 0 );
		assertThat( joinColumnUsage0.getString( "name" ) ).isEmpty();
		assertThat( joinColumnUsage0.getString( "referencedColumnName" ) ).isEmpty();
		assertThat( joinColumnUsage0.getString( "table" ) ).isEmpty();
		assertThat( joinColumnUsage0.getString( "columnDefinition" ) ).isEmpty();
		assertThat( joinColumnUsage0.getBoolean( "insertable" ) ).isTrue();
		assertThat( joinColumnUsage0.getBoolean( "updatable" ) ).isTrue();
		assertThat( joinColumnUsage0.getBoolean( "nullable" ) ).isTrue();
		assertThat( joinColumnUsage0.getBoolean( "unique" ) ).isFalse();

		final AnnotationUsage<JoinColumn> joinColumnUsage1 = joinColumnUsages.get( 1 );
		assertThat( joinColumnUsage1.getString( "name" ) ).isEqualTo( "col1" );
		assertThat( joinColumnUsage1.getString( "referencedColumnName" ) ).isEqualTo( "col2" );
		assertThat( joinColumnUsage1.getString( "table" ) ).isEqualTo( "table2" );
		assertThat( joinColumnUsage1.getString( "columnDefinition" ) ).isEqualTo( "int" );
		assertThat( joinColumnUsage1.getBoolean( "insertable" ) ).isFalse();
		assertThat( joinColumnUsage1.getBoolean( "updatable" ) ).isFalse();
		assertThat( joinColumnUsage1.getBoolean( "nullable" ) ).isFalse();
		assertThat( joinColumnUsage1.getBoolean( "unique" ) ).isTrue();

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// InverseJoinColumns
		final List<AnnotationUsage<JoinColumn>> inverseJoinColumnUsages = joinTableUsage.getList( "inverseJoinColumns" );
		assertThat( inverseJoinColumnUsages ).hasSize( 2 );

		final AnnotationUsage<JoinColumn> inverseJoinColumnUsage0 = inverseJoinColumnUsages.get( 0 );
		assertThat( inverseJoinColumnUsage0.getString( "name" ) ).isEmpty();
		assertThat( inverseJoinColumnUsage0.getString( "referencedColumnName" ) ).isEmpty();
		assertThat( inverseJoinColumnUsage0.getString( "table" ) ).isEmpty();
		assertThat( inverseJoinColumnUsage0.getString( "columnDefinition" ) ).isEmpty();
		assertThat( inverseJoinColumnUsage0.getBoolean( "insertable" ) ).isTrue();
		assertThat( inverseJoinColumnUsage0.getBoolean( "updatable" ) ).isTrue();
		assertThat( inverseJoinColumnUsage0.getBoolean( "nullable" ) ).isTrue();
		assertThat( inverseJoinColumnUsage0.getBoolean( "unique" ) ).isFalse();

		final AnnotationUsage<JoinColumn> inverseJoinColumnUsage1 = inverseJoinColumnUsages.get( 1 );
		assertThat( inverseJoinColumnUsage1.getString( "name" ) ).isEqualTo( "col3" );
		assertThat( inverseJoinColumnUsage1.getString( "referencedColumnName" ) ).isEqualTo( "col4" );
		assertThat( inverseJoinColumnUsage1.getString( "table" ) ).isEqualTo( "table3" );
		assertThat( inverseJoinColumnUsage1.getString( "columnDefinition" ) ).isEqualTo( "int" );
		assertThat( inverseJoinColumnUsage1.getBoolean( "insertable" ) ).isFalse();
		assertThat( inverseJoinColumnUsage1.getBoolean( "updatable" ) ).isFalse();
		assertThat( inverseJoinColumnUsage1.getBoolean( "nullable" ) ).isFalse();
		assertThat( inverseJoinColumnUsage1.getBoolean( "unique" ) ).isTrue();

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// UniqueConstraints
		final List<AnnotationUsage<UniqueConstraint>> uniqueConstraintUsages = joinTableUsage.getList( "uniqueConstraints" );
		assertThat( uniqueConstraintUsages ).hasSize( 2 );

		final AnnotationUsage<UniqueConstraint> uniqueConstraintUsage0 = uniqueConstraintUsages.get( 0 );
		assertThat( uniqueConstraintUsage0.getString( "name" ) ).isEmpty();
		assertThat( uniqueConstraintUsage0.getList( "columnNames" ) ).hasSize( 1 );
		assertThat( uniqueConstraintUsage0.getList( "columnNames" ) ).containsOnly( "col5" );

		final AnnotationUsage<UniqueConstraint> uniqueConstraintUsage1 = uniqueConstraintUsages.get( 1 );
		assertThat( uniqueConstraintUsage1.getString( "name" ) ).isEqualTo( "uq1" );
		assertThat( uniqueConstraintUsage1.getList( "columnNames" ) ).hasSize( 2 );
		assertThat( uniqueConstraintUsage1.getList( "columnNames" ) ).containsOnly( "col6", "col7" );
	}

	@Test
	public void testCascadeAll() {
		final MemberDetails memberDetails = getAttributeMember( Entity1.class, "field1", "one-to-one.orm8.xml" );
		assertThat( memberDetails.hasAnnotationUsage( OneToOne.class ) ).isTrue();
		final AnnotationUsage<OneToOne> oneToOneUsage = memberDetails.getAnnotationUsage( OneToOne.class );
		assertThat( oneToOneUsage.getList( "cascade" ) ).isEmpty();

		final AnnotationUsage<Cascade> cascadeUsage = memberDetails.getAnnotationUsage( Cascade.class );
		assertThat( cascadeUsage.getList( "value" ) ).containsOnly( CascadeType.ALL );
	}

	@Test
	public void testCascadeSomeWithDefaultPersist() {
		final MemberDetails memberDetails = getAttributeMember( Entity1.class, "field1", "one-to-one.orm9.xml" );
		assertThat( memberDetails.hasAnnotationUsage( OneToOne.class ) ).isTrue();
		final AnnotationUsage<OneToOne> oneToOneUsage = memberDetails.getAnnotationUsage( OneToOne.class );
		assertThat( oneToOneUsage.getList( "cascade" ) ).isEmpty();

		final AnnotationUsage<Cascade> cascadeUsage = memberDetails.getAnnotationUsage( Cascade.class );
		assertThat( cascadeUsage.getList( "value" ) ).containsOnly( CascadeType.REMOVE, CascadeType.REFRESH, CascadeType.DETACH, CascadeType.PERSIST );
	}

	/**
	 * Make sure that it doesn't break the handler when {@link CascadeType#ALL}
	 * is specified in addition to a default cascade-persist or individual
	 * cascade settings.
	 */
	@Test
	public void testCascadeAllPlusMore() {
		final MemberDetails memberDetails = getAttributeMember( Entity1.class, "field1", "one-to-one.orm10.xml" );
		assertThat( memberDetails.hasAnnotationUsage( OneToOne.class ) ).isTrue();
		final AnnotationUsage<OneToOne> oneToOneUsage = memberDetails.getAnnotationUsage( OneToOne.class );
		assertThat( oneToOneUsage.getList( "cascade" ) ).isEmpty();

		final AnnotationUsage<Cascade> cascadeUsage = memberDetails.getAnnotationUsage( Cascade.class );
		assertThat( cascadeUsage.getList( "value" ) ).containsOnly(
				CascadeType.ALL,
				CascadeType.PERSIST,
				CascadeType.REMOVE,
				CascadeType.REFRESH,
				CascadeType.DETACH,
				CascadeType.MERGE
		);
	}

	@Test
	public void testAllAttributes() {
		final MemberDetails memberDetails = getAttributeMember( Entity1.class, "field1", "one-to-one.orm11.xml" );
		assertThat( memberDetails.hasAnnotationUsage( OneToOne.class ) ).isTrue();
		assertThat( memberDetails.hasAnnotationUsage( MapsId.class ) ).isTrue();
		assertThat( memberDetails.hasAnnotationUsage( Id.class ) ).isTrue();
		assertThat( memberDetails.hasAnnotationUsage( Access.class ) ).isTrue();
		assertThat( memberDetails.hasAnnotationUsage( Target.class ) ).isTrue();

		final AnnotationUsage<OneToOne> oneToOneUsage = memberDetails.getAnnotationUsage( OneToOne.class );
		assertThat( oneToOneUsage.getList( "cascade" ) ).isEmpty();
		assertThat( oneToOneUsage.getEnum( "fetch", FetchType.class ) ).isEqualTo( FetchType.LAZY );
		assertThat( oneToOneUsage.getString( "mappedBy" ) ).isEqualTo( "field2" );
		assertThat( oneToOneUsage.getBoolean( "optional" ) ).isFalse();
		assertThat( oneToOneUsage.getBoolean( "orphanRemoval" ) ).isTrue();

		final AnnotationUsage<Target> targetUsage = memberDetails.getAnnotationUsage( Target.class );
		assertThat( targetUsage.getString( "value" ) ).isEqualTo( Entity3.class.getName() );

		final AnnotationUsage<Access> accessUsage = memberDetails.getAnnotationUsage( Access.class );
		assertThat( accessUsage.getEnum( "value", AccessType.class ) ).isEqualTo( AccessType.PROPERTY );

		final AnnotationUsage<MapsId> mapsIsUsage = memberDetails.getAnnotationUsage( MapsId.class );
		assertThat( mapsIsUsage.getString( "value" ) ).isEqualTo( "field3" );
	}

}
