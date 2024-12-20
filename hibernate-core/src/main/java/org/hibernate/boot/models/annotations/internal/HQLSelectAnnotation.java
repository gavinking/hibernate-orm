/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.boot.models.annotations.internal;

import java.lang.annotation.Annotation;
import java.util.Map;

import org.hibernate.annotations.HQLSelect;
import org.hibernate.models.spi.SourceModelBuildingContext;

@SuppressWarnings({ "ClassExplicitlyAnnotation", "unused" })
@jakarta.annotation.Generated("org.hibernate.orm.build.annotations.ClassGeneratorProcessor")
public class HQLSelectAnnotation implements HQLSelect {


	private String query;

	/**
	 * Used in creating dynamic annotation instances (e.g. from XML)
	 */
	public HQLSelectAnnotation(SourceModelBuildingContext modelContext) {
	}

	/**
	 * Used in creating annotation instances from JDK variant
	 */
	public HQLSelectAnnotation(HQLSelect annotation, SourceModelBuildingContext modelContext) {
		this.query = annotation.query();
	}

	/**
	 * Used in creating annotation instances from Jandex variant
	 */
	public HQLSelectAnnotation(Map<String, Object> attributeValues, SourceModelBuildingContext modelContext) {
		this.query = (String) attributeValues.get( "query" );
	}

	@Override
	public Class<? extends Annotation> annotationType() {
		return HQLSelect.class;
	}

	@Override
	public String query() {
		return query;
	}

	public void query(String value) {
		this.query = value;
	}


}
