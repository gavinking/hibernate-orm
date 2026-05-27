/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.tool.reveng.internal.export.java;

import org.hibernate.tool.reveng.api.export.Exporter;
import org.hibernate.tool.reveng.api.export.ExporterConstants;
import org.hibernate.tool.reveng.api.export.ExporterFactory;
import org.hibernate.tool.reveng.api.export.ExporterType;
import org.hibernate.tool.reveng.internal.export.common.GenericExporter;

/**
 * @author max
 */
public class JavaExporter extends GenericExporter {

	private static final String POJO_JAVACLASS_FTL = "pojo/Pojo.ftl";

	protected void init() {
		getProperties().put(TEMPLATE_NAME, POJO_JAVACLASS_FTL);
		getProperties().put(FILE_PATTERN, "{package-name}/{class-name}.java");
	}

	public JavaExporter() {
		init();
	}

	public String getName() {
		return "hbm2java";
	}

	protected void setupContext() {
		//TODO: this safe guard should be in the root templates instead for each variable they depend on.
		if(!getProperties().containsKey("ejb3")) {
			getProperties().put("ejb3", "false");
		}
		if(!getProperties().containsKey("jdk5")) {
			getProperties().put("jdk5", "false");
		}
		if(!getProperties().containsKey("useSchemaAnnotations")) {
			getProperties().put("useSchemaAnnotations", "false");
		}
		super.setupContext();
	}

	@Override
	protected void doStart() {
		if ("true".equals(getProperties().get("useSchemaAnnotations"))) {
			String schemaPackage = (String) getProperties().get("schemaPackage");
			if (schemaPackage == null || schemaPackage.isBlank()) {
				throw new RuntimeException(
						"schemaPackage must be set when useSchemaAnnotations is true");
			}
			Exporter schemaExporter = ExporterFactory.createExporter(ExporterType.SCHEMA);
			schemaExporter.getProperties().put(ExporterConstants.METADATA_DESCRIPTOR, getProperties().get(ExporterConstants.METADATA_DESCRIPTOR));
			schemaExporter.getProperties().put(ExporterConstants.DESTINATION_FOLDER, getOutputDirectory());
			schemaExporter.getProperties().put("schemaPackage", schemaPackage);
			schemaExporter.start();
		}
		super.doStart();
	}
}
