package com.pronet.evaluator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pronet.evaluator.domain.Employee;
import com.pronet.evaluator.domain.ExternalIdentity;
import com.pronet.evaluator.repository.EmployeeRepository;
import com.pronet.evaluator.repository.ExternalIdentityRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class IdentityDiscoveryServiceTest {

    @Test
    void provisionsUnknownEmployeeFromOneExactIdentityPerHealthyConnector() {
        var employees = mock(EmployeeRepository.class);
        var identities = mock(ExternalIdentityRepository.class);
        when(employees.findByCanonicalEmailIgnoreCase("angel.angelov@pronetgaming.com"))
                .thenReturn(Optional.empty());
        when(employees.save(any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(identities.findByEmployeeIdAndToolKey(any(), anyString()))
                .thenReturn(Optional.empty());
        when(identities.save(any(ExternalIdentity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var service =
                new IdentityDiscoveryService(
                        List.of(
                                connector(
                                        "gitlab",
                                        new IdentityCandidate(
                                                "42",
                                                "angel.angelov",
                                                "angel.angelov@pronetgaming.com",
                                                true)),
                                connector(
                                        "jira",
                                        new IdentityCandidate(
                                                "jira-42",
                                                "Angel Angelov",
                                                "angel.angelov@pronetgaming.com",
                                                true))),
                        employees,
                        identities);

        var employee = service.findOrProvision(" Angel.Angelov@PronetGaming.com ");

        assertEquals("angel.angelov@pronetgaming.com", employee.getCanonicalEmail());
        assertEquals("Angel Angelov", employee.getDisplayName());
        assertEquals("Engineering", employee.getTeam());
        verify(identities, times(2)).save(any(ExternalIdentity.class));
    }

    @Test
    void refusesProvisioningWithoutAnExactSourceIdentity() {
        var employees = mock(EmployeeRepository.class);
        when(employees.findByCanonicalEmailIgnoreCase("unknown@pronetgaming.com"))
                .thenReturn(Optional.empty());
        var service =
                new IdentityDiscoveryService(
                        List.of(
                                connector(
                                        "gitlab",
                                        new IdentityCandidate("42", "similar.user", "", false))),
                        employees,
                        mock(ExternalIdentityRepository.class));

        var error =
                assertThrows(
                        NoSuchElementException.class,
                        () -> service.findOrProvision("unknown@pronetgaming.com"));

        assertTrue(error.getMessage().contains("configured integrations"));
        verify(employees, never()).save(any());
    }

    private EngineeringConnector connector(String key, IdentityCandidate candidate) {
        return new EngineeringConnector() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public ConnectorHealth testConnection() {
                return new ConnectorHealth(true, "healthy");
            }

            @Override
            public List<IdentityCandidate> discoverUsers(String email) {
                return List.of(candidate);
            }

            @Override
            public List<EvidenceInput> syncEvidence(
                    String externalUserId, Instant from, Instant to) {
                return List.of();
            }
        };
    }
}
