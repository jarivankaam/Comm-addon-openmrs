package org.openmrs.module.azaricomm.web.controller;

import org.openmrs.module.azaricomm.messaging.NotificationMessage;
import org.openmrs.module.azaricomm.messaging.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/rest/v1/azaricomm")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @RequestMapping(value = "/notification", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendNotification(@RequestBody NotificationMessage message) {
        Map<String, Object> response = new HashMap<>();

        if (message.getPatientUuid() == null || message.getPatientPhone() == null) {
            response.put("success", false);
            response.put("error", "patientUuid and patientPhone are required");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        boolean sent = notificationService.sendNotification(message);

        response.put("success", sent);
        response.put("notificationType", message.getNotificationType());
        response.put("patientUuid", message.getPatientUuid());

        if (!sent) {
            response.put("error", "Failed to publish to RabbitMQ, check logs");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
