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
import org.hl7.fhir.r4.model.DateTimeType;
import org.openmrs.Auditable;
import org.openmrs.Diagnosis;
import org.openmrs.OpenmrsObject;
import org.openmrs.Patient;
import org.openmrs.module.fhir2.api.translators.ConceptTranslator;
import org.openmrs.module.fhir2.api.translators.PatientReferenceTranslator;
import org.openmrs.module.interop.api.processors.translators.InteropConditionTranslator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Date;

@Component("interop.interopDiagnosisTranslator")
public class InteropDiagnosisTranslatorImp implements InteropConditionTranslator<Diagnosis> {
	
	@Autowired
	private PatientReferenceTranslator patientReferenceTranslator;
	
	@Autowired
	private ConceptTranslator conceptTranslator;
	
	@Override
	public org.hl7.fhir.r4.model.Condition toFhirResource(@Nonnull Diagnosis diagnosis) {
		org.hl7.fhir.r4.model.Condition fhirCondition = new org.hl7.fhir.r4.model.Condition();
		fhirCondition.setId(diagnosis.getUuid());
		Patient patient = diagnosis.getPatient();
		fhirCondition.setSubject(patientReferenceTranslator.toFhirResource(patient));
		if (diagnosis.getDiagnosis() != null && diagnosis.getDiagnosis().getCoded() != null) {
			fhirCondition.setCode(conceptTranslator.toFhirResource(diagnosis.getDiagnosis().getCoded()));
		}
		fhirCondition.setClinicalStatus(new CodeableConcept()
		        .addCoding(new Coding("http://terminology.hl7.org/CodeSystem/condition-clinical", "active", "ACTIVE")));
		if (diagnosis.getCertainty() != null) {
			fhirCondition.setVerificationStatus(
			    new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/condition-ver-status",
			            diagnosis.getCertainty().toString().toLowerCase(), diagnosis.getCertainty().toString())));
		}
		Coding category = new Coding("http://hl7.org/fhir/ValueSet/condition-category", "encounter-diagnosis",
		        "Encounter Diagnosis");
		fhirCondition.setCategory(Collections.singletonList(new CodeableConcept().addCoding(category)));
		fhirCondition.setOnset(new DateTimeType().setValue(diagnosis.getDateCreated()));
		fhirCondition.setRecordedDate(diagnosis.getDateCreated());
		fhirCondition.getMeta().setLastUpdated(this.getLastUpdated(diagnosis));
		
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
