/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.query;

/**
 * Defines the set of basic types which should be
 * accepted by the {@code cast()} function on every
 * platform.
 * <p>
 * Note that while almost every database supports
 * the ANSI {@code cast()} function, the actual type
 * conversions supported vary widely. Therefore, it
 * is sometimes necessary to emulate certain type
 * conversions that we consider "basic". In particular,
 * some databases (looking at you, MySQL and friends)
 * don't have a proper {@link java.sql.Types#BOOLEAN}
 * type, and so type conversions to and from
 * {@link Boolean} must be emulated.
 *
 * see org.hibernate.dialect.Dialect#castPattern(CastType, CastType)
 *
 * @author Gavin King
 */
public enum CastType {
	STRING(CastTypeKind.TEXT),
	BOOLEAN(CastTypeKind.BOOLEAN), INTEGER_BOOLEAN(CastTypeKind.NUMERIC), YN_BOOLEAN(CastTypeKind.TEXT), TF_BOOLEAN(CastTypeKind.TEXT),
	INTEGER(CastTypeKind.NUMERIC), LONG(CastTypeKind.NUMERIC),
	FLOAT(CastTypeKind.NUMERIC), DOUBLE(CastTypeKind.NUMERIC),
	FIXED(CastTypeKind.NUMERIC),
	DATE(CastTypeKind.TEMPORAL), TIME(CastTypeKind.TEMPORAL), TIMESTAMP(CastTypeKind.TEMPORAL),
	OFFSET_TIMESTAMP(CastTypeKind.TEMPORAL), ZONE_TIMESTAMP(CastTypeKind.TEMPORAL),
	NULL(null),
	OTHER(null);

	private final CastTypeKind kind;

	CastType(CastTypeKind kind) {
		this.kind = kind;
	}

	public CastTypeKind getKind() {
		return kind;
	}

}
