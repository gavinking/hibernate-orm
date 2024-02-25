/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.jpamodelgen.annotation;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.hibernate.AssertionFailure;
import org.hibernate.jpamodelgen.Context;
import org.hibernate.jpamodelgen.ImportContextImpl;
import org.hibernate.jpamodelgen.ProcessLaterException;
import org.hibernate.jpamodelgen.annotation.CriteriaFinderMethod.OrderBy;
import org.hibernate.jpamodelgen.model.ImportContext;
import org.hibernate.jpamodelgen.model.MetaAttribute;
import org.hibernate.jpamodelgen.model.Metamodel;
import org.hibernate.jpamodelgen.util.AccessType;
import org.hibernate.jpamodelgen.util.AccessTypeInformation;
import org.hibernate.jpamodelgen.util.Constants;
import org.hibernate.jpamodelgen.validation.ProcessorSessionFactory;
import org.hibernate.jpamodelgen.validation.Validation;
import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.query.Order;
import org.hibernate.query.criteria.JpaEntityJoin;
import org.hibernate.query.criteria.JpaRoot;
import org.hibernate.query.criteria.JpaSelection;
import org.hibernate.query.sql.internal.ParameterParser;
import org.hibernate.query.sql.spi.ParameterRecognizer;
import org.hibernate.query.sqm.SqmExpressible;
import org.hibernate.query.sqm.tree.SqmStatement;
import org.hibernate.query.sqm.tree.expression.SqmParameter;
import org.hibernate.query.sqm.tree.select.SqmSelectStatement;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import static java.beans.Introspector.decapitalize;
import static java.lang.Boolean.FALSE;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.util.ElementFilter.fieldsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.hibernate.internal.util.StringHelper.qualify;
import static org.hibernate.jpamodelgen.annotation.QueryMethod.isOrderParam;
import static org.hibernate.jpamodelgen.annotation.QueryMethod.isPageParam;
import static org.hibernate.jpamodelgen.util.Constants.FIND;
import static org.hibernate.jpamodelgen.util.Constants.HIB_SESSION;
import static org.hibernate.jpamodelgen.util.Constants.HIB_STATELESS_SESSION;
import static org.hibernate.jpamodelgen.util.Constants.HQL;
import static org.hibernate.jpamodelgen.util.Constants.ITERABLE;
import static org.hibernate.jpamodelgen.util.Constants.JD_DELETE;
import static org.hibernate.jpamodelgen.util.Constants.JD_FIND;
import static org.hibernate.jpamodelgen.util.Constants.JD_INSERT;
import static org.hibernate.jpamodelgen.util.Constants.JD_ORDER;
import static org.hibernate.jpamodelgen.util.Constants.JD_QUERY;
import static org.hibernate.jpamodelgen.util.Constants.JD_REPOSITORY;
import static org.hibernate.jpamodelgen.util.Constants.JD_SAVE;
import static org.hibernate.jpamodelgen.util.Constants.JD_SORT;
import static org.hibernate.jpamodelgen.util.Constants.JD_UPDATE;
import static org.hibernate.jpamodelgen.util.Constants.MUTINY_SESSION;
import static org.hibernate.jpamodelgen.util.Constants.SESSION_TYPES;
import static org.hibernate.jpamodelgen.util.Constants.SQL;
import static org.hibernate.jpamodelgen.util.NullnessUtil.castNonNull;
import static org.hibernate.jpamodelgen.util.TypeUtils.containsAnnotation;
import static org.hibernate.jpamodelgen.util.TypeUtils.determineAccessTypeForHierarchy;
import static org.hibernate.jpamodelgen.util.TypeUtils.determineAnnotationSpecifiedAccessType;
import static org.hibernate.jpamodelgen.util.TypeUtils.findMappedSuperClass;
import static org.hibernate.jpamodelgen.util.TypeUtils.getAnnotationMirror;
import static org.hibernate.jpamodelgen.util.TypeUtils.getAnnotationValue;
import static org.hibernate.jpamodelgen.util.TypeUtils.getAnnotationValueRef;
import static org.hibernate.jpamodelgen.util.TypeUtils.hasAnnotation;
import static org.hibernate.jpamodelgen.util.TypeUtils.primitiveClassMatchesKind;

/**
 * Class used to collect meta information about an annotated type (entity, embeddable or mapped superclass).
 * Also repurposed for any type with "auxiliary" annotations like {@code @NamedQuery}, {@code @FetchProfile},
 * {@code @Find}, or {@code @HQL}. We do not distinguish these two kinds of thing, since an entity class may
 * {@code @NamedQuery} or {@code @FetchProfile} annotations. Entities may not, however, have methods annotated
 * {@code @Find} or {@code @HQL}, since entity classes are usually concrete classes.
 *
 * @author Max Andersen
 * @author Hardy Ferentschik
 * @author Emmanuel Bernard
 * @author Gavin King
 * @author Yanming Zhou
 */
public class AnnotationMetaEntity extends AnnotationMeta {

	private final ImportContext importContext;
	private final TypeElement element;
	private final Map<String, MetaAttribute> members;
	private final Context context;
	private final boolean managed;
	private boolean jakartaDataRepository;
	private String qualifiedName;
	private final boolean jakartaDataStaticModel;

	private AccessTypeInformation entityAccessTypeInfo;

	/**
	 * Whether the members of this type have already been initialized or not.
	 * <p>
	 * Embeddables and mapped superclasses need to be lazily initialized since the access type may be determined
	 * by the class which is embedding or subclassing the entity or superclass. This might not be known until
	 * annotations are processed.
	 * <p>
	 * Also note, that if two different classes with different access types embed this entity or extend this mapped
	 * superclass, the access type of the embeddable/superclass will be the one of the last embedding/subclassing
	 * entity processed. The result is not determined (that's ok according to the spec).
	 */
	private boolean initialized;

	/**
	 * Another meta entity for the same type which should be merged lazily with this meta entity. Doing the merge
	 * lazily is required for embeddables and mapped supertypes, to only pull in those members matching the access
	 * type as configured via the embedding entity or subclass (also see METAGEN-85).
	 */
	private Metamodel entityToMerge;

	/**
	 * True if this "metamodel class" is actually an instantiable DAO-style repository.
	 */
	private boolean repository = false;

	/**
	 * The type of the "session getter" method of a DAO-style repository.
	 */
	private String sessionType = Constants.ENTITY_MANAGER;

	private final Map<String,String> memberTypes = new HashMap<>();

	public AnnotationMetaEntity(TypeElement element, Context context, boolean managed, boolean jakartaData) {
		this.element = element;
		this.context = context;
		this.managed = managed;
		this.members = new HashMap<>();
		this.importContext = new ImportContextImpl( getPackageName( context, element ) );
		jakartaDataStaticModel = jakartaData;
	}

	public static AnnotationMetaEntity create(
			TypeElement element, Context context,
			boolean lazilyInitialised, boolean managed) {
		return create( element,context, lazilyInitialised, managed, false );
	}

	public static AnnotationMetaEntity create(
			TypeElement element, Context context,
			boolean lazilyInitialised, boolean managed, boolean jakartaData) {
		final AnnotationMetaEntity annotationMetaEntity = new AnnotationMetaEntity( element, context, managed, jakartaData );
		if ( !lazilyInitialised ) {
			annotationMetaEntity.init();
		}
		return annotationMetaEntity;
	}

	public @Nullable String getMemberType(String entityType, String memberName) {
		return memberTypes.get( qualify(entityType, memberName) );
	}

	public AccessTypeInformation getEntityAccessTypeInfo() {
		return entityAccessTypeInfo;
	}

	@Override
	public final Context getContext() {
		return context;
	}

	@Override
	public boolean isImplementation() {
		return repository;
	}

	@Override
	public boolean isJakartaDataStyle() {
		return jakartaDataStaticModel;
	}

	@Override
	public final String getSimpleName() {
		return element.getSimpleName().toString();
	}

	@Override
	public final String getQualifiedName() {
		if ( qualifiedName == null ) {
			qualifiedName = element.getQualifiedName().toString();
		}
		return qualifiedName;
	}

	@Override
	public @Nullable String getSupertypeName() {
		return findMappedSuperClass( this, context );
	}

	@Override
	public final String getPackageName() {
		return getPackageName( context, element );
	}

	private static String getPackageName(Context context, TypeElement element) {
		return context.getElementUtils().getPackageOf( element ).getQualifiedName().toString();
	}

	@Override
	public List<MetaAttribute> getMembers() {
		if ( !initialized ) {
			init();
			if ( entityToMerge != null ) {
				mergeInMembers( entityToMerge.getMembers() );
			}
		}

		return new ArrayList<>( members.values() );
	}

	@Override
	public boolean isMetaComplete() {
		return false;
	}

	private void mergeInMembers(Collection<MetaAttribute> attributes) {
		for ( MetaAttribute attribute : attributes ) {
			// propagate types to be imported
			importType( attribute.getMetaType() );
			importType( attribute.getTypeDeclaration() );

			members.put( attribute.getPropertyName(), attribute );
		}
	}

	public void mergeInMembers(Metamodel other) {
		// store the entity in order do the merge lazily in case of
		// an uninitialized embeddedable or mapped superclass
		if ( !initialized ) {
			this.entityToMerge = other;
		}
		else {
			mergeInMembers( other.getMembers() );
		}
	}

	@Override
	public final String generateImports() {
		return importContext.generateImports();
	}

	@Override
	public final String importType(String fqcn) {
		return importContext.importType( fqcn );
	}

	@Override
	public final String staticImport(String fqcn, String member) {
		return importContext.staticImport( fqcn, member );
	}

	@Override
	public final TypeElement getElement() {
		return element;
	}

	@Override
	void putMember(String name, MetaAttribute nameMetaAttribute) {
		members.put( name, nameMetaAttribute );
	}

	@Override
	boolean isRepository() {
		return repository;
	}

	@Override
	String getSessionType() {
		return sessionType;
	}

	@Override
	public boolean isInjectable() {
		return repository;
	}

	@Override
	public String scope() {
		if (jakartaDataRepository) {
			return context.addTransactionScopedAnnotation()
					? "javax.transaction.TransactionScoped"
					: "jakarta.enterprise.context.RequestScoped";
		}
		else {
			return "jakarta.enterprise.context.Dependent";
		}
	}

	@Override
	public String toString() {
		return new StringBuilder()
				.append( "AnnotationMetaEntity" )
				.append( "{element=" )
				.append( element )
				.append( ", members=" )
				.append( members )
				.append( '}' )
				.toString();
	}

	protected final void init() {
		getContext().logMessage( Diagnostic.Kind.OTHER, "Initializing type '" + getQualifiedName() + "'" );

		determineAccessTypeForHierarchy( element, context );
		entityAccessTypeInfo = castNonNull( context.getAccessTypeInfo( getQualifiedName() ) );

		final List<VariableElement> fieldsOfClass = fieldsIn( element.getEnclosedElements() );
		final List<ExecutableElement> methodsOfClass = methodsIn( element.getEnclosedElements() );
		final List<ExecutableElement> gettersAndSettersOfClass = new ArrayList<>();
		final List<ExecutableElement> queryMethods = new ArrayList<>();
		final List<ExecutableElement> lifecycleMethods = new ArrayList<>();
		for ( ExecutableElement method: methodsOfClass ) {
			if ( isGetterOrSetter( method ) ) {
				gettersAndSettersOfClass.add( method );
			}
			else if ( containsAnnotation( method, HQL, SQL, JD_QUERY, FIND, JD_FIND ) ) {
				queryMethods.add( method );
			}
			else if ( containsAnnotation( method, JD_INSERT, JD_UPDATE, JD_DELETE, JD_SAVE ) ) {
				lifecycleMethods.add( method );
			}
		}

		jakartaDataRepository = hasAnnotation( element, JD_REPOSITORY );
		findSessionGetter( element );
		if ( !repository && jakartaDataRepository) {
			repository = true;
			sessionType = HIB_STATELESS_SESSION;
			addDaoConstructor( null );
		}
		if ( jakartaDataRepository ) {
			addDefaultConstructor();
		}

		if ( managed && !jakartaDataStaticModel ) {
			putMember( "class", new AnnotationMetaType(this) );
		}

		addPersistentMembers( fieldsOfClass, AccessType.FIELD );
		addPersistentMembers( gettersAndSettersOfClass, AccessType.PROPERTY );

		addAuxiliaryMembers();

		checkNamedQueries();

		addLifecycleMethods( lifecycleMethods );

		addQueryMethods( queryMethods );

		initialized = true;
	}

	private void addDefaultConstructor() {
		final String sessionVariableName = getSessionVariableName(sessionType);
		final String typeName = element.getSimpleName().toString() + '_';
		putMember("_", new DefaultConstructor(
				this,
				typeName,
				sessionVariableName,
				sessionType,
				sessionVariableName,
				dataStore(),
				context.addInjectAnnotation()
		));
	}

	private @Nullable String dataStore() {
		final AnnotationMirror repo = getAnnotationMirror( element, JD_REPOSITORY );
		if ( repo != null ) {
			final String dataStore = (String) getAnnotationValue( repo, "dataStore" );
			if ( dataStore != null && !dataStore.isEmpty() ) {
				return dataStore;
			}
		}
		return null;
	}

	private void findSessionGetter(TypeElement type) {
		if ( !hasAnnotation( type, Constants.ENTITY )
				&& !hasAnnotation( type, Constants.MAPPED_SUPERCLASS )
				&& !hasAnnotation( type, Constants.EMBEDDABLE ) ) {
			for ( ExecutableElement method : methodsIn( type.getEnclosedElements() ) ) {
				if ( isSessionGetter( method ) ) {
					repository = true;
					sessionType = addDaoConstructor( method );
				}
			}
			if ( !repository) {
				final TypeMirror superclass = type.getSuperclass();
				if ( superclass.getKind() == TypeKind.DECLARED ) {
					final DeclaredType declaredType = (DeclaredType) superclass;
					findSessionGetter( (TypeElement) declaredType.asElement() );
				}
				for ( TypeMirror superinterface : type.getInterfaces() ) {
					if ( superinterface.getKind() == TypeKind.DECLARED ) {
						final DeclaredType declaredType = (DeclaredType) superinterface;
						findSessionGetter( (TypeElement) declaredType.asElement() );
					}
				}
			}
		}
	}

	/**
	 * If there is a session getter method, we generate an instance
	 * variable backing it, together with a constructor that initializes
	 * it.
	 */
	private String addDaoConstructor(@Nullable ExecutableElement method) {
		final String sessionType = method == null ? this.sessionType : method.getReturnType().toString();
		final String sessionVariableName = getSessionVariableName( sessionType );
		final String name = method == null ? sessionVariableName : method.getSimpleName().toString();
		final String typeName = element.getSimpleName().toString() + '_';
		putMember( name,
				new RepositoryConstructor(
						this,
						typeName,
						name,
						sessionType,
						sessionVariableName,
						dataStore(),
						context.addInjectAnnotation(),
						context.addNonnullAnnotation(),
						method != null,
						jakartaDataRepository
				)
		);
		return sessionType;
	}

	/**
	 * The session getter method doesn't have to be a JavaBeans-style
	 * getter. It can be any method with no parameters and one of the
	 * needed return types.
	 */
	private static boolean isSessionGetter(ExecutableElement method) {
		if ( method.getParameters().isEmpty() ) {
			final TypeMirror type = method.getReturnType();
			if ( type.getKind() == TypeKind.DECLARED ) {
				final DeclaredType declaredType = (DeclaredType) type;
				final Element element = declaredType.asElement();
				if ( element.getKind() == ElementKind.INTERFACE ) {
					final Name name = ((TypeElement) element).getQualifiedName();
					return name.contentEquals(Constants.HIB_SESSION)
						|| name.contentEquals(Constants.HIB_STATELESS_SESSION)
						|| name.contentEquals(Constants.MUTINY_SESSION)
						|| name.contentEquals(Constants.ENTITY_MANAGER);
				}
			}
		}
		return false;
	}

	/**
	 * Check if method respects Java Bean conventions for getter and setters.
	 *
	 * @param methodOfClass method element
	 *
	 * @return whether method respects Java Bean conventions.
	 */
	private boolean isGetterOrSetter(Element methodOfClass) {
		final ExecutableType methodType = (ExecutableType) methodOfClass.asType();
		final Name methodSimpleName = methodOfClass.getSimpleName();
		final List<? extends TypeMirror> methodParameterTypes = methodType.getParameterTypes();
		final TypeMirror returnType = methodType.getReturnType();
		return isSetter( methodSimpleName, methodParameterTypes, returnType )
			|| isGetter( methodSimpleName, methodParameterTypes, returnType );
	}

	private static boolean hasPrefix(Name methodSimpleName, String prefix) {
		return methodSimpleName.length() > prefix.length()
			&& methodSimpleName.subSequence( 0, prefix.length() ).toString().equals( prefix );
	}

	private static boolean isGetter(
			Name methodSimpleName,
			List<? extends TypeMirror> methodParameterTypes,
			TypeMirror returnType) {
		return ( hasPrefix( methodSimpleName, "get" ) || hasPrefix( methodSimpleName,"is" ) )
			&& methodParameterTypes.isEmpty()
			&& returnType.getKind() != TypeKind.VOID;
	}

	private static boolean isSetter(
			Name methodSimpleName,
			List<? extends TypeMirror> methodParameterTypes,
			TypeMirror returnType) {
		return hasPrefix( methodSimpleName, "set")
			&& methodParameterTypes.size() == 1
			&& returnType.getKind() != TypeKind.VOID;
	}

	private void addPersistentMembers(List<? extends Element> membersOfClass, AccessType membersKind) {
		for ( Element memberOfClass : membersOfClass ) {
			if ( isPersistent( memberOfClass, membersKind ) ) {
				if ( jakartaDataStaticModel ) {
					final DataAnnotationMetaAttribute dataMetaAttribute =
							memberOfClass.asType()
									.accept( new DataMetaAttributeGenerationVisitor( this, context ), memberOfClass );
					if ( dataMetaAttribute != null ) {
						members.put( '_' + dataMetaAttribute.getPropertyName(), dataMetaAttribute );
					}
				}
				else {
					final AnnotationMetaAttribute jpaMetaAttribute =
							memberOfClass.asType()
									.accept( new MetaAttributeGenerationVisitor( this, context ), memberOfClass );
					if ( jpaMetaAttribute != null ) {
						members.put( jpaMetaAttribute.getPropertyName(), jpaMetaAttribute );
					}
				}
			}
		}
	}

	private boolean isPersistent(Element memberOfClass, AccessType membersKind) {
		return ( entityAccessTypeInfo.getAccessType() == membersKind
					|| determineAnnotationSpecifiedAccessType( memberOfClass ) != null )
			&& !containsAnnotation( memberOfClass, Constants.TRANSIENT )
			&& !memberOfClass.getModifiers().contains( Modifier.TRANSIENT )
			&& !memberOfClass.getModifiers().contains( Modifier.STATIC )
			&& !( memberOfClass.getKind() == ElementKind.METHOD
				&& isSessionGetter( (ExecutableElement) memberOfClass ) );
	}

	private void addLifecycleMethods(List<ExecutableElement> queryMethods) {
		for ( ExecutableElement method : queryMethods) {
			if ( method.getModifiers().contains(Modifier.ABSTRACT) ) {
				if ( hasAnnotation( method, JD_INSERT, JD_UPDATE, JD_DELETE, JD_SAVE ) ) {
					addLifecycleMethod( method );
				}
			}
		}
	}

	private void addQueryMethods(List<ExecutableElement> queryMethods) {
		for ( ExecutableElement method : queryMethods) {
			if ( method.getModifiers().contains(Modifier.ABSTRACT) ) {
				addQueryMethod( method );
			}
		}
	}

	private void addQueryMethod(ExecutableElement method) {
		final TypeMirror returnType = method.getReturnType();
		final TypeKind kind = returnType.getKind();
		if ( kind == TypeKind.VOID ||  kind == TypeKind.ARRAY || kind.isPrimitive() ) {
			addQueryMethod( method, returnType, null );
		}
		else if ( kind == TypeKind.DECLARED ) {
			final DeclaredType declaredType = ununi( (DeclaredType) returnType );
			final TypeElement typeElement = (TypeElement) declaredType.asElement();
			final List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
			switch ( typeArguments.size() ) {
				case 0:
					if ( containsAnnotation( declaredType.asElement(), Constants.ENTITY ) ) {
						addQueryMethod( method, declaredType, null );
					}
					else {
						if ( isLegalRawResultType( typeElement.getQualifiedName().toString() ) ) {
							addQueryMethod( method, null, typeElement );
						}
						else {
							// probably a projection
							addQueryMethod( method, declaredType, null );
						}
					}
					break;
				case 1:
					if ( isLegalGenericResultType( typeElement.toString() ) ) {
						addQueryMethod( method, typeArguments.get(0), typeElement );
					}
					else {
						context.message( method,
								"incorrect return type '" + typeElement + "'",
								Diagnostic.Kind.ERROR );
					}
					break;
				default:
					context.message( method,
							"incorrect return type '" + declaredType + "'",
							Diagnostic.Kind.ERROR );
					break;
			}
		}
	}

	private static DeclaredType ununi(DeclaredType returnType) {
		final TypeElement typeElement = (TypeElement) returnType.asElement();
		return typeElement.getQualifiedName().contentEquals(Constants.UNI)
				? (DeclaredType) returnType.getTypeArguments().get(0)
				: returnType;
	}

	private static boolean isLegalRawResultType(String containerTypeName) {
		return containerTypeName.equals(Constants.LIST)
			|| containerTypeName.equals(Constants.QUERY)
			|| containerTypeName.equals(Constants.HIB_QUERY);
	}

	private static boolean isLegalGenericResultType(String containerTypeName) {
		return containerTypeName.equals(Constants.LIST)
			|| containerTypeName.equals(Constants.STREAM)
			|| containerTypeName.equals(Constants.OPTIONAL)
			|| containerTypeName.equals(Constants.TYPED_QUERY)
			|| containerTypeName.equals(Constants.HIB_QUERY)
			|| containerTypeName.equals(Constants.HIB_SELECTION_QUERY);
	}

	private void addQueryMethod(
			ExecutableElement method,
			@Nullable TypeMirror returnType,
			@Nullable TypeElement containerType) {
		final AnnotationMirror hql = getAnnotationMirror( method, HQL );
		if ( hql != null ) {
			addQueryMethod( method, returnType, containerType, hql, false );
		}
		final AnnotationMirror sql = getAnnotationMirror( method, SQL );
		if ( sql != null ) {
			addQueryMethod( method, returnType, containerType, sql, true );
		}
		final AnnotationMirror jdql = getAnnotationMirror( method, JD_QUERY );
		if ( jdql != null ) {
			addQueryMethod( method, returnType, containerType, jdql, false );
		}
		if ( hasAnnotation( method, FIND, JD_FIND ) ) {
			addFinderMethod( method, returnType, containerType );
		}
	}

	private void addLifecycleMethod(ExecutableElement method) {
		final TypeMirror returnType = method.getReturnType();
		if ( !HIB_STATELESS_SESSION.equals(sessionType) ) {
			context.message( method,
					"repository must be backed by a 'StatelessSession'",
					Diagnostic.Kind.ERROR );
		}
		else if ( method.getParameters().size() != 1 ) {
			context.message( method,
					"must have exactly one parameter",
					Diagnostic.Kind.ERROR );

		}
		else if ( returnType == null || returnType.getKind() != TypeKind.VOID ) {
			context.message( method,
					"must be declared 'void'",
					Diagnostic.Kind.ERROR );
		}
		else {
			final String operation = lifecycleOperation( method );
			final VariableElement parameter = method.getParameters().get(0);
			final TypeMirror parameterType = parameter.asType();
			final DeclaredType declaredType = entityType( parameterType );
			if ( declaredType == null ) {
				context.message( parameter,
						"incorrect parameter type '" + parameterType + "' is not an entity type",
						Diagnostic.Kind.ERROR );
			}
			else if ( !containsAnnotation( declaredType.asElement(), Constants.ENTITY ) ) {
				context.message( parameter,
						"incorrect parameter type '" + parameterType + "' is not annotated '@Entity'",
						Diagnostic.Kind.ERROR );
			}
			else {
				putMember(
						method.getSimpleName().toString()
								+ '.' + operation,
						new LifecycleMethod(
								this,
								parameterType.toString(),
								method.getSimpleName().toString(),
								parameter.getSimpleName().toString(),
								getSessionVariableName(),
								operation,
								context.addNonnullAnnotation(),
								declaredType != parameterType
						)
				);
			}
		}
	}

	private @Nullable DeclaredType entityType(TypeMirror parameterType) {
		switch ( parameterType.getKind() ) {
			case DECLARED:
				final DeclaredType declaredType = (DeclaredType) parameterType;
				final Types types = context.getTypeUtils();
				final Elements elements = context.getElementUtils();
				if ( types.isAssignable( declaredType,
						types.erasure( elements.getTypeElement(ITERABLE).asType() ) )
							&& !declaredType.getTypeArguments().isEmpty() ) {
					final TypeMirror elementType = declaredType.getTypeArguments().get(0);
					return elementType.getKind() == TypeKind.DECLARED ? (DeclaredType) elementType : null;
				}
				else {
					return declaredType;
				}
			case ARRAY:
				final ArrayType arrayType = (ArrayType) parameterType;
				final TypeMirror componentType = arrayType.getComponentType();
				return componentType.getKind() == TypeKind.DECLARED ? (DeclaredType) componentType : null;
			default:
				return null;
		}
	}

	private static String lifecycleOperation(ExecutableElement method) {
		if ( hasAnnotation(method, JD_INSERT) ) {
			return "insert";
		}
		else if ( hasAnnotation(method, JD_UPDATE) ) {
			return "update";
		}
		else if ( hasAnnotation(method, JD_DELETE) ) {
			return "delete";
		}
		else if ( hasAnnotation(method, JD_SAVE) ) {
			return "upsert";
		}
		else {
			throw new AssertionFailure("Unrecognized lifecycle operation");
		}
	}

	private void addFinderMethod(
			ExecutableElement method,
			@Nullable TypeMirror returnType,
			@Nullable TypeElement containerType) {
		if ( returnType == null || returnType.getKind() != TypeKind.DECLARED ) {
			context.message( method,
					"incorrect return type '" + returnType + "' is not an entity type",
					Diagnostic.Kind.ERROR );
		}
		else {
			final DeclaredType declaredType = ununi( (DeclaredType) returnType );
			final TypeElement entity = (TypeElement) declaredType.asElement();
			if ( !containsAnnotation( entity, Constants.ENTITY ) ) {
				context.message( method,
						"incorrect return type '" + returnType + "' is not annotated '@Entity'",
						Diagnostic.Kind.ERROR );
			}
			else {
				if ( containerType != null ) {
					// multiple results, it has to be a criteria finder
					createCriteriaFinder( method, returnType, containerType, entity );
				}
				else {
					for ( VariableElement parameter : method.getParameters() ) {
						final String type = parameter.asType().toString();
						if ( isPageParam(type) ) {
							context.message( parameter, "pagination would have no effect", Diagnostic.Kind.ERROR);
						}
						else if ( isOrderParam(type) ) {
							context.message( parameter, "ordering would have no effect", Diagnostic.Kind.ERROR);
						}
					}
					final long parameterCount =
							method.getParameters().stream()
									.filter(AnnotationMetaEntity::isFinderParameterMappingToAttribute)
									.count();
					switch ( (int) parameterCount ) {
						case 0:
							context.message( method, "missing parameter", Diagnostic.Kind.ERROR );
							break;
						case 1:
							createSingleParameterFinder( method, returnType, entity );
							break;
						default:
							createMultipleParameterFinder( method, returnType, entity );
					}
				}
			}
		}
	}

	/**
	 * Create a finder method which returns multiple results.
	 */
	private void createCriteriaFinder(
			ExecutableElement method, TypeMirror returnType, @Nullable TypeElement containerType, TypeElement entity) {
		final String methodName = method.getSimpleName().toString();
		final List<String> paramNames = parameterNames( method );
		final List<String> paramTypes = parameterTypes( method );
		final String[] sessionType = sessionTypeFromParameters( paramNames, paramTypes );
		final String methodKey = methodName + paramTypes;
		for ( VariableElement parameter : method.getParameters() ) {
			if ( isFinderParameterMappingToAttribute( parameter ) ) {
				validateFinderParameter( entity, parameter );
			}
			else {
				final TypeMirror parameterType = parameter.asType();
				if ( isOrderParam( parameterType.toString() ) ) {
					final TypeMirror typeArgument = getTypeArgument( parameterType );
					if ( typeArgument == null ) {
						context.message( parameter, "missing type of order (should be 'Order<? super "
								+ entity.getSimpleName() + ">')", Diagnostic.Kind.ERROR );
					}
					else if ( !context.getTypeUtils().isSameType( typeArgument, entity.asType() ) ) {
						context.message( parameter, "mismatched type of order (should be 'Order<? super "
								+ entity.getSimpleName() + ">')", Diagnostic.Kind.ERROR);
					}
				}
			}
		}
		putMember( methodKey,
				new CriteriaFinderMethod(
						this,
						methodName,
						returnType.toString(),
						containerType == null ? null : containerType.toString(),
						paramNames,
						paramTypes,
						parameterNullability(method, entity),
						repository,
						sessionType[0],
						sessionType[1],
						enabledFetchProfiles( method ),
						orderByList( method, entity ),
						context.addNonnullAnnotation(),
						jakartaDataRepository
				)
		);
	}

	private List<OrderBy> orderByList(ExecutableElement method, TypeElement entityType) {
		final AnnotationMirror orderByList =
				getAnnotationMirror( method, "jakarta.data.repository.OrderBy.List" );
		if ( orderByList != null ) {
			final List<OrderBy> result = new ArrayList<>();
			@SuppressWarnings("unchecked")
			final List<AnnotationValue> list = (List<AnnotationValue>)
					castNonNull( getAnnotationValue( orderByList, "value" ) );
			for ( AnnotationValue element : list ) {
				result.add( orderByExpression( castNonNull( (AnnotationMirror) element.getValue() ), entityType, method ) );
			}
			return result;
		}
		final AnnotationMirror orderBy =
				getAnnotationMirror( method, "jakarta.data.repository.OrderBy" );
		if ( orderBy != null ) {
			return List.of( orderByExpression(orderBy, entityType, method) );
		}
		return emptyList();
	}

	private OrderBy orderByExpression(AnnotationMirror orderBy, TypeElement entityType, ExecutableElement method) {
		final String fieldName = (String) castNonNull( getAnnotationValue(orderBy, "value") );
		final Boolean descendingOrNull = (Boolean) getAnnotationValue(orderBy, "descending");
		final Boolean ignoreCaseOrNull = (Boolean) getAnnotationValue(orderBy, "ignoreCase");
		final boolean descending = descendingOrNull != null && descendingOrNull;
		final boolean ignoreCase = ignoreCaseOrNull != null && ignoreCaseOrNull;
		if ( memberMatchingPath( entityType, fieldName ) == null ) {
			context.message( method, orderBy,
					"no matching field named '" + fieldName
							+ "' in entity class '" + entityType.getQualifiedName() + "'",
					Diagnostic.Kind.ERROR );
		}
		return new OrderBy( fieldName, descending, ignoreCase );
	}

	private static @Nullable TypeMirror getTypeArgument(TypeMirror parameterType) {
		switch ( parameterType.getKind() ) {
			case ARRAY:
				final ArrayType arrayType = (ArrayType) parameterType;
				return getTypeArgument( arrayType.getComponentType() );
			case DECLARED:
				final DeclaredType type = (DeclaredType) parameterType;
				final String parameterTypeName = parameterType.toString();
				if ( parameterTypeName.startsWith( List.class.getName() ) ) {
					for (TypeMirror arg : type.getTypeArguments()) {
						return getTypeArgument( arg );
					}
				}
				else if ( parameterTypeName.startsWith( Order.class.getName() )
						|| parameterTypeName.startsWith( JD_SORT )
						|| parameterTypeName.startsWith( JD_ORDER )) {
					for ( TypeMirror arg : type.getTypeArguments() ) {
						switch ( arg.getKind() ) {
							case WILDCARD:
								return ((WildcardType) arg).getSuperBound();
							case ARRAY:
							case DECLARED:
							case TYPEVAR:
								return arg;
							default:
								return null;
						}
					}
				}
				return null;
			default:
				return null;
		}
	}

	private static boolean isFinderParameterMappingToAttribute(VariableElement param) {
		final String type = param.asType().toString();
		return !isSessionParameter( type )
			&& !isPageParam( type )
			&& !isOrderParam( type );
	}

	private String[] sessionTypeFromParameters(List<String> paramNames, List<String> paramTypes) {
		for ( int i = 0; i < paramNames.size(); i ++ ) {
			final String type = paramTypes.get(i);
			final String name = paramNames.get(i);
			if ( isSessionParameter(type) ) {
				return new String[] { type, name };
			}
		}
		return new String[] { getSessionType(), getSessionVariableName() };
	}

	@Override
	protected String getSessionVariableName() {
		return getSessionVariableName(sessionType);
	}

	private static String getSessionVariableName(String sessionType) {
		switch (sessionType) {
			case HIB_SESSION:
			case HIB_STATELESS_SESSION:
			case MUTINY_SESSION:
				return "session";
			default:
				return "entityManager";
		}
	}

	private static List<String> enabledFetchProfiles(ExecutableElement method) {
		final AnnotationMirror findAnnotation = getAnnotationMirror( method, FIND );
		if ( findAnnotation == null ) {
			return emptyList();
		}
		else {
			final Object enabledFetchProfiles =
					getAnnotationValue( findAnnotation, "enabledFetchProfiles" );
			if ( enabledFetchProfiles == null ) {
				return emptyList();
			}
			else {
				@SuppressWarnings("unchecked")
				final List<AnnotationValue> annotationValues = (List<AnnotationValue>) enabledFetchProfiles;
				final List<String> result = annotationValues.stream().map(AnnotationValue::toString).collect(toList());
				if ( result.stream().anyMatch("<error>"::equals) ) {
					throw new ProcessLaterException();
				}
				return result;
			}
		}
	}

	private void createMultipleParameterFinder(ExecutableElement method, TypeMirror returnType, TypeElement entity) {
		final String methodName = method.getSimpleName().toString();
		final List<String> paramNames = parameterNames( method );
		final List<String> paramTypes = parameterTypes( method );
		final String[] sessionType = sessionTypeFromParameters( paramNames, paramTypes );
		final String methodKey = methodName + paramTypes;
		if (  !usingStatelessSession(sessionType[0]) // no byNaturalId() lookup API for SS
				&& matchesNaturalKey( method, entity ) ) {
			putMember( methodKey,
					new NaturalIdFinderMethod(
							this,
							methodName,
							returnType.toString(),
							paramNames,
							paramTypes,
							parameterNullability(method, entity),
							repository,
							sessionType[0],
							sessionType[1],
							enabledFetchProfiles( method ),
							context.addNonnullAnnotation(),
							jakartaDataRepository
					)
			);
		}
		else {
			putMember( methodKey,
					new CriteriaFinderMethod(
							this,
							methodName,
							returnType.toString(),
							null,
							paramNames,
							paramTypes,
							parameterNullability(method, entity),
							repository,
							sessionType[0],
							sessionType[1],
							enabledFetchProfiles( method ),
							orderByList( method, entity ),
							context.addNonnullAnnotation(),
							jakartaDataRepository
					)
			);
		}
	}

	private void createSingleParameterFinder(ExecutableElement method, TypeMirror returnType, TypeElement entity) {
		final String methodName = method.getSimpleName().toString();
		final VariableElement parameter =
				method.getParameters().stream()
						.filter(AnnotationMetaEntity::isFinderParameterMappingToAttribute)
						.findFirst().orElseThrow();
		final List<String> paramNames = parameterNames( method );
		final List<String> paramTypes = parameterTypes( method );
		final String[] sessionType = sessionTypeFromParameters( paramNames, paramTypes );
		final FieldType fieldType = validateFinderParameter( entity, parameter );
		if ( fieldType != null ) {
			final String methodKey = methodName + "!";
			final List<String> profiles = enabledFetchProfiles( method );
			switch ( pickStrategy( fieldType, sessionType[0], profiles ) ) {
				case ID:
					putMember( methodKey,
							new IdFinderMethod(
									this,
									methodName,
									returnType.toString(),
									paramNames,
									paramTypes,
									repository,
									sessionType[0],
									sessionType[1],
									profiles,
									context.addNonnullAnnotation(),
									jakartaDataRepository
							)
					);
					break;
				case NATURAL_ID:
					putMember( methodKey,
							new NaturalIdFinderMethod(
									this,
									methodName,
									returnType.toString(),
									paramNames,
									paramTypes,
									parameterNullability(method, entity),
									repository,
									sessionType[0],
									sessionType[1],
									profiles,
									context.addNonnullAnnotation(),
									jakartaDataRepository
							)
					);
					break;
				case BASIC:
					putMember( methodKey,
							new CriteriaFinderMethod(
									this,
									methodName,
									returnType.toString(),
									null,
									paramNames,
									paramTypes,
									parameterNullability(method, entity),
									repository,
									sessionType[0],
									sessionType[1],
									profiles,
									orderByList( method, entity ),
									context.addNonnullAnnotation(),
									jakartaDataRepository
							)
					);
					break;
			}
		}
	}

	private FieldType pickStrategy(FieldType fieldType, String sessionType, List<String> profiles) {
		switch (fieldType) {
			case ID:
				// no byId() API for SS or M.S, only get()
				return (usingStatelessSession(sessionType) || usingReactiveSession(sessionType)) && !profiles.isEmpty()
						? FieldType.BASIC : FieldType.ID;
			case NATURAL_ID:
				// no byNaturalId() lookup API for SS
				// no byNaturalId() in M.S, but we do have Identifier workaround
				return usingStatelessSession(sessionType) || (usingReactiveSession(sessionType) && !profiles.isEmpty())
						? FieldType.BASIC : FieldType.NATURAL_ID;
			default:
				return FieldType.BASIC;
		}
	}

	private boolean matchesNaturalKey(ExecutableElement method, TypeElement entity) {
		boolean result = true;
		final List<? extends VariableElement> parameters = method.getParameters();
		int count = 0;
		for ( VariableElement param : parameters ) {
			if ( isFinderParameterMappingToAttribute( param ) ) {
				count ++;
				if ( validateFinderParameter( entity, param ) != FieldType.NATURAL_ID ) {
					// no short-circuit here because we want to validate
					// all of them and get the nice error report
					result = false;
				}
			}
		}
		return result && countNaturalIdFields( entity ) == count;
	}

	enum FieldType {
		ID, NATURAL_ID, BASIC
	}

	private int countNaturalIdFields(TypeElement entity) {
		int count = 0;
		for ( Element member : entity.getEnclosedElements() ) {
			if ( containsAnnotation( member, Constants.NATURAL_ID ) ) {
				count ++;
			}
		}
		return count;
	}

	private @Nullable FieldType validateFinderParameter(TypeElement entityType, VariableElement param) {
		final Element member = memberMatchingPath( entityType, parameterName( param ) );
		if ( member != null) {
			final String memberType = memberType( member ).toString();
			final String paramType = param.asType().toString();
			if ( !isLegalAssignment( paramType, memberType ) ) {
				context.message( param,
						"matching field has type '" + memberType
								+ "' in entity class '" + entityType + "'",
						Diagnostic.Kind.ERROR );
			}

			if ( containsAnnotation( member, Constants.ID, Constants.EMBEDDED_ID ) ) {
				return FieldType.ID;
			}
			else if ( containsAnnotation( member, Constants.NATURAL_ID ) ) {
				return FieldType.NATURAL_ID;
			}
			else {
				return FieldType.BASIC;
			}
		}
		final AnnotationMirror idClass = getAnnotationMirror( entityType, Constants.ID_CLASS );
		if ( idClass != null ) {
			final Object value = getAnnotationValue( idClass, "value" );
			if ( value instanceof TypeMirror ) {
				if ( context.getTypeUtils().isSameType( param.asType(), (TypeMirror) value ) ) {
					return FieldType.ID;
				}
			}
		}
		context.message( param,
				"no matching field named '"
						+ parameterName( param ).replace('$', '.')
						+ "' in entity class '" + entityType + "'",
				Diagnostic.Kind.ERROR );
		return null;
	}

	private boolean finderParameterNullable(TypeElement entity, VariableElement param) {
		final Element member = memberMatchingPath( entity, parameterName( param ) );
		return member == null || isNullable(member);
	}

	private AccessType getAccessType(TypeElement entity) {
		final String entityClassName = entity.getQualifiedName().toString();
		determineAccessTypeForHierarchy(entity, context );
		return castNonNull( context.getAccessTypeInfo( entityClassName ) ).getAccessType();
	}

	private static TypeMirror memberType(Element member) {
		if ( member.getKind() == ElementKind.METHOD ) {
			final ExecutableElement method = (ExecutableElement) member;
			return method.getReturnType();
		}
		else {
			return member.asType();
		}
	}

	private @Nullable Element memberMatchingPath(TypeElement entityType, String path) {
		final StringTokenizer tokens = new StringTokenizer( path, "$" );
		return memberMatchingPath( entityType, tokens );
	}

	private @Nullable Element memberMatchingPath(TypeElement entityType, StringTokenizer tokens) {
		final AccessType accessType = getAccessType(entityType);
		final String nextToken = tokens.nextToken();
		for ( Element member : entityType.getEnclosedElements() ) {
			final Element match =
					memberMatchingPath(entityType, member, accessType, tokens, nextToken);
			if ( match != null ) {
				return match;
			}
		}
		return null;
	}

	private @Nullable Element memberMatchingPath(
			TypeElement entityType,
			Element candidate,
			AccessType accessType,
			StringTokenizer tokens,
			String token) {
		final Name memberName = candidate.getSimpleName();
		final TypeMirror type = memberType( candidate, accessType, token, memberName );
		if (type == null) {
			return null;
		}
		else if ( tokens.hasMoreTokens() ) {
			return type.getKind() == TypeKind.DECLARED
					? memberForPath( entityType, tokens, (DeclaredType) type, memberName )
					: null;
		}
		else {
			return candidate;
		}
	}

	private @Nullable Element memberForPath(
			TypeElement entityType, StringTokenizer tokens, DeclaredType type, Name memberName) {
		final TypeElement memberType = (TypeElement) type.asElement();
		memberTypes.put( qualify( entityType.getQualifiedName().toString(), memberName.toString() ),
				memberType.getQualifiedName().toString() ); // NOTE SIDE EFFECT!
		return memberMatchingPath( memberType, tokens );
	}

	private static @Nullable TypeMirror memberType(Element candidate, AccessType accessType, String token, Name memberName) {
		final ElementKind kind = candidate.getKind();
		if ( accessType == AccessType.FIELD && kind == ElementKind.FIELD ) {
			return fieldMatches(token, memberName)
					? candidate.asType()
					: null;
		}
		else if ( accessType == AccessType.PROPERTY && kind == ElementKind.METHOD ) {
			final ExecutableElement executable = (ExecutableElement) candidate;
			return getterMatches(token, memberName)
					? executable.getReturnType()
					: null;
		}
		else {
			return null;
		}
	}

	private static boolean fieldMatches(String token, Name fieldName) {
		return fieldName.contentEquals( token );
	}

	private static boolean getterMatches(String token, Name methodName) {
		if ( hasPrefix( methodName, "get" ) ) {
			return token.equals( decapitalize( methodName.subSequence( 3, methodName.length()).toString() ) );
		}
		else if ( hasPrefix( methodName, "is" ) ) {
			return token.equals( decapitalize( methodName.subSequence( 2, methodName.length() ).toString() ) );
		}
		else {
			return false;
		}
	}

	private void addQueryMethod(
			ExecutableElement method,
			@Nullable TypeMirror returnType,
			@Nullable TypeElement containerType,
			AnnotationMirror mirror,
			boolean isNative) {
		final AnnotationValue value = getAnnotationValueRef( mirror, "value" );
		if ( value != null ) {
			final Object query = value.getValue();
			if ( query instanceof String ) {
				final String queryString = (String) query;
				final List<String> paramNames = parameterNames( method );
				final List<String> paramTypes = parameterTypes( method );
				final String[] sessionType = sessionTypeFromParameters( paramNames, paramTypes );
				if ( returnType != null && returnType.getKind() == TypeKind.DECLARED ) {
					final DeclaredType declaredType = (DeclaredType) returnType;
					if ( !declaredType.getTypeArguments().isEmpty() ) {
						context.message( method, mirror, value,
								"query result type may not be a generic type"
										+ " (change '" + returnType +
										"' to '" + context.getTypeUtils().erasure( returnType ) + "')",
								Diagnostic.Kind.ERROR );
					}
				}
				final QueryMethod attribute =
						new QueryMethod(
								this,
								method.getSimpleName().toString(),
								queryString,
								returnType == null ? null : returnType.toString(),
								containerType == null ? null : containerType.getQualifiedName().toString(),
								paramNames,
								paramTypes,
								isInsertUpdateDelete( queryString ),
								isNative,
								repository,
								sessionType[0],
								sessionType[1],
								context.addNonnullAnnotation(),
								jakartaDataRepository
						);
				putMember( attribute.getPropertyName() + paramTypes, attribute );

				if ( isNative ) {
					validateSql( method, mirror, queryString, paramNames, value );
				}
				else {
					validateHql( method, returnType, mirror, value, queryString, paramNames, paramTypes );
				}

				// now check that the query has a parameter for every method parameter
				checkParameters( method, returnType, paramNames, paramTypes, mirror, value, queryString );
			}
		}
	}

	private static boolean isInsertUpdateDelete(String hql) {
		final String trimmed = hql.trim();
		final String keyword = trimmed.length() > 6 ? trimmed.substring(0, 6) : "";
		return keyword.equalsIgnoreCase("update")
			|| keyword.equalsIgnoreCase("delete")
			|| keyword.equalsIgnoreCase("insert");
	}

	private void validateHql(
			ExecutableElement method,
			@Nullable TypeMirror returnType,
			AnnotationMirror mirror,
			AnnotationValue value,
			String hql,
			List<String> paramNames, List<String> paramTypes) {
		final SqmStatement<?> statement =
				Validation.validate(
						hql,
						returnType,
						true,
						new ErrorHandler( context, method, mirror, value, hql),
						ProcessorSessionFactory.create( context.getProcessingEnvironment() )
				);
		if ( statement != null ) {
			if ( statement instanceof SqmSelectStatement ) {
				validateSelectHql( method, returnType, mirror, value, (SqmSelectStatement<?>) statement );
			}
			else {
				validateUpdateHql( method, returnType, mirror, value );
			}
			for ( SqmParameter<?> param : statement.getSqmParameters() ) {
				checkParameter( param, paramNames, paramTypes, method, mirror, value);
			}
		}
	}

	private void validateUpdateHql(
			ExecutableElement method,
			@Nullable TypeMirror returnType,
			AnnotationMirror mirror,
			AnnotationValue value) {
		if ( returnType == null
				|| returnType.getKind() != TypeKind.VOID
				&& returnType.getKind() != TypeKind.BOOLEAN
				&& returnType.getKind() != TypeKind.INT ) {
			context.message( method, mirror, value,
					"return type of mutation query method must be 'int', 'boolean', or 'void'",
					Diagnostic.Kind.ERROR );
		}
	}

	private void validateSelectHql(
			ExecutableElement method,
			@Nullable TypeMirror returnType,
			AnnotationMirror mirror,
			AnnotationValue value,
			SqmSelectStatement<?> statement) {
		if ( returnType != null ) {
			final JpaSelection<?> selection = statement.getSelection();
			boolean returnTypeCorrect;
			if ( selection.isCompoundSelection() ) {
				switch ( returnType.getKind() ) {
					case ARRAY:
						returnTypeCorrect = checkReturnedArrayType( (ArrayType) returnType );
						break;
					case DECLARED:
						if ( !checkConstructorReturn( (DeclaredType) returnType, selection ) ) {
							context.message(method, mirror, value,
									"return type '" + returnType
											+ "' of method has no constructor matching query selection list",
									Diagnostic.Kind.ERROR);
						}
						returnTypeCorrect = true;
						break;
					default:
						returnTypeCorrect = false;
				}
			}
			else if ( selection instanceof JpaEntityJoin ) {
				final JpaEntityJoin<?> from = (JpaEntityJoin<?>) selection;
				returnTypeCorrect = checkReturnedEntity( from.getModel(), returnType );
			}
			else if ( selection instanceof JpaRoot ) {
				final JpaRoot<?> from = (JpaRoot<?>) selection;
				returnTypeCorrect = checkReturnedEntity( from.getModel(), returnType );
			}
			else {
				// TODO: anything more we can do here? e.g. check constructor
				try {
					final Class<?> javaResultType = selection.getJavaType();
					final TypeElement typeElement = context.getTypeElementForFullyQualifiedName( javaResultType.getName() );
					final Types types = context.getTypeUtils();
					returnTypeCorrect = types.isAssignable( returnType,  types.erasure( typeElement.asType() ) );
				}
				catch (Exception e) {
					//ignore
					returnTypeCorrect = true;
				}
			}
			if ( !returnTypeCorrect ) {
				context.message(method, mirror, value,
						"return type of query did not match return type '" + returnType + "' of method",
						Diagnostic.Kind.ERROR);
			}
		}
	}

	private void validateSql(
			ExecutableElement method,
			AnnotationMirror mirror,
			String sql,
			List<String> paramNames,
			AnnotationValue value) {
		// for SQL queries check that there is a method parameter for every query parameter
		ParameterParser.parse(sql, new ParameterRecognizer() {
			int ordinalCount = 0;
			@Override
			public void ordinalParameter(int sourcePosition) {
				ordinalCount++;
				if ( ordinalCount > paramNames.size() ) {
					context.message(method, mirror, value,
							"missing method parameter for query parameter " + ordinalCount
									+ " (add a parameter to '" + method.getSimpleName() + "')",
							Diagnostic.Kind.ERROR );
				}
			}

			@Override
			public void namedParameter(String name, int sourcePosition) {
				if ( !paramNames.contains(name) ) {
					context.message(method, mirror, value,
							"missing method parameter for query parameter :" + name
									+ " (add a parameter '" + name + "' to '" + method.getSimpleName() + "')",
							Diagnostic.Kind.ERROR );
				}
			}

			@Override
			public void jpaPositionalParameter(int label, int sourcePosition) {
				if ( label > paramNames.size() ) {
					context.message(method, mirror, value,
							"missing method parameter for query parameter ?" + label
									+ " (add a parameter to '" + method.getSimpleName() + "')",
							Diagnostic.Kind.ERROR );
				}
			}

			@Override
			public void other(char character) {
			}
		});
	}

	private static boolean checkConstructorReturn(DeclaredType returnType, JpaSelection<?> selection) {
		final List<? extends JpaSelection<?>> selectionItems = selection.getSelectionItems();
		if ( selectionItems == null ) {
			// should not occur
			return true;
		}
		final TypeElement typeElement = (TypeElement) returnType.asElement();
		final Name qualifiedName = typeElement.getQualifiedName();
		if ( qualifiedName.contentEquals(Constants.TUPLE)
				|| qualifiedName.contentEquals(Constants.LIST)
				|| qualifiedName.contentEquals(Constants.MAP) ) {
			// these are exceptionally allowed
			return true;
		}
		else {
			// otherwise we need appropriate constructor
			for ( Element member : typeElement.getEnclosedElements() ) {
				if ( member.getKind() == ElementKind.CONSTRUCTOR ) {
					final ExecutableElement constructor = (ExecutableElement) member;
					if ( constructorMatches( selectionItems, constructor.getParameters() ) ) {
						return true;
					}
				}
			}
			return false;
		}
	}

	private static boolean constructorMatches(
			List<? extends JpaSelection<?>> selectionItems,
			List<? extends VariableElement> parameters) {
		int itemCount = selectionItems.size();
		if ( parameters.size() == itemCount ) {
			for (int i = 0; i < itemCount; i++ ) {
				final JpaSelection<?> item = selectionItems.get(i);
				if ( item != null && item.getJavaType() != null ) {
					if ( !parameterMatches( parameters.get(i), item ) ) {
						return false;
					}
				}
			}
			return true;
		}
		else {
			return false;
		}
	}

	private static boolean parameterMatches(VariableElement parameter, JpaSelection<?> item) {
		return parameterMatches( parameter.asType(), item.getJavaType() );
	}

	private static boolean parameterMatches(TypeMirror parameterType, Class<?> itemType) {
		final TypeKind kind = parameterType.getKind();
		final String itemTypeName = itemType.getName();
		if ( kind == TypeKind.DECLARED ) {
			final DeclaredType declaredType = (DeclaredType) parameterType;
			final TypeElement paramTypeElement = (TypeElement) declaredType.asElement();
			return paramTypeElement.getQualifiedName().contentEquals(itemTypeName);
		}
		else if ( kind.isPrimitive() ) {
			return primitiveClassMatchesKind( itemType, kind );
		}
		else if ( kind == TypeKind.ARRAY ) {
			final ArrayType arrayType = (ArrayType) parameterType;
			return itemType.isArray()
				&& parameterMatches( arrayType.getComponentType(), itemType.getComponentType() );
		}
		else {
			return false;
		}
	}

	private static boolean checkReturnedArrayType(ArrayType returnType) {
		final TypeMirror componentType = returnType.getComponentType();
		if ( componentType.getKind() == TypeKind.DECLARED ) {
			final DeclaredType declaredType = (DeclaredType) componentType;
			final TypeElement typeElement = (TypeElement) declaredType.asElement();
			return typeElement.getQualifiedName().contentEquals(Constants.JAVA_OBJECT);
		}
		else {
			return false;
		}
	}

	private boolean checkReturnedEntity(EntityDomainType<?> model, TypeMirror returnType) {
		if ( returnType.getKind() == TypeKind.DECLARED ) {
			final DeclaredType declaredType = (DeclaredType) returnType;
			final TypeElement typeElement = (TypeElement) declaredType.asElement();
			final AnnotationMirror mirror = getAnnotationMirror(typeElement, Constants.ENTITY );
			if ( mirror != null ) {
				final Object value = getAnnotationValue( mirror, "name" );
				final String entityName = value instanceof String ? (String) value : typeElement.getSimpleName().toString();
				return model.getHibernateEntityName().equals( entityName );
			}
		}
		return false;
	}

	private void checkParameter(
			SqmParameter<?> param, List<String> paramNames, List<String> paramTypes,
			ExecutableElement method, AnnotationMirror mirror, AnnotationValue value) {
		final SqmExpressible<?> expressible = param.getExpressible();
		final String queryParamType = expressible == null ? "unknown" : expressible.getTypeName(); //getTypeName() can return "unknown"
		if ( param.getName() != null ) {
			final String name = param.getName();
			int index = paramNames.indexOf( name );
			if ( index < 0 ) {
				context.message( method, mirror, value,
						"missing method parameter for query parameter :" + name
						+ " (add a parameter '" + queryParamType + ' ' + name + "' to '" + method.getSimpleName() + "')",
						Diagnostic.Kind.ERROR );
			}
			else if ( !isLegalAssignment( paramTypes.get(index), queryParamType ) ) {
				context.message( method, mirror, value,
						"parameter matching query parameter :" + name + " has the wrong type"
								+ " (change the method parameter type to '" + queryParamType + "')",
						Diagnostic.Kind.ERROR );
			}
		}
		else if ( param.getPosition() != null ) {
			int position = param.getPosition();
			if ( position > paramNames.size() ) {
				context.message( method, mirror, value,
						"missing method parameter for query parameter ?" + position
								+ " (add a parameter of type '" + queryParamType + "' to '" + method.getSimpleName() + "')",
						Diagnostic.Kind.ERROR );
			}
			else if ( !isLegalAssignment( paramTypes.get(position-1), queryParamType ) ) {
				context.message( method, mirror, value,
						"parameter matching query parameter ?" + position + " has the wrong type"
								+ " (change the method parameter type to '" + queryParamType + "')",
						Diagnostic.Kind.ERROR );
			}
		}
	}

	private static boolean isLegalAssignment(String argType, String paramType) {
		return paramType.equals("unknown")
			|| paramType.equals(argType)
			|| paramType.equals(fromPrimitive(argType));
	}

	private static @Nullable String fromPrimitive(String argType) {
		switch (argType) {
			case "boolean":
				return Boolean.class.getName();
			case "char":
				return Character.class.getName();
			case "int":
				return Integer.class.getName();
			case "long":
				return Long.class.getName();
			case "short":
				return Short.class.getName();
			case "byte":
				return Byte.class.getName();
			case "float":
				return Float.class.getName();
			case "double":
				return Double.class.getName();
			default:
				return null;
		}
	}

	private List<Boolean> parameterNullability(ExecutableElement method, TypeElement entity) {
		return method.getParameters().stream()
				.map(param -> finderParameterNullable(entity, param))
				.collect(toList());
	}

	private static List<String> parameterTypes(ExecutableElement method) {
		return method.getParameters().stream()
				.map(param -> param.asType().toString())
				.collect(toList());
	}

	private static List<String> parameterNames(ExecutableElement method) {
		return method.getParameters().stream()
				.map(AnnotationMetaEntity::parameterName)
				.collect(toList());
	}

	private static String parameterName(VariableElement parameter) {
		final AnnotationMirror by = getAnnotationMirror( parameter, "jakarta.data.repository.By" );
		final AnnotationMirror param = getAnnotationMirror( parameter, "jakarta.data.repository.Param" );
		if ( by != null ) {
			final String name = (String) castNonNull(getAnnotationValue(by, "value"));
			if ( name.contains("<error>") ) {
				throw new ProcessLaterException();
			}
			return name;
		}
		else if ( param != null ) {
			final String name = (String) castNonNull(getAnnotationValue(param, "value"));
			if ( name.contains("<error>") ) {
				throw new ProcessLaterException();
			}
			return name;
		}
		else {
			return parameter.getSimpleName().toString();
		}
	}

	private static boolean isNullable(Element member) {
		switch ( member.getKind() ) {
			case METHOD:
				final ExecutableElement method = (ExecutableElement) member;
				if ( method.getReturnType().getKind().isPrimitive() ) {
					return false;
				}
			case FIELD:
				if ( member.asType().getKind().isPrimitive() ) {
					return false;
				}
		}
		boolean nullable = true;
		for ( AnnotationMirror mirror : member.getAnnotationMirrors() ) {
			final TypeElement annotationType = (TypeElement) mirror.getAnnotationType().asElement();
			final Name name = annotationType.getQualifiedName();
			if ( name.contentEquals(Constants.ID) ) {
				nullable = false;
			}
			if ( name.contentEquals("jakarta.validation.constraints.NotNull")) {
				nullable = false;
			}
			if ( name.contentEquals(Constants.BASIC)
					|| name.contentEquals(Constants.MANY_TO_ONE)
					|| name.contentEquals(Constants.ONE_TO_ONE)) {
				if ( FALSE.equals( getAnnotationValue(mirror, "optional") ) ) {
					nullable = false;
				}
			}
		}
		return nullable;
	}

	private void checkParameters(
			ExecutableElement method,
			@Nullable TypeMirror returnType,
			List<String> paramNames, List<String> paramTypes,
			AnnotationMirror mirror,
			AnnotationValue value,
			String hql) {
		for (int i = 1; i <= paramNames.size(); i++) {
			final String param = paramNames.get(i-1);
			final String type = paramTypes.get(i-1);
			if ( parameterIsMissing( hql, i, param, type ) ) {
				context.message( method, mirror, value,
						"missing query parameter for '" + param
								+ "' (no parameter named :" + param + " or ?" + i + ")",
						Diagnostic.Kind.ERROR );
			}
		}
		if ( returnType != null ) {
			for ( VariableElement parameter : method.getParameters() ) {
				final TypeMirror parameterType = parameter.asType();
				final TypeMirror typeArgument = getTypeArgument( parameterType );
				if ( isOrderParam( parameterType.toString() ) ) {
					if ( typeArgument == null ) {
						context.message( parameter, "missing type of order (should be 'Order<? super "
								+ returnType + ">')", Diagnostic.Kind.ERROR );
					}
					else if ( !context.getTypeUtils().isSameType(typeArgument, returnType) ) {
						context.message( parameter, "mismatched type of order (should be 'Order<? super "
								+ returnType + ">')", Diagnostic.Kind.ERROR );
					}
				}
			}
		}
	}

	private static boolean parameterIsMissing(String hql, int i, String param, String type) {
		return !hasParameter(hql, i, param)
			&& !isSessionParameter(type)
			&& !isPageParam(type)
			&& !isOrderParam(type);
	}

	private static boolean hasParameter(String hql, int i, String param) {
		return Pattern.compile(".*(:" + param + "|\\?" + i + ")\\b.*", Pattern.DOTALL)
				.matcher(hql).matches();
	}

	private static boolean isSessionParameter(String type) {
		return SESSION_TYPES.contains(type);
	}

	private boolean usingReactiveSession(String sessionType) {
		return Constants.MUTINY_SESSION.equals(sessionType);
	}

	private boolean usingStatelessSession(String sessionType) {
		return Constants.HIB_STATELESS_SESSION.equals(sessionType);
	}
}
