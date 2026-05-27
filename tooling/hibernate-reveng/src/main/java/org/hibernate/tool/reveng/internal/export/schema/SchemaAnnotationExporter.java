/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.tool.reveng.internal.export.schema;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.hibernate.mapping.Table;
import org.hibernate.tool.reveng.internal.export.common.AbstractExporter;
import org.hibernate.tool.reveng.internal.export.common.TemplateProducer;

public class SchemaAnnotationExporter extends AbstractExporter {

	private static final String SCHEMA_ANNOTATION_FTL = "schema/SchemaAnnotation.ftl";

	@Override
	protected void setupContext() {
		super.setupContext();
		getTemplateHelper().putInContext( "schemaHelper", new SchemaAnnotationHelper() );
		String pkg = (String) getProperties().get( "schemaPackage" );
		if ( pkg != null ) {
			getTemplateHelper().putInContext( "schemaPackage", pkg );
		}
	}

	@Override
	protected void doStart() {
		TemplateProducer producer = new TemplateProducer( getTemplateHelper(), getArtifactCollector() );
		String schemaPackage = (String) getProperties().get( "schemaPackage" );
		for ( Table table : getMetadata().collectTableMappings() ) {
			if ( table.isPhysicalTable() ) {
				Map<String, Object> context = new HashMap<>();
				context.put( "table", table );
				String annotationName = table.getName().toUpperCase( Locale.ROOT );
				File output = new File( getOutputDirectory(), resolveFilename( annotationName, schemaPackage ) );
				producer.produce( context, SCHEMA_ANNOTATION_FTL, output, annotationName );
			}
		}
	}

	private String resolveFilename(String annotationName, String schemaPackage) {
		String packagePath = schemaPackage != null ? schemaPackage.replace( '.', '/' ) : "";
		if ( packagePath.isEmpty() ) {
			return annotationName + ".java";
		}
		return packagePath + "/" + annotationName + ".java";
	}

	public String getName() {
		return "schemaAnnotationsExporter";
	}
}
