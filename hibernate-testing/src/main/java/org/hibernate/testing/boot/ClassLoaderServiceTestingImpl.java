/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.testing.boot;

import org.hibernate.boot.registry.classloading.internal.ClassLoaderServiceImpl;

/**
 * @author Steve Ebersole
 */
public class ClassLoaderServiceTestingImpl extends ClassLoaderServiceImpl {
	/**
	 * Singleton access
	 */
	public static final ClassLoaderServiceTestingImpl INSTANCE = new ClassLoaderServiceTestingImpl();
}
