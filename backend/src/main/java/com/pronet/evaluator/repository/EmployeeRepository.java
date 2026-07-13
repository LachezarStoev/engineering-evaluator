package com.pronet.evaluator.repository;

import com.pronet.evaluator.domain.Employee;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByCanonicalEmailIgnoreCase(String email);
}
