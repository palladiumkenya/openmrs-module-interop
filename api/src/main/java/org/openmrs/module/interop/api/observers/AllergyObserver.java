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
import org.openmrs.Allergy;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.Event;
import org.openmrs.module.fhir2.api.FhirAllergyIntoleranceService;
import org.openmrs.module.interop.api.Subscribable;
import org.openmrs.module.interop.api.metadata.EventMetadata;
import org.openmrs.module.interop.utils.ObserverUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jms.Message;
import javax.validation.constraints.NotNull;
import java.util.List;

@Slf4j
@Component("interop.allergyCreationObserver")
public class AllergyObserver extends BaseObserver implements Subscribable<Allergy> {
	
	@Autowired
	private FhirAllergyIntoleranceService allergyIntoleranceService;
	
	@Override
	public Class<?> clazz() {
		return Allergy.class;
	}
	
	@Override
	public List<Event.Action> actions() {
		return ObserverUtils.defaultActions();
	}
	
	@Override
	public void onMessage(Message message) {
		processMessage(message)
		        .ifPresent(metadata -> Daemon.runInDaemonThread(() -> prepareAllergyMessage(metadata), getDaemonToken()));
	}
	
	private void prepareAllergyMessage(@NotNull EventMetadata metadata) {
		org.hl7.fhir.r4.model.AllergyIntolerance allergyIntolerance = allergyIntoleranceService
		        .get(metadata.getString("uuid"));
		if (allergyIntolerance != null) {
			this.publish(allergyIntolerance);
		} else {
			log.error("Couldn't find allergy with UUID {} ", metadata.getString("uuid"));
		}
	}
}
