/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.sql.ast.tree.expression;

import java.util.List;

import org.hibernate.sql.ast.tree.select.SortSpecification;

/**
 * Models an ordered set-aggregate function expression at the SQL AST level.
 *
 * @author Christian Beikov
 */
public interface OrderedSetAggregateFunctionExpression extends AggregateFunctionExpression {

	List<SortSpecification> getWithinGroup();
}
