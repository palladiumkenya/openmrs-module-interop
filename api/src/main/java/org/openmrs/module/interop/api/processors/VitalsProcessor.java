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
import org.hl7.fhir.r4.model.Observation;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.api.translators.ObservationTranslator;
import org.openmrs.module.interop.InteropConstant;
import org.openmrs.module.interop.api.InteropProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component("interop.vitalsProcessor")
public class VitalsProcessor implements InteropProcessor<Encounter> {
	
	@Autowired
	private ObservationTranslator observationTranslator;
	
	@Override
	public List<String> encounterTypes() {
		return Arrays.asList(Context.getAdministrationService()
		        .getGlobalPropertyValue(InteropConstant.VITALS_PROCESSOR_ENCOUNTER_TYPE_UUIDS, "").split(","));
	}
	
	@Override
	public List<String> questions() {
		String vitalstString = Context.getAdministrationService().getGlobalPropertyValue(InteropConstant.VITAL_CONCEPT_UUIDS,
		    "");
		
		return Arrays.asList(vitalstString.split(","));
	}
	
	@Override
	public List<String> forms() {
		return null;
	}
	
	@Override
	public List<Observation> process(Encounter encounter) {
		List<Obs> encounterObs = new ArrayList<>(encounter.getAllObs());
		
		List<Obs> vitalObs = new ArrayList<>();
		if (validateEncounterType(encounter)) {
			encounterObs.forEach(obs -> {
				if (validateConceptQuestions(obs)) {
					vitalObs.add(obs);
				}
			});
		}
		
		List<Observation> vitals = new ArrayList<>();
		if (!vitalObs.isEmpty()) {
			for (Obs obs : vitalObs) {
				Observation observation = observationTranslator.toFhirResource(obs);
				vitals.add(observation);
			}
		}
		
		return vitals;
	}
	
	private boolean validateEncounterType(Encounter encounter) {
		return encounterTypes().contains(encounter.getEncounterType().getUuid());
	}
	
	private boolean validateConceptQuestions(Obs conceptObs) {
		return questions().contains(conceptObs.getConcept().getUuid());
	}
}
