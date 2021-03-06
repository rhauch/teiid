/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */
package org.teiid.rhq.plugin;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.managed.api.ComponentType;
import org.jboss.managed.api.ManagedComponent;
import org.jboss.managed.plugins.ManagedObjectImpl;
import org.jboss.metatype.api.values.CollectionValueSupport;
import org.jboss.metatype.api.values.GenericValueSupport;
import org.jboss.metatype.api.values.MetaValue;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.pluginapi.inventory.DiscoveredResourceDetails;
import org.rhq.core.pluginapi.inventory.InvalidPluginConfigurationException;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryComponent;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryContext;
import org.rhq.plugins.jbossas5.connection.ProfileServiceConnection;
import org.teiid.rhq.plugin.util.PluginConstants;
import org.teiid.rhq.plugin.util.ProfileServiceUtil;

/**
 * Discovery component for VDBs
 * 
 */
public class VDBDiscoveryComponent implements ResourceDiscoveryComponent {

	private final Log log = LogFactory
			.getLog(PluginConstants.DEFAULT_LOGGER_CATEGORY);

	public Set<DiscoveredResourceDetails> discoverResources(
			ResourceDiscoveryContext discoveryContext)
			throws InvalidPluginConfigurationException, Exception {
		Set<DiscoveredResourceDetails> discoveredResources = new HashSet<DiscoveredResourceDetails>();
		ProfileServiceConnection connection = ((PlatformComponent) discoveryContext
				.getParentResourceComponent()).getConnection();

		Set<ManagedComponent> vdbs = ProfileServiceUtil
				.getManagedComponents(connection, new ComponentType(
						PluginConstants.ComponentType.VDB.TYPE,
						PluginConstants.ComponentType.VDB.SUBTYPE));
		
		PropertySimple displayPreviewVdbs = ((PlatformComponent)discoveryContext.getParentResourceComponent()).getResourceConfiguration().getSimple("displayPreviewVDBS");
		
		for (ManagedComponent mcVdb : vdbs) {

			boolean skipVdb = false;
			if (!displayPreviewVdbs.getBooleanValue()){
				MetaValue[] propsArray = ((CollectionValueSupport)mcVdb.getProperty("JAXBProperties").getValue()).getElements();
				String isPreview = "false";
				
				for (MetaValue propertyMetaData : propsArray) {
					GenericValueSupport genValueSupport = (GenericValueSupport) propertyMetaData;
					ManagedObjectImpl managedObject = (ManagedObjectImpl) genValueSupport
							.getValue();
	
					String propertyName = ProfileServiceUtil.getSimpleValue(
							managedObject, "name", String.class);
					if (propertyName.equals("preview")){
						isPreview =ProfileServiceUtil.getSimpleValue(
								managedObject, "value", String.class);
						if (Boolean.valueOf(isPreview)) skipVdb=true;
						break;
					}
				}	
			}
				
			//If this is a Preview VDB and displayPreviewVdbs is false, skip this VDB
			if (skipVdb) continue;
				
			String vdbKey = (String)mcVdb.getName();
			String vdbName = vdbKey;
			String fullName = ProfileServiceUtil.getSimpleValue(mcVdb, "fullName",
					String.class);
			Integer vdbVersion = ProfileServiceUtil.getSimpleValue(mcVdb,
					"version", Integer.class);
			String vdbDescription = ProfileServiceUtil.getSimpleValue(mcVdb,
					"description", String.class);
			String vdbStatus = ProfileServiceUtil.getSimpleValue(mcVdb,
					"status", String.class);
			String vdbURL = ProfileServiceUtil.getSimpleValue(mcVdb, "url",
					String.class);

			/**
			 * 
			 * A discovered resource must have a unique key, that must stay the
			 * same when the resource is discovered the next time
			 */
			DiscoveredResourceDetails detail = new DiscoveredResourceDetails(
					discoveryContext.getResourceType(), // ResourceType
					vdbKey, // Resource Key
					vdbName, // Resource Name
					vdbVersion.toString(), // Version
					PluginConstants.ComponentType.VDB.DESCRIPTION, // Description
					discoveryContext.getDefaultPluginConfiguration(), // Plugin
					// Config
					null // Process info from a process scan
			);

			// Get plugin config map for properties
			Configuration configuration = detail.getPluginConfiguration();

			configuration.put(new PropertySimple("name", vdbName));
			configuration.put(new PropertySimple("fullName", fullName));
			configuration.put(new PropertySimple("version", vdbVersion));
			configuration
					.put(new PropertySimple("description", vdbDescription));
			configuration.put(new PropertySimple("status", vdbStatus));
			configuration.put(new PropertySimple("url", vdbURL));

			detail.setPluginConfiguration(configuration);

			// Add to return values
			discoveredResources.add(detail);
			log.debug("Discovered Teiid VDB: " + vdbName);
		}

		return discoveredResources;
	}

}