/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.tool.ant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SchemaAnnotationExporterTaskTest {

	@Test
	public void testGetName() {
		HibernateToolTask parent = new HibernateToolTask();
		SchemaAnnotationExporterTask task = new SchemaAnnotationExporterTask(parent);
		assertEquals("schemaAnnotations (Generates schema annotation classes)", task.getName());
	}

	@Test
	public void testSetSchemaPackage() {
		HibernateToolTask parent = new HibernateToolTask();
		SchemaAnnotationExporterTask task = new SchemaAnnotationExporterTask(parent);
		task.setSchemaPackage("org.example.schema");
		assertEquals("org.example.schema", task.properties.get("schemaPackage"));
	}
}
