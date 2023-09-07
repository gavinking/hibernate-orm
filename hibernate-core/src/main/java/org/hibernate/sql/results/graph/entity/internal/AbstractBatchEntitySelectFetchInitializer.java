/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.sql.results.graph.entity.internal;

import java.util.function.Consumer;

import org.hibernate.bytecode.enhance.spi.interceptor.EnhancementAsProxyLazinessInterceptor;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.PersistentAttributeInterceptable;
import org.hibernate.engine.spi.PersistentAttributeInterceptor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.internal.ToOneAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.results.graph.AbstractFetchParentAccess;
import org.hibernate.sql.results.graph.DomainResultAssembler;
import org.hibernate.sql.results.graph.FetchParentAccess;
import org.hibernate.sql.results.graph.entity.EntityInitializer;
import org.hibernate.sql.results.graph.entity.LoadingEntityEntry;
import org.hibernate.sql.results.jdbc.spi.RowProcessingState;

import static org.hibernate.engine.internal.ManagedTypeHelper.asPersistentAttributeInterceptableOrNull;

public abstract class AbstractBatchEntitySelectFetchInitializer extends AbstractFetchParentAccess
		implements EntityInitializer {

	protected final FetchParentAccess parentAccess;
	private final NavigablePath navigablePath;

	protected final EntityPersister concreteDescriptor;
	protected final DomainResultAssembler<?> identifierAssembler;
	protected final ToOneAttributeMapping referencedModelPart;
	protected final EntityInitializer firstEntityInitializer;

	protected Object initializedEntityInstance;
	protected EntityKey entityKey;

	protected State state = State.UNINITIALIZED;

	public AbstractBatchEntitySelectFetchInitializer(
			FetchParentAccess parentAccess,
			ToOneAttributeMapping referencedModelPart,
			NavigablePath fetchedNavigable,
			EntityPersister concreteDescriptor,
			DomainResultAssembler<?> identifierAssembler) {
		this.parentAccess = parentAccess;
		this.referencedModelPart = referencedModelPart;
		this.navigablePath = fetchedNavigable;
		this.concreteDescriptor = concreteDescriptor;
		this.identifierAssembler = identifierAssembler;
		this.firstEntityInitializer = parentAccess.findFirstEntityInitializer();
		assert firstEntityInitializer != null : "This initializer requires parentAccess.findFirstEntityInitializer() to not be null";
	}

	public ModelPart getInitializedPart() {
		return referencedModelPart;
	}

	@Override
	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	@Override
	public void resolveKey(RowProcessingState rowProcessingState) {
	}

	@Override
	public void initializeInstance(RowProcessingState rowProcessingState) {
	}

	protected abstract void registerResolutionListener();

	protected void resolveKey(
			RowProcessingState rowProcessingState,
			ToOneAttributeMapping referencedModelPart,
			FetchParentAccess parentAccess) {

		if ( state != State.UNINITIALIZED ) {
			return;
		}
		if ( !isAttributeAssignableToConcreteDescriptor( parentAccess, referencedModelPart ) ) {
			state = State.MISSING;
			return;
		}

		final Object entityIdentifier = identifierAssembler.assemble( rowProcessingState );
		if ( entityIdentifier == null ) {
			state = State.MISSING;
		}
		else {
			entityKey = new EntityKey( entityIdentifier, concreteDescriptor );
			state = State.KEY_RESOLVED;
		}
	}

	protected Object getExistingInitializedInstance(RowProcessingState rowProcessingState) {
		assert entityKey != null;
		final SharedSessionContractImplementor session = rowProcessingState.getSession();
		final PersistenceContext persistenceContext = session.getPersistenceContext();
		final Object instance = persistenceContext.getEntity( entityKey );
		if ( instance == null ) {
			final LoadingEntityEntry loadingEntityEntry = persistenceContext
					.getLoadContexts().findLoadingEntityEntry( entityKey );
			if ( loadingEntityEntry != null ) {
				return loadingEntityEntry.getEntityInstance();
			}
		}
		else if ( isInitialized( instance ) ) {
			return instance;
		}
		else {
			// the instance is not initialized but there is another initialzier that is loading it
			final LoadingEntityEntry loadingEntityEntry = persistenceContext
					.getLoadContexts().findLoadingEntityEntry( entityKey );
			if ( loadingEntityEntry != null ) {
				return loadingEntityEntry.getEntityInstance();
			}
		}
		// we need to register a resolution listener only if there is not an already initialized instance
		// or an instance that another initialzier is loading
		registerResolutionListener();
		return null;
	}

	private boolean isInitialized(Object entity) {
		final PersistentAttributeInterceptable attributeInterceptable = asPersistentAttributeInterceptableOrNull(
				entity );
		if ( attributeInterceptable == null ) {
			return true;
		}
		final PersistentAttributeInterceptor interceptor =
				attributeInterceptable.$$_hibernate_getInterceptor();
		if ( interceptor instanceof EnhancementAsProxyLazinessInterceptor ) {
			return ( (EnhancementAsProxyLazinessInterceptor) interceptor ).isInitialized();
		}
		else {
			return true;
		}
	}

	protected void registerToBatchFetchQueue(RowProcessingState rowProcessingState) {
		assert entityKey != null;
		rowProcessingState.getSession().getPersistenceContext()
				.getBatchFetchQueue().addBatchLoadableEntityKey( entityKey );
	}

	@Override
	public void finishUpRow(RowProcessingState rowProcessingState) {
		initializedEntityInstance = null;
		entityKey = null;
		state = State.UNINITIALIZED;
		clearResolutionListeners();
	}

	@Override
	public EntityPersister getEntityDescriptor() {
		return concreteDescriptor;
	}

	@Override
	public Object getEntityInstance() {
		return initializedEntityInstance;
	}

	@Override
	public EntityKey getEntityKey() {
		return entityKey;
	}

	@Override
	public Object getParentKey() {
		return findFirstEntityInitializer().getEntityKey().getIdentifier();
	}

	@Override
	public void registerResolutionListener(Consumer<Object> listener) {
		if ( initializedEntityInstance != null ) {
			listener.accept( initializedEntityInstance );
		}
		else {
			super.registerResolutionListener( listener );
		}
	}

	protected static Object loadInstance(
			EntityKey entityKey,
			ToOneAttributeMapping referencedModelPart,
			SharedSessionContractImplementor session) {
		return session.internalLoad(
				entityKey.getEntityName(),
				entityKey.getIdentifier(),
				true,
				referencedModelPart.isInternalLoadNullable()
		);
	}

	protected AttributeMapping getParentEntityAttribute(String attributeName) {
		final AttributeMapping parentAttribute = firstEntityInitializer.getConcreteDescriptor()
				.findAttributeMapping( attributeName );
		if ( parentAttribute != null && parentAttribute.getDeclaringType() == referencedModelPart.getDeclaringType()
				.findContainingEntityMapping() ) {
			// These checks are needed to avoid setting the instance using the wrong (child's) model part or
			// setting it multiple times in case parent and child share the same attribute name for the association.
			return parentAttribute;
		}
		return null;
	}

	@Override
	public FetchParentAccess getFetchParentAccess() {
		return parentAccess;
	}

	@Override
	public EntityPersister getConcreteDescriptor() {
		return concreteDescriptor;
	}

	enum State {
		UNINITIALIZED,
		MISSING,
		KEY_RESOLVED,
		INITIALIZED
	}

}
