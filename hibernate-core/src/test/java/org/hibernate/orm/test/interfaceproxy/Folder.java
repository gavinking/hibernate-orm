/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.interfaceproxy;


/**
 * @author Gavin King
 */
public interface Folder extends Item {
	/**
	 * @return Returns the parent.
	 */
	public Folder getParent();

	/**
	 * @param parent The parent to set.
	 */
	public void setParent(Folder parent);
}
