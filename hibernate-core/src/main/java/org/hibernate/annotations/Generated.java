/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.hibernate.generator.EventType;
import org.hibernate.generator.internal.GeneratedGeneration;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hibernate.generator.EventType.INSERT;

/**
 * Specifies that the value of the annotated property is generated by the
 * database. The generated value will be automatically retrieved using a
 * SQL {@code select} after it is generated.
 * <p>
 * {@code @Generated} relieves the program of the need to explicitly call
 * {@link org.hibernate.Session#refresh(Object) refresh()} to synchronize
 * state held in memory with state generated by the database when a SQL
 * {@code insert} or {@code update} is executed.
 * <p>
 * This is most useful when:
 * <ul>
 * <li>a database table has a column value populated by a database trigger,
 * <li>a mapped column has a default value defined in DDL, in which case
 *     {@code @Generated} is used in conjunction with {@link ColumnDefault},
 * <li>a {@linkplain #sql() SQL expression} is used to compute the value of
 *     a mapped column,
 * <li>a custom SQL {@link SQLInsert insert} or {@link SQLUpdate update}
 *     statement specified by an entity assigns a value to the annotated
 *     property of the entity, or {@linkplain #writable transforms} the
 *     value currently assigned to the annotated property, or
 * <li>there is no mapped column, and the value of the field is determined
 *     by evaluating a SQL {@link Formula}.
 * </ul>
 * <p>
 * On the other hand:
 * <ul>
 * <li>for identity/autoincrement columns mapped to an identifier property,
 *     use {@link jakarta.persistence.GeneratedValue}, and
 * <li>for columns with a {@code generated always as} clause, prefer the
 *     {@link GeneratedColumn} annotation, so that Hibernate automatically
 *     generates the correct DDL.
 * </ul>
 * <p>
 * A {@code @Generated} field may be generated on
 * {@linkplain EventType#INSERT inserts}, on
 * {@linkplain EventType#UPDATE updates}, or on both inserts and updates,
 * as specified by the {@link #event} member.
 * By default, {@code @Generated} fields are not immutable, and so a field
 * which is generated on insert may later be explicitly assigned a new value
 * by the application program, resulting in its value being updated in the
 * database. If this is not desired, the {@link Immutable @Immutable}
 * annotation may be used in conjunction with {@code @Generated} to specify
 * that the field may never be updated after initial generation of its value.
 *
 * @author Emmanuel Bernard
 *
 * @see jakarta.persistence.GeneratedValue
 * @see ColumnDefault
 * @see GeneratedColumn
 * @see Formula
 * @see Immutable
 */
@ValueGenerationType( generatedBy = GeneratedGeneration.class )
@IdGeneratorType( GeneratedGeneration.class )
@Target( {FIELD, METHOD} )
@Retention( RUNTIME )
public @interface Generated {
	/**
	 * Specifies the events that cause the value to be generated by the
	 * database.
	 * <ul>
	 * <li>If {@link EventType#INSERT} is included, the generated value
	 *     will be selected after each SQL {@code insert} statement is
	 *     executed.
	 * <li>If {@link EventType#UPDATE} is included, the generated value
	 *     will be selected after each SQL {@code update} statement is
	 *     executed.
	 * </ul>
	 */
	EventType[] event() default INSERT;

	/**
	 * A SQL expression used to generate the value of the column mapped by
	 * the annotated property. The expression is included in generated SQL
	 * {@code insert} and {@code update} statements.
	 */
	String sql() default "";

	/**
	 * Determines if the value currently assigned to the annotated property
	 * is included in SQL {@code insert} and {@code update} statements. This
	 * is useful if the generated value is obtained by transforming the
	 * assigned property value as it is being written.
	 * <p>
	 * Often used in combination with {@link SQLInsert}, {@link SQLUpdate},
	 * or {@link ColumnTransformer#write()}.
	 *
	 * @return {@code true} if the current value should be included in SQL
	 *         {@code insert} and {@code update} statements.
	 */
	boolean writable() default false;
}
