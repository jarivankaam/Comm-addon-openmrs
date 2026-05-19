package org.openmrs.module.appointmentwebhook.advice;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointmentwebhook.api.WebhookService;
import org.springframework.aop.AfterReturningAdvice;

import java.lang.reflect.Method;

/**
 * Fires after Bahmni's {@code AppointmentsService.validateAndSave()} completes successfully.
 * <p>
 * No-arg constructor required — OpenMRS instantiates advice classes declared in {@code config.xml}
 * via reflection.
 * </p>
 */
public class AppointmentCreationAdvice implements AfterReturningAdvice {
	
	private static final Log log = LogFactory.getLog(AppointmentCreationAdvice.class);
	
	private static final WebhookService WEBHOOK_SERVICE = new WebhookService();
	
	public AppointmentCreationAdvice() {
	}
	
	public static WebhookService getWebhookService() {
		return WEBHOOK_SERVICE;
	}
	
	@Override
	public void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable {
		if (!"validateAndSave".equals(method.getName()) || !(returnValue instanceof Appointment)) {
			return;
		}
		
		Appointment appointment = (Appointment) returnValue;
		try {
			log.info("Dispatching FHIR webhook for Appointment/" + appointment.getUuid());
			WEBHOOK_SERVICE.sendAppointmentAsync(appointment);
		}
		catch (Exception e) {
			log.error("Webhook dispatch error for " + appointment.getUuid(), e);
		}
	}
}
