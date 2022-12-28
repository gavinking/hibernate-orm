/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.annotations;

/**
 * Enumeration extending the {@linkplain jakarta.persistence.FlushModeType JPA flush modes}
 * with flush modes specific to Hibernate, and a "null" mode, {@link #PERSISTENCE_CONTEXT},
 * for use as a default annotation value. Except for the null value, this enumeration is
 * isomorphic to {@link org.hibernate.FlushMode}.
 *
 * @author Carlos Gonzalez-Cadenas
 *
 * @see NamedQuery
 * @see NamedNativeQuery
 *
 * @deprecated use {@link org.hibernate.ForcedFlushMode}
 */
@Deprecated(since="6")
public enum FlushModeType {
	/**
	 * Corresponds to {@link org.hibernate.FlushMode#ALWAYS}.
	 */
	ALWAYS,
	/**
	 * Corresponds to  {@link org.hibernate.FlushMode#AUTO}.
	 */
	AUTO,
	/**
	 * Corresponds to  {@link org.hibernate.FlushMode#COMMIT}.
	 */
	COMMIT,
	/**
	 * Corresponds to  {@link org.hibernate.FlushMode#MANUAL}.
	 */
	MANUAL,
	/**
	 * Current flush mode of the persistence context at the time the query is executed.
	 */
	PERSISTENCE_CONTEXT
}
