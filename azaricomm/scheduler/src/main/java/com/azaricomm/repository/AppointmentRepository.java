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
    @Query("{ 'status': 'SCHEDULED', 'scheduledTime': { $gte: ?0, $lte: ?1 }, 'notifications.reminder24h': 'SCHEDULED' }")
    List<Appointment> findTasksFor24hReminder(Instant windowStart, Instant windowEnd);

    // Query voor Stap 3: Zoek afspraken in het 1h-venster waarvan de 1h-reminder nog SCHEDULED is
    @Query("{ 'status': 'SCHEDULED', 'scheduledTime': { $gte: ?0, $lte: ?1 }, 'notifications.reminder1h': 'SCHEDULED' }")
    List<Appointment> findTasksFor1hReminder(Instant windowStart, Instant windowEnd);
}