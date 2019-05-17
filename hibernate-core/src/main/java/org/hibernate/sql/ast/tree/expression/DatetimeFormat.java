/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.sql.ast.tree.expression;

import org.hibernate.dialect.Dialect;
import org.hibernate.sql.SqlExpressableType;

/**
 * @author Gavin King
 */
public class DatetimeFormat extends Format {
	public DatetimeFormat(String format, SqlExpressableType type) {
		super(format, type);
	}

	@Override
	public String translate(Dialect dialect) {
		return dialect.translateDatetimeFormat( getFormat() );
	}
}
