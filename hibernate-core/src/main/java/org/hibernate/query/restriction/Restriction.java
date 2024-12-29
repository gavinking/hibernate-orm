/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.query.restriction;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;
import org.hibernate.Incubating;
import org.hibernate.Internal;
import org.hibernate.query.Order;
import org.hibernate.query.SelectionQuery;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.range.Range;

import java.util.List;

/**
 * A rule for restricting query results.
 * <p>
 * This allows restrictions to be added to a {@link SelectionQuery} by calling
 * {@link SelectionQuery#addRestriction(Restriction)}.
 * <pre>
 * session.createSelectionQuery("from Book", Book.class)
 *         .addRestriction(Restriction.like(Book_.title, "%Hibernate%", false))
 *         .addRestriction(Restriction.greaterThan(Book_.pages, 100))
 *         .setOrder(Order.desc(Book_.title))
 *         .getResultList() );
 * </pre>
 * <p>
 * Each restriction pairs an {@linkplain SingularAttribute attribute} of the
 * entity with a {@link Range} of allowed values for the attribute.
 * <p>
 * A parameter of a {@linkplain org.hibernate.annotations.processing.Find
 * finder method} or {@linkplain org.hibernate.annotations.processing.HQL
 * HQL query method} may be declared with type {@code Restriction<? super E>},
 * {@code List<Restriction<? super E>>}, or {@code Restriction<? super E>...}
 * (varargs) where {@code E} is the entity type returned by the query.
 * <p>
 * To create a {@code Restriction} on a compound path, use {@link Path}.
 *
 * @param <X> The entity result type of the query
 *
 * @apiNote This class is similar to {@code jakarta.data.Restriction}, and
 *          is used by Hibernate Data Repositories to implement Jakarta Data
 *          query methods.
 *
 * @see SelectionQuery#addRestriction(Restriction)
 * @see Path
 * @see Order
 *
 * @author Gavin King
 *
 * @since 7.0
 */
@Incubating
public interface Restriction<X> {

	/**
	 * Negate this restriction.
	 */
	Restriction<X> negated();

	default Restriction<X> or(Restriction<X> restriction) {
		return any( this, restriction );
	}

	default Restriction<X> and(Restriction<X> restriction) {
		return all( this, restriction );
	}

	/**
	 * Return a JPA Criteria {@link Predicate} constraining the given
	 * root entity by this restriction.
	 */
	@Internal
	Predicate toPredicate(Root<? extends X> root, CriteriaBuilder builder);

	/**
	 * Apply this restriction to the given root entity of the given
	 * {@linkplain CriteriaQuery criteria query}.
	 */
	default void apply(CriteriaQuery<?> query, Root<? extends X> root) {
		if ( !(query instanceof JpaCriteriaQuery<?> criteriaQuery) ) {
			throw new IllegalArgumentException( "Not a JpaCriteriaQuery" );
		}
		query.where( query.getRestriction(), toPredicate( root, criteriaQuery.getCriteriaBuilder() ) );
	}

	/**
	 * Restrict the allowed values of the given attribute to the given
	 * {@linkplain Range range}.
	 */
	static <T, U> Restriction<T> restrict(SingularAttribute<T, U> attribute, Range<U> range) {
		return new AttributeRange<>( attribute, range );
	}

	/**
	 * Restrict the allowed values of the named attribute of the given
	 * entity class to the given {@linkplain Range range}.
	 * <p>
	 * This operation is not compile-time type safe. Prefer the use of
	 * {@link #restrict(SingularAttribute, Range)}.
	 */
	static <T> Restriction<T> restrict(Class<T> type, String attributeName, Range<?> range) {
		return new NamedAttributeRange<>( type, attributeName, range );
	}

	static <T, U> Restriction<T> equal(SingularAttribute<T, U> attribute, U value) {
		return restrict( attribute, Range.singleValue( value ) );
	}

	static <T, U> Restriction<T> unequal(SingularAttribute<T, U> attribute, U value) {
		return equal( attribute, value ).negated();
	}

	static <T> Restriction<T> equalIgnoringCase(SingularAttribute<T, String> attribute, String value) {
		return restrict( attribute, Range.singleCaseInsensitiveValue( value ) );
	}

	@SafeVarargs
	static <T, U> Restriction<T> in(SingularAttribute<T, U> attribute, U... values) {
		return in( attribute, List.of(values ) );
	}

	@SafeVarargs
	static <T, U> Restriction<T> notIn(SingularAttribute<T, U> attribute, U... values) {
		return notIn( attribute, List.of(values ) );
	}

	static <T, U> Restriction<T> in(SingularAttribute<T, U> attribute, java.util.List<U> values) {
		return restrict( attribute, Range.valueList( values ) );
	}

	static <T, U> Restriction<T> notIn(SingularAttribute<T, U> attribute, java.util.List<U> values) {
		return in( attribute, values ).negated();
	}

	static <T, U extends Comparable<U>> Restriction<T> between(SingularAttribute<T, U> attribute, U lowerBound, U upperBound) {
		return restrict( attribute, Range.closed( lowerBound, upperBound ) );
	}

	static <T, U extends Comparable<U>> Restriction<T> notBetween(SingularAttribute<T, U> attribute, U lowerBound, U upperBound) {
		return between( attribute, lowerBound, upperBound ).negated();
	}

	static <T, U extends Comparable<U>> Restriction<T> greaterThan(SingularAttribute<T, U> attribute, U lowerBound) {
		return restrict( attribute, Range.greaterThan( lowerBound ) );
	}

	static <T, U extends Comparable<U>> Restriction<T> lessThan(SingularAttribute<T, U> attribute, U upperBound) {
		return restrict( attribute, Range.lessThan( upperBound ) );
	}

	static <T, U extends Comparable<U>> Restriction<T> greaterThanOrEqual(SingularAttribute<T, U> attribute, U lowerBound) {
		return restrict( attribute, Range.greaterThanOrEqualTo( lowerBound ) );
	}

	static <T, U extends Comparable<U>> Restriction<T> lessThanOrEqual(SingularAttribute<T, U> attribute, U upperBound) {
		return restrict( attribute, Range.lessThanOrEqualTo( upperBound ) );
	}

	static <T> Restriction<T> like(
			SingularAttribute<T, String> attribute,
			String pattern, boolean caseSensitive,
			char charWildcard, char stringWildcard) {
		return restrict( attribute, Range.pattern( pattern, caseSensitive, charWildcard, stringWildcard ) );
	}

	static <T> Restriction<T> like(SingularAttribute<T, String> attribute, String pattern, boolean caseSensitive) {
		return restrict( attribute, Range.pattern( pattern, caseSensitive ) );
	}

	static <T> Restriction<T> like(SingularAttribute<T, String> attribute, String pattern) {
		return like( attribute, pattern, true );
	}

	static <T> Restriction<T> notLike(SingularAttribute<T, String> attribute, String pattern) {
		return like( attribute, pattern, true ).negated();
	}

	static <T> Restriction<T> notLike(SingularAttribute<T, String> attribute, String pattern, boolean caseSensitive) {
		return like( attribute, pattern, caseSensitive ).negated();
	}

	static <T> Restriction<T> startsWith(SingularAttribute<T, String> attribute, String prefix) {
		return startsWith( attribute, prefix, true );
	}

	static <T> Restriction<T> endsWith(SingularAttribute<T, String> attribute, String suffix) {
		return endsWith( attribute, suffix, true );
	}

	static <T> Restriction<T> contains(SingularAttribute<T, String> attribute, String substring) {
		return contains( attribute, substring, true );
	}

	static <T> Restriction<T> notContains(SingularAttribute<T, String> attribute, String substring) {
		return notContains( attribute, substring, true );
	}

	static <T> Restriction<T> startsWith(SingularAttribute<T, String> attribute, String prefix, boolean caseSensitive) {
		return restrict( attribute, Range.prefix( prefix, caseSensitive ) );
	}

	static <T> Restriction<T> endsWith(SingularAttribute<T, String> attribute, String suffix, boolean caseSensitive) {
		return restrict( attribute, Range.suffix( suffix, caseSensitive ) );
	}

	static <T> Restriction<T> contains(SingularAttribute<T, String> attribute, String substring, boolean caseSensitive) {
		return restrict( attribute, Range.containing( substring, caseSensitive ) );
	}

	static <T> Restriction<T> notContains(SingularAttribute<T, String> attribute, String substring, boolean caseSensitive) {
		return contains( attribute, substring, caseSensitive ).negated();
	}

	static <T, U> Restriction<T> notNull(SingularAttribute<T, U> attribute) {
		return restrict( attribute, Range.notNull( attribute.getJavaType() ) );
	}

	static <T> Restriction<T> all(List<? extends Restriction<? super T>> restrictions) {
		return new Conjunction<>( restrictions );
	}

	static <T> Restriction<T> any(List<? extends Restriction<? super T>> restrictions) {
		return new Disjunction<>( restrictions );
	}

	@SafeVarargs
	static <T> Restriction<T> all(Restriction<? super T>... restrictions) {
		return new Conjunction<T>( java.util.List.of( restrictions ) );
	}

	@SafeVarargs
	static <T> Restriction<T> any(Restriction<? super T>... restrictions) {
		return new Disjunction<T>( java.util.List.of( restrictions ) );
	}

	static <T> Restriction<T> unrestricted() {
		return new Unrestricted<>();
	}
}
