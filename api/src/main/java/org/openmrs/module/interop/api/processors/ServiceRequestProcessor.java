/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * <p>
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.interop.api.processors;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
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
        return Arrays.asList(Context.getAdministrationService()
                .getGlobalPropertyValue(InteropConstant.CANCER_SCREENING_ENCOUNTER_TYPE_UUIDS, ""));
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
        System.out.println("Referral messages");
        List<Obs> allObs = new ArrayList<>(encounter.getAllObs());

        List<Obs> cancerSymptoms = new ArrayList<>();
        List<Obs> cancerReferralReason = new ArrayList<>();
        List<Obs> cancerScreeningObs = new ArrayList<>();
        if (validateEncounterType(encounter)) {
            allObs.forEach(obs -> {
                if (validateCancerSymptomsObs(obs)) {
                    cancerSymptoms.add(obs);
                }
                if (validateCancerReferralReasonObs(obs)) {
                    cancerReferralReason.add(obs);
                }
                if (validateConceptScreeningObs(obs)) {
                    cancerScreeningObs.add(obs);
                }
            });
        }

        if (!cancerScreeningObs.isEmpty()) {
            List<Reference> obsRefs = new ArrayList<>();
            cancerScreeningObs.forEach(r -> {
                obsRefs.add(new Reference(r.getUuid()).setType("Observation"));
            });

            ServiceRequest serviceRequest = serviceRequestObsTranslator.toFhirResource(encounter);
            serviceRequest.setSupportingInfo(obsRefs);

            if (!cancerSymptoms.isEmpty()) {
                cancerSymptoms.forEach(r -> {
                    serviceRequest.addReasonCode(conceptTranslator.toFhirResource(r.getValueCoded()));
                });
            }

            if (!cancerReferralReason.isEmpty()) {
                cancerReferralReason.forEach(r -> {
                    serviceRequest.addCategory(conceptTranslator.toFhirResource(r.getValueCoded()));
                });
            }

            serviceRequest.setRequester(
                    ReferencesUtil.buildKhmflLocationReference(geLocationByGp(InteropConstant.DEFAULT_FACILITY)));
            serviceRequest.setPerformer(Arrays.asList(ReferencesUtil
                    .buildKhmflLocationReference(geLocationByGp(InteropConstant.CANCER_TREATMENT_REFERRAL_FACILITY))));
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

    public Location geLocationByGp(String gpVal) {
        try {
            Context.addProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
            Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
            String GP_DEFAULT_LOCATION = gpVal;
            GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(GP_DEFAULT_LOCATION);
            return gp != null ? ((Location) gp.getValue()) : null;
        } finally {
            Context.removeProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
            Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
        }

    }

}
