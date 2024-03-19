/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.boot.models.xml.internal.attr;

import org.hibernate.annotations.Formula;
import org.hibernate.boot.jaxb.mapping.spi.JaxbBasicImpl;
import org.hibernate.boot.models.xml.internal.XmlAnnotationHelper;
import org.hibernate.boot.models.xml.internal.XmlProcessingHelper;
import org.hibernate.boot.models.xml.internal.db.ColumnProcessing;
import org.hibernate.boot.models.xml.spi.XmlDocumentContext;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.models.internal.MutableAnnotationUsage;
import org.hibernate.models.internal.MutableClassDetails;
import org.hibernate.models.internal.MutableMemberDetails;

import jakarta.persistence.AccessType;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;

import static org.hibernate.internal.util.NullnessHelper.coalesce;

/**
 * @author Steve Ebersole
 */
public class BasicAttributeProcessing {

	public static MutableMemberDetails processBasicAttribute(
			JaxbBasicImpl jaxbBasic,
			MutableClassDetails declarer,
			AccessType classAccessType,
			XmlDocumentContext xmlDocumentContext) {
		final AccessType accessType = coalesce( jaxbBasic.getAccess(), classAccessType );
		final MutableMemberDetails memberDetails = XmlProcessingHelper.getAttributeMember(
				jaxbBasic.getName(),
				accessType,
				declarer
		);

		final MutableAnnotationUsage<Basic> basicAnn = XmlProcessingHelper.getOrMakeAnnotation( Basic.class, memberDetails, xmlDocumentContext );
		CommonAttributeProcessing.applyAttributeBasics( jaxbBasic, memberDetails, basicAnn, accessType, xmlDocumentContext );

		if ( StringHelper.isNotEmpty( jaxbBasic.getFormula() ) ) {
			assert jaxbBasic.getColumn() == null;
			final MutableAnnotationUsage<Formula> formulaAnn = XmlProcessingHelper.getOrMakeAnnotation( Formula.class, memberDetails, xmlDocumentContext );
			formulaAnn.setAttributeValue( "value", jaxbBasic.getFormula() );
		}
		else {
			final MutableAnnotationUsage<Column> columnAnn = XmlProcessingHelper.getOrMakeAnnotation( Column.class, memberDetails, xmlDocumentContext );
			ColumnProcessing.applyColumnDetails( jaxbBasic.getColumn(), memberDetails, columnAnn, xmlDocumentContext );
		}

		XmlAnnotationHelper.applyConvert( jaxbBasic.getConvert(), memberDetails, xmlDocumentContext );

		XmlAnnotationHelper.applyBasicTypeComposition( jaxbBasic, memberDetails, xmlDocumentContext );
		XmlAnnotationHelper.applyTemporal( jaxbBasic.getTemporal(), memberDetails, xmlDocumentContext );
		XmlAnnotationHelper.applyLob( jaxbBasic.getLob(), memberDetails, xmlDocumentContext );
		XmlAnnotationHelper.applyEnumerated( jaxbBasic.getEnumerated(), memberDetails, xmlDocumentContext );
		XmlAnnotationHelper.applyNationalized( jaxbBasic.getNationalized(), memberDetails, xmlDocumentContext );

		// todo : value generation
		// todo : ...

		return memberDetails;
	}
}
