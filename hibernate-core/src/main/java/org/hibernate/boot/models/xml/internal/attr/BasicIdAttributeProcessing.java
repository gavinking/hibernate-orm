/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.boot.models.xml.internal.attr;

import org.hibernate.boot.jaxb.mapping.spi.JaxbIdImpl;
import org.hibernate.boot.models.JpaAnnotations;
import org.hibernate.boot.models.xml.internal.XmlAnnotationHelper;
import org.hibernate.boot.models.xml.internal.XmlProcessingHelper;
import org.hibernate.boot.models.xml.spi.XmlDocumentContext;
import org.hibernate.models.spi.MutableAnnotationUsage;
import org.hibernate.models.spi.MutableClassDetails;
import org.hibernate.models.spi.MutableMemberDetails;

import jakarta.persistence.AccessType;
import jakarta.persistence.Basic;

import static org.hibernate.internal.util.NullnessHelper.coalesce;

/**
 * @author Steve Ebersole
 */
public class BasicIdAttributeProcessing {

	public static MutableMemberDetails processBasicIdAttribute(
			JaxbIdImpl jaxbId,
			MutableClassDetails declarer,
			AccessType classAccessType,
			XmlDocumentContext xmlDocumentContext) {
		final AccessType accessType = coalesce( jaxbId.getAccess(), classAccessType );
		final MutableMemberDetails memberDetails = XmlProcessingHelper.getAttributeMember(
				jaxbId.getName(),
				accessType,
				declarer
		);

		memberDetails.applyAnnotationUsage( JpaAnnotations.ID, xmlDocumentContext.getModelBuildingContext() );
		final MutableAnnotationUsage<Basic> basicAnn = memberDetails.applyAnnotationUsage( JpaAnnotations.BASIC, xmlDocumentContext.getModelBuildingContext() );

		CommonAttributeProcessing.applyAttributeBasics( jaxbId, memberDetails, basicAnn, accessType, xmlDocumentContext );

		XmlAnnotationHelper.applyColumn( jaxbId.getColumn(), memberDetails, xmlDocumentContext );
		XmlAnnotationHelper.applyBasicTypeComposition( jaxbId, memberDetails, xmlDocumentContext );
		XmlAnnotationHelper.applyTemporal( jaxbId.getTemporal(), memberDetails, xmlDocumentContext );
		XmlAnnotationHelper.applyGeneratedValue( jaxbId.getGeneratedValue(), memberDetails, xmlDocumentContext );
		XmlAnnotationHelper.applySequenceGenerator( jaxbId.getSequenceGenerator(), memberDetails, xmlDocumentContext );
		XmlAnnotationHelper.applyTableGenerator( jaxbId.getTableGenerator(), memberDetails, xmlDocumentContext );
		XmlAnnotationHelper.applyUuidGenerator( jaxbId.getUuidGenerator(), memberDetails, xmlDocumentContext );

		// todo : unsaved-value?
		// todo : ...

		return memberDetails;
	}
}