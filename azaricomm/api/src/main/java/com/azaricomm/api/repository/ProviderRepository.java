package com.azaricomm.api.repository;

import com.azaricomm.api.model.Provider;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderRepository extends MongoRepository<Provider, String> {
    // Check if the ID does exist and if is_active is true.
    boolean existsByIdAndIsActiveTrue(String id);
}