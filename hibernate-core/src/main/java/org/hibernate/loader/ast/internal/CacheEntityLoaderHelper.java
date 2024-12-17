/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.loader.ast.internal;

import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.bytecode.enhance.spi.interceptor.EnhancementAsProxyLazinessInterceptor;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.hibernate.cache.spi.entry.ReferenceCacheEntryImpl;
import org.hibernate.cache.spi.entry.StandardCacheEntryImpl;
import org.hibernate.engine.internal.TwoPhaseLoad;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.EntityHolder;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.PersistentAttributeInterceptor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.event.spi.LoadEvent;
import org.hibernate.event.spi.LoadEventListener;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.metamodel.model.domain.NavigableRole;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.LazyInitializer;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.hibernate.type.Type;
import org.hibernate.type.TypeHelper;

import static org.hibernate.engine.internal.CacheHelper.fromSharedCache;
import static org.hibernate.engine.internal.ManagedTypeHelper.asPersistentAttributeInterceptable;
import static org.hibernate.engine.internal.ManagedTypeHelper.isManagedEntity;
import static org.hibernate.engine.internal.ManagedTypeHelper.isPersistentAttributeInterceptable;
import static org.hibernate.engine.internal.Versioning.getVersion;
import static org.hibernate.loader.ast.internal.CacheEntityLoaderHelper.EntityStatus.INCONSISTENT_RTN_CLASS_MARKER;
import static org.hibernate.loader.ast.internal.CacheEntityLoaderHelper.EntityStatus.REMOVED_ENTITY_MARKER;
import static org.hibernate.loader.ast.internal.LoaderHelper.upgradeLock;
import static org.hibernate.proxy.HibernateProxy.extractLazyInitializer;
import static org.hibernate.sql.results.LoadingLogger.LOADING_LOGGER;
import static org.hibernate.stat.internal.StatsHelper.getRootEntityRole;

/**
 * @author Vlad Mihalcea
 */
public class CacheEntityLoaderHelper {

	public enum EntityStatus {
		MANAGED,
		REMOVED_ENTITY_MARKER,
		INCONSISTENT_RTN_CLASS_MARKER
	}

	private CacheEntityLoaderHelper() {
	}

//	@Incubating
//	public static PersistenceContextEntry loadFromSessionCache(
//			EntityKey keyToLoad,
//			LoadEventListener.LoadType options,
//			LockOptions lockOptions,
//			EventSource session) {
//		final Object old = session.getEntityUsingInterceptor( keyToLoad );
//		if ( old != null ) {
//			// this object was already loaded
//			final EntityEntry oldEntry = session.getPersistenceContext().getEntry( old );
//			if ( options.isCheckDeleted() ) {
//				if ( oldEntry.getStatus().isDeletedOrGone() ) {
//					LOADING_LOGGER.foundEntityScheduledForRemoval();
//					return new PersistenceContextEntry( old, REMOVED_ENTITY_MARKER );
//				}
//			}
//			if ( options.isAllowNulls() ) {
//				final EntityPersister persister =
//						session.getFactory().getMappingMetamodel()
//								.getEntityDescriptor( keyToLoad.getEntityName() );
//				if ( ! persister.isInstance( old ) ) {
//					LOADING_LOGGER.foundEntityWrongType();
//					return new PersistenceContextEntry( old, INCONSISTENT_RTN_CLASS_MARKER );
//				}
//			}
//			upgradeLock( old, oldEntry, lockOptions, session );
//		}
//		return new PersistenceContextEntry( old, EntityStatus.MANAGED );
//	}

	/**
	 * Attempts to locate the entity in the session-level cache.
	 * <p>
	 * If allowed to return nulls, then if the entity happens to be found in
	 * the session cache, we check the entity type for proper handling
	 * of entity hierarchies.
	 * <p>
	 * If checkDeleted was set to true, then if the entity is found in the
	 * session-level cache, its current status within the session cache
	 * is checked to see if it has previously been scheduled for deletion.
	 *
	 * @param event The load event
	 * @param keyToLoad The EntityKey representing the entity to be loaded.
	 * @param options The load options.
	 *
	 * @return The entity from the session-level cache, or null.
	 *
	 * @throws HibernateException Generally indicates problems applying a lock-mode.
	 */
	public static PersistenceContextEntry loadFromSessionCacheStatic(
			final LoadEvent event,
			final EntityKey keyToLoad,
			final LoadEventListener.LoadType options) {
		return loadFromSessionCache( event, keyToLoad, options );
	}

	/**
	 * Attempts to locate the entity in the session-level cache.
	 * <p>
	 * If allowed to return nulls, then if the entity happens to be found in
	 * the session cache, we check the entity type for proper handling
	 * of entity hierarchies.
	 * <p>
	 * If checkDeleted was set to true, then if the entity is found in the
	 * session-level cache, its current status within the session cache
	 * is checked to see if it has previously been scheduled for deletion.
	 *
	 * @param event The load event
	 * @param keyToLoad The EntityKey representing the entity to be loaded.
	 * @param options The load options.
	 *
	 * @return The entity from the session-level cache, or null.
	 *
	 * @throws HibernateException Generally indicates problems applying a lock-mode.
	 */
	public static PersistenceContextEntry loadFromSessionCache(
			final LoadEvent event,
			final EntityKey keyToLoad,
			final LoadEventListener.LoadType options) throws HibernateException {
		final SessionImplementor session = event.getSession();
		final Object old = session.getEntityUsingInterceptor( keyToLoad );
		if ( old != null ) {
			// this object was already loaded
			final EntityEntry oldEntry = session.getPersistenceContext().getEntry( old );
			if ( options.isCheckDeleted() ) {
				if ( oldEntry.getStatus().isDeletedOrGone() ) {
					LOADING_LOGGER.foundEntityScheduledForRemoval();
					return new PersistenceContextEntry( old, REMOVED_ENTITY_MARKER );
				}
			}
			if ( options.isAllowNulls() ) {
				final EntityPersister persister =
						event.getFactory().getMappingMetamodel()
								.getEntityDescriptor( keyToLoad.getEntityName() );
				if ( !persister.isInstance( old ) ) {
					LOADING_LOGGER.foundEntityWrongType();
					return new PersistenceContextEntry( old, INCONSISTENT_RTN_CLASS_MARKER );
				}
			}
			upgradeLock( old, oldEntry, event.getLockOptions(), event.getSession() );
		}
		return new PersistenceContextEntry( old, EntityStatus.MANAGED );
	}

	/**
	 * Attempts to load the entity from the second-level cache.
	 *
	 * @param event The load event
	 * @param persister The persister for the entity being requested for load
	 * @param entityKey The entity key
	 *
	 * @return The entity from the second-level cache, or null.
	 */
	public static Object loadFromSecondLevelCache(
			final LoadEvent event,
			final EntityPersister persister,
			final EntityKey entityKey) {
		final Object entity = loadFromSecondLevelCache(
				event.getSession(),
				event.getInstanceToLoad(),
				event.getLockMode(),
				persister,
				entityKey
		);
		if ( entity != null ) {
			//PostLoad is needed for EJB3
			final PostLoadEvent postLoadEvent =
					event.getPostLoadEvent()
							.setEntity( entity )
							.setId( event.getEntityId() )
							.setPersister( persister );
			event.getFactory()
					.getFastSessionServices()
					.firePostLoadEvent( postLoadEvent );
		}
		return entity;
	}

	/**
	 * Attempts to load the entity from the second-level cache.
	 *
	 * @param source The source
	 * @param entity The entity
	 * @param lockMode The lock mode
	 * @param persister The persister for the entity being requested for load
	 * @param entityKey The entity key
	 *
	 * @return The entity from the second-level cache, or null.
	 */
	public static Object loadFromSecondLevelCache(
			final SharedSessionContractImplementor source,
			final Object entity,
			final LockMode lockMode,
			final EntityPersister persister,
			final EntityKey entityKey) {
		final boolean useCache =
				persister.canReadFromCache()
						&& source.getCacheMode().isGetEnabled()
						&& lockMode.lessThan( LockMode.READ );
		if ( !useCache ) {
			// we can't use cache here
			return null;
		}
		else {
			final Object ce = getFromSharedCache( entityKey.getIdentifier(), persister, source );
			// nothing was found in cache
			return ce == null ? null : processCachedEntry( entity, persister, ce, source, entityKey );
		}
	}


	private static Object getFromSharedCache(
			final Object entityId,
			final EntityPersister persister,
			SharedSessionContractImplementor source) {
		final EntityDataAccess cache = persister.getCacheAccessStrategy();
		final SessionFactoryImplementor factory = source.getFactory();
		final Object cacheKey = cache.generateCacheKey(
				entityId,
				persister,
				factory,
				source.getTenantIdentifier()
		);
		final Object ce = fromSharedCache( source, cacheKey, persister, persister.getCacheAccessStrategy() );
		final StatisticsImplementor statistics = factory.getStatistics();
		if ( statistics.isStatisticsEnabled() ) {
			final NavigableRole rootEntityRole = getRootEntityRole( persister );
			final String regionName = cache.getRegion().getName();
			if ( ce == null ) {
				statistics.entityCacheMiss( rootEntityRole, regionName );
			}
			else {
				statistics.entityCacheHit( rootEntityRole, regionName );
			}
		}
		return ce;
	}

	private static Object processCachedEntry(
			final Object instanceToLoad,
			final EntityPersister persister,
			final Object ce,
			final SharedSessionContractImplementor source,
			final EntityKey entityKey) {
		final CacheEntry entry = (CacheEntry)
				persister.getCacheEntryStructure().destructure( ce, source.getFactory() );
		if ( entry.isReferenceEntry() ) {
			if ( instanceToLoad != null ) {
				throw new HibernateException( "Attempt to load entity from cache using provided object instance, "
						+ "but cache is storing references: " + entityKey.getIdentifier() );
			}
			else {
				return convertCacheReferenceEntryToEntity(
						(ReferenceCacheEntryImpl) entry,
						source,
						entityKey
				);
			}
		}
		else {
			final Object entity =
					convertCacheEntryToEntity(
							entry,
							entityKey.getIdentifier(),
							source,
							persister,
							instanceToLoad,
							entityKey
					);
			if ( !persister.isInstance( entity ) ) {
				// Cleanup the inconsistent return class entity from the persistence context
				final PersistenceContext persistenceContext = source.getPersistenceContext();
				persistenceContext.removeEntry( entity );
				persistenceContext.removeEntity( entityKey );
				return null;
			}
			return entity;
		}
	}

	private static Object convertCacheReferenceEntryToEntity(
			ReferenceCacheEntryImpl referenceCacheEntry,
			SharedSessionContractImplementor session,
			EntityKey entityKey) {
		final Object entity = referenceCacheEntry.getReference();
		if ( entity == null ) {
			throw new IllegalStateException( "Reference cache entry contained null: " + referenceCacheEntry );
		}
		else {
			makeEntityCircularReferenceSafe( referenceCacheEntry, session, entity, entityKey );
			return entity;
		}
	}

	private static void makeEntityCircularReferenceSafe(
			ReferenceCacheEntryImpl referenceCacheEntry,
			SharedSessionContractImplementor session,
			Object entity,
			EntityKey entityKey) {
		// make it circular-reference safe
		final PersistenceContext persistenceContext = session.getPersistenceContext();
		if ( isManagedEntity( entity ) ) {
			final EntityHolder entityHolder =
					persistenceContext.addEntityHolder( entityKey, entity );
			final EntityEntry entityEntry =
					persistenceContext.addReferenceEntry( entity, Status.READ_ONLY );
			entityHolder.setEntityEntry( entityEntry );
		}
		else {
			TwoPhaseLoad.addUninitializedCachedEntity(
					entityKey,
					entity,
					referenceCacheEntry.getSubclassPersister(),
					LockMode.NONE,
					referenceCacheEntry.getVersion(),
					session
			);
		}
		persistenceContext.initializeNonLazyCollections();
	}

	private static Object convertCacheEntryToEntity(
			CacheEntry entry,
			Object entityId,
			SharedSessionContractImplementor source,
			EntityPersister persister,
			Object instanceToLoad,
			EntityKey entityKey) {

		final EntityPersister subclassPersister =
				source.getFactory().getRuntimeMetamodels().getMappingMetamodel()
						.getEntityDescriptor( entry.getSubclass() );
		final PersistenceContext persistenceContext = source.getPersistenceContextInternal();
		final EntityHolder oldHolder = persistenceContext.getEntityHolder( entityKey );

		final Object entity;
		if ( instanceToLoad != null ) {
			entity = instanceToLoad;
		}
		else {
			if ( oldHolder != null && oldHolder.getEntity() != null ) {
				// Use the entity which might already be
				entity = oldHolder.getEntity();
			}
			else {
				entity = source.instantiate( subclassPersister, entityId );
			}
		}

		if ( isPersistentAttributeInterceptable( entity ) ) {
			PersistentAttributeInterceptor persistentAttributeInterceptor =
					asPersistentAttributeInterceptable( entity ).$$_hibernate_getInterceptor();
			// if we do this after the entity has been initialized the
			// BytecodeLazyAttributeInterceptor#isAttributeLoaded(String fieldName)
			// would return false
			if ( persistentAttributeInterceptor == null
					|| persistentAttributeInterceptor instanceof EnhancementAsProxyLazinessInterceptor ) {
				persister.getBytecodeEnhancementMetadata()
						.injectInterceptor( entity, entityId, source );
			}
		}

		// make it circular-reference safe
		final EntityHolder holder = persistenceContext.addEntityHolder( entityKey, entity );
		final Object proxy = holder.getProxy();
		final boolean isReadOnly;
		if ( proxy != null ) {
			// there is already a proxy for this impl
			// only set the status to read-only if the proxy is read-only
			final LazyInitializer lazyInitializer = extractLazyInitializer( proxy );
			assert lazyInitializer != null;
			lazyInitializer.setImplementation( entity );

			isReadOnly = lazyInitializer.isReadOnly();
		}
		else {
			isReadOnly = source.isDefaultReadOnly();
		}
		holder.setEntityEntry(
				persistenceContext.addEntry(
						entity,
						Status.LOADING,
						null,
						null,
						entityKey.getIdentifier(),
						entry.getVersion(),
						LockMode.NONE,
						true,
						persister,
						false
				)
		);

		final Type[] types = subclassPersister.getPropertyTypes();
		// initializes the entity by (desired) side effect
		final StandardCacheEntryImpl standardCacheEntry = (StandardCacheEntryImpl) entry;
		final Object[] values = standardCacheEntry.assemble(
				entity,
				entityId,
				subclassPersister,
				source.getInterceptor(),
				source
		);
		if ( standardCacheEntry.isDeepCopyNeeded() ) {
			TypeHelper.deepCopy(
					values,
					types,
					subclassPersister.getPropertyUpdateability(),
					values,
					source
			);
		}
		final Object version = getVersion( values, subclassPersister );
		holder.setEntityEntry(
				persistenceContext.addEntry(
						entity,
						isReadOnly ? Status.READ_ONLY : Status.MANAGED,
						values,
						null,
						entityId,
						version,
						LockMode.NONE,
						true,
						subclassPersister,
						false
				)
		);
		subclassPersister.afterInitialize( entity, source );
		persistenceContext.initializeNonLazyCollections();

		return entity;
	}

	public static class PersistenceContextEntry {
		private final Object entity;
		private final EntityStatus status;

		public PersistenceContextEntry(Object entity, EntityStatus status) {
			this.entity = entity;
			this.status = status;
		}

		public Object getEntity() {
			return entity;
		}

		public EntityStatus getStatus() {
			return status;
		}

		public boolean isManaged() {
			return EntityStatus.MANAGED == status;
		}
	}
}
