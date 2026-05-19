package com.azaricomm.repository;

import com.azaricomm.model.NotificationTask;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationTaskRepository extends MongoRepository<NotificationTask, String> {

    // Zoek alle taken die de status 'PENDING' hebben en waarvan de geplande tijd NU of in het verleden ligt
    List<NotificationTask> findByStatusAndScheduledTimeBefore(String status, Instant time);
}