/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.tool.reveng.internal.export.schema;

import java.util.Iterator;
import java.util.Locale;

import org.hibernate.mapping.Column;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.Table;

public class SchemaAnnotationHelper {

	public boolean isForeignKeyColumn(Table table, Column column) {
		for ( ForeignKey fk : table.getForeignKeys().values() ) {
			if ( fk.containsColumn( column ) ) {
				return true;
			}
		}
		return false;
	}

	public String getReferencedTableName(Table table, Column column) {
		for ( ForeignKey fk : table.getForeignKeys().values() ) {
			if ( fk.containsColumn( column ) ) {
				return fk.getReferencedTable().getName();
			}
		}
		return null;
	}

	public String getReferencedColumnName(Table table, Column column) {
		for ( ForeignKey fk : table.getForeignKeys().values() ) {
			if ( fk.containsColumn( column ) ) {
				Iterator<Column> fkColumns = fk.getColumns().iterator();
				Iterator<Column> refColumns = fk.getReferencedColumns().iterator();
				while ( fkColumns.hasNext() && refColumns.hasNext() ) {
					Column fkCol = fkColumns.next();
					Column refCol = refColumns.next();
					if ( fkCol.getName().equals( column.getName() ) ) {
						return refCol.getName();
					}
				}
				if ( fk.getReferencedColumns().isEmpty() ) {
					return fk.getReferencedTable().getPrimaryKey()
							.getColumns().get( fk.getColumns().indexOf( column ) ).getName();
				}
			}
		}
		return null;
	}

	public String toUpperCase(String name) {
		return name.toUpperCase( Locale.ROOT );
	}

	public int columnLength(Column column) {
		Long length = column.getLength();
		return length != null ? length.intValue() : 255;
	}

	public int columnPrecision(Column column) {
		Integer precision = column.getPrecision();
		return precision != null ? precision : 0;
	}

	public int columnScale(Column column) {
		Integer scale = column.getScale();
		return scale != null ? scale : 0;
	}

}
