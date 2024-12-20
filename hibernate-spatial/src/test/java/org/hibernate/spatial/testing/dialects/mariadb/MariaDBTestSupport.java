/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.spatial.testing.dialects.mariadb;

import java.util.Map;

import org.hibernate.spatial.CommonSpatialFunction;
import org.hibernate.spatial.GeomCodec;
import org.hibernate.spatial.testing.datareader.TestData;
import org.hibernate.spatial.testing.datareader.TestSupport;
import org.hibernate.spatial.testing.dialects.NativeSQLTemplates;
import org.hibernate.spatial.testing.dialects.PredicateRegexes;
import org.hibernate.spatial.testing.dialects.mysql.MySqlNativeSqlTemplates;

import org.geolatte.geom.Geometry;

public class MariaDBTestSupport extends TestSupport {

	@Override
	public TestData createTestData(TestDataPurpose purpose) {
		return TestData.fromFile( "mariadb/test-mariadb-functions-data-set.xml" );
	}

	@Override
	public NativeSQLTemplates templates() {
		return new MySqlNativeSqlTemplates();
	}

	@Override
	public Map<CommonSpatialFunction, String> hqlOverrides() {
		return super.hqlOverrides();
	}

	@Override
	public PredicateRegexes predicateRegexes() {
		return new PredicateRegexes("st_geomfromtext");
	}

	@Override
	public GeomCodec codec() {
		return in -> (Geometry<?>) in;
	}
}
