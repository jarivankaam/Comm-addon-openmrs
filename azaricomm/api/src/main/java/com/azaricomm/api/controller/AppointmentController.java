package com.azaricomm.api.controller;

import com.azaricomm.api.model.Appointment;
import com.azaricomm.api.repository.AppointmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentRepository repository;

    public AppointmentController(AppointmentRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Appointment> create(@RequestBody Appointment appointment) {
        appointment.setStatus("SCHEDULED");
        appointment.setCreatedAt(Instant.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(appointment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Appointment> get(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<Appointment> cancel(@PathVariable String id) {
        return repository.findById(id)
                .map(a -> {
                    a.setStatus("CANCELLED");
                    return ResponseEntity.ok(repository.save(a));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
