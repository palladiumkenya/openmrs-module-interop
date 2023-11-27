/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.interop.api.processors;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.api.translators.ConceptTranslator;
import org.openmrs.module.fhir2.api.translators.ObservationTranslator;
import org.openmrs.module.interop.InteropConstant;
import org.openmrs.module.interop.api.InteropProcessor;
import org.openmrs.module.interop.api.processors.translators.ServiceRequestObsTranslator;
import org.openmrs.module.interop.utils.ReferencesUtil;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Component("interop.serviceRequestProcessor")
public class ServiceRequestProcessor implements InteropProcessor<Encounter> {
	
	@Autowired
	@Qualifier("interop.serviceRequestObsTranslator")
	private ServiceRequestObsTranslator serviceRequestObsTranslator;
	
	@Autowired
	private ObservationTranslator observationTranslator;
	
	@Autowired
	private ConceptTranslator conceptTranslator;
	
	@Override
	public List<String> encounterTypes() {
		String encUuid = Context.getAdministrationService()
		        .getGlobalPropertyValue(InteropConstant.CANCER_SCREENING_ENCOUNTER_TYPE_UUIDS, "");
		return Arrays.asList(encUuid.split(","));
	}
	
	@Override
	public List<String> questions() {
		String cancerScreeningConcepts = Context.getAdministrationService()
		        .getGlobalPropertyValue(InteropConstant.CANCER_SCREENING_CONCEPT_UUID, "");
		
		return Arrays.asList(cancerScreeningConcepts.split(","));
	}
	
	@Override
	public List<String> forms() {
		return null;
	}
	
	@Override
	public List<ServiceRequest> process(Encounter encounter) {
		System.out.println("Preparing Referral message");
		List<Obs> allObs = new ArrayList<>(encounter.getAllObs());
		String facilityConcept = "159495AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
		//		String referralNoteConcept = "162169AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
		String referralNote = "";
		Location location = new Location();
		
		String referralNoteConcept = Context.getAdministrationService()
		        .getGlobalPropertyValue(InteropConstant.REFERRAL_NOTE_CONCEPT_UUID, "");
		
		List<Obs> cancerSymptoms = new ArrayList<>();
		List<Obs> cancerReferralReason = new ArrayList<>();
		List<Obs> cancerScreeningObs = new ArrayList<>();
		if (validateEncounterType(encounter)) {
			for (Obs obs : allObs) {
				if (validateCancerSymptomsObs(obs)) {
					cancerSymptoms.add(obs);
				}
				if (validateCancerReferralReasonObs(obs)) {
					cancerReferralReason.add(obs);
				}
				if (validateConceptScreeningObs(obs)) {
					cancerScreeningObs.add(obs);
				}
				if (obs.getConcept().getUuid().equals(facilityConcept)) {
					String locationUuid = obs.getValueText();
					location = Context.getLocationService().getLocationByUuid(locationUuid);
				}
				if (!(Strings.isNullOrEmpty(referralNoteConcept))
				        && obs.getConcept().getUuid().equals(referralNoteConcept)) {
					referralNote = obs.getValueText();
				} else {
					System.out.println("Referral notes not captured || referral note concept was not specified");
				}
			}
		}
		
		if (!cancerScreeningObs.isEmpty()) {
			List<Reference> obsRefs = new ArrayList<>();
			cancerScreeningObs.forEach(r -> {
				obsRefs.add(new Reference(r.getUuid()).setType("Observation"));
			});
			
			ServiceRequest serviceRequest = serviceRequestObsTranslator.toFhirResource(encounter);
			serviceRequest.setSupportingInfo(obsRefs);
			if (!Strings.isNullOrEmpty(referralNote)) {
				System.out.println("Processing referral notes");
				serviceRequest.addNote(new Annotation().setText(referralNote));
			}
			
			if (!cancerSymptoms.isEmpty()) {
				cancerSymptoms.forEach(r -> {
					serviceRequest.addReasonCode(conceptTranslator.toFhirResource(r.getValueCoded()));
				});
			}
			
			if (!cancerReferralReason.isEmpty()) {
				cancerReferralReason.forEach(r -> {
					serviceRequest.addCategory(conceptTranslator.toFhirResource(r.getValueCoded()));
				});
			} else {
				CodeableConcept codeableConcept = new CodeableConcept();
				Coding code = new Coding("https://openconceptlab.org/orgs/CIEL/sources/CIEL", "consultation",
				        "Consultation");
				code.setDisplay("Consultation");
				serviceRequest.addCategory(codeableConcept.addCoding(code));
			}
			
			//Requester
			if (getDefaultLocation(InteropConstant.DEFAULT_FACILITY) != null) {
				serviceRequest.setRequester(
				    ReferencesUtil.buildKhmflLocationReference(getDefaultLocation(InteropConstant.DEFAULT_FACILITY)));
			}
			
			//Performer
			if (location.getId() != null) {
				serviceRequest.setPerformer(Arrays.asList(ReferencesUtil.buildKhmflLocationReference(location)));
			} else {
				System.out.println("Facility data not captured");
			}
			//			if (geLocationByGp(InteropConstant.CANCER_TREATMENT_REFERRAL_FACILITY) != null) {
			//				Reference reference = ReferencesUtil
			//						.buildKhmflLocationReference(geLocationByGp(InteropConstant.CANCER_TREATMENT_REFERRAL_FACILITY));
			//				if (reference.getIdentifier() != null) {
			//					System.out.println("The facility was configured as gp");
			//					serviceRequest.setPerformer(Arrays.asList(reference));
			//				} else {
			//					if (location.getId() != null) {
			//						serviceRequest.setPerformer(Arrays.asList(ReferencesUtil.buildKhmflLocationReference(location)));
			//					}
			//				}
			//			}
			
			return Arrays.asList(serviceRequest);
		}
		
		return new ArrayList<>();
	}
	
	public boolean validateCancerSymptomsObs(Obs conceptObs) {
		String conceptString = Context.getAdministrationService()
		        .getGlobalPropertyValue(InteropConstant.CANCER_SCREENING_SYMPTOMS_CONCEPT_UUID, "");
		
		List<String> conceptUuids = Arrays.asList(conceptString.split(","));
		
		return conceptUuids.contains(conceptObs.getConcept().getUuid());
	}
	
	public boolean validateCancerReferralReasonObs(Obs conceptObs) {
		String conceptString = Context.getAdministrationService()
		        .getGlobalPropertyValue(InteropConstant.CANCER_SCREENING_REFERRAL_REASON_CONCEPT_UUID, "");
		
		List<String> conceptUuids = Arrays.asList(conceptString.split(","));
		
		return conceptUuids.contains(conceptObs.getConcept().getUuid());
	}
	
	private boolean validateEncounterType(Encounter encounter) {
		return encounterTypes().contains(encounter.getEncounterType().getUuid());
	}
	
	private boolean validateConceptScreeningObs(Obs conceptObs) {
		return questions().contains(conceptObs.getConcept().getUuid());
	}
	
	public Location getDefaultLocation(String gpVal) {
		try {
			Context.addProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
			Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
			GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(gpVal);
			return gp != null ? ((Location) gp.getValue()) : null;
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
			Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
		}
		
	}
	
	public Location geLocationByGp(String gpVal) {
		try {
			Context.addProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
			Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
			GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(gpVal);
			if (gp != null) {
				return Context.getLocationService().getLocation(Integer.valueOf((String) gp.getValue()));
			} else {
				return null;
			}
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
			Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
		}
		
	}
}
