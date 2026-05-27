<#--
~ SPDX-License-Identifier: Apache-2.0
~ Copyright Red Hat Inc. and Hibernate Authors
-->
<#assign pkg = schemaPackage!"">
<#if pkg?has_content>
package ${pkg};

</#if>
import java.lang.annotation.Retention;

import org.hibernate.annotations.schema.ColumnMapping;
import org.hibernate.annotations.schema.JoinColumnMapping;
import org.hibernate.annotations.schema.TableMapping;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@TableMapping(@Table(name = "${table.name}"))
public @interface ${schemaHelper.toUpperCase(table.name)} {
<#list table.columns as column>

<#if schemaHelper.isForeignKeyColumn(table, column)>
	@Retention(RUNTIME)
	@JoinColumnMapping(@JoinColumn(name = "${column.name}", referencedColumnName = "${schemaHelper.getReferencedColumnName(table, column)}", nullable = ${column.nullable?c}))
	@interface ${schemaHelper.toUpperCase(column.name)} {
	}
<#else>
	@Retention(RUNTIME)
	@ColumnMapping(@Column(name = "${column.name}", nullable = ${column.nullable?c}, unique = ${column.unique?c}, length = ${schemaHelper.columnLength(column)}, precision = ${schemaHelper.columnPrecision(column)}, scale = ${schemaHelper.columnScale(column)}))
	@interface ${schemaHelper.toUpperCase(column.name)} {
	}
</#if>
</#list>
}
