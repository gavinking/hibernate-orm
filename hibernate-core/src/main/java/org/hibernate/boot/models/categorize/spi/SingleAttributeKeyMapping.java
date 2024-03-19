/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.boot.models.categorize.spi;

import org.hibernate.models.spi.ClassDetails;

/**
 * @author Steve Ebersole
 */
public interface SingleAttributeKeyMapping extends KeyMapping {
	AttributeMetadata getAttribute();

	default String getAttributeName() {
		return getAttribute().getName();
	}

	default ClassDetails getKeyType() {
		return getAttribute().getMember().getType();
	}

	@Override
	default void forEachAttribute(AttributeConsumer consumer) {
		consumer.accept( 0, getAttribute() );
	}

	@Override
	default boolean contains(AttributeMetadata attributeMetadata) {
		return attributeMetadata == getAttribute();
	}
}
