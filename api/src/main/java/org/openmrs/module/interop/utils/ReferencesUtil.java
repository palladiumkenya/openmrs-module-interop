/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.interop.utils;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.Provider;
import org.openmrs.ProviderAttribute;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.interop.InteropConstant;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ReferencesUtil {
	
	public static void buildLocationReference(@NotNull Location location, @NotNull Reference reference) {
		if (ObserverUtils.getMFLCODELocationAttributeType() != null) {
			String mflCodeUuid = ObserverUtils.getMFLCODELocationAttributeType().getUuid();
			List<LocationAttribute> mflCodeAttribute = location.getActiveAttributes().stream()
			        .filter(loc -> loc.getAttributeType().getUuid().equals(mflCodeUuid)).collect(Collectors.toList());
			if (!mflCodeAttribute.isEmpty()) {
				reference.getIdentifier().setSystem(ObserverUtils.getSystemUrlConfiguration())
				        .setValue(mflCodeAttribute.get(0).getValue().toString()).setUse(Identifier.IdentifierUse.OFFICIAL);
			}
		}
	}
	
	public static Reference buildKhmflLocationReference(@NotNull Location location) {
		Reference locationRef = new Reference(ObserverUtils.getKhmflSystemUrlConfiguration()).setType("Organization");
		if (ObserverUtils.getMFLCODELocationAttributeType() != null) {
			String mflCodeUuid = ObserverUtils.getMFLCODELocationAttributeType().getUuid();
			List<LocationAttribute> mflCodeAttribute = location.getActiveAttributes().stream()
			        .filter(loc -> loc.getAttributeType().getUuid().equals(mflCodeUuid)).collect(Collectors.toList());
			if (!mflCodeAttribute.isEmpty()) {
				locationRef.setIdentifier(new Identifier().setSystem(ObserverUtils.getKhmflSystemUrlConfiguration())
				        .setValue(mflCodeAttribute.get(0).getValue().toString()).setUse(Identifier.IdentifierUse.OFFICIAL));
				locationRef.setDisplay(mflCodeAttribute.get(0).getValue().toString());
			}
		}
		return locationRef;
	}
	
	public static Reference buildKhmflOrganizationReference(@NotNull Location location) {
		Reference locationRef = new Reference(ObserverUtils.getKhmflSystemUrlConfiguration()).setType("Organization");
		if (ObserverUtils.getMFLCODELocationAttributeType() != null) {
			String mflCodeUuid = ObserverUtils.getMFLCODELocationAttributeType().getUuid();
			List<LocationAttribute> mflCodeAttribute = location.getActiveAttributes().stream()
			        .filter(loc -> loc.getAttributeType().getUuid().equals(mflCodeUuid)).collect(Collectors.toList());
			if (!mflCodeAttribute.isEmpty()) {
				locationRef.setIdentifier(new Identifier().setSystem(ObserverUtils.getKhmflSystemUrlConfiguration())
				        .setValue(mflCodeAttribute.get(0).getValue().toString()).setUse(Identifier.IdentifierUse.OFFICIAL));
				
			}
		}
		return locationRef;
	}
	
	public static List<Resource> resolveProvenceReference(List<Resource> resources, @NotNull Encounter encounter) {
		List<Resource> result = resources.stream().filter(resource -> resource.fhirType().equals("Provenance"))
		        .collect(Collectors.toList());
		List<Resource> provenceReferences = new ArrayList<>();
		for (Resource resource : result) {
			Provenance provenance = (Provenance) resource;
			provenance.getAgentFirstRep().getWho().setIdentifier(buildProviderIdentifier(encounter));
			provenceReferences.add(provenance);
		}
		return provenceReferences;
	}
	
	public static Identifier buildProviderIdentifier(@NotNull Encounter encounter) {
		Identifier identifier = new Identifier();
		identifier.setSystem(ObserverUtils.getSystemUrlConfiguration());
		identifier.setUse(Identifier.IdentifierUse.OFFICIAL);
		if (!encounter.getEncounterProviders().isEmpty()) {
			Provider provider = encounter.getEncounterProviders().iterator().next().getProvider();
			String providerNationalId = providerUniversalIdentifier(provider);
			if (!providerNationalId.isEmpty()) {
				identifier.setValue(providerNationalId);
				return identifier;
			}
			
			identifier.setValue(provider.getName());
		}
		return identifier;
	}
	
	private static String providerUniversalIdentifier(Provider provider) {
		if (ObserverUtils.getProviderAttributeType() != null) {
			List<ProviderAttribute> attributes = provider.getActiveAttributes().stream().filter(attribute -> attribute
			        .getAttributeType().getUuid().equals(ObserverUtils.getProviderAttributeType().getUuid()))
			        .collect(Collectors.toList());
			return attributes.isEmpty() ? "" : attributes.get(0).getValue().toString();
		}
		return "";
	}
	
	public static Identifier buildProviderIdentifierByUser(User user) {
		Collection<Provider> provider = Context.getProviderService().getProvidersByPerson(user.getPerson());
		Identifier identifier = new Identifier();
		identifier.setSystem(ObserverUtils.getSystemUrlConfiguration());
		identifier.setUse(Identifier.IdentifierUse.OFFICIAL);
		if (!provider.isEmpty()) {
			Provider userProvider = provider.iterator().next();
			String providerNationalId = providerUniversalIdentifier(userProvider);
			if (!providerNationalId.isEmpty()) {
				identifier.setValue(providerNationalId);
				return identifier;
			}
			identifier.setValue(userProvider.getName());
			return identifier;
		}
		identifier.setValue(user.getGivenName() + " " + user.getGivenName());
		return identifier;
	}
	
	public static Reference buildPatientReference(@NotNull Patient patient) {
		// Reference reference = new Reference("Patient/" + getPatientNUPI(patient)).setType("Patient");
		Reference reference = new Reference("Patient/" + getPatientCRID(patient)).setType("Patient");
		Identifier identifier = new Identifier();
		identifier.setSystem(ObserverUtils.getCRSystemUrlConfiguration());
		identifier.setUse(Identifier.IdentifierUse.OFFICIAL);
		// identifier.setValue(getPatientNUPI(patient));
		identifier.setValue(getPatientCRID(patient));
		reference.setIdentifier(identifier);
		return reference;
	}
	
	public static Reference buildServiceRequestReference(@NotNull ServiceRequest request) {
		Reference reference = new Reference(ObserverUtils.getCRSystemUrlConfiguration()).setType("ServiceRequest");
		Identifier identifier = new Identifier();
		identifier.setSystem(ObserverUtils.getCRSystemUrlConfiguration());
		identifier.setUse(Identifier.IdentifierUse.OFFICIAL);
		identifier.setValue(request.getId());
		reference.setIdentifier(identifier);
		return reference;
	}
	
	private static String getPatientNUPI(Patient patient) {
		if (ObserverUtils.getNUPIIdentifierType() != null) {
			List<PatientIdentifier> nUpi = patient.getActiveIdentifiers().stream()
			        .filter(id -> id.getIdentifierType().getUuid().equals(ObserverUtils.getNUPIIdentifierType().getUuid()))
			        .collect(Collectors.toList());
			return nUpi.isEmpty() ? "" : nUpi.get(0).getIdentifier();
		}
		return "";
	}
	
	private static String getPatientCRID(Patient patient) {
		if (ObserverUtils.getCRIDIdentifierType() != null) {
			List<PatientIdentifier> crId = patient.getActiveIdentifiers().stream()
			        .filter(id -> id.getIdentifierType().getUuid().equals(ObserverUtils.getCRIDIdentifierType().getUuid()))
			        .collect(Collectors.toList());
			return crId.isEmpty() ? "" : crId.get(0).getIdentifier();
		}
		return "";
	}
}
