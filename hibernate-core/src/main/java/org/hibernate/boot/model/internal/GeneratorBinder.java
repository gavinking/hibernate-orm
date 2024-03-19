/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.boot.model.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.AnnotationException;
import org.hibernate.AssertionFailure;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.annotations.ValueGenerationType;
import org.hibernate.boot.internal.GenerationStrategyInterpreter;
import org.hibernate.boot.model.IdentifierGeneratorDefinition;
import org.hibernate.boot.model.relational.ExportableProducer;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.PropertyData;
import org.hibernate.generator.AnnotationBasedGenerator;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.generator.OnExecutionGenerator;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext;
import org.hibernate.internal.CoreLogging;
import org.hibernate.mapping.GeneratorCreator;
import org.hibernate.mapping.IdentifierGeneratorCreator;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;
import org.hibernate.models.spi.AnnotationTarget;
import org.hibernate.models.spi.AnnotationUsage;
import org.hibernate.models.spi.ClassDetails;
import org.hibernate.models.spi.MemberDetails;

import org.jboss.logging.Logger;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Version;

import static org.hibernate.boot.model.internal.AnnotationHelper.extractParameterMap;
import static org.hibernate.boot.model.internal.BinderHelper.isCompositeId;
import static org.hibernate.boot.model.internal.BinderHelper.isGlobalGeneratorNameGlobal;
import static org.hibernate.mapping.SimpleValue.DEFAULT_ID_GEN_STRATEGY;

public class GeneratorBinder {

	private static final Logger LOG = CoreLogging.logger( BinderHelper.class );

	/**
	 * Apply an id generation strategy and parameters to the
	 * given {@link SimpleValue} which represents an identifier.
	 */
	public static void makeIdGenerator(
			SimpleValue id,
			MemberDetails idAttributeMember,
			String generatorType,
			String generatorName,
			MetadataBuildingContext buildingContext,
			Map<String, IdentifierGeneratorDefinition> localGenerators) {
		LOG.debugf( "#makeIdGenerator(%s, %s, %s, %s, ...)", id, idAttributeMember, generatorType, generatorName );

		final Table table = id.getTable();
		table.setIdentifierValue( id );
		//generator settings
		id.setIdentifierGeneratorStrategy( generatorType );

		final Map<String,Object> parameters = new HashMap<>();

		//always settable
		parameters.put( PersistentIdentifierGenerator.TABLE, table.getName() );

		if ( id.getColumnSpan() == 1 ) {
			parameters.put( PersistentIdentifierGenerator.PK, id.getColumns().get(0).getName() );
		}
		// YUCK!  but cannot think of a clean way to do this given the string-config based scheme
		parameters.put( PersistentIdentifierGenerator.IDENTIFIER_NORMALIZER, buildingContext.getObjectNameNormalizer() );
		parameters.put( IdentifierGenerator.GENERATOR_NAME, generatorName );

		if ( !generatorName.isEmpty() ) {
			//we have a named generator
			final IdentifierGeneratorDefinition definition = makeIdentifierGeneratorDefinition(
					generatorName,
					idAttributeMember,
					localGenerators,
					buildingContext
			);
			if ( definition == null ) {
				throw new AnnotationException( "No id generator was declared with the name '" + generatorName
						+ "' specified by '@GeneratedValue'"
						+ " (define a named generator using '@SequenceGenerator', '@TableGenerator', or '@GenericGenerator')" );
			}
			//This is quite vague in the spec but a generator could override the generator choice
			final String identifierGeneratorStrategy = definition.getStrategy();
			//yuk! this is a hack not to override 'AUTO' even if generator is set
			final boolean avoidOverriding = identifierGeneratorStrategy.equals( "identity" )
					|| identifierGeneratorStrategy.equals( "seqhilo" );
			if ( generatorType == null || !avoidOverriding ) {
				id.setIdentifierGeneratorStrategy( identifierGeneratorStrategy );
				if ( identifierGeneratorStrategy.equals( "assigned" ) ) {
					id.setNullValue( "undefined" );
				}
			}
			//checkIfMatchingGenerator(definition, generatorType, generatorName);
			parameters.putAll( definition.getParameters() );
		}
		if ( DEFAULT_ID_GEN_STRATEGY.equals( generatorType ) ) {
			id.setNullValue( "undefined" );
		}
		id.setIdentifierGeneratorParameters( parameters );
	}

	/**
	 * apply an id generator to a SimpleValue
	 */
	public static void makeIdGenerator(
			SimpleValue id,
			MemberDetails idAttributeMember,
			String generatorType,
			String generatorName,
			MetadataBuildingContext buildingContext,
			IdentifierGeneratorDefinition foreignKGeneratorDefinition) {
		Map<String, IdentifierGeneratorDefinition> localIdentifiers = null;
		if ( foreignKGeneratorDefinition != null ) {
			localIdentifiers = new HashMap<>();
			localIdentifiers.put( foreignKGeneratorDefinition.getName(), foreignKGeneratorDefinition );
		}
		makeIdGenerator( id, idAttributeMember, generatorType, generatorName, buildingContext, localIdentifiers );
	}

	private static IdentifierGeneratorDefinition makeIdentifierGeneratorDefinition(
			String name,
			MemberDetails idAttributeMember,
			Map<String, IdentifierGeneratorDefinition> localGenerators,
			MetadataBuildingContext buildingContext) {
		if ( localGenerators != null ) {
			final IdentifierGeneratorDefinition result = localGenerators.get( name );
			if ( result != null ) {
				return result;
			}
		}

		final IdentifierGeneratorDefinition globalDefinition =
				buildingContext.getMetadataCollector().getIdentifierGenerator( name );
		if ( globalDefinition != null ) {
			return globalDefinition;
		}

		LOG.debugf( "Could not resolve explicit IdentifierGeneratorDefinition - using implicit interpretation (%s)", name );

		final AnnotationUsage<GeneratedValue> generatedValue = idAttributeMember.getAnnotationUsage( GeneratedValue.class );
		if ( generatedValue == null ) {
			// this should really never happen, but it's easy to protect against it...
			return new IdentifierGeneratorDefinition( DEFAULT_ID_GEN_STRATEGY, DEFAULT_ID_GEN_STRATEGY );
		}

		return IdentifierGeneratorDefinition.createImplicit(
				name,
				idAttributeMember.getType().determineRawClass().toJavaClass(),
				generatedValue.getString( "generator" ),
				interpretGenerationType( generatedValue )
		);
	}

	private static GenerationType interpretGenerationType(AnnotationUsage<GeneratedValue> generatedValueAnn) {
		// todo (jpa32) : when can this ever be null?
		final GenerationType strategy = generatedValueAnn.getEnum( "strategy" );
		return strategy == null ? GenerationType.AUTO : strategy;
	}

	public static Map<String, IdentifierGeneratorDefinition> buildGenerators(
			AnnotationTarget annotatedElement,
			MetadataBuildingContext context) {

		final InFlightMetadataCollector metadataCollector = context.getMetadataCollector();
		final Map<String, IdentifierGeneratorDefinition> generators = new HashMap<>();

		annotatedElement.forEachAnnotationUsage( TableGenerator.class, (usage) -> {
			IdentifierGeneratorDefinition idGenerator = buildIdGenerator( usage, context );
			generators.put(
					idGenerator.getName(),
					idGenerator
			);
			metadataCollector.addIdentifierGenerator( idGenerator );
		} );

		annotatedElement.forEachAnnotationUsage( SequenceGenerator.class, (usage) -> {
			IdentifierGeneratorDefinition idGenerator = buildIdGenerator( usage, context );
			generators.put( idGenerator.getName(), idGenerator );
			metadataCollector.addIdentifierGenerator( idGenerator );
		} );

		annotatedElement.forEachAnnotationUsage( GenericGenerator.class, (usage) -> {
			final IdentifierGeneratorDefinition idGenerator = buildIdGenerator( usage, context );
			generators.put( idGenerator.getName(), idGenerator );
			metadataCollector.addIdentifierGenerator( idGenerator );
		} );

		return generators;
	}

	static String generatorType(
			MetadataBuildingContext context,
			ClassDetails entityXClass,
			boolean isComponent,
			AnnotationUsage<GeneratedValue> generatedValue) {
		if ( isComponent ) {
			//a component must not have any generator
			return DEFAULT_ID_GEN_STRATEGY;
		}
		else {
			return generatedValue == null ? DEFAULT_ID_GEN_STRATEGY : generatorType( generatedValue, entityXClass, context );
		}
	}

	static String generatorType(
			AnnotationUsage<GeneratedValue> generatedValue,
			final ClassDetails javaClass,
			MetadataBuildingContext context) {
		return GenerationStrategyInterpreter.STRATEGY_INTERPRETER.determineGeneratorName(
				generatedValue.getEnum( "strategy" ),
				new GenerationStrategyInterpreter.GeneratorNameDeterminationContext() {
					Class<?> javaType = null;
					@Override
					public Class<?> getIdType() {
						if ( javaType == null ) {
							javaType = javaClass.toJavaClass();
						}
						return javaType;
					}
					@Override
					public String getGeneratedValueGeneratorName() {
						return generatedValue.getString( "generator" );
					}
				}
		);
	}

	static IdentifierGeneratorDefinition buildIdGenerator(
			AnnotationUsage<?> generatorAnnotation,
			MetadataBuildingContext context) {
		if ( generatorAnnotation == null ) {
			return null;
		}

		final IdentifierGeneratorDefinition.Builder definitionBuilder = new IdentifierGeneratorDefinition.Builder();
		if ( TableGenerator.class.isAssignableFrom( generatorAnnotation.getAnnotationType() ) ) {
			//noinspection unchecked
			GenerationStrategyInterpreter.STRATEGY_INTERPRETER.interpretTableGenerator(
					(AnnotationUsage<TableGenerator>) generatorAnnotation,
					definitionBuilder
			);
			if ( LOG.isTraceEnabled() ) {
				LOG.tracev( "Add table generator with name: {0}", definitionBuilder.getName() );
			}
		}
		else if ( SequenceGenerator.class.isAssignableFrom( generatorAnnotation.getAnnotationType() )   ) {
			//noinspection unchecked
			GenerationStrategyInterpreter.STRATEGY_INTERPRETER.interpretSequenceGenerator(
					(AnnotationUsage<SequenceGenerator>) generatorAnnotation,
					definitionBuilder
			);
			if ( LOG.isTraceEnabled() ) {
				LOG.tracev( "Add sequence generator with name: {0}", definitionBuilder.getName() );
			}
		}
		else if ( GenericGenerator.class.isAssignableFrom( generatorAnnotation.getAnnotationType() )  ) {
			//noinspection unchecked
			final AnnotationUsage<GenericGenerator> genericGenerator = (AnnotationUsage<GenericGenerator>) generatorAnnotation;
			definitionBuilder.setName( genericGenerator.getString( "name" ) );
			final Class<? extends Generator> generatorClass = genericGenerator.getClassDetails( "type" ).toJavaClass();
			final String strategy = generatorClass.equals(Generator.class)
					? genericGenerator.getString( "strategy" )
					: generatorClass.getName();
			definitionBuilder.setStrategy( strategy );
			definitionBuilder.addParams( extractParameterMap( genericGenerator.getList( "parameters" ) ) );
			if ( LOG.isTraceEnabled() ) {
				LOG.tracev( "Add generic generator with name: {0}", definitionBuilder.getName() );
			}
		}
		else {
			throw new AssertionFailure( "Unknown Generator annotation: " + generatorAnnotation );
		}
		return definitionBuilder.build();
	}

	private static void checkGeneratorClass(Class<? extends Generator> generatorClass) {
		if ( !BeforeExecutionGenerator.class.isAssignableFrom( generatorClass )
				&& !OnExecutionGenerator.class.isAssignableFrom( generatorClass ) ) {
			throw new MappingException("Generator class '" + generatorClass.getName()
					+ "' must implement either 'BeforeExecutionGenerator' or 'OnExecutionGenerator'");
		}
	}

	private static void checkGeneratorInterfaces(Class<? extends Generator> generatorClass) {
		// we don't yet support the additional "fancy" operations of
		// IdentifierGenerator with regular generators, though this
		// would be extremely easy to add if anyone asks for it
		if ( IdentifierGenerator.class.isAssignableFrom( generatorClass ) ) {
			throw new AnnotationException("Generator class '" + generatorClass.getName()
					+ "' implements 'IdentifierGenerator' and may not be used with '@ValueGenerationType'");
		}
		if ( ExportableProducer.class.isAssignableFrom( generatorClass ) ) {
			throw new AnnotationException("Generator class '" + generatorClass.getName()
					+ "' implements 'ExportableProducer' and may not be used with '@ValueGenerationType'");
		}
	}

	/**
	 * In case the given annotation is a value generator annotation, the corresponding value generation strategy to be
	 * applied to the given property is returned, {@code null} otherwise.
	 * Instantiates the given generator annotation type, initializing it with the given instance of the corresponding
	 * generator annotation and the property's type.
	 */
	static GeneratorCreator generatorCreator(MemberDetails member, AnnotationUsage<?> annotation) {
		final Class<? extends Annotation> annotationType = annotation.getAnnotationType();
		final ValueGenerationType generatorAnnotation = annotationType.getAnnotation( ValueGenerationType.class );
		if ( generatorAnnotation == null ) {
			return null;
		}
		final Class<? extends Generator> generatorClass = generatorAnnotation.generatedBy();
		checkGeneratorClass( generatorClass );
		checkGeneratorInterfaces( generatorClass );
		return creationContext -> {
			final Generator generator = instantiateGenerator(
					annotation,
					member,
					annotationType,
					creationContext,
					GeneratorCreationContext.class,
					generatorClass
			);
			callInitialize( annotation, member, creationContext, generator );
			checkVersionGenerationAlways( member, generator );
			return generator;
		};
	}

	static IdentifierGeneratorCreator identifierGeneratorCreator(MemberDetails idAttributeMember, AnnotationUsage<? extends Annotation> annotation) {
		final Class<? extends Annotation> annotationType = annotation.getAnnotationType();
		final IdGeneratorType idGeneratorType = annotationType.getAnnotation( IdGeneratorType.class );
		assert idGeneratorType != null;
		return creationContext -> {
			final Class<? extends Generator> generatorClass = idGeneratorType.value();
			checkGeneratorClass( generatorClass );
			final Generator generator =
					instantiateGenerator(
							annotation,
							idAttributeMember,
							annotationType,
							creationContext,
							CustomIdGeneratorCreationContext.class,
							generatorClass
					);
			callInitialize( annotation, idAttributeMember, creationContext, generator );
			checkIdGeneratorTiming( annotationType, generator );
			return generator;
		};
	}

	private static <C, G extends Generator> G instantiateGenerator(
			AnnotationUsage<?> annotation,
			MemberDetails memberDetails,
			Class<? extends Annotation> annotationType,
			C creationContext,
			Class<C> contextClass,
			Class<? extends G> generatorClass) {
		try {
			try {
				return generatorClass
						.getConstructor( annotationType, Member.class, contextClass )
						.newInstance( annotation.toAnnotation(), memberDetails.toJavaMember(), creationContext);
			}
			catch (NoSuchMethodException ignore) {
				try {
					return generatorClass
							.getConstructor( annotationType )
							.newInstance( annotation.toAnnotation() );
				}
				catch (NoSuchMethodException i) {
					return generatorClass.newInstance();
				}
			}
		}
		catch (InvocationTargetException | InstantiationException | IllegalAccessException | IllegalArgumentException e ) {
			throw new HibernateException(
					"Could not instantiate generator of type '" + generatorClass.getName() + "'",
					e
			);
		}
	}

	private static <A extends Annotation> void callInitialize(
			AnnotationUsage<A> annotation,
			MemberDetails memberDetails,
			GeneratorCreationContext creationContext,
			Generator generator) {
		if ( generator instanceof AnnotationBasedGenerator) {
			// This will cause a CCE in case the generation type doesn't match the annotation type; As this would be
			// a programming error of the generation type developer and thus should show up during testing, we don't
			// check this explicitly; If required, this could be done e.g. using ClassMate
			@SuppressWarnings("unchecked")
			final AnnotationBasedGenerator<A> generation = (AnnotationBasedGenerator<A>) generator;
			generation.initialize( annotation.toAnnotation(), memberDetails.toJavaMember(), creationContext );
		}
	}

	private static void checkVersionGenerationAlways(MemberDetails property, Generator generator) {
		if ( property.hasAnnotationUsage(Version.class) ) {
			if ( !generator.generatesOnInsert() ) {
				throw new AnnotationException("Property '" + property.getName()
						+ "' is annotated '@Version' but has a 'Generator' which does not generate on inserts"
				);
			}
			if ( !generator.generatesOnUpdate() ) {
				throw new AnnotationException("Property '" + property.getName()
						+ "' is annotated '@Version' but has a 'Generator' which does not generate on updates"
				);
			}
		}
	}

	private static void checkIdGeneratorTiming(Class<? extends Annotation> annotationType, Generator generator) {
		if ( !generator.generatesOnInsert() ) {
			throw new MappingException( "Annotation '" + annotationType
					+ "' is annotated 'IdGeneratorType' but the given 'Generator' does not generate on inserts");
		}
		if ( generator.generatesOnUpdate() ) {
			throw new MappingException( "Annotation '" + annotationType
					+ "' is annotated 'IdGeneratorType' but the given 'Generator' generates on updates (it must generate only on inserts)");
		}
	}

	static void createIdGenerator(
			SimpleValue idValue,
			Map<String, IdentifierGeneratorDefinition> classGenerators,
			MetadataBuildingContext context,
			ClassDetails entityClass,
			MemberDetails idAttributeMember) {
		//manage composite related metadata
		//guess if its a component and find id data access (property, field etc)
		final AnnotationUsage<GeneratedValue> generatedValue = idAttributeMember.getAnnotationUsage( GeneratedValue.class );
		final String generatorType = generatorType( context, entityClass, isCompositeId( entityClass, idAttributeMember ), generatedValue );
		final String generatorName = generatedValue == null ? "" : generatedValue.getString( "generator" );
		if ( isGlobalGeneratorNameGlobal( context ) ) {
			buildGenerators( idAttributeMember, context );
			context.getMetadataCollector().addSecondPass( new IdGeneratorResolverSecondPass(
					idValue,
					idAttributeMember,
					generatorType,
					generatorName,
					context
			) );
		}
		else {
			//clone classGenerator and override with local values
			final Map<String, IdentifierGeneratorDefinition> generators = new HashMap<>( classGenerators );
			generators.putAll( buildGenerators( idAttributeMember, context ) );
			makeIdGenerator( idValue, idAttributeMember, generatorType, generatorName, context, generators );
		}
	}

	static IdentifierGeneratorDefinition createForeignGenerator(PropertyData mapsIdProperty) {
		final IdentifierGeneratorDefinition.Builder foreignGeneratorBuilder =
				new IdentifierGeneratorDefinition.Builder();
		foreignGeneratorBuilder.setName( "Hibernate-local--foreign generator" );
		foreignGeneratorBuilder.setStrategy( "foreign" );
		foreignGeneratorBuilder.addParam( "property", mapsIdProperty.getPropertyName() );
		return foreignGeneratorBuilder.build();
	}
}
