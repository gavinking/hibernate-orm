/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.event.spi;

import org.hibernate.Incubating;

/**
 * An event which may be collected by the {@link EventMonitor}.
 * <p>
 * An implementation of {@code EventMonitor} must define its
 * own implementation or implementations of this interface,
 * but these subtypes are never visible to the code which
 * calls the {@code EventMonitor} to report events.
 *
 * @since 7.0
 */
@Incubating
public interface DiagnosticEvent {
}
