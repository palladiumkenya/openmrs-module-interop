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
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.DrugOrder;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.Event;
import org.openmrs.module.fhir2.api.translators.MedicationRequestTranslator;
import org.openmrs.module.fhir2.api.translators.MedicationTranslator;
import org.openmrs.module.fhir2.api.translators.ServiceRequestTranslator;
import org.openmrs.module.interop.api.Subscribable;
import org.openmrs.module.interop.api.metadata.EventMetadata;
import org.openmrs.module.interop.utils.ObserverUtils;
import org.openmrs.module.interop.utils.ReferencesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jms.Message;
import javax.validation.constraints.NotNull;
import java.util.List;

@Slf4j
@Component("interop.drugOrderObserver")
public class PatientOrdersObserver extends BaseObserver implements Subscribable<Order> {
	
	@Autowired
	private MedicationRequestTranslator medicationRequestTranslator;
	
	@Autowired
	private MedicationTranslator medicationTranslator;
	
	@Autowired
	private ServiceRequestTranslator<TestOrder> testOrderServiceRequestTranslator;
	
	@Override
	public Class<?> clazz() {
		return Order.class;
	}
	
	@Override
	public List<Event.Action> actions() {
		return ObserverUtils.defaultActions();
	}
	
	@Override
	public void onMessage(Message message) {
		processMessage(message)
		        .ifPresent(metadata -> Daemon.runInDaemonThread(() -> prepareOrderMessage(metadata), getDaemonToken()));
	}
	
	private void prepareOrderMessage(@NotNull EventMetadata metadata) {
		Order createdOrder = Context.getOrderService().getOrderByUuid(metadata.getString("uuid"));
		
		if (createdOrder.getOrderType().getUuid().equals("131168f4-15f5-102d-96e4-000c29c2a5d7")) {
			DrugOrder order = (DrugOrder) createdOrder;
			Medication medication = null;
			if (order.getDrug() != null) {
				medication = medicationTranslator.toFhirResource(order.getDrug());
			}
			MedicationRequest medicationRequest = medicationRequestTranslator.toFhirResource(order);
			if (medicationRequest != null) {
				if (medication != null) {
					this.publish(medication);
				}
				String reference = medicationRequest.getSubject().getReference();
				String arr[] = reference.split("/");
				if (arr.length == 2) {
					Patient patient = Context.getPatientService().getPatientByUuid(arr[1]);
					medicationRequest.setSubject(ReferencesUtil.buildPatientReference(patient));
				}
				this.publish(medicationRequest);
			} else {
				log.error("Couldn't find allergy with UUID {} ", metadata.getString("uuid"));
			}
		} else if (createdOrder.getOrderType().getUuid().equals("52a447d3-a64a-11e3-9aeb-50e549534c5e")) {
			TestOrder testOrder = (TestOrder) createdOrder;
			ServiceRequest translatedOrder = testOrderServiceRequestTranslator.toFhirResource(testOrder);
			if (translatedOrder != null) {
				
				String reference = translatedOrder.getSubject().getReference();
				String arr[] = reference.split("/");
				if (arr.length == 2) {
					Patient patient = Context.getPatientService().getPatientByUuid(arr[1]);
					translatedOrder.setSubject(ReferencesUtil.buildPatientReference(patient));
				}
				this.publish(translatedOrder);
			}
		}
	}
}
