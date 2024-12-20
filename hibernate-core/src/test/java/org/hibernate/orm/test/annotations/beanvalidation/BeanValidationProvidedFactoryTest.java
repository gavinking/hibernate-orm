/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.annotations.beanvalidation;

import java.math.BigDecimal;
import java.util.Locale;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.hibernate.boot.beanvalidation.ValidationMode;
import org.junit.Test;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;

import static org.hibernate.cfg.ValidationSettings.JAKARTA_VALIDATION_FACTORY;
import static org.hibernate.cfg.ValidationSettings.JAKARTA_VALIDATION_MODE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Emmanuel Bernard
 */
public class BeanValidationProvidedFactoryTest extends BaseCoreFunctionalTestCase {
	@Test
	public void testListeners() {
		CupHolder ch = new CupHolder();
		ch.setRadius( new BigDecimal( "12" ) );
		Session s = openSession();
		Transaction tx = s.beginTransaction();
		try {
			s.persist( ch );
			s.flush();
			fail( "invalid object should not be persisted" );
		}
		catch ( ConstraintViolationException e ) {
			assertEquals( 1, e.getConstraintViolations().size() );
			assertEquals( "Oops", e.getConstraintViolations().iterator().next().getMessage() );
		}
		tx.rollback();
		s.close();
	}

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] {
				CupHolder.class
		};
	}

	@Override
	protected void configure(Configuration cfg) {
		super.configure( cfg );
		final MessageInterpolator messageInterpolator = new MessageInterpolator() {

			public String interpolate(String s, Context context) {
				return "Oops";
			}

			public String interpolate(String s, Context context, Locale locale) {
				return interpolate( s, context );
			}
		};
		final jakarta.validation.Configuration<?> configuration = Validation.byDefaultProvider().configure();
		configuration.messageInterpolator( messageInterpolator );
		ValidatorFactory vf = configuration.buildValidatorFactory();
		cfg.getProperties().put( JAKARTA_VALIDATION_FACTORY, vf );
		cfg.setProperty( JAKARTA_VALIDATION_MODE, ValidationMode.AUTO );
	}
}
