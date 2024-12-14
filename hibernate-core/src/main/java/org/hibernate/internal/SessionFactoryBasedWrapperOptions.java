/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.internal;

import java.util.TimeZone;

import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.LobCreator;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.format.FormatMapper;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * An incomplete implementation of {@link WrapperOptions} which is not backed by a session.
 *
 * @see SessionFactoryImplementor#getWrapperOptions()
 *
 * @author Christian Beikov
 */
class SessionFactoryBasedWrapperOptions implements WrapperOptions {

	private final SessionFactoryImplementor factory;
	private final FastSessionServices fastSessionServices;

	SessionFactoryBasedWrapperOptions(SessionFactoryImplementor factory) {
		this.factory = factory;
		fastSessionServices = factory.getFastSessionServices();
	}

	@Override
	public SharedSessionContractImplementor getSession() {
		throw new UnsupportedOperationException( "No session" );
	}

	@Override
	public boolean useStreamForLobBinding() {
		return fastSessionServices.useStreamForLobBinding;
	}

	@Override
	public int getPreferredSqlTypeCodeForBoolean() {
		return fastSessionServices.preferredSqlTypeCodeForBoolean;
	}

	@Override
	public LobCreator getLobCreator() {
		return fastSessionServices.jdbcServices.getLobCreator( getSession() );
	}

	@Override
	public TimeZone getJdbcTimeZone() {
		return fastSessionServices.jdbcTimeZone;
	}

	@Override
	public Dialect getDialect() {
		return fastSessionServices.dialect;
	}

	@Override
	public TypeConfiguration getTypeConfiguration() {
		return fastSessionServices.typeConfiguration;
	}

	@Override
	public FormatMapper getXmlFormatMapper() {
		return fastSessionServices.getXmlFormatMapper();
	}

	@Override
	public FormatMapper getJsonFormatMapper() {
		return fastSessionServices.getJsonFormatMapper();
	}
}
