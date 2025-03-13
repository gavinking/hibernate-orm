/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.event.jfr.internal;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import org.hibernate.event.monitor.spi.DiagnosticEvent;
import org.hibernate.internal.build.AllowNonPortable;

@Name(EntityUpsertEvent.NAME)
@Label("Entity Upsert")
@Category("Hibernate ORM")
@Description("Entity Upsert")
@StackTrace
@AllowNonPortable
public class EntityUpsertEvent extends Event implements DiagnosticEvent {
	public static final String NAME = "org.hibernate.orm.EntityUpsertEvent";

	@Label("Session Identifier")
	public String sessionIdentifier;

	@Label("Entity Identifier")
	public String id;

	@Label("Entity Name")
	public String entityName;

	@Label("Success")
	public boolean success;

	@Override
	public String toString() {
		return NAME;
	}

}
