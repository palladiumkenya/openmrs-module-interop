/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.interop.utils;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.OpenmrsMetadata;
import org.openmrs.util.OpenmrsUtil;

/**
 * Used to generate simplified representations of data or metadata, making it easier to serialize to
 * json
 */
public class SimpleObject extends LinkedHashMap<String, Object> {
	
	private static final long serialVersionUID = 1L;
	
	public SimpleObject() {
		super();
	}
	
	/**
	 * Convenience constructor for creating a {@link SimpleObject} representing {@link OpenmrsMetadata},
	 * which will set 'id' and 'label' properties
	 * 
	 * @param metadata
	 */
	public SimpleObject(OpenmrsMetadata metadata) {
		super();
		put("id", metadata.getId());
		put("label", metadata.getName());
	}
	
	/**
	 * Utility method to create a {@link SimpleObject} given a varargs style list of property names and
	 * values. The array passed in must have even length. Every other element (starting from the 0-index
	 * one) must be a String (representing a property name) and be followed by its value.
	 * 
	 * @param propertyNamesAndValues
	 * @return
	 */
	public static SimpleObject create(Object... propertyNamesAndValues) {
		SimpleObject ret = new SimpleObject();
		for (int i = 0; i < propertyNamesAndValues.length; i += 2) {
			String prop = (String) propertyNamesAndValues[i];
			ret.put(prop, propertyNamesAndValues[i + 1]);
		}
		return ret;
	}
	
	private static Map<String, Set<String>> splitIntoLevels(String[] propertiesToInclude) {
		Map<String, Set<String>> ret = new LinkedHashMap<String, Set<String>>();
		for (String prop : propertiesToInclude) {
			prop = convertBracketNotationToDotNotation(prop);
			String[] components = prop.split("\\.");
			trimAnyLeadingOrTrailingQuotes(components);
			for (int i = 0; i < components.length; ++i) {
				splitIntoLevelsHelper(ret, Arrays.asList(components), i);
			}
		}
		return ret;
	}
	
	private static String convertBracketNotationToDotNotation(String prop) {
		prop = prop.replaceAll("\\[", ".");
		prop = prop.replaceAll("\\]", "");
		return prop;
	}
	
	private static void trimAnyLeadingOrTrailingQuotes(String[] components) {
		for (int i = 0; i < components.length; ++i) {
			components[i] = components[i].replaceAll("^\"|\"$", "");
			components[i] = components[i].replaceAll("^\'|\'$", "");
		}
	}
	
	private static void splitIntoLevelsHelper(Map<String, Set<String>> ret, List<String> components, int index) {
		String level = OpenmrsUtil.join(components.subList(0, index), ".");
		Set<String> atLevel = ret.get(level);
		if (atLevel == null) {
			atLevel = new LinkedHashSet<String>();
			ret.put(level, atLevel);
		}
		atLevel.add(components.get(index));
	}
	
	public String toJson() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			StringWriter sw = new StringWriter();
			mapper.writeValue(sw, this);
			return sw.toString();
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		return ("");
	}
	
}
