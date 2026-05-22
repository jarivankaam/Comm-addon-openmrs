package com.azaricomm.api.repository;

import com.azaricomm.api.model.Appointment;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.Instant;
import java.util.Optional;

public interface AppointmentRepository extends MongoRepository<Appointment, String> {
    Optional<Appointment> findByPatientIdAndScheduledTime(String patientId, Instant scheduledTime);
}
