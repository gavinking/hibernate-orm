/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.any.hbm;


/**
 * todo: describe IntegerPropertyValue
 *
 * @author Steve Ebersole
 */
public class IntegerPropertyValue implements PropertyValue {
	private Long id;
	private int value;

	public IntegerPropertyValue() {
	}

	public IntegerPropertyValue(int value) {
		this.value = value;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}

	public String asString() {
		return Integer.toString( value );
	}
}
