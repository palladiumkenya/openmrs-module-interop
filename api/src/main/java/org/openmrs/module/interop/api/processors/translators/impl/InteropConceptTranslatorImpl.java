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

import groovy.util.logging.Slf4j;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.openmrs.Concept;
import org.openmrs.module.fhir2.api.translators.impl.ConceptTranslatorImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Iterator;

@Slf4j
@Primary
@Component
public class InteropConceptTranslatorImpl extends ConceptTranslatorImpl {
	
	@Override
	public CodeableConcept toFhirResource(@Nonnull Concept concept) {
		if (concept == null) {
			return null;
		} else {
			CodeableConcept codeableConcept = new CodeableConcept();
			codeableConcept.setText(concept.getDisplayString());
			this.addConceptCoding(codeableConcept.addCoding(), "https://openconceptlab.org/orgs/CIEL/sources/CIEL",
			    concept.getUuid(), concept);
			return codeableConcept;
		}
	}
	
	private void addConceptCoding(Coding coding, String system, String code, Concept concept) {
		coding.setSystem(system);
		coding.setCode(code.replace("A", ""));
		coding.setDisplay(concept.getDisplayString());
	}
}
