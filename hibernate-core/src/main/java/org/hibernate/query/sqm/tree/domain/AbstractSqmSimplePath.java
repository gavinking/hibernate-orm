/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.query.sqm.tree.domain;

import org.hibernate.query.sqm.tree.SqmRenderContext;
import org.hibernate.spi.NavigablePath;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SqmPathSource;

import java.util.Objects;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractSqmSimplePath<T> extends AbstractSqmPath<T> implements SqmSimplePath<T> {

	public AbstractSqmSimplePath(
			NavigablePath navigablePath,
			SqmPathSource<T> referencedPathSource,
			SqmPath<?> lhs,
			NodeBuilder nodeBuilder) {
		this( navigablePath, referencedPathSource, lhs, null, nodeBuilder );
	}

	public AbstractSqmSimplePath(
			NavigablePath navigablePath,
			SqmPathSource<T> referencedPathSource,
			SqmPath<?> lhs,
			String explicitAlias,
			NodeBuilder nodeBuilder) {
		super( navigablePath, referencedPathSource, lhs, nodeBuilder );

		setExplicitAlias( explicitAlias );
	}

	@Override
	public void appendHqlString(StringBuilder hql, SqmRenderContext context) {
		if ( getLhs() != null ) {
			getLhs().appendHqlString( hql, context );
			hql.append( '.' );
		}
		hql.append( getReferencedPathSource().getPathName() );
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof AbstractSqmSimplePath<?> that
			&& getClass() == other.getClass()
			&& Objects.equals( this.getNavigablePath(), that.getNavigablePath() );
	}

	@Override
	public int hashCode() {
		return getNavigablePath().hashCode();
	}

	@Override
	public SqmPathSource<T> getNodeType() {
		return getReferencedPathSource();
	}

	@Override
	public SqmPathSource<T> getReferencedPathSource() {
		final SqmPathSource<T> pathSource = super.getNodeType();
		return pathSource instanceof SqmSingularPersistentAttribute<?, T> attribute
				? attribute.getSqmPathSource()
				: pathSource;
	}

	@Override
	public SqmPathSource<T> getModel() {
		return super.getNodeType();
	}
}
