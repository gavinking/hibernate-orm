/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.query.criteria;

import org.hibernate.Incubating;

/**
 * @since 7.0
 */
@Incubating
public interface JpaFunctionFrom<O, T> extends JpaFrom<O, T> {

	/**
	 * The function for this from node.
	 */
	JpaSetReturningFunction<T> getFunction();

	/**
	 * The expression referring to an iteration variable, indexing the rows produced by the function.
	 * This is the equivalent of the SQL {@code with ordinality} clause.
	 */
	JpaExpression<Long> index();

}
