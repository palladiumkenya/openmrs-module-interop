/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * <p>
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.interop.api.processors.translators.impl;

import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.translators.EncounterReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.PatientReferenceTranslator;
import org.openmrs.module.interop.api.processors.translators.ServiceRequestObsTranslator;
import org.openmrs.module.interop.utils.ReferencesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

import static org.apache.commons.lang3.Validate.notNull;

@Component("interop.serviceRequestObsTranslator")
public class ServiceRequestObsTranslatorImpl implements ServiceRequestObsTranslator {

    @Autowired
    private PatientReferenceTranslator patientReferenceTranslator;

    @Autowired
    private EncounterReferenceTranslator<Encounter> encounterReferenceTranslator;

    @Override
    public ServiceRequest toFhirResource(@Nonnull Encounter encounter) {
        notNull(encounter, "The encounter object should not be null");

        ServiceRequest serviceRequest = new ServiceRequest();

        serviceRequest.setId(encounter.getUuid());

        serviceRequest.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);

        serviceRequest.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);

        serviceRequest.setSubject(patientReferenceTranslator.toFhirResource(encounter.getPatient()));

        return serviceRequest;
    }

    private Reference getProviderReference(Encounter encounter) {
        EncounterProvider encounterProvider = encounter.getActiveEncounterProviders().iterator().next();
        Reference reference = new Reference()
                .setReference(FhirConstants.PRACTITIONER + "/" + encounterProvider.getProvider().getUuid())
                .setType(FhirConstants.PRACTITIONER);
        reference.setDisplay(encounterProvider.getProvider().getName());
        reference.setIdentifier(ReferencesUtil.buildProviderIdentifier(encounter));
        return reference;
    }
}
