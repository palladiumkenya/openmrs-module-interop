/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.interop.api.observers;

import java.text.SimpleDateFormat;
import java.util.List;

import javax.jms.Message;
import javax.validation.constraints.NotNull;

import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.Event;
import org.openmrs.module.interop.api.Subscribable;
import org.openmrs.module.interop.api.metadata.EventMetadata;
import org.openmrs.module.interop.utils.ObserverUtils;
import org.springframework.stereotype.Component;
import org.openmrs.ui.framework.SimpleObject;
import org.openmrs.util.PrivilegeConstants;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Setter
@Component("interop.rmsPatientRegistrationObserver")
public class RMSPatientRegistrationObserver extends BaseObserver implements Subscribable<Patient> {
	
	@Override
	public void onMessage(Message message) {
		System.out.println("Got message: " + message.toString() + " : " + message.getClass());
		processMessage(message)
		        .ifPresent(metadata -> Daemon.runInDaemonThread(() -> preparePatientRMSPayload(metadata), getDaemonToken()));
	}
	
	private String preparePatientRMSPayload(@NotNull EventMetadata metadata) {
		System.out.println("Got metadata action: " + metadata.getString("action"));
		
		// Check to see if it is a new patient
		if (metadata.getProperty("action") == Event.Action.CREATED) {
			System.out.println("This is a new patient registration");
		}
		
		Patient patient = Context.getPatientService().getPatientByUuid(metadata.getString("uuid"));
		String ret = "";
		if (patient != null) {
			System.out
			        .println("New patient created: " + patient.getPersonName().getFullName() + ", Age: " + patient.getAge());
			SimpleObject payloadPrep = new SimpleObject();
			payloadPrep.put("first_name", patient.getPersonName().getGivenName());
			payloadPrep.put("middle_name", patient.getPersonName().getMiddleName());
			payloadPrep.put("patient_unique_id", patient.getPatientId());
			payloadPrep.put("last_name", patient.getPersonName().getFamilyName());
			PatientIdentifierType nationalIDIdentifierType = Context.getPatientService()
			        .getPatientIdentifierTypeByUuid("49af6cdc-7968-4abb-bf46-de10d7f4859f");
			String natID = "";
			if (nationalIDIdentifierType != null) {
				PatientIdentifier piNatId = patient.getPatientIdentifier(nationalIDIdentifierType);
				
				if (piNatId != null) {
					natID = piNatId.getIdentifier();
					System.err.println("Got the national id as: " + natID);
				}
			}
			payloadPrep.put("id_number", natID);
			String phoneNumber = patient.getAttribute("Telephone contact") != null
			        ? patient.getAttribute("Telephone contact").getValue()
			        : "";
			payloadPrep.put("phone", phoneNumber);
			payloadPrep.put("hospital_code", getDefaultLocationMflCode(null));
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
			payloadPrep.put("dob", formatter.format(patient.getBirthdate()));
			payloadPrep
			        .put("gender",
			            patient.getGender() != null
			                    ? (patient.getGender().equalsIgnoreCase("M") ? "Male"
			                            : (patient.getGender().equalsIgnoreCase("F") ? "Female" : ""))
			                    : "");
			ret = payloadPrep.toJson();
			System.out.println("Got patient registration details: " + ret);
		} else {
			System.out.println("Couldn't find patient with UUID: " + metadata.getString("uuid"));
			log.error("Couldn't find patient with UUID {} ", metadata.getString("uuid"));
		}
		return (ret);
	}
	
	@Override
	public Class<?> clazz() {
		return Patient.class;
	}
	
	@Override
	public List<Event.Action> actions() {
		return ObserverUtils.defaultActions();
	}
	
	/**
	 * gets default location from global property
	 *
	 * @return
	 */
	public static Location getDefaultLocation() {
		Location ret = null;
		try {
			Context.addProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
			Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
			String GP_DEFAULT_LOCATION = "kenyaemr.defaultLocation";
			GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(GP_DEFAULT_LOCATION);
			if (gp != null) {
				Location location = (Location) gp.getValue();
				ret = location;
			}
		}
		catch (Exception ex) {
			System.err.println("Lab System getting location error: " + ex.getMessage());
			ex.printStackTrace();
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
			Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
		}
		return (ret);
	}
	
	/**
	 * Borrowed from KenyaEMR Returns the MFL code for a location
	 *
	 * @param location
	 * @return
	 */
	public static String getDefaultLocationMflCode(Location location) {
		String MASTER_FACILITY_CODE = "8a845a89-6aa5-4111-81d3-0af31c45c002";
		
		if (location == null) {
			location = getDefaultLocation();
		}
		try {
			Context.addProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
			Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
			for (LocationAttribute attr : location.getAttributes()) {
				if (attr.getAttributeType().getUuid().equals(MASTER_FACILITY_CODE) && !attr.getVoided()) {
					return attr.getValueReference();
				}
			}
		}
		catch (Exception ex) {
			System.err.println("Lab System getting location mfl code error: " + ex.getMessage());
			ex.printStackTrace();
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
			Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
		}
		return null;
	}
}
