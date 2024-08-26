/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.interop;

public class InteropConstant {
	
	public static final String GP_ENABLE_KAFKA = "interop.enableKafka";
	
	public static final String GP_ENABLE_OPENHIM = "interop.enableOpenHIM";
	
	public static final String NATIONAL_UNIQUE_PATIENT_NUMBER_UUID = "interop.nupi.patientIdentifierTypeUuid";

	public static final String SOCIAL_HEALTH_AUTHORITY_IDENTIFICATION_NUMBER_UUID = "interop.crid.patientIdentifierTypeUuid";
	
	public static final String INTEROP_MFLCODE_LOCATION_ATTRIBUTE_TYPE_UUID = "interop.mflcode.locationAttributeTypeUuid";
	
	public static final String INTEROP_PROVIDER_ATTRIBUTE_TYPE_UUID = "interop.practitionerAttributeTypeUuid";
	
	public static String CONDITIONS_CONCEPT_UUID = "interop.conditions";
	
	public static String CONDITION_BROKER_ENCOUNTER_TYPE_UUIDS = "interop.encounterTypes.enabled";
	
	public static String SYSTEM_URL = "interop.system.url.configuration";
	
	public static String CR_SYSTEM_URL = "interop.cr.system.url.configuration";
	
	public static String KMHFL_SYSTEM_URL = "interop.kmhfl.system.url.configuration";
	
	public static String APPOINTMENT_PROCESSOR_ENCOUNTER_TYPE_UUIDS = "interop.encounterTypes.appointments";
	
	public static String APPOINTMENT_WITH_CODED_TYPES = "interop.appointmentWithCodedTypes";
	
	public static String APPOINTMENT_WITH_NON_CODED_TYPES = "interop.appointmentWithNonCodedTypes";
	
	public static String ALLERGY_PROCESSOR_ENCOUNTER_TYPE_UUIDS = "interop.encounterTypes.allergyIntolerance";
	
	public static String ALLERGY_CAUSATIVE_AGENT_CONCEPT_UUID = "interop.allergyCausativeAgent";
	
	public static String ALLERGY_REACTION_CONCEPT_UUID = "interop.allergyReaction";
	
	public static String ALLERGY_SEVERITY_CONCEPT_UUID = "interop.allergySeverity";
	
	public static String ALLERGY_ONSET_DATE_CONCEPT_UUID = "interop.allergyOnsetDate";
	
	public static String ALLERGY_ACTION_TAKEN_CONCEPT_UUID = "interop.allergyActionTaken";
	
	public static String ALLERGY_SEVERITY_MODERATE_CONCEPT_UUID = "interop.allergyReactionModerate";
	
	public static String ALLERGY_SEVERITY_SEVERE_CONCEPT_UUID = "interop.allergyReactionSevere";
	
	public static String DIAGNOSIS_CONCEPTS = "interop.diagnosisConcepts";
	
	public static String DIAGNOSIS_ENCOUNTER_TYPES = "interop.encounterTypes.diagnosis";
	
	public static String LAB_RESULT_PROCESSOR_ENCOUNTER_TYPE_UUIDS = "interop.encounterTypes.labResults";
	
	public static String LAB_RESULT_CONCEPT_UUID = "interop.labResults";
	
	public static String CANCER_SCREENING_ENCOUNTER_TYPE_UUIDS = "interop.encounterTypes.cancerReferral";
	
	public static String CANCER_SCREENING_SYMPTOMS_CONCEPT_UUID = "interop.cancerReferralSymptoms";
	
	public static String REFERRAL_NOTE_CONCEPT_UUID = "interop.referralNoteConcept";
	
	public static String CANCER_SCREENING_CONCEPT_UUID = "interop.cancerScreeningConcepts";
	
	public static String CANCER_SCREENING_FINDINGS_CONCEPT_UUID = "interop.cancerScreeningFindingsConcepts";
	
	public static String CANCER_SCREENING_ACTION_CONCEPT_UUID = "interop.cancerScreeningTreatmentActionConcepts";
	
	public static String CANCER_SCREENING_REFERRAL_REASON_CONCEPT_UUID = "interop.cancerReferralReason";
	
	public static String CANCER_TREATMENT_REFERRAL_FACILITY = "interop.cancerTxReferralFacility";
	
	public static String DEFAULT_FACILITY = "kenyaemr.defaultLocation";
	
	public static String VITALS_PROCESSOR_ENCOUNTER_TYPE_UUIDS = "interop.encounterTypes.vitals";
	
	public static String VITAL_CONCEPT_UUIDS = "interop.vitalsConcepts";
	
	public static String COMPLAINTS_PROCESSOR_ENCOUNTER_TYPE_UUIDS = "interop.encounterTypes.complaints";
	
	public static String COMPLAINTS_CONCEPT_UUIDS = "interop.complaintsConcepts";
	
	public static final String GP_SHR_TOKEN = "interop.shr.token";
	
	public static final String GP_SHR_TOKEN_URL = "interop.shr.token.url";
	
	public static final String GP_SHR_OAUTH2_CLIENT_ID = "interop.shr.oauth2.client.id";
	
	public static final String GP_SHR_OAUTH2_CLIENT_SECRET = "interop.shr.oauth2.client.secret";
	
	public static final String GP_SHR_OAUTH2_SCOPE = "interop.shr.oauth2.scope";

	public static final String OAUTH_USERNAME = "interop.shr.oauth.username";

	public static final String OAUTH_PASSWORD = "interop.shr.oauth.password";

}
