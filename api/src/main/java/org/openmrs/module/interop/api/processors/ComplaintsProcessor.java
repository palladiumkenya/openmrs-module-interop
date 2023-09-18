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

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.api.translators.ObservationTranslator;
import org.openmrs.module.interop.InteropConstant;
import org.openmrs.module.interop.api.InteropProcessor;
import org.openmrs.module.interop.utils.ReferencesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component("interop.complaintsProcessor")
public class ComplaintsProcessor implements InteropProcessor<Encounter> {
	
	@Autowired
	private ObservationTranslator observationTranslator;
	
	@Override
	public List<String> encounterTypes() {
		return Arrays.asList(Context.getAdministrationService()
		        .getGlobalPropertyValue(InteropConstant.COMPLAINTS_PROCESSOR_ENCOUNTER_TYPE_UUIDS, "").split(","));
	}
	
	@Override
	public List<String> questions() {
		String complaintsString = Context.getAdministrationService()
		        .getGlobalPropertyValue(InteropConstant.COMPLAINTS_CONCEPT_UUIDS, "");
		
		return Arrays.asList(complaintsString.split(","));
	}
	
	@Override
	public List<String> forms() {
		return null;
	}
	
	@Override
	public List<Observation> process(Encounter encounter) {
		List<Obs> encounterObs = new ArrayList<>(encounter.getAllObs());
		
		List<Obs> complaintsObs = new ArrayList<>();
		if (validateEncounterType(encounter)) {
			encounterObs.forEach(obs -> {
				if (validateConceptQuestions(obs)) {
					complaintsObs.add(obs);
				}
			});
		}
		
		List<Observation> allComplaints = new ArrayList<>();
		if (!complaintsObs.isEmpty()) {
			for (Obs obs : complaintsObs) {
				Observation observation = observationTranslator.toFhirResource(obs);
				observation.setSubject(ReferencesUtil.buildPatientReference(encounter.getPatient()));
				observation.addCategory(new CodeableConcept().addCoding(
				    new Coding("http://terminology.hl7.org/CodeSystem/observation-category", "exam", "Exam")));
				allComplaints.add(observation);
			}
		}
		
		return allComplaints;
	}
	
	private boolean validateEncounterType(Encounter encounter) {
		return encounterTypes().contains(encounter.getEncounterType().getUuid());
	}
	
	private boolean validateConceptQuestions(Obs conceptObs) {
		return questions().contains(conceptObs.getConcept().getUuid());
	}
}
