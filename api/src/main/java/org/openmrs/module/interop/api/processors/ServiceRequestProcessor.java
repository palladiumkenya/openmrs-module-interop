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
import org.openmrs.*;
import org.openmrs.Encounter;
import org.openmrs.Location;
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

import java.util.*;

@Slf4j
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
		String referralNote = "";
		Location location = null;
		
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
					if (obs.getValueText() != null) {
						String locationUuid = obs.getValueText();
						String mflCode = obs.getValueText().split("-")[0];
						location = getLocationByMflCode(mflCode);
						if (location == null) {
							location = Context.getLocationService().getLocationByUuid(locationUuid);
						}
					}
				}
				if (!(Strings.isNullOrEmpty(referralNoteConcept))
				        && obs.getConcept().getUuid().equals(referralNoteConcept)) {
					referralNote = obs.getValueText();
				}
			}
		}
		
		ServiceRequest serviceRequest = serviceRequestObsTranslator.toFhirResource(encounter);
		serviceRequest.setAuthoredOn(encounter.getEncounterDatetime());
		//serviceRequest.setSupportingInfo(obsRefs);
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
			Coding code = new Coding("https://openconceptlab.org/orgs/CIEL/sources/CIEL", "consultation", "Consultation");
			code.setDisplay("Consultation");
			serviceRequest.addCategory(codeableConcept.addCoding(code));
		}
		
		//Requester
		if (getDefaultLocation(InteropConstant.DEFAULT_FACILITY) != null) {
			serviceRequest.setRequester(
			    ReferencesUtil.buildKhmflLocationReference(getDefaultLocation(InteropConstant.DEFAULT_FACILITY)));
		}
		
		//Performer
		if (location != null) {
			serviceRequest.setPerformer(Collections.singletonList(ReferencesUtil.buildKhmflLocationReference(location)));
		} else if (geLocationByGp(InteropConstant.CANCER_TREATMENT_REFERRAL_FACILITY) != null) {
			Reference reference = ReferencesUtil
			        .buildKhmflLocationReference(geLocationByGp(InteropConstant.CANCER_TREATMENT_REFERRAL_FACILITY));
			if (reference.getIdentifier() != null) {
				System.out.println("The facility was configured as gp");
				serviceRequest.setPerformer(Collections.singletonList(reference));
			}
			
		} else {
			System.out.println("Facility data not captured");
		}
		
		return Collections.singletonList(serviceRequest);
		// Screening and findings data
		//		if (!cancerScreeningObs.isEmpty()) {
		//			List<Reference> obsRefs = new ArrayList<>();
		//			cancerScreeningObs.forEach(r -> {
		//				obsRefs.add(new Reference(r.getUuid()).setType("Observation"));
		//			});
		//		}
		
		//		return new ArrayList<>();
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
	
	public Location getLocationByMflCode(String mflCode) {
		LocationAttributeType mflCodeAttrType = Context.getLocationService()
		        .getLocationAttributeTypeByUuid("8a845a89-6aa5-4111-81d3-0af31c45c002");
		Map<LocationAttributeType, Object> attrVals = new HashMap();
		attrVals.put(mflCodeAttrType, mflCode);
		List<Location> locations = Context.getLocationService().getLocations(null, null, attrVals, false, null, null);
		return locations.size() > 0 ? locations.get(0) : null;
	}
}
