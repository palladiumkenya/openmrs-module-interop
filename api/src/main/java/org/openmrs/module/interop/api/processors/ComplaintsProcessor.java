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
        String complaintsString = Context.getAdministrationService().getGlobalPropertyValue(InteropConstant.COMPLAINTS_CONCEPT_UUIDS,
                "");

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

