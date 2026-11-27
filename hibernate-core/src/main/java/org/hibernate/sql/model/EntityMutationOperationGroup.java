package org.hibernate.sql.model;

import org.hibernate.SPI;
import org.hibernate.generator.values.GeneratedValuesMutationDelegate;
import org.hibernate.id.insert.GetGeneratedKeysDelegate;
import org.hibernate.persister.entity.mutation.EntityMutationTarget;
import org.hibernate.sql.spi.mutation.SelfExecutingUpdateOperation;
import org.hibernate.sql.spi.mutation.jdbc.MergeOperation;

import static org.hibernate.SPI.Role.USE;

/**
 * @deprecated This contract is used only by the legacy action queue. The graph-based
 * action queue decomposes mutations into individual flush operations.
 */
@Deprecated(since = "8.0", forRemoval = true)
@SPI(USE)
public interface EntityMutationOperationGroup extends MutationOperationGroup {

	/**
	 * The model-part being mutated.
	 * N.B. it returns a widened type compared to the same method in the super interface.
	 */
	@Override
	EntityMutationTarget getMutationTarget();

	@Override
	default EntityMutationOperationGroup asEntityMutationOperationGroup() {
		return this;
	}

	default GeneratedValuesMutationDelegate getMutationDelegate() {
		final var target = getMutationTarget();
		final var delegate = target.getMutationDelegate( getMutationType() );
		if ( delegate == null ) {
			return null;
		}
		final var operation = getOperation( target.getIdentifierTableName() );
		if ( operation == null || operation instanceof SelfExecutingUpdateOperation ) {
			// Generated values belong to a JDBC mutation of the identifier table.
			return null;
		}
		if ( operation instanceof MergeOperation merge ) {
			if ( merge.hasConditionalVersion() ) {
				return delegate;
			}
			// A native upsert can use JDBC generated keys to return its optimistic lock value.
			final var version = target.getTargetPart().getVersionMapping();
			final var generator = version == null ? null : version.getVersionAttribute().getGenerator();
			return delegate instanceof GetGeneratedKeysDelegate && generator != null && generator.generatedOnExecution()
					? delegate : null;
		}
		return delegate;
	}
}
