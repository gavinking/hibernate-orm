/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.internal;

import org.hibernate.AnnotationException;

/**
 * Indicates a failure processing a {@link org.hibernate.boot.spi.SecondPass},
 * where the hope is that subsequent processing will be able to recover from it.
 *
 * @author Emmanuel Bernard
 *
 * @deprecated It would be nice to be able to eliminate this completely
 */
@Deprecated
public class FailedSecondPassException extends AnnotationException {
	/**
	 * Constructs a FailedSecondPassException using the given message and underlying cause.
	 *
	 * @param msg The message explaining the condition that caused the exception
	 * @param cause The underlying exception
	 */
	public FailedSecondPassException(String msg, Throwable cause) {
		super( msg, cause );
	}
}