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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;

import javax.jms.Message;
import javax.net.ssl.HttpsURLConnection;
import javax.validation.constraints.NotNull;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.Event;
import org.openmrs.module.interop.InteropConstant;
import org.openmrs.module.interop.api.Subscribable;
import org.openmrs.module.interop.api.metadata.EventMetadata;
import org.openmrs.module.interop.utils.ObserverUtils;
import org.springframework.stereotype.Component;
import org.openmrs.module.interop.utils.SimpleObject;
import org.openmrs.util.PrivilegeConstants;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Setter
@Component("interop.rmsPatientRegistrationObserver")
public class RMSPatientRegistrationObserver extends BaseObserver implements Subscribable<Patient> {
	
	@Override
	public void onMessage(Message message) {
		System.out
		        .println("RMS Sync: Got Pattient Registration message: " + message.toString() + " : " + message.getClass());
		
		GlobalProperty rmsSyncEnabledGP = Context.getAdministrationService()
		        .getGlobalPropertyObject(InteropConstant.RMS_SYNC_ENABLED);
		String rmsSyncEnabled = rmsSyncEnabledGP.getPropertyValue();
		
		if (rmsSyncEnabled != null && !rmsSyncEnabled.trim().isEmpty() && rmsSyncEnabled.trim().equalsIgnoreCase("true")) {
			processMessage(message).ifPresent(
			    metadata -> Daemon.runInDaemonThread(() -> sendRMSPatientRegistration(metadata), getDaemonToken()));
		} else {
			System.out.println("RMS Sync: Sync disabled. Not syncing");
		}
	}
	
	private Boolean sendRMSPatientRegistration(@NotNull EventMetadata metadata) {
		Boolean ret = false;
		String payload = preparePatientRMSPayload(metadata);
		
		HttpsURLConnection con = null;
		HttpsURLConnection connection = null;
		try {
			System.out.println("RMS using payload: " + payload);
			
			// Create URL
			GlobalProperty globalPostUrl = Context.getAdministrationService()
			        .getGlobalPropertyObject(InteropConstant.RMS_ENDPOINT_URL);
			String baseURL = globalPostUrl.getPropertyValue();
			if (baseURL == null || baseURL.trim().isEmpty()) {
				baseURL = "https://siaya.tsconect.com/api";
			}
			String completeURL = baseURL + "/login";
			System.out.println("RMS Sync Auth URL: " + completeURL);
			URL url = new URL(completeURL);
			GlobalProperty rmsUserGP = Context.getAdministrationService()
			        .getGlobalPropertyObject(InteropConstant.RMS_USERNAME);
			String rmsUser = rmsUserGP.getPropertyValue();
			GlobalProperty rmsPasswordGP = Context.getAdministrationService()
			        .getGlobalPropertyObject(InteropConstant.RMS_PASSWORD);
			String rmsPassword = rmsPasswordGP.getPropertyValue();
			SimpleObject authPayloadCreator = SimpleObject.create("email", rmsUser != null ? rmsUser : "", "password",
			    rmsPassword != null ? rmsPassword : "");
			String authPayload = authPayloadCreator.toJson();
			
			// Get token
			con = (HttpsURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			con.setRequestProperty("Accept", "application/json");
			con.setConnectTimeout(10000); // set timeout to 10 seconds
			
			PrintStream os = new PrintStream(con.getOutputStream());
			os.print(authPayload);
			os.close();
			
			int responseCode = con.getResponseCode();
			
			if (responseCode == HttpURLConnection.HTTP_OK) { //success
				BufferedReader in = null;
				in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				
				String input;
				StringBuffer response = new StringBuffer();
				
				while ((input = in.readLine()) != null) {
					response.append(input);
				}
				in.close();
				
				String returnResponse = response.toString();
				System.out.println("RMS Sync: Got Auth Response as: " + returnResponse);
				
				// Extract the token and token expiry date
				ObjectMapper mapper = new ObjectMapper();
				JsonNode jsonNode = null;
				String token = "";
				String expires_at = "";
				SimpleObject authObj = new SimpleObject();
				
				try {
					jsonNode = mapper.readTree(returnResponse);
					if (jsonNode != null) {
						token = jsonNode.get("token") == null ? "" : jsonNode.get("token").getTextValue();
						authObj.put("token", token);
						expires_at = jsonNode.get("expires_at") == null ? "" : jsonNode.get("expires_at").getTextValue();
						authObj.put("expires_at", expires_at);
					}
				}
				catch (Exception e) {
					System.err.println("RMS Sync: Error getting auth token: " + e.getMessage());
					e.printStackTrace();
				}
				
				if (!token.isEmpty()) {
					try {
						// We send the payload to RMS
						System.err.println(
						    "RMS Sync: We got the Auth token. Now sending the patient registration details. Token: "
						            + token);
						String finalUrl = baseURL + "/create-patient-profile";
						System.out.println("RMS Sync Final URL: " + finalUrl);
						URL finUrl = new URL(finalUrl);
						
						connection = (HttpsURLConnection) finUrl.openConnection();
						connection.setRequestMethod("POST");
						connection.setDoOutput(true);
						connection.setRequestProperty("Authorization", "Bearer " + token);
						connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
						connection.setRequestProperty("Accept", "application/json");
						connection.setConnectTimeout(10000);
						
						PrintStream pos = new PrintStream(connection.getOutputStream());
						pos.print(payload);
						pos.close();
						
						int finalResponseCode = connection.getResponseCode();
						
						if (finalResponseCode == HttpURLConnection.HTTP_OK) { //success
							BufferedReader fin = null;
							fin = new BufferedReader(new InputStreamReader(connection.getInputStream()));
							
							String finalOutput;
							StringBuffer finalResponse = new StringBuffer();
							
							while ((finalOutput = fin.readLine()) != null) {
								finalResponse.append(finalOutput);
							}
							in.close();
							
							String finalReturnResponse = finalResponse.toString();
							System.out.println("RMS Sync: Got Auth Response as: " + finalReturnResponse);
							
							ObjectMapper finalMapper = new ObjectMapper();
							JsonNode finaljsonNode = null;
							Boolean success = false;
							String message = "";
							
							try {
								finaljsonNode = finalMapper.readTree(finalReturnResponse);
								if (finaljsonNode != null) {
									success = finaljsonNode.get("success") == null ? false
									        : finaljsonNode.get("success").getBooleanValue();
									message = finaljsonNode.get("message") == null ? ""
									        : finaljsonNode.get("message").getTextValue();
								}
								
								System.err.println("RMS Sync: Got patient registration final response: success: " + success
								        + " message: " + message);
							}
							catch (Exception e) {
								System.err.println(
								    "RMS Sync: Error getting patient registration final response: " + e.getMessage());
								e.printStackTrace();
							}
							
							if (success != null && success == true) {
								ret = true;
							}
							
						} else {
							System.err.println("RMS Sync. Failed to send final payload: " + finalResponseCode);
						}
					}
					catch (Exception em) {
						System.out.println("RMS Sync Error. Failed to send the final payload: " + em.getMessage());
						em.printStackTrace();
					}
				}
			} else {
				System.err.println("RMS Sync. Failed to get auth: " + responseCode);
			}
			
		}
		catch (Exception ex) {
			System.out.println("RMS Sync Error. Failed to get auth token: " + ex.getMessage());
			ex.printStackTrace();
		}
		
		return (ret);
	}
	
	private String preparePatientRMSPayload(@NotNull EventMetadata metadata) {
		System.out.println("RMS Sync: Got metadata action: " + metadata.getString("action"));
		
		// Check to see if it is a new patient
		if (metadata.getProperty("action") == Event.Action.CREATED) {
			System.out.println("RMS Sync: This is a new patient registration");
		}
		
		Patient patient = Context.getPatientService().getPatientByUuid(metadata.getString("uuid"));
		String ret = "";
		if (patient != null) {
			System.out.println(
			    "RMS Sync: New patient created: " + patient.getPersonName().getFullName() + ", Age: " + patient.getAge());
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
					System.err.println("RMS Sync: Got the national id as: " + natID);
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
			System.out.println("RMS Sync: Got patient registration details: " + ret);
		} else {
			System.out.println("RMS Sync: Couldn't find patient with UUID: " + metadata.getString("uuid"));
			log.error("RMS Sync: Couldn't find patient with UUID {} ", metadata.getString("uuid"));
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
			System.err.println("RMS Sync: getting location error: " + ex.getMessage());
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
			System.err.println("RMS Sync: getting location mfl code error: " + ex.getMessage());
			ex.printStackTrace();
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
			Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
		}
		return null;
	}
}
