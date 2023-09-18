/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.interop.api.observers;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.Event;
import org.openmrs.module.fhir2.api.translators.EncounterReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.EncounterTranslator;
import org.openmrs.module.fhir2.api.translators.ObservationTranslator;
import org.openmrs.module.interop.api.Subscribable;
import org.openmrs.module.interop.api.metadata.EventMetadata;
import org.openmrs.module.interop.api.processors.AllergyIntoleranceProcessor;
import org.openmrs.module.interop.api.processors.AppointmentProcessor;
import org.openmrs.module.interop.api.processors.ComplaintsProcessor;
import org.openmrs.module.interop.api.processors.ConditionProcessor;
import org.openmrs.module.interop.api.processors.DiagnosisProcessor;
import org.openmrs.module.interop.api.processors.DiagnosticReportProcessor;
import org.openmrs.module.interop.api.processors.LabResultsProcessor;
import org.openmrs.module.interop.api.processors.VitalsProcessor;
import org.openmrs.module.interop.api.processors.translators.AppointmentRequestTranslator;
import org.openmrs.module.interop.utils.ObserverUtils;
import org.openmrs.module.interop.utils.ReferencesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.jms.Message;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.openmrs.module.interop.utils.ReferencesUtil.buildProviderIdentifier;

@Slf4j
@Component("interop.encounterCreationObserver")
public class EncounterObserver extends BaseObserver implements Subscribable<org.openmrs.Encounter> {
	
	@Autowired
	private EncounterTranslator<Encounter> encounterTranslator;
	
	@Autowired
	private EncounterReferenceTranslator<Encounter> encounterReferenceTranslator;
	
	@Autowired
	private ObservationTranslator observationTranslator;
	
	@Autowired
	private ConditionProcessor conditionProcessor;
	
	@Autowired
	private DiagnosisProcessor diagnosisProcessor;
	
	@Autowired
	@Qualifier("interop.appointmentProcessor")
	private AppointmentProcessor appointmentProcessor;
	
	@Autowired
	@Qualifier("interop.appointmentRequestTranslator")
	private AppointmentRequestTranslator appointmentRequestTranslator;
	
	@Autowired
	private DiagnosticReportProcessor diagnosticReportProcessor;
	
	@Autowired
	private AllergyIntoleranceProcessor allergyIntoleranceProcessor;
	
	@Autowired
	private VitalsProcessor vitalsProcessor;
	
	@Autowired
	private ComplaintsProcessor complaintsProcessor;
	
	@Autowired
	private LabResultsProcessor labResultsProcessor;
	
	@Override
	public Class<?> clazz() {
		return Encounter.class;
	}
	
	@Override
	public List<Event.Action> actions() {
		return ObserverUtils.defaultActions();
	}
	
	@Override
	public void onMessage(Message message) {
		processMessage(message).ifPresent(metadata -> {
		    //formatter:off
		    Daemon.runInDaemonThread(() -> prepareEncounterMessage(metadata), getDaemonToken());
			//formatter:on
		});
	}
	
	private void prepareEncounterMessage(@NotNull EventMetadata metadata) {
		//Create bundle
		Encounter encounter = Context.getEncounterService().getEncounterByUuid(metadata.getString("uuid"));
		Bundle preparedBundle = new Bundle();
		preparedBundle.setType(Bundle.BundleType.TRANSACTION);
		
		org.hl7.fhir.r4.model.Encounter fhirEncounter = encounterTranslator.toFhirResource(encounter);
		fhirEncounter.setSubject(ReferencesUtil.buildPatientReference(encounter.getPatient()));
		org.hl7.fhir.r4.model.Encounter.EncounterLocationComponent locationComponent = new org.hl7.fhir.r4.model.Encounter.EncounterLocationComponent();
		locationComponent.setLocation(ReferencesUtil.buildKhmflLocationReference(encounter.getLocation()));
		fhirEncounter.setLocation(Collections.singletonList(locationComponent));
		fhirEncounter.getParticipant().clear();
		fhirEncounter.setPartOf(new Reference());
		
		List<Resource> encounterContainedResources = ReferencesUtil.resolveProvenceReference(fhirEncounter.getContained(),
		    encounter);
		fhirEncounter.getContained().clear();
		//fhirEncounter.setContained(encounterContainedResources);
		
		Bundle.BundleEntryComponent encounterBundleEntryComponent = new Bundle.BundleEntryComponent();
		Bundle.BundleEntryRequestComponent bundleEntryRequestComponent = new Bundle.BundleEntryRequestComponent();
		bundleEntryRequestComponent.setMethod(Bundle.HTTPVerb.PUT);
		bundleEntryRequestComponent.setUrl("Encounter/" + fhirEncounter.getId());
		encounterBundleEntryComponent.setRequest(bundleEntryRequestComponent);
		encounterBundleEntryComponent.setResource(fhirEncounter);
		preparedBundle.addEntry(encounterBundleEntryComponent);
		
		/* Todo: Specify which observations to include */
		//Observations - Only enable this when you want to send all form obs as Fhir observations
		/**
		 * List<Obs> encounterObservations = new ArrayList<>(encounter.getObs()); for (Obs obs :
		 * encounterObservations) { Observation fhirObs = observationTranslator.toFhirResource(obs);
		 * fhirObs.setSubject(ReferencesUtil.buildPatientReference(encounter.getPatient())); // provence
		 * references List<Resource> resources =
		 * ReferencesUtil.resolveProvenceReference(fhirObs.getContained(), encounter);
		 * fhirObs.getContained().clear(); //fhirObs.setContained(resources); Bundle.BundleEntryComponent
		 * obsBundleEntry = new Bundle.BundleEntryComponent(); Bundle.BundleEntryRequestComponent
		 * requestComponent = new Bundle.BundleEntryRequestComponent();
		 * requestComponent.setMethod(Bundle.HTTPVerb.PUT); requestComponent.setUrl("Observation/" +
		 * fhirObs.getId()); obsBundleEntry.setRequest(requestComponent);
		 * obsBundleEntry.setResource(fhirObs); preparedBundle.addEntry(obsBundleEntry); }
		 **/
		
		//Vital obs
		List<Observation> vitalsObs = vitalsProcessor.process(encounter);
		for (Observation obs : vitalsObs) {
			Bundle.BundleEntryComponent obsBundleEntry = new Bundle.BundleEntryComponent();
			Bundle.BundleEntryRequestComponent requestComponent = new Bundle.BundleEntryRequestComponent();
			requestComponent.setMethod(Bundle.HTTPVerb.PUT);
			requestComponent.setUrl("Observation/" + obs.getId());
			obsBundleEntry.setRequest(requestComponent);
			obsBundleEntry.setResource(obs);
			preparedBundle.addEntry(obsBundleEntry);
		}
		
		//Complaints obs
		List<Observation> complaintsObs = complaintsProcessor.process(encounter);
		for (Observation obs : complaintsObs) {
			Bundle.BundleEntryComponent obsBundleEntry = new Bundle.BundleEntryComponent();
			Bundle.BundleEntryRequestComponent requestComponent = new Bundle.BundleEntryRequestComponent();
			requestComponent.setMethod(Bundle.HTTPVerb.PUT);
			requestComponent.setUrl("Observation/" + obs.getId());
			obsBundleEntry.setRequest(requestComponent);
			obsBundleEntry.setResource(obs);
			preparedBundle.addEntry(obsBundleEntry);
		}
		
		//Lab results obs
		List<Observation> labResultsObs = labResultsProcessor.process(encounter);
		for (Observation obs : labResultsObs) {
			Bundle.BundleEntryComponent obsBundleEntry = new Bundle.BundleEntryComponent();
			Bundle.BundleEntryRequestComponent requestComponent = new Bundle.BundleEntryRequestComponent();
			requestComponent.setMethod(Bundle.HTTPVerb.PUT);
			requestComponent.setUrl("Observation/" + obs.getId());
			obsBundleEntry.setRequest(requestComponent);
			obsBundleEntry.setResource(obs);
			preparedBundle.addEntry(obsBundleEntry);
		}
		
		this.processFhirResources(encounter, preparedBundle);
		this.publish(preparedBundle);
	}
	
	private Bundle.BundleEntryComponent buildConditionBundleEntry(Condition condition) {
		Bundle.BundleEntryRequestComponent bundleEntryRequestComponent = new Bundle.BundleEntryRequestComponent();
		bundleEntryRequestComponent.setMethod(Bundle.HTTPVerb.POST);
		bundleEntryRequestComponent.setUrl("Condition");
		Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
		bundleEntryComponent.setRequest(bundleEntryRequestComponent);
		bundleEntryComponent.setResource(condition);
		return bundleEntryComponent;
	}
	
	private Bundle.BundleEntryComponent createAppointmentBundleComponent(Appointment appointment) {
		Bundle.BundleEntryRequestComponent bundleEntryRequestComponent = new Bundle.BundleEntryRequestComponent();
		bundleEntryRequestComponent.setMethod(Bundle.HTTPVerb.POST);
		bundleEntryRequestComponent.setUrl("Appointment");
		Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
		bundleEntryComponent.setRequest(bundleEntryRequestComponent);
		bundleEntryComponent.setResource(appointment);
		return bundleEntryComponent;
	}
	
	private Bundle.BundleEntryComponent createAppointmentRequestBundleComponent(ServiceRequest serviceRequest) {
		Bundle.BundleEntryRequestComponent bundleEntryRequestComponent = new Bundle.BundleEntryRequestComponent();
		bundleEntryRequestComponent.setMethod(Bundle.HTTPVerb.PUT);
		bundleEntryRequestComponent.setUrl("ServiceRequest/" + serviceRequest.getId());
		Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
		bundleEntryComponent.setRequest(bundleEntryRequestComponent);
		bundleEntryComponent.setResource(serviceRequest);
		return bundleEntryComponent;
	}
	
	private Bundle.BundleEntryComponent createDiagnosticReportComponent(DiagnosticReport diagnosticReport) {
		Bundle.BundleEntryRequestComponent bundleEntryRequestComponent = new Bundle.BundleEntryRequestComponent();
		bundleEntryRequestComponent.setMethod(Bundle.HTTPVerb.POST);
		bundleEntryRequestComponent.setUrl("DiagnosticReport");
		Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
		bundleEntryComponent.setRequest(bundleEntryRequestComponent);
		bundleEntryComponent.setResource(diagnosticReport);
		return bundleEntryComponent;
	}
	
	private Bundle.BundleEntryComponent createAllergyComponent(AllergyIntolerance allergyIntolerance) {
		Bundle.BundleEntryRequestComponent bundleEntryRequestComponent = new Bundle.BundleEntryRequestComponent();
		bundleEntryRequestComponent.setMethod(Bundle.HTTPVerb.POST);
		bundleEntryRequestComponent.setUrl("AllergyIntolerance");
		Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
		bundleEntryComponent.setRequest(bundleEntryRequestComponent);
		bundleEntryComponent.setResource(allergyIntolerance);
		return bundleEntryComponent;
	}
	
	private void processFhirResources(@Nonnull Encounter encounter, @NotNull Bundle bundle) {
		
		List<Condition> conditions = conditionProcessor.process(encounter);
		conditions.forEach(condition -> {
			condition.setSubject(ReferencesUtil.buildPatientReference(encounter.getPatient()));
			condition.getRecorder().setIdentifier(buildProviderIdentifier(encounter));
			condition.setEncounter(encounterReferenceTranslator.toFhirResource(encounter));
			bundle.addEntry(buildConditionBundleEntry(condition));
		});
		
		List<Condition> diagnosis = diagnosisProcessor.process(encounter);
		diagnosis.forEach(d -> {
			d.setSubject(ReferencesUtil.buildPatientReference(encounter.getPatient()));
			d.getRecorder().setIdentifier(buildProviderIdentifier(encounter));
			d.setEncounter(encounterReferenceTranslator.toFhirResource(encounter));
			bundle.addEntry(buildConditionBundleEntry(d));
		});
		
		List<Appointment> appointments = appointmentProcessor.process(encounter);
		if (!appointments.isEmpty()) {
			ServiceRequest serviceRequest = appointmentRequestTranslator.toFhirResource(encounter);
			serviceRequest.setSubject(ReferencesUtil.buildPatientReference(encounter.getPatient()));
			Reference locationRef = ReferencesUtil.buildKhmflOrganizationReference(encounter.getLocation());
			serviceRequest.setRequester(locationRef);
			bundle.addEntry(createAppointmentRequestBundleComponent(serviceRequest));
			
			appointments.forEach(appointment -> {
				List<Resource> resources = ReferencesUtil.resolveProvenceReference(appointment.getContained(), encounter);
				appointment
				        .setBasedOn(Collections.singletonList(ReferencesUtil.buildServiceRequestReference(serviceRequest)));
				appointment.getContained().clear();
				appointment.setContained(resources);
				
				for (Appointment.AppointmentParticipantComponent participantComponent : appointment.getParticipant()) {
					participantComponent.setActor(ReferencesUtil.buildPatientReference(encounter.getPatient()));
				}
				bundle.addEntry(createAppointmentBundleComponent(appointment));
			});
		}
		
		List<DiagnosticReport> diagnosticReports = diagnosticReportProcessor.process(encounter);
		if (!diagnosticReports.isEmpty()) {
			diagnosticReports.get(0).setEncounter(encounterReferenceTranslator.toFhirResource(encounter));
			diagnosticReports.get(0).setSubject(ReferencesUtil.buildPatientReference(encounter.getPatient()));
			bundle.addEntry(createDiagnosticReportComponent(diagnosticReports.get(0)));
		}
		
		List<AllergyIntolerance> allergyIntolerancesList = allergyIntoleranceProcessor.process(encounter);
		allergyIntolerancesList.forEach(allergy -> {
			allergy.setPatient(ReferencesUtil.buildPatientReference(encounter.getPatient()));
			allergy.setEncounter(encounterReferenceTranslator.toFhirResource(encounter));
			bundle.addEntry(createAllergyComponent(allergy));
		});
		
	}
}
