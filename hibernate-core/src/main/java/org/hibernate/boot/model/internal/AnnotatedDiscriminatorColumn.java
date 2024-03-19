/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.boot.model.internal;

import org.hibernate.AssertionFailure;
import org.hibernate.annotations.DiscriminatorFormula;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.models.spi.AnnotationUsage;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;

/**
 * A {@link jakarta.persistence.DiscriminatorColumn} annotation
 *
 * @author Emmanuel Bernard
 */
public class AnnotatedDiscriminatorColumn extends AnnotatedColumn {
	public static final String DEFAULT_DISCRIMINATOR_COLUMN_NAME = "DTYPE";
	public static final String DEFAULT_DISCRIMINATOR_TYPE = "string";
	private static final long DEFAULT_DISCRIMINATOR_LENGTH = 31;

	private String discriminatorTypeName;

	public AnnotatedDiscriminatorColumn() {
		//discriminator default value
		super();
		setLogicalColumnName( DEFAULT_DISCRIMINATOR_COLUMN_NAME );
		setNullable( false );
		setDiscriminatorTypeName( DEFAULT_DISCRIMINATOR_TYPE );
		setLength( DEFAULT_DISCRIMINATOR_LENGTH );
	}

	public String getDiscriminatorTypeName() {
		return discriminatorTypeName;
	}

	public void setDiscriminatorTypeName(String discriminatorTypeName) {
		this.discriminatorTypeName = discriminatorTypeName;
	}

	public static AnnotatedDiscriminatorColumn buildDiscriminatorColumn(
			AnnotationUsage<DiscriminatorColumn> discriminatorColumn,
			AnnotationUsage<DiscriminatorFormula> discriminatorFormula,
			MetadataBuildingContext context) {
		final AnnotatedColumns parent = new AnnotatedColumns();
		parent.setBuildingContext( context );
		final AnnotatedDiscriminatorColumn column = new AnnotatedDiscriminatorColumn();
		final DiscriminatorType discriminatorType;
		if ( discriminatorFormula != null ) {
			final DiscriminatorType type = discriminatorFormula.getEnum( "discriminatorType" );
			if ( type == DiscriminatorType.STRING ) {
				discriminatorType = discriminatorColumn == null ? type : discriminatorColumn.getEnum( "discriminatorType" );
			}
			else {
				discriminatorType = type;
			}
			column.setImplicit( false );
			column.setFormula( discriminatorFormula.getString( "value" ) );
		}
		else if ( discriminatorColumn != null ) {
			discriminatorType = discriminatorColumn.getEnum( "discriminatorType" );
			column.setImplicit( false );
			if ( !discriminatorColumn.getString( "columnDefinition" ).isEmpty() ) {
				column.setSqlType( discriminatorColumn.getString( "columnDefinition" ) );
			}
			if ( !discriminatorColumn.getString( "name" ).isEmpty() ) {
				column.setLogicalColumnName( discriminatorColumn.getString( "name" ) );
			}
			column.setNullable( false );
		}
		else {
			discriminatorType = DiscriminatorType.STRING;
			column.setImplicit( true );
		}
		setDiscriminatorType( discriminatorType, discriminatorColumn, column );
		column.setParent( parent );
		column.bind();
		return column;
	}

	private static void setDiscriminatorType(
			DiscriminatorType type,
			AnnotationUsage<DiscriminatorColumn> discriminatorColumn,
			AnnotatedDiscriminatorColumn column) {
		if ( type == null ) {
			column.setDiscriminatorTypeName( "string" );
		}
		else {
			switch ( type ) {
				case CHAR:
					column.setDiscriminatorTypeName( "character" );
					column.setImplicit( false );
					column.setLength( 1L );
					break;
				case INTEGER:
					column.setDiscriminatorTypeName( "integer" );
					column.setImplicit( false );
					break;
				case STRING:
					column.setDiscriminatorTypeName( "string" );
					if ( discriminatorColumn != null ) {
						column.setLength( (long) discriminatorColumn.getInteger( "length" ) );
					}
					break;
				default:
					throw new AssertionFailure( "Unknown discriminator type: " + type );
			}
		}
	}
}
