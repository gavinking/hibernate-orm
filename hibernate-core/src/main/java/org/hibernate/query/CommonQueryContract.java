/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.query;

import java.time.Instant;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import org.hibernate.FlushMode;
import org.hibernate.ForcedFlushMode;
import org.hibernate.Session;

import jakarta.persistence.FlushModeType;
import jakarta.persistence.Parameter;
import jakarta.persistence.TemporalType;

/**
 * Defines the aspects of query execution and parameter binding that apply to all
 * forms of querying: HQL/JPQL queries, native SQL queries,
 * {@linkplain jakarta.persistence.criteria.CriteriaBuilder criteria queries}, and
 * {@linkplain org.hibernate.procedure.ProcedureCall stored procedure calls}.
 *
 * @author Steve Ebersole
 * @author Gavin King
 */
public interface CommonQueryContract {

	/**
	 * The {@link ForcedFlushMode} in effect for this query.
	 * <p>
	 * By default, this is {@link ForcedFlushMode#NO_FORCING}, and the
	 * {@link FlushMode} of the owning {@link Session} determines whether
	 * it is flushed.
	 *
	 * @see Session#getHibernateFlushMode()
	 */
	ForcedFlushMode getForcedFlushMode();

	/**
	 * Set the {@link ForcedFlushMode} in to use for this query.
	 *
	 * @see Session#getHibernateFlushMode()
	 */
	CommonQueryContract setForcedFlushMode(ForcedFlushMode forcedFlushMode);

	/**
	 * The JPA {@link FlushModeType} in effect for this query.  By default, the
	 * query inherits the {@link FlushMode} of the {@link Session} from which
	 * it originates.
	 *
	 * @see #getForcedFlushMode()
	 * @see #getHibernateFlushMode()
	 * @see Session#getHibernateFlushMode()
	 *
	 * @deprecated use {@link #getForcedFlushMode()}
	 */
	@Deprecated(since = "6")
	FlushModeType getFlushMode();

	/**
	 * Set the {@link FlushMode} in to use for this query.
	 * <p>
	 * Setting this to {@code null} ultimately indicates to use the
	 * {@link FlushMode} of the Session. Use {@link #setHibernateFlushMode}
	 * passing {@link FlushMode#MANUAL} instead to indicate that no automatic
	 * flushing should occur.
	 *
	 * @see #getForcedFlushMode()
	 * @see #getHibernateFlushMode()
	 * @see Session#getHibernateFlushMode()
	 *
	 * @deprecated use {@link #setForcedFlushMode(ForcedFlushMode)}
	 */
	@Deprecated(since = "6")
	CommonQueryContract setFlushMode(FlushModeType flushMode);

	/**
	 * The {@link FlushMode} in effect for this query. By default, the query
	 * inherits the {@code FlushMode} of the {@link Session} from which it
	 * originates.
	 *
	 * @see #getForcedFlushMode()
	 * @see Session#getHibernateFlushMode()
	 *
	 * @deprecated use {@link #getForcedFlushMode()}
	 */
	@Deprecated(since = "6")
	FlushMode getHibernateFlushMode();

	/**
	 * Set the current {@link FlushMode} in effect for this query.
	 *
	 * @implNote Setting to {@code null} ultimately indicates to use the
	 * {@link FlushMode} of the Session.  Use {@link FlushMode#MANUAL}
	 * instead to indicate that no automatic flushing should occur.
	 *
	 * @see #getForcedFlushMode()
	 * @see #getHibernateFlushMode()
	 * @see Session#getHibernateFlushMode()
	 *
	 * @deprecated use {@link #setForcedFlushMode(ForcedFlushMode)}
	 */
	@Deprecated(since = "6")
	CommonQueryContract setHibernateFlushMode(FlushMode flushMode);

	/**
	 * Obtain the query timeout <em>in seconds</em>.
	 * <p>
	 * This value is eventually passed along to the JDBC statement via
	 * {@link java.sql.Statement#setQueryTimeout(int)}.
	 * <p>
	 * A value of zero indicates no timeout.
	 *
	 * @see java.sql.Statement#getQueryTimeout()
	 * @see java.sql.Statement#setQueryTimeout(int)
	 */
	Integer getTimeout();

	/**
	 * Set the query timeout <em>in seconds</em>.
	 * <p>
	 * Any value set here is eventually passed directly along to the
	 * {@linkplain java.sql.Statement#setQueryTimeout(int) JDBC
	 * statement}, which expressly disallows negative values.  So
	 * negative values should be avoided as a general rule.
	 * <p>
	 * A value of zero indicates no timeout.
	 *
	 * @param timeout the timeout <em>in seconds</em>
	 *
	 * @return {@code this}, for method chaining
	 *
	 * @see #getTimeout()
	 */
	CommonQueryContract setTimeout(int timeout);

	/**
	 * Get the comment that has been set for this query, if any.
	 */
	String getComment();

	/**
	 * Set a comment for this query.
	 *
	 * @see Query#setComment(String)
	 */
	CommonQueryContract setComment(String comment);

	/**
	 * Set a hint. The hints understood by Hibernate are enumerated by
	 * {@link org.hibernate.jpa.AvailableHints}.
	 *
	 * @see org.hibernate.jpa.HibernateHints
	 * @see org.hibernate.jpa.SpecHints
	 */
	CommonQueryContract setHint(String hintName, Object value);

	/**
	 * Bind the given argument to a named query parameter.
	 * <p>
	 * If the type of the parameter cannot be inferred from the context in
	 * which it occurs, use one of the forms which accepts a "type".
	 *
	 * @see #setParameter(String, Object, Class)
	 * @see #setParameter(String, Object, BindableType)
	 */
	CommonQueryContract setParameter(String parameter, Object value);

	/**
	 * Bind the given argument to a named query parameter using the given
	 * {@link Class} reference to attempt to infer the {@link BindableType}.
	 * If unable to infer an appropriate {@link BindableType}, fall back to
	 * {@link #setParameter(String, Object)}.
	 *
	 * @see #setParameter(String, Object, BindableType)
	 */
	<P> CommonQueryContract setParameter(String parameter, P value, Class<P> type);

	/**
	 * Bind the given argument to a named query parameter using the given
	 * {@link BindableType}.
	 */
	<P> CommonQueryContract setParameter(String parameter, P value, BindableType<P> type);

	/**
	 * Bind an {@link Instant} to the named query parameter using just the
	 * portion indicated by the given {@link TemporalType}.
	 */
	CommonQueryContract setParameter(String parameter, Instant value, TemporalType temporalType);

	/**
	 * @see jakarta.persistence.Query#setParameter(String, Calendar, TemporalType)
	 */
	CommonQueryContract setParameter(String parameter, Calendar value, TemporalType temporalType);

	/**
	 * @see jakarta.persistence.Query#setParameter(String, Date, TemporalType)
	 */
	CommonQueryContract setParameter(String parameter, Date value, TemporalType temporalType);

	/**
	 * Bind the given argument to an ordinal query parameter.
	 * <p>
	 * If the type of the parameter cannot be inferred from the context in which
	 * it occurs, use one of the forms which accepts a "type".
	 *
	 * @see #setParameter(int, Object, Class)
	 * @see #setParameter(int, Object, BindableType)
	 */
	CommonQueryContract setParameter(int parameter, Object value);

	/**
	 * Bind the given argument to an ordinal query parameter using the given
	 * {@link Class} reference to attempt to infer the {@link BindableType}.
	 * If unable to infer an appropriate {@link BindableType}, fall back to
	 * {@link #setParameter(int, Object)}.
	 *
	 * @see #setParameter(int, Object, BindableType)
	 */
	<P> CommonQueryContract setParameter(int parameter, P value, Class<P> type);

	/**
	 * Bind the given argument to an ordinal query parameter using the given
	 * {@link BindableType}.
	 */
	<P> CommonQueryContract setParameter(int parameter, P value, BindableType<P> type);

	/**
	 * Bind an {@link Instant} to an ordinal query parameter using just the
	 * portion indicated by the given {@link TemporalType}.
	 */
	CommonQueryContract setParameter(int parameter, Instant value, TemporalType temporalType);

	/**
	 * @see jakarta.persistence.Query#setParameter(int, Date, TemporalType)
	 */
	CommonQueryContract setParameter(int parameter, Date value, TemporalType temporalType);

	/**
	 * @see jakarta.persistence.Query#setParameter(int, Calendar, TemporalType)
	 */
	CommonQueryContract setParameter(int parameter, Calendar value, TemporalType temporalType);

	/**
	 * Bind an argument to the query parameter represented by the given
	 * {@link QueryParameter}.
	 * <p>
	 * If the type of the parameter cannot be inferred from the context in which
	 * it occurs, use one of the forms which accepts a "type".
	 *
	 * @see #setParameter(QueryParameter, Object, BindableType)
	 *
	 * @param parameter the query parameter memento
	 * @param value the argument, which might be null
	 *
	 * @return {@code this}, for method chaining
	 */
	<T> CommonQueryContract setParameter(QueryParameter<T> parameter, T value);

	/**
	 * Bind an argument to the query parameter represented by the given
	 * {@link QueryParameter}, using the given {@link Class} reference to attempt
	 * to infer the {@link BindableType} to use.  If unable to infer an appropriate
	 * {@link BindableType}, fall back to {@link #setParameter(QueryParameter, Object)}.
	 *
	 * @param parameter the query parameter memento
	 * @param value the argument, which might be null
	 * @param type a {@link BindableType} representing the type of the parameter
	 *
	 * @return {@code this}, for method chaining
	 *
	 * @see #setParameter(QueryParameter, Object, BindableType)
	 */
	<P> CommonQueryContract setParameter(QueryParameter<P> parameter, P value, Class<P> type);

	/**
	 * Bind an argument to the query parameter represented by the given
	 * {@link QueryParameter}, using the given {@link BindableType}.
	 *
	 * @param parameter the query parameter memento
	 * @param val the argument, which might be null
	 * @param type a {@link BindableType} representing the type of the parameter
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameter(QueryParameter<P> parameter, P val, BindableType<P> type);

	/**
	 * @see jakarta.persistence.Query#setParameter(Parameter, Object)
	 */
	<T> CommonQueryContract setParameter(Parameter<T> param, T value);

	/**
	 * @see jakarta.persistence.Query#setParameter(Parameter, Calendar, TemporalType)
	 */
	CommonQueryContract setParameter(Parameter<Calendar> param, Calendar value, TemporalType temporalType);

	/**
	 * @see jakarta.persistence.Query#setParameter(Parameter, Date, TemporalType)
	 */
	CommonQueryContract setParameter(Parameter<Date> param, Date value, TemporalType temporalType);

	/**
	 * Bind multiple arguments to a named query parameter.
	 * <p>
	 * The "type mapping" for the binding is inferred from the type of
	 * the first collection element.
	 *
	 * @see #setParameterList(java.lang.String, java.util.Collection, BindableType)
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	CommonQueryContract setParameterList(String parameter, @SuppressWarnings("rawtypes") Collection values);

	/**
	 * Bind multiple arguments to a named query parameter using the given
	 * {@link Class} reference to attempt to infer the {@link BindableType}
	 * If unable to infer an appropriate {@link BindableType}, fall back to
	 * {@link #setParameterList(String, Collection)}.
	 *
	 * @see #setParameterList(java.lang.String, java.util.Collection, BindableType)
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(String parameter, Collection<? extends P> values, Class<P> javaType);

	/**
	 * Bind multiple arguments to a named query parameter using the given
	 * {@link BindableType}.
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(String parameter, Collection<? extends P> values, BindableType<P> type);


	/**
	 * Bind multiple arguments to a named query parameter.
	 * <p>
	 * The "type mapping" for the binding is inferred from the type of
	 * the first collection element
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	CommonQueryContract setParameterList(String parameter, Object[] values);

	/**
	 * Bind multiple arguments to a named query parameter using the given
	 * Class reference to attempt to determine the {@link BindableType}
	 * to use.  If unable to determine an appropriate {@link BindableType},
	 * {@link #setParameterList(String, Collection)} is used
	 *
	 * @see #setParameterList(java.lang.String, Object[], BindableType)
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(String parameter, P[] values, Class<P> javaType);


	/**
	 * Bind multiple arguments to a named query parameter using the given
	 * {@link BindableType}.
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(String parameter, P[] values, BindableType<P> type);

	/**
	 * Bind multiple arguments to an ordinal query parameter.
	 * <p>
	 * The "type mapping" for the binding is inferred from the type of
	 * the first collection element
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	CommonQueryContract setParameterList(int parameter, @SuppressWarnings("rawtypes") Collection values);

	/**
	 * Bind multiple arguments to an ordinal query parameter using the given
	 * {@link Class} reference to attempt to infer the {@link BindableType}.
	 * If unable to infer an appropriate {@link BindableType}, fall back to
	 * {@link #setParameterList(String, Collection)}.
	 *
	 * @see #setParameterList(int, Collection, BindableType)
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(int parameter, Collection<? extends P> values, Class<P> javaType);

	/**
	 * Bind multiple arguments to an ordinal query parameter using the given
	 * {@link BindableType}.
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(int parameter, Collection<? extends P> values, BindableType<P> type);

	/**
	 * Bind multiple arguments to an ordinal query parameter.
	 * <p>
	 * The "type mapping" for the binding is inferred from the type of
	 * the first collection element
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	CommonQueryContract setParameterList(int parameter, Object[] values);

	/**
	 * Bind multiple arguments to an ordinal query parameter using the given
	 * {@link Class} reference to attempt to infer the {@link BindableType}.
	 * If unable to infer an appropriate {@link BindableType}, fall back to
	 * {@link #setParameterList(String, Collection)}.
	 *
	 * @see #setParameterList(int, Object[], BindableType)
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(int parameter, P[] values, Class<P> javaType);

	/**
	 * Bind multiple arguments to an ordinal query parameter using the given
	 * {@link BindableType}.
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(int parameter, P[] values, BindableType<P> type);

	/**
	 * Bind multiple arguments to the query parameter represented by the
	 * given {@link QueryParameter}.
	 * <p>
	 * The type of the parameter is inferred from the context in which it
	 * occurs, and from the type of the first given argument.
	 *
	 * @param parameter the parameter memento
	 * @param values a collection of arguments
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(QueryParameter<P> parameter, Collection<? extends P> values);

	/**
	 * Bind multiple arguments to the query parameter represented by the
	 * given {@link QueryParameter} using the given {@link Class} reference
	 * to attempt to infer the {@link BindableType} to use.  If unable to
	 * infer an appropriate {@link BindableType}, fall back to using
	 * {@link #setParameterList(String, Collection)}.
	 *
	 * @see #setParameterList(QueryParameter, java.util.Collection, BindableType)
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(QueryParameter<P> parameter, Collection<? extends P> values, Class<P> javaType);

	/**
	 * Bind multiple arguments to the query parameter represented by the
	 * given {@link QueryParameter}, using the given {@link BindableType}.
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(QueryParameter<P> parameter, Collection<? extends P> values, BindableType<P> type);

	/**
	 * Bind multiple arguments to the query parameter represented by the
	 * given {@link QueryParameter}.
	 * <p>
	 * The type of the parameter is inferred between the context in which it
	 * occurs, the type associated with the {@code QueryParameter} and the
	 * type of the first given argument.
	 *
	 * @param parameter the parameter memento
	 * @param values a collection of arguments
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(QueryParameter<P> parameter, P[] values);

	/**
	 * Bind multiple arguments to the query parameter represented by the
	 * given {@link QueryParameter} using the given {@link Class} reference
	 * to attempt to infer the {@link BindableType} to use.  If unable to
	 * infer an appropriate {@link BindableType}, fall back to using
	 * {@link #setParameterList(String, Collection)}.
	 *
	 * @see #setParameterList(QueryParameter, Object[], BindableType)
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(QueryParameter<P> parameter, P[] values, Class<P> javaType);

	/**
	 * Bind multiple arguments to the query parameter represented by the
	 * given {@link QueryParameter}, using the given the {@link BindableType}.
	 *
	 * @apiNote This is used for binding a list of values to an expression
	 * such as {@code entity.field in (:values)}.
	 *
	 * @return {@code this}, for method chaining
	 */
	<P> CommonQueryContract setParameterList(QueryParameter<P> parameter, P[] values, BindableType<P> type);

	/**
	 * Bind the property values of the given bean to named parameters of
	 * the query, matching property names with parameter names and mapping
	 * property types to Hibernate types using heuristics.
	 *
	 * @param bean any JavaBean or POJO
	 *
	 * @return {@code this}, for method chaining
	 */
	CommonQueryContract setProperties(Object bean);

	/**
	 * Bind the values of the given {@code Map} to named parameters of the
	 * query, matching key names with parameter names and mapping value types
	 * to Hibernate types using heuristics.
	 *
	 * @param bean a {@link Map} of names to arguments
	 *
	 * @return {@code this}, for method chaining
	 */
	CommonQueryContract setProperties(@SuppressWarnings("rawtypes") Map bean);
}
