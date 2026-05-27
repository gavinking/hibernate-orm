/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.tooling.maven.reveng;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.hibernate.tool.reveng.api.export.Exporter;
import org.hibernate.tool.reveng.api.export.ExporterConstants;
import org.hibernate.tool.reveng.api.export.ExporterFactory;
import org.hibernate.tool.reveng.api.export.ExporterType;
import org.hibernate.tool.reveng.api.metadata.MetadataDescriptor;

import java.io.File;

import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_SOURCES;

/**
 * Mojo to generate static schema annotation types from an existing database.
 * <p>
 * For each table, the generated top-level annotation type is meta-annotated with {@code @TableMapping}
 * holding a {@code @Table}. For each non-foreign key column, the generated nested annotation type is
 * meta-annotated with {@code @ColumnMapping} holding a {@code @Column}. For each foreign key column,
 * the generated nested annotation type is meta-annotated with {@code @JoinColumnMapping} holding a
 * {@code @JoinColumn}.
 */
@Mojo(
	name = "generateSchemaAnnotations",
	defaultPhase = GENERATE_SOURCES,
	requiresDependencyResolution = ResolutionScope.RUNTIME)
public class GenerateSchemaAnnotationsMojo extends AbstractGenerationMojo {

	/** The directory into which the schema annotations will be generated. */
	@Parameter(defaultValue = "${project.build.directory}/generated-sources/")
	private File outputDirectory;

	/** A path used for looking up user-edited templates. */
	@Parameter
	private String templatePath;

	/** The Java package for generated annotation types. */
	@Parameter
	private String schemaPackage;

	@Override
	protected void executeExporter(MetadataDescriptor metadataDescriptor) {
		Exporter exporter = ExporterFactory.createExporter(ExporterType.SCHEMA);
		exporter.getProperties().put(ExporterConstants.METADATA_DESCRIPTOR, metadataDescriptor);
		exporter.getProperties().put(ExporterConstants.DESTINATION_FOLDER, outputDirectory);
		if (templatePath != null) {
			getLog().info("Setting template path to: " + templatePath);
			exporter.getProperties().put(ExporterConstants.TEMPLATE_PATH, new String[] {templatePath});
		}
		if (schemaPackage != null) {
			exporter.getProperties().put("schemaPackage", schemaPackage);
		}
		getLog().info("Starting schema annotation export to directory: " + outputDirectory + "...");
		exporter.start();
	}
}
