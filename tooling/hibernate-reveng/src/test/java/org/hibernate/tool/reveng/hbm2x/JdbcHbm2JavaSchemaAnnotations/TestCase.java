/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.tool.reveng.hbm2x.JdbcHbm2JavaSchemaAnnotations;

import org.hibernate.tool.reveng.api.export.Exporter;
import org.hibernate.tool.reveng.api.export.ExporterConstants;
import org.hibernate.tool.reveng.api.export.ExporterFactory;
import org.hibernate.tool.reveng.api.export.ExporterType;
import org.hibernate.tool.reveng.api.metadata.MetadataDescriptorFactory;
import org.hibernate.tool.reveng.test.utils.JUnitUtil;
import org.hibernate.tool.reveng.test.utils.JdbcUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCase {

	@TempDir
	public File outputDir = new File("output");

	@BeforeEach
	public void setUp() {
		JdbcUtil.createDatabase(this);
		Exporter exporter = ExporterFactory.createExporter(ExporterType.JAVA);
		exporter.getProperties().put(
				ExporterConstants.METADATA_DESCRIPTOR,
				MetadataDescriptorFactory.createReverseEngineeringDescriptor(null, null));
		exporter.getProperties().put(ExporterConstants.DESTINATION_FOLDER, outputDir);
		exporter.getProperties().put(ExporterConstants.TEMPLATE_PATH, new String[0]);
		exporter.getProperties().setProperty("ejb3", "true");
		exporter.getProperties().setProperty("jdk5", "true");
		exporter.getProperties().setProperty("useSchemaAnnotations", "true");
		exporter.getProperties().setProperty("schemaPackage", "org.example.schema");
		exporter.start();
	}

	@AfterEach
	public void tearDown() {
		JdbcUtil.dropDatabase(this);
	}

	@Test
	public void testEntityFilesExist() {
		JUnitUtil.assertIsNonEmptyFile(new File(outputDir, "Person.java"));
		JUnitUtil.assertIsNonEmptyFile(new File(outputDir, "Item.java"));
	}

	@Test
	public void testSchemaAnnotationFilesExist() {
		JUnitUtil.assertIsNonEmptyFile(new File(outputDir, "org/example/schema/PERSON.java"));
		JUnitUtil.assertIsNonEmptyFile(new File(outputDir, "org/example/schema/ITEM.java"));
	}

	@Test
	public void testEntityUsesTableSchemaAnnotation() throws Exception {
		String personContent = Files.readString(new File(outputDir, "Person.java").toPath());
		assertTrue(personContent.contains("@PERSON"), "Entity should use @PERSON schema annotation");
		assertFalse(personContent.contains("@Table"), "Entity should not use @Table");
	}

	@Test
	public void testEntityUsesColumnSchemaAnnotation() throws Exception {
		String personContent = Files.readString(new File(outputDir, "Person.java").toPath());
		assertTrue(personContent.contains("@PERSON.NAME"), "Property should use @PERSON.NAME schema annotation");
		assertFalse(personContent.contains("@Column"), "Property should not use @Column");
	}

	@Test
	public void testEntityUsesJoinColumnSchemaAnnotation() throws Exception {
		String itemContent = Files.readString(new File(outputDir, "Item.java").toPath());
		assertNotNull(itemContent);
		assertTrue(itemContent.contains("@ITEM.OWNER_ID"), "FK property should use @ITEM.OWNER_ID schema annotation");
		assertFalse(itemContent.contains("@JoinColumn"), "FK property should not use @JoinColumn");
	}

	@Test
	public void testEntityKeepsRelationshipAnnotations() throws Exception {
		String itemContent = Files.readString(new File(outputDir, "Item.java").toPath());
		assertTrue(itemContent.contains("@ManyToOne"), "FK property should keep @ManyToOne");
		String personContent = Files.readString(new File(outputDir, "Person.java").toPath());
		assertTrue(personContent.contains("@Entity"), "Entity should keep @Entity");
		assertTrue(personContent.contains("@Id"), "Id property should keep @Id");
	}

	@Test
	public void testEntityImportsSchemaAnnotation() throws Exception {
		String personContent = Files.readString(new File(outputDir, "Person.java").toPath());
		assertTrue(personContent.contains("import org.example.schema.PERSON"), "Entity should import schema annotation");
	}

}
