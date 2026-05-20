package org.openmrs.module.appointmentwebhook;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.appointmentwebhook.advice.AppointmentCreationAdvice;
import org.openmrs.module.appointmentwebhook.api.WebhookService;

public class AppointmentWebhookActivator extends BaseModuleActivator {
	
	private static final Log log = LogFactory.getLog(AppointmentWebhookActivator.class);
	
	@Override
	public void started() {
		log.info("Appointment FHIR Webhook module started.");
		log.info("Configure endpoint via: " + WebhookService.GP_WEBHOOK_URL);
	}
	
	@Override
	public void stopped() {
		log.info("Appointment FHIR Webhook module stopping...");
		AppointmentCreationAdvice.getWebhookService().shutdown();
	}
}
