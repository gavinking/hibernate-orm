/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.tool.ant;

import org.hibernate.tool.reveng.api.export.Exporter;
import org.hibernate.tool.reveng.api.export.ExporterFactory;
import org.hibernate.tool.reveng.api.export.ExporterType;

public class SchemaAnnotationExporterTask extends ExporterTask {

	public SchemaAnnotationExporterTask(HibernateToolTask parent) {
		super( parent );
	}

	public void setSchemaPackage(String schemaPackage) {
		properties.put( "schemaPackage", schemaPackage );
	}

	protected Exporter createExporter() {
		return ExporterFactory.createExporter(ExporterType.SCHEMA);
	}

	String getName() {
		return "schemaAnnotations (Generates schema annotation classes)";
	}
}
