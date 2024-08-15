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
import org.hl7.fhir.r4.model.Condition;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.api.translators.ConceptTranslator;
import org.openmrs.module.interop.InteropConstant;
import org.openmrs.module.interop.api.InteropProcessor;
import org.openmrs.module.interop.api.processors.translators.ConditionObsTranslator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component("interop.diagnosisProcessor")
public class DiagnosisProcessor implements InteropProcessor<Encounter> {
	
	@Autowired
	private ConceptTranslator conceptTranslator;
	
	@Autowired
	@Qualifier("interop.diagnosis")
	private ConditionObsTranslator diagnosisObsTranslator;
	
	@Override
	public List<String> encounterTypes() {
		
		return Arrays.asList(Context.getAdministrationService()
		        .getGlobalPropertyValue(InteropConstant.DIAGNOSIS_ENCOUNTER_TYPES, "").split(","));
	}
	
	@Override
	public List<String> questions() {
		String conditionString = Context.getAdministrationService()
		        .getGlobalPropertyValue(InteropConstant.DIAGNOSIS_CONCEPTS, "");
		
		return Arrays.asList(conditionString.split(","));
	}
	
	@Override
	public List<String> forms() {
		return null;
	}
	
	@Override
	public List<Condition> process(Encounter encounter) {
		List<Obs> allObs = new ArrayList<>(encounter.getAllObs());
		
		List<Obs> conditionsObs = new ArrayList<>();
		if (validateEncounterType(encounter)) {
			allObs.forEach(obs -> {
				if (validateConceptQuestions(obs)) {
					conditionsObs.add(obs);
				}
			});
		}
		
		List<Condition> conditions = new ArrayList<>();
		if (!conditionsObs.isEmpty()) {
			conditionsObs.forEach(obs -> {
				Condition condition = diagnosisObsTranslator.toFhirResource(obs);
				conditions.add(condition);
			});
		}
		
		return conditions;
	}
	
	private boolean validateEncounterType(Encounter encounter) {
		return encounterTypes().contains(encounter.getEncounterType().getUuid());
	}
	
	private boolean validateConceptQuestions(Obs conceptObs) {
		return questions().contains(conceptObs.getConcept().getUuid());
	}
	
}
