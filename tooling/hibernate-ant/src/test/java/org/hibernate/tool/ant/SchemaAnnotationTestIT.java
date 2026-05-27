/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.tool.ant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SchemaAnnotationTestIT extends TestTemplate {

	@Test
	public void testGenerateSchemaAnnotations() throws Exception {
		setHibernateToolTaskXml(
				"""
								<hibernatetool destdir='generated'>                         \s
									<jdbcconfiguration propertyfile='hibernate.properties'/>\s
									<schemaAnnotations/>                                    \s
								</hibernatetool>                                            \s
						"""
		);
		setDatabaseCreationScript(new String[] {
				"create table PERSON (ID int not null, NAME varchar(20), primary key (ID))",
				"create table ITEM (ID int not null, NAME varchar(20), OWNER_ID int not null, " +
						"primary key (ID), foreign key (OWNER_ID) references PERSON(ID))"
		});
		createProjectAndBuild();
		assertFolderExists("generated", 2);
		assertFileExists("generated/PERSON.java");
		assertFileExists("generated/ITEM.java");
		String personContents = getFileContents("generated/PERSON.java");
		assertTrue(personContents.contains("@TableMapping"));
		assertTrue(personContents.contains("@ColumnMapping"));
		String itemContents = getFileContents("generated/ITEM.java");
		assertTrue(itemContents.contains("@TableMapping"));
		assertTrue(itemContents.contains("@JoinColumnMapping"));
	}

	@Test
	public void testGenerateSchemaAnnotationsWithPackage() throws Exception {
		setHibernateToolTaskXml(
				"""
								<hibernatetool destdir='generated'>                         \s
									<jdbcconfiguration propertyfile='hibernate.properties'/>\s
									<schemaAnnotations schemaPackage='org.example.schema'/>  \s
								</hibernatetool>                                            \s
						"""
		);
		setDatabaseCreationScript(new String[] {
				"create table PERSON (ID int not null, NAME varchar(20), primary key (ID))"
		});
		createProjectAndBuild();
		assertFolderExists("generated/org/example/schema", 1);
		assertFileExists("generated/org/example/schema/PERSON.java");
		String personContents = getFileContents("generated/org/example/schema/PERSON.java");
		assertTrue(personContents.contains("package org.example.schema;"));
		assertTrue(personContents.contains("@TableMapping"));
	}

	@Test
	public void testGenerateSchemaAnnotationsWithoutPackage() throws Exception {
		setHibernateToolTaskXml(
				"""
								<hibernatetool destdir='generated'>                         \s
									<jdbcconfiguration propertyfile='hibernate.properties'/>\s
									<schemaAnnotations/>                                    \s
								</hibernatetool>                                            \s
						"""
		);
		setDatabaseCreationScript(new String[] {
				"create table PERSON (ID int not null, NAME varchar(20), primary key (ID))"
		});
		createProjectAndBuild();
		assertFileExists("generated/PERSON.java");
		String personContents = getFileContents("generated/PERSON.java");
		assertFalse(personContents.contains("package "));
		assertTrue(personContents.contains("@TableMapping"));
	}

}
