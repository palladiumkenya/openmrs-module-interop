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
import org.openmrs.Condition;
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

@Component("interop.interopConditionTranslator")
public class InteropConditionTranslatorImpl implements InteropConditionTranslator<Condition> {
	
	@Autowired
	private PatientReferenceTranslator patientReferenceTranslator;
	
	@Autowired
	private ConceptTranslator conceptTranslator;
	
	@Override
	public org.hl7.fhir.r4.model.Condition toFhirResource(@Nonnull Condition condition) {
		org.hl7.fhir.r4.model.Condition fhirCondition = new org.hl7.fhir.r4.model.Condition();
		fhirCondition.setId(condition.getUuid());
		Patient patient = condition.getPatient();
		fhirCondition.setSubject(patientReferenceTranslator.toFhirResource(patient));
		if (condition.getCondition() != null && condition.getCondition().getCoded() != null) {
			fhirCondition.setCode(conceptTranslator.toFhirResource(condition.getCondition().getCoded()));
		}
		if (condition.getClinicalStatus() != null) {
			fhirCondition.setClinicalStatus(
			    new CodeableConcept().addCoding(new Coding("http://hl7.org/fhir/ValueSet/condition-clinical",
			            condition.getClinicalStatus().toString().toLowerCase(), condition.getClinicalStatus().toString())));
		}
		if (condition.getVerificationStatus() != null) {
			fhirCondition.setVerificationStatus(
			    new CodeableConcept().addCoding(new Coding("http://hl7.org/fhir/ValueSet/condition-ver-status",
			            condition.getVerificationStatus().toString().toLowerCase(),
			            condition.getVerificationStatus().toString())));
		}
		Coding category = new Coding("http://hl7.org/fhir/ValueSet/condition-category ", "conditions", "Conditions");
		fhirCondition.setCategory(Collections.singletonList(new CodeableConcept().addCoding(category)));
		fhirCondition.setOnset(new DateTimeType().setValue(condition.getOnsetDate()));
		fhirCondition.setRecordedDate(condition.getDateCreated());
		fhirCondition.setAbatement(new DateTimeType().setValue(condition.getEndDate()));
		fhirCondition.getMeta().setLastUpdated(this.getLastUpdated(condition));
		
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
