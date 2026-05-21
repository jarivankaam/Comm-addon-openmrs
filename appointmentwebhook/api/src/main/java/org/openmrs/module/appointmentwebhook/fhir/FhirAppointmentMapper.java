package org.openmrs.module.appointmentwebhook.fhir;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentKind;
import org.openmrs.module.appointments.model.AppointmentProvider;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentServiceType;
import org.openmrs.module.appointments.model.AppointmentStatus;

import java.util.Set;

/**
 * Builds an HL7 FHIR R4 Appointment resource as JSON from a Bahmni {@link Appointment} object. Zero
 * external dependencies. Reference: https://www.hl7.org/fhir/R4/appointment.html
 */
public class FhirAppointmentMapper {
	
	private static final String OPENMRS_SYSTEM = "http://openmrs.org/fhir";
	
	private static final String PARTICIPATION_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ParticipationType";
	
	/**
	 * Converts a Bahmni Appointment to FHIR R4 JSON.
	 * 
	 * @param src the Bahmni appointment
	 * @param serverBase optional FHIR server base URL for absolute references
	 * @param messageProvider optional custom text value included as an extension
	 */
	public static String toFhirJson(Appointment src, String serverBase, String messageProvider) {
		JsonWriter j = new JsonWriter();
		
		j.objectStart();
		j.key("resourceType").value("Appointment");
		j.key("id").value(src.getUuid());
		writeMeta(j, src);
		j.key("status").value(mapStatus(src.getStatus()));
		writeServiceType(j, src.getService());
		writeServiceCategory(j, src.getServiceType());
		writeAppointmentKind(j, src.getAppointmentKind());
		j.key("priority").value(0);
		if (src.getComments() != null && !src.getComments().isEmpty()) {
			j.key("comment").value(src.getComments());
		}
		writeTimings(j, src);
		writeParticipants(j, src, serverBase);
		writeExtensions(j, src, serverBase, messageProvider);
		j.objectEnd();
		
		return j.toString();
	}
	
	private static void writeMeta(JsonWriter j, Appointment src) {
		j.key("meta").objectStart();
		j.key("versionId").value("1");
		if (src.getDateChanged() != null) {
			j.key("lastUpdated").dateValue(src.getDateChanged());
		} else if (src.getDateCreated() != null) {
			j.key("lastUpdated").dateValue(src.getDateCreated());
		}
		j.key("profile").arrayStart();
		j.value("http://hl7.org/fhir/StructureDefinition/Appointment");
		j.arrayEnd();
		j.objectEnd();
	}
	
	private static void writeServiceType(JsonWriter j, AppointmentServiceDefinition service) {
		if (service == null) {
			return;
		}
		j.key("serviceType").arrayStart();
		j.objectStart();
		j.key("coding").arrayStart();
		j.objectStart();
		j.key("system").value(OPENMRS_SYSTEM + "/appointment-service");
		j.key("code").value(service.getUuid());
		j.key("display").value(service.getName());
		j.objectEnd();
		j.arrayEnd();
		if (service.getDescription() != null) {
			j.key("text").value(service.getDescription());
		}
		j.objectEnd();
		j.arrayEnd();
	}
	
	private static void writeServiceCategory(JsonWriter j, AppointmentServiceType serviceType) {
		if (serviceType == null) {
			return;
		}
		j.key("serviceCategory").arrayStart();
		j.objectStart();
		j.key("coding").arrayStart();
		j.objectStart();
		j.key("system").value(OPENMRS_SYSTEM + "/appointment-service-type");
		j.key("code").value(serviceType.getUuid());
		j.key("display").value(serviceType.getName());
		j.objectEnd();
		j.arrayEnd();
		j.objectEnd();
		j.arrayEnd();
	}
	
	private static void writeAppointmentKind(JsonWriter j, AppointmentKind kind) {
		if (kind == null) {
			return;
		}
		j.key("appointmentType").objectStart();
		j.key("coding").arrayStart();
		j.objectStart();
		j.key("system").value(OPENMRS_SYSTEM + "/appointment-kind");
		j.key("code").value(kind.name());
		j.key("display").value(kind.name());
		j.objectEnd();
		j.arrayEnd();
		j.objectEnd();
	}
	
	private static void writeTimings(JsonWriter j, Appointment src) {
		if (src.getStartDateTime() != null) {
			j.key("start").dateValue(src.getStartDateTime());
		}
		if (src.getEndDateTime() != null) {
			j.key("end").dateValue(src.getEndDateTime());
		}
		if (src.getStartDateTime() != null && src.getEndDateTime() != null) {
			long mins = (src.getEndDateTime().getTime() - src.getStartDateTime().getTime()) / 60_000;
			j.key("minutesDuration").value((int) mins);
		}
		if (src.getDateCreated() != null) {
			j.key("created").dateValue(src.getDateCreated());
		}
	}
	
	private static void writeParticipants(JsonWriter j, Appointment src, String serverBase) {
		j.key("participant").arrayStart();
		writePatientParticipant(j, src.getPatient(), serverBase);
		writeProviderParticipants(j, src.getProviders(), serverBase);
		writeLocationParticipant(j, src.getLocation(), serverBase);
		writeOrganizationParticipant(j, src.getLocation(), serverBase);
		j.arrayEnd();
	}
	
	private static void writePatientParticipant(JsonWriter j, Patient patient, String serverBase) {
		if (patient == null) {
			return;
		}
		j.objectStart();
		writeParticipantType(j, "SBJ", "subject");
		j.key("actor").objectStart();
		j.key("reference").value(ref(serverBase, "Patient", patient.getUuid()));
		j.key("type").value("Patient");
		PersonName name = patient.getPersonName();
		if (name != null) {
			j.key("display").value(name.getFullName());
		}
		PatientIdentifier pid = patient.getPatientIdentifier();
		if (pid != null) {
			j.key("identifier").objectStart();
			j.key("system").value(
			    OPENMRS_SYSTEM + "/patient-identifier/"
			            + (pid.getIdentifierType() != null ? pid.getIdentifierType().getUuid() : "default"));
			j.key("value").value(pid.getIdentifier());
			j.objectEnd();
		}
		j.objectEnd();
		j.key("required").value("required");
		j.key("status").value("accepted");
		j.objectEnd();
	}
	
	private static void writeProviderParticipants(JsonWriter j, Set<AppointmentProvider> providers, String serverBase) {
		if (providers == null) {
			return;
		}
		for (AppointmentProvider ap : providers) {
			Provider provider = ap.getProvider();
			if (provider == null) {
				continue;
			}
			j.objectStart();
			writeParticipantType(j, "ATND", "attender");
			j.key("actor").objectStart();
			j.key("reference").value(ref(serverBase, "Practitioner", provider.getUuid()));
			j.key("type").value("Practitioner");
			if (provider.getName() != null) {
				j.key("display").value(provider.getName());
			}
			if (provider.getIdentifier() != null) {
				j.key("identifier").objectStart();
				j.key("system").value(OPENMRS_SYSTEM + "/provider-identifier");
				j.key("value").value(provider.getIdentifier());
				j.objectEnd();
			}
			j.objectEnd();
			j.key("required").value("required");
			j.key("status").value(mapProviderResponse(ap));
			j.objectEnd();
		}
	}
	
	private static void writeLocationParticipant(JsonWriter j, Location location, String serverBase) {
		if (location == null) {
			return;
		}
		j.objectStart();
		writeParticipantType(j, "LOC", "location");
		j.key("actor").objectStart();
		j.key("reference").value(ref(serverBase, "Location", location.getUuid()));
		j.key("type").value("Location");
		j.key("display").value(location.getName());
		j.objectEnd();
		j.key("required").value("required");
		j.key("status").value("accepted");
		j.objectEnd();
	}
	
	private static void writeOrganizationParticipant(JsonWriter j, Location location, String serverBase) {
		if (location == null) {
			return;
		}
		Location org = location;
		while (org.getParentLocation() != null) {
			org = org.getParentLocation();
		}
		j.objectStart();
		writeParticipantType(j, "PART", "participant");
		j.key("actor").objectStart();
		j.key("reference").value(ref(serverBase, "Organization", org.getUuid()));
		j.key("type").value("Organization");
		j.key("display").value(org.getName());
		j.objectEnd();
		j.key("required").value("required");
		j.key("status").value("accepted");
		j.objectEnd();
	}
	
	private static void writeExtensions(JsonWriter j, Appointment src, String serverBase, String messageProvider) {
		boolean hasCreator = src.getCreator() != null;
		boolean hasMessageProvider = messageProvider != null && !messageProvider.trim().isEmpty();
		boolean hasTimezone = src.getStartDateTime() != null;

		if (!hasCreator && !hasMessageProvider && !hasTimezone) {
			return;
		}

		j.key("extension").arrayStart();
		if (hasCreator) {
			j.objectStart();
			j.key("url").value(OPENMRS_SYSTEM + "/extension/creator");
			j.key("valueReference").objectStart();
			j.key("reference").value(ref(serverBase, "Practitioner", src.getCreator().getUuid()));
			if (src.getCreator().getPersonName() != null) {
				j.key("display").value(src.getCreator().getPersonName().getFullName());
			} else {
				j.key("display").value(src.getCreator().getUsername());
			}
			j.objectEnd();
			j.objectEnd();
		}
		if (hasMessageProvider) {
			j.objectStart();
			j.key("url").value(OPENMRS_SYSTEM + "/extension/messageProvider");
			j.key("valueString").value(messageProvider.trim());
			j.objectEnd();
		}
		if (hasTimezone) {
			java.util.TimeZone tz = java.util.TimeZone.getDefault();
			int offsetMs = tz.getOffset(src.getStartDateTime().getTime());
			int offsetHours = offsetMs / 3_600_000;
			int offsetMins = Math.abs((offsetMs % 3_600_000) / 60_000);
			String offsetId = String.format("%+03d:%02d", offsetHours, offsetMins);
			j.objectStart();
			j.key("url").value(OPENMRS_SYSTEM + "/extension/timezone");
			j.key("valueString").value(offsetId);
			j.objectEnd();
		}
		j.arrayEnd();
	}
	
	// ── Helpers ───────────────────────────────────────────────────────
	
	private static void writeParticipantType(JsonWriter j, String code, String display) {
		j.key("type").arrayStart();
		j.objectStart();
		j.key("coding").arrayStart();
		j.objectStart();
		j.key("system").value(PARTICIPATION_SYSTEM);
		j.key("code").value(code);
		j.key("display").value(display);
		j.objectEnd();
		j.arrayEnd();
		j.objectEnd();
		j.arrayEnd();
	}
	
	private static String ref(String serverBase, String resourceType, String uuid) {
		if (serverBase != null && !serverBase.isEmpty()) {
			String base = serverBase.endsWith("/") ? serverBase : serverBase + "/";
			return base + resourceType + "/" + uuid;
		}
		return resourceType + "/" + uuid;
	}
	
	/**
	 * Maps Bahmni AppointmentStatus → FHIR R4 appointment-status. Uses name() string comparison to
	 * avoid compile errors when enum values differ across Bahmni versions. See:
	 * https://www.hl7.org/fhir/R4/valueset-appointmentstatus.html
	 */
	private static String mapStatus(AppointmentStatus status) {
		if (status == null)
			return "proposed";
		String name = status.name();
		if ("Scheduled".equals(name) || "Rescheduled".equals(name)) {
			return "booked";
		} else if ("Requested".equals(name) || "WaitList".equals(name)) {
			return "waitlist";
		} else if ("CheckedIn".equals(name)) {
			return "arrived";
		} else if ("Completed".equals(name)) {
			return "fulfilled";
		} else if ("Cancelled".equals(name)) {
			return "cancelled";
		} else if ("Missed".equals(name)) {
			return "noshow";
		}
		return "proposed";
	}
	
	/**
	 * Maps Bahmni provider response to FHIR participation status. Uses name() string comparison for
	 * version safety.
	 */
	private static String mapProviderResponse(AppointmentProvider ap) {
		if (ap.getResponse() == null)
			return "needs-action";
		String name = ap.getResponse().name();
		if ("ACCEPTED".equals(name)) {
			return "accepted";
		} else if ("TENTATIVE".equals(name)) {
			return "tentative";
		} else if ("REJECTED".equals(name) || "CANCELLED".equals(name)) {
			return "declined";
		}
		return "needs-action";
	}
}
