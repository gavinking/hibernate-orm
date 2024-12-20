/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.any.annotations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.AnyKeyJavaClass;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.ManyToAny;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table( name = "property_map" )
public class ImplicitPropertyMap {
	private Integer id;
	private String name;

	private Map<String, Property> properties = new HashMap<String, Property>();

	public ImplicitPropertyMap(String name) {
		this.name = name;
	}

	public ImplicitPropertyMap() {
		super();
	}

	@Id
	@GeneratedValue
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@ManyToAny
	@Column( name = "property_type" )
	@AnyKeyJavaClass( Integer.class )
	@Cascade( org.hibernate.annotations.CascadeType.ALL )
	@JoinTable(
			name = "map_properties",
			joinColumns = @JoinColumn( name = "map_id" ),
			inverseJoinColumns = @JoinColumn( name = "property_id" ) )
	@MapKeyColumn( name = "map_key", nullable = false )   //keep for legacy test
	public Map<String, Property> getProperties() {
		return properties;
	}

	public void setProperties(Map<String, Property> properties) {
		this.properties = properties;
	}


}
