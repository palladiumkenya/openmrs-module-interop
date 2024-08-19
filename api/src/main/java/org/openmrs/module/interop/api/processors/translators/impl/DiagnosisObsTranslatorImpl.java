/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.interop.api.processors.translators.impl;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.openmrs.Auditable;
import org.openmrs.Obs;
import org.openmrs.OpenmrsObject;
import org.openmrs.module.fhir2.api.translators.ConceptTranslator;
import org.openmrs.module.fhir2.api.translators.PatientReferenceTranslator;
import org.openmrs.module.interop.api.processors.translators.ConditionObsTranslator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Date;

import static org.apache.commons.lang3.Validate.notNull;

public class DiagnosisObsTranslatorImpl implements ConditionObsTranslator {
	
	@Autowired
	private PatientReferenceTranslator patientReferenceTranslator;
	
	@Autowired
	private ConceptTranslator conceptTranslator;
	
	@Override
	public Condition toFhirResource(@Nonnull Obs obs) {
		notNull(obs, "The Openmrs Obs object should not be null");
		
		/** Todo - Updated this to read from repeated obs */
		Condition fhirCondition = new Condition();
		fhirCondition.setId(obs.getUuid());
		
		fhirCondition.setSubject(patientReferenceTranslator.toFhirResource(obs.getEncounter().getPatient()));
		if (obs.getValueCoded() != null) {
			fhirCondition.setCode(conceptTranslator.toFhirResource(obs.getValueCoded()));
		}
		fhirCondition.setClinicalStatus(new CodeableConcept()
		        .addCoding(new Coding("http://terminology.hl7.org/CodeSystem/condition-clinical", "active", "ACTIVE")));
		
		fhirCondition.setVerificationStatus(new CodeableConcept().addCoding(
		    new Coding("http://terminology.hl7.org/CodeSystem/condition-ver-status", "provisional", "PROVISIONAL")));
		Coding category = new Coding("http://hl7.org/fhir/ValueSet/condition-category", "encounter-diagnosis",
		        "Encounter Diagnosis");
		fhirCondition.addCategory(new CodeableConcept().addCoding(category));
		fhirCondition.setRecordedDate(obs.getDateCreated());
		fhirCondition.getMeta().setLastUpdated(this.getLastUpdated(obs));
		
		return fhirCondition;
	}
	
	public Date getLastUpdated(OpenmrsObject object) {
		if (object instanceof Auditable) {
			Auditable auditable = (Auditable) object;
			return auditable.getDateChanged() != null ? auditable.getDateChanged() : auditable.getDateCreated();
		} else {
			return null;
		}
	}
}
