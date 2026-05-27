/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.tooling.gradle.reveng;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

import org.apache.tools.ant.BuildException;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.hibernate.tool.reveng.api.core.RevengSettings;
import org.hibernate.tool.reveng.api.core.RevengStrategy;
import org.hibernate.tool.reveng.api.core.RevengStrategyFactory;
import org.hibernate.tool.reveng.api.export.Exporter;
import org.hibernate.tool.reveng.api.export.ExporterConstants;
import org.hibernate.tool.reveng.api.export.ExporterFactory;
import org.hibernate.tool.reveng.api.export.ExporterType;
import org.hibernate.tool.reveng.api.metadata.MetadataConstants;
import org.hibernate.tool.reveng.api.metadata.MetadataDescriptor;
import org.hibernate.tool.reveng.api.metadata.MetadataDescriptorFactory;
import org.hibernate.tool.reveng.internal.core.strategy.TableSelectorStrategy;

import static org.hibernate.orm.tooling.gradle.reveng.RevengFileHelper.findRequiredResourceFile;
import static org.hibernate.orm.tooling.gradle.reveng.RevengFileHelper.loadPropertiesFile;

/**
 * Generates static schema annotation types from JDBC metadata.
 * <p>
 * The Hibernate Gradle plugin registers this task as {@code generateSchemaAnnotations}. The task
 * connects to the configured JDBC database, reads table, column, and imported foreign key metadata,
 * and writes one Java annotation type per table to the configured package.
 * <p>
 * Basic Groovy DSL usage:
 *
 * <pre>{@code
 * dependencies {
 *     runtimeOnly "com.h2database:h2:<version>"
 * }
 *
 * tasks.named("generateSchemaAnnotations") {
 *     hibernateProperties = "hibernate.properties"
 *     revengFile = "hibernate.reveng.xml"
 *     schemaName = "PUBLIC"
 *     tableNamePattern = "%"
 *     packageName = "org.example.schema"
 * }
 * }</pre>
 * <p>
 * The JDBC driver is loaded from the project {@code runtimeClasspath}. Generated sources are written
 * below {@code build/generated/sources/schemaAnnotations} by default, with package directories appended.
 * For example, package {@code org.example.schema} and table {@code BOOK} produce
 * {@code build/generated/sources/schemaAnnotations/org/example/schema/BOOK.java}.
 * <p>
 * The task can read JDBC configuration from a {@code hibernate.properties} file in the main resource
 * set. A Hibernate Tools reverse-engineering file can be used for schema selection, table filters,
 * table exclusions, column exclusions, and user-defined foreign keys.
 * <ul>
 * <li>For each table, the generated top-level annotation type is meta-annotated with {@code @TableMapping}
 *     holding a {@code @Table}.
 * <li>For each non-foreign key column, the generated nested annotation type is meta-annotated with
 *     {@code @ColumnMapping} holding a {@code @Column}.
 * <li>For each foreign key column, the generated nested annotation type is meta-annotated with
 *     {@code @JoinColumnMapping} holding a {@code @JoinColumn}.
 * </ul>
 * <p>
 * Table and column names are used as Java annotation type names, so matched table and column names
 * must be legal Java identifiers.
 */
@DisableCachingByDefault(because = "Schema annotation generation performs JDBC operations and is not cacheable")
public abstract class GenerateSchemaAnnotationsTask extends DefaultTask {

	public GenerateSchemaAnnotationsTask() {
		getTableNamePattern().convention( "%" );
		getOutputDirectory().convention(
				getProject().getLayout().getBuildDirectory().dir( "generated/sources/schemaAnnotations" )
		);
		getOutputs().upToDateWhen( task -> false );
	}

	/**
	 * The Hibernate properties file to read from the main resource set.
	 * <p>
	 * Defaults to {@code hibernate.properties}. The task uses the file for JDBC driver,
	 * URL, user name, password, catalog, and schema. Direct task properties take precedence.
	 */
	@Input
	@Optional
	abstract public Property<String> getHibernateProperties();

	/**
	 * The optional Hibernate Tools reverse-engineering XML file to read from the main resource set.
	 * <p>
	 * The task supports schema selection, table filters, table exclusions, column exclusions, and
	 * user-defined foreign keys from this file.
	 */
	@Input
	@Optional
	abstract public Property<String> getRevengFile();

	/**
	 * The Java package for generated annotation types, for example {@code org.example.schema}.
	 */
	@Input
	abstract public Property<String> getPackageName();

	/**
	 * The optional catalog name passed to JDBC metadata lookup.
	 * <p>
	 * If not specified, the task reads {@code hibernate.default_catalog} from the configured
	 * Hibernate properties file.
	 */
	@Input
	@Optional
	abstract public Property<String> getCatalogName();

	/**
	 * The optional schema name passed to JDBC metadata lookup.
	 * <p>
	 * If not specified, the task reads {@code hibernate.default_schema} from the configured
	 * Hibernate properties file.
	 */
	@Input
	@Optional
	abstract public Property<String> getSchemaName();

	/**
	 * The table-name pattern passed to {@link java.sql.DatabaseMetaData#getTables}.
	 * <p>
	 * Defaults to {@code %}.
	 */
	@Input
	@Optional
	abstract public Property<String> getTableNamePattern();

	/**
	 * The root output directory for generated sources.
	 * <p>
	 * Defaults to {@code build/generated/sources/schemaAnnotations}.
	 */
	@OutputDirectory
	abstract public DirectoryProperty getOutputDirectory();

	@TaskAction
	public void generateSchemaAnnotations() {
		String packageName = getPackageName().get();
		getLogger().lifecycle( "Starting schema annotation generation" );
		ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
		URLClassLoader classLoader = null;
		try {
			classLoader = new URLClassLoader( resolveProjectClassPath(), oldLoader );
			Thread.currentThread().setContextClassLoader( classLoader );
			Properties properties = resolveHibernateProperties();
			RevengStrategy strategy = createReverseEngineeringStrategy( packageName );
			properties.put( MetadataConstants.PREFER_BASIC_COMPOSITE_IDS, true );
			MetadataDescriptor metadataDescriptor =
					MetadataDescriptorFactory.createReverseEngineeringDescriptor( strategy, properties );

			Exporter exporter = ExporterFactory.createExporter( ExporterType.SCHEMA );
			exporter.getProperties().put( ExporterConstants.METADATA_DESCRIPTOR, metadataDescriptor );
			exporter.getProperties().put(
					ExporterConstants.DESTINATION_FOLDER,
					getOutputDirectory().get().getAsFile()
			);
			exporter.getProperties().put( "schemaPackage", packageName );
			exporter.start();
		}
		catch (Exception e) {
			throw new GradleException( "Unable to generate schema annotations", e );
		}
		finally {
			Thread.currentThread().setContextClassLoader( oldLoader );
			closeClassLoader( classLoader );
			getLogger().lifecycle( "Finished schema annotation generation" );
		}
	}

	private Properties resolveHibernateProperties() {
		String filename = getHibernateProperties().getOrElse( RevengSpec.DEFAULT_HIBERNATE_PROPERTIES );
		File propertiesFile = RevengFileHelper.findResourceFile( getProject(), filename );
		Properties properties;
		if ( propertiesFile != null ) {
			properties = loadPropertiesFile( getLogger(), propertiesFile );
		}
		else if ( getHibernateProperties().isPresent() ) {
			throw new GradleException(
					"Hibernate properties file '" + filename + "' could not be found"
			);
		}
		else {
			properties = new Properties();
		}
		if ( getCatalogName().isPresent() ) {
			properties.put( "hibernate.default_catalog", getCatalogName().get() );
		}
		if ( getSchemaName().isPresent() ) {
			properties.put( "hibernate.default_schema", getSchemaName().get() );
		}
		return properties;
	}

	private RevengStrategy createReverseEngineeringStrategy(String packageName) {
		File[] revengFiles = null;
		if ( getRevengFile().isPresent() && !getRevengFile().get().isBlank() ) {
			File revengFile = findRequiredResourceFile( getProject(), getRevengFile().get() );
			revengFiles = new File[] { revengFile };
		}
		RevengStrategy strategy = RevengStrategyFactory
				.createReverseEngineeringStrategy( null, revengFiles );
		RevengSettings settings = new RevengSettings( strategy );
		settings.setDefaultPackageName( packageName );
		strategy.setSettings( settings );

		String tableNamePattern = getTableNamePattern().getOrElse( "%" );
		String schemaName = getSchemaName().getOrNull();
		String catalogName = getCatalogName().getOrNull();
		if ( schemaName != null || catalogName != null || !"%".equals( tableNamePattern ) ) {
			TableSelectorStrategy selector = new TableSelectorStrategy( strategy );
			selector.addSchemaSelection( new RevengStrategy.SchemaSelection() {
				@Override
				public String getMatchCatalog() {
					return catalogName;
				}

				@Override
				public String getMatchSchema() {
					return schemaName;
				}

				@Override
				public String getMatchTable() {
					return tableNamePattern;
				}
			} );
			return selector;
		}
		return strategy;
	}

	private void closeClassLoader(URLClassLoader classLoader) {
		if ( classLoader != null ) {
			try {
				classLoader.close();
			}
			catch (IOException e) {
				getLogger().warn( "Unable to close classloader", e );
			}
		}
	}

	URL[] resolveProjectClassPath() {
		try {
			final var configurations = getProject().getConfigurations();
			final var runtimeClasspath = configurations.getByName( "runtimeClasspath" );
			final var resolvedConfiguration = runtimeClasspath.getResolvedConfiguration();
			final var artifacts = resolvedConfiguration.getResolvedArtifacts();
			final var urls = new URL[artifacts.size()];
			int index = 0;
			for ( ResolvedArtifact artifact : artifacts ) {
				urls[index++] = artifact.getFile().toURI().toURL();
			}
			return urls;
		}
		catch (MalformedURLException e) {
			throw new BuildException( e );
		}
	}
}
