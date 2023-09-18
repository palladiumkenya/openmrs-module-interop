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
import org.openmrs.DrugOrder;
import org.openmrs.Order;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.Event;
import org.openmrs.module.fhir2.api.translators.MedicationTranslator;
import org.openmrs.module.interop.api.Subscribable;
import org.openmrs.module.interop.api.metadata.EventMetadata;
import org.openmrs.module.interop.utils.ObserverUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jms.Message;
import javax.validation.constraints.NotNull;
import java.util.List;

@Slf4j
@Component("interop.drugOrderObserver")
public class DrugOrderObserver extends BaseObserver implements Subscribable<Order> {
	
	@Autowired
	private MedicationTranslator medicationTranslator;
	
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
		        .ifPresent(metadata -> Daemon.runInDaemonThread(() -> prepareDrugMessage(metadata), getDaemonToken()));
	}
	
	private void prepareDrugMessage(@NotNull EventMetadata metadata) {
		Order drugOrder = Context.getOrderService().getOrderByUuid(metadata.getString("uuid"));
		if (drugOrder.getOrderType().getUuid().equals("131168f4-15f5-102d-96e4-000c29c2a5d7")) {
			DrugOrder order = (DrugOrder) drugOrder;
			Medication medication = medicationTranslator.toFhirResource(order.getDrug());
			if (medication != null) {
				this.publish(medication);
			} else {
				log.error("Couldn't find allergy with UUID {} ", metadata.getString("uuid"));
			}
		}
	}
}
