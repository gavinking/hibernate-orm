/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.function;

import org.hibernate.metamodel.mapping.BasicValuedMapping;
import org.hibernate.metamodel.mapping.MappingModelExpressable;
import org.hibernate.metamodel.model.domain.AllowableFunctionReturnType;
import org.hibernate.metamodel.model.domain.DomainType;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SqmPathSource;
import org.hibernate.query.sqm.produce.function.FunctionReturnTypeResolver;
import org.hibernate.query.sqm.sql.SqmToSqlAstConverter;
import org.hibernate.query.sqm.tree.SqmTypedNode;
import org.hibernate.query.sqm.tree.SqmVisitableNode;
import org.hibernate.query.sqm.tree.expression.SqmFunction;
import org.hibernate.sql.ast.tree.SqlAstNode;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * @author Steve Ebersole
 */
public class SelfRenderingSqlFunctionExpression<T> extends SqmFunction<T> {
	private final AllowableFunctionReturnType<T> impliedResultType;
	private final FunctionReturnTypeResolver returnTypeResolver;
	private final String name;
	private final FunctionRenderingSupport renderingSupport;
	private final List<SqmTypedNode<?>> arguments;

	public SelfRenderingSqlFunctionExpression(
			SqmFunctionDescriptor descriptor,
			FunctionRenderingSupport renderingSupport,
			List<SqmTypedNode<?>> arguments,
			AllowableFunctionReturnType<T> impliedResultType,
			FunctionReturnTypeResolver returnTypeResolver,
			NodeBuilder nodeBuilder,
			String name) {
		super( name, descriptor, impliedResultType, nodeBuilder );
		this.renderingSupport = renderingSupport;
		this.arguments = arguments;
		this.impliedResultType = impliedResultType;
		this.returnTypeResolver = returnTypeResolver;
		this.name = name;
	}

	public List<SqmTypedNode<?>> getArguments() {
		return arguments;
	}

	public FunctionRenderingSupport getRenderingSupport() {
		return renderingSupport;
	}

	private static List<SqlAstNode> resolveSqlAstArguments(List<SqmTypedNode<?>> sqmArguments, SqmToSqlAstConverter walker) {
		if ( sqmArguments == null || sqmArguments.isEmpty() ) {
			return emptyList();
		}

		final ArrayList<SqlAstNode> sqlAstArguments = new ArrayList<>();
		for ( SqmTypedNode<?> sqmArgument : sqmArguments ) {
			sqlAstArguments.add((SqlAstNode) ((SqmVisitableNode) sqmArgument).accept(walker));
		}
		return sqlAstArguments;
	}

	@Override
	public SelfRenderingFunctionSqlAstExpression convertToSqlAst(SqmToSqlAstConverter walker) {
		AllowableFunctionReturnType<?> resultType =
				returnTypeResolver.resolveFunctionReturnType(
						impliedResultType,
						getArguments(),
						walker.getCreationContext().getDomainModel().getTypeConfiguration()
				);

		MappingModelExpressable<?> mapping;
		if ( resultType instanceof MappingModelExpressable ) {
			mapping = (MappingModelExpressable<?>) resultType;
		}
		else {
			// here we have something that is not a BasicType,
			// and we have no way to get a BasicValuedMapping
			// from it directly
			mapping = returnTypeResolver.resolveFunctionReturnType(
					() -> null,
					resolveSqlAstArguments(arguments, walker)
			);
		}

		return new SelfRenderingFunctionSqlAstExpression(
				getRenderingSupport(),
				resolveSqlAstArguments( getArguments(), walker),
				resultType,
				mapping
		);
	}

	@Override
	public String getFunctionName() {
		return name;
	}

}
