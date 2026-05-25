package com.azaricomm.repository;

import com.azaricomm.model.Appointment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AppointmentRepository extends MongoRepository<Appointment, String> {

    // Query voor Stap 2: Zoek afspraken in het 24h-venster waarvan de 24h-reminder nog SCHEDULED is
    @Query(value = "{ 'status': 'SCHEDULED', 'scheduledTime': { $gt: ?0, $lte: ?1 }, 'notifications.reminder24h': 'SCHEDULED' }",
            fields = "{ 'id': 1, 'scheduledTime': 1, 'notifications': 1, 'traceContext': 1 }")
    List<Appointment> findTasksFor24hReminder(Instant lowerbound, Instant upperBound);

    // Query voor Stap 3: Zoek afspraken in het 1h-venster waarvan de 1h-reminder nog SCHEDULED is
    @Query(value = "{ 'status': 'SCHEDULED', 'scheduledTime': { $gt: ?0, $lte: ?1 }, 'notifications.reminder1h': 'SCHEDULED' }",
            fields = "{ 'id': 1, 'scheduledTime': 1, 'notifications': 1, 'traceContext': 1 }")
    List<Appointment> findTasksFor1hReminder(Instant now, Instant upperBound);
}