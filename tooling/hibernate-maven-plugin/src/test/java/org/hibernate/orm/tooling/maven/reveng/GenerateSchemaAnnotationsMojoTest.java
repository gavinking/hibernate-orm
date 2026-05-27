/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.tooling.maven.reveng;

import org.apache.maven.project.MavenProject;
import org.hibernate.tool.reveng.api.metadata.MetadataDescriptor;
import org.hibernate.tool.reveng.api.metadata.MetadataDescriptorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GenerateSchemaAnnotationsMojoTest {

	private static final String CREATE_PERSON_TABLE =
			"create table PERSON (ID int not null, NAME varchar(20), primary key (ID))";
	private static final String CREATE_ITEM_TABLE =
			"create table ITEM (ID int not null, NAME varchar(20), OWNER_ID int not null, primary key (ID), foreign key (OWNER_ID) references PERSON(ID))";
	private static final String DROP_ITEM_TABLE =
			"drop table ITEM";
	private static final String DROP_PERSON_TABLE =
			"drop table PERSON";

	@TempDir
	private File tempDir;

	private File outputDirectory;
	private GenerateSchemaAnnotationsMojo mojo;

	@BeforeEach
	public void beforeEach() throws Exception {
		createDatabase();
		createOutputDirectory();
		createMojo();
	}

	@AfterEach
	public void afterEach() throws Exception {
		dropDatabase();
	}

	@Test
	public void testGenerateSchemaAnnotations() throws Exception {
		File personFile = new File(outputDirectory, "PERSON.java");
		File itemFile = new File(outputDirectory, "ITEM.java");
		assertFalse(personFile.exists());
		assertFalse(itemFile.exists());
		mojo.executeExporter(createMetadataDescriptor());
		assertTrue(personFile.exists());
		assertTrue(itemFile.exists());
		String personContent = new String(Files.readAllBytes(personFile.toPath()));
		assertTrue(personContent.contains("@TableMapping"));
		assertTrue(personContent.contains("@ColumnMapping"));
		String itemContent = new String(Files.readAllBytes(itemFile.toPath()));
		assertTrue(itemContent.contains("@JoinColumnMapping"));
	}

	@Test
	public void testGenerateSchemaAnnotationsWithPackage() throws Exception {
		Field schemaPackageField = GenerateSchemaAnnotationsMojo.class.getDeclaredField("schemaPackage");
		schemaPackageField.setAccessible(true);
		schemaPackageField.set(mojo, "org.example.schema");
		File personFile = new File(outputDirectory, "org/example/schema/PERSON.java");
		assertFalse(personFile.exists());
		mojo.executeExporter(createMetadataDescriptor());
		assertTrue(personFile.exists());
		String personContent = new String(Files.readAllBytes(personFile.toPath()));
		assertTrue(personContent.contains("package org.example.schema;"));
		assertTrue(personContent.contains("@TableMapping"));
	}

	@Test
	public void testGenerateSchemaAnnotationsWithoutPackage() throws Exception {
		File personFile = new File(outputDirectory, "PERSON.java");
		assertFalse(personFile.exists());
		mojo.executeExporter(createMetadataDescriptor());
		assertTrue(personFile.exists());
		String personContent = new String(Files.readAllBytes(personFile.toPath()));
		assertFalse(personContent.contains("package "));
	}

	private void createDatabase() throws Exception {
		Connection connection = DriverManager.getConnection(constructJdbcConnectionString());
		Statement statement = connection.createStatement();
		statement.execute(CREATE_PERSON_TABLE);
		statement.execute(CREATE_ITEM_TABLE);
		statement.close();
		connection.close();
	}

	private void dropDatabase() throws Exception {
		Connection connection = DriverManager.getConnection(constructJdbcConnectionString());
		Statement statement = connection.createStatement();
		statement.execute(DROP_ITEM_TABLE);
		statement.execute(DROP_PERSON_TABLE);
		statement.close();
		connection.close();
	}

	private void createMojo() throws Exception {
		mojo = new GenerateSchemaAnnotationsMojo();
		Field projectField = AbstractGenerationMojo.class.getDeclaredField("project");
		projectField.setAccessible(true);
		projectField.set(mojo, new MavenProject());
		Field propertyFileField = AbstractGenerationMojo.class.getDeclaredField("propertyFile");
		propertyFileField.setAccessible(true);
		propertyFileField.set(mojo, new File(tempDir, "hibernate.properties"));
		Field outputDirectoryField = GenerateSchemaAnnotationsMojo.class.getDeclaredField("outputDirectory");
		outputDirectoryField.setAccessible(true);
		outputDirectoryField.set(mojo, outputDirectory);
	}

	private void createOutputDirectory() {
		outputDirectory = new File(tempDir, "generated");
		if (!outputDirectory.mkdir()) throw new RuntimeException("Unable to create output directory: " + outputDirectory);
	}

	private MetadataDescriptor createMetadataDescriptor() {
		return MetadataDescriptorFactory.createReverseEngineeringDescriptor(
				null,
				createProperties());
	}

	private Properties createProperties() {
		Properties result = new Properties();
		result.put("hibernate.connection.url", constructJdbcConnectionString());
		result.put("hibernate.default_catalog", "TEST");
		result.put("hibernate.default_schema", "PUBLIC");
		return result;
	}

	private String constructJdbcConnectionString() {
		return "jdbc:h2:" + tempDir.getAbsolutePath() + "/database/test;AUTO_SERVER=TRUE";
	}

}
