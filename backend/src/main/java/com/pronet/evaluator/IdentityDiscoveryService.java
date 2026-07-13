package com.pronet.evaluator;

import com.pronet.evaluator.domain.*;
import com.pronet.evaluator.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class IdentityDiscoveryService {
    private final List<EngineeringConnector> connectors;
    private final EmployeeRepository employees;
    private final ExternalIdentityRepository identities;

    record Discovery(String tool, ConnectorHealth health, List<IdentityCandidate> candidates) {}

    List<Discovery> discover(String email) {
        employees.findByCanonicalEmailIgnoreCase(EvaluationService.normalize(email)).orElseThrow();
        return discoverFromConnectors(email);
    }

    @Transactional
    Employee findOrProvision(String email) {
        var normalizedEmail = EvaluationService.normalize(email);
        var existing = employees.findByCanonicalEmailIgnoreCase(normalizedEmail);
        if (existing.isPresent()) return existing.get();

        var discoveries = discoverFromConnectors(normalizedEmail);
        List<Map.Entry<String, IdentityCandidate>> exactMatches = new ArrayList<>();
        for (var discovery : discoveries) {
            var candidates =
                    discovery.candidates().stream().filter(IdentityCandidate::exactMatch).toList();
            if (candidates.size() == 1) {
                exactMatches.add(Map.entry(discovery.tool(), candidates.getFirst()));
            }
        }
        if (exactMatches.isEmpty()) {
            throw new NoSuchElementException(
                    "Employee not found in the configured integrations: " + normalizedEmail);
        }

        var employee = new Employee();
        employee.setCanonicalEmail(normalizedEmail);
        employee.setDisplayName(displayName(normalizedEmail, exactMatches));
        employee.setTeam("Engineering");
        employee = employees.save(employee);

        for (var match : exactMatches) {
            confirm(employee, match.getKey(), match.getValue());
        }
        return employee;
    }

    private List<Discovery> discoverFromConnectors(String email) {
        var discovered =
                new ArrayList<>(
                        connectors.stream()
                                .map(
                                        c -> {
                                            var h = c.testConnection();
                                            if (!h.healthy())
                                                return new Discovery(c.key(), h, List.of());
                                            try {
                                                return new Discovery(
                                                        c.key(), h, c.discoverUsers(email));
                                            } catch (Exception e) {
                                                return new Discovery(
                                                        c.key(),
                                                        new ConnectorHealth(false, e.getMessage()),
                                                        List.of());
                                            }
                                        })
                                .toList());
        var jiraIdentity =
                discovered.stream()
                        .filter(d -> d.tool().equals("jira"))
                        .flatMap(d -> d.candidates().stream())
                        .filter(IdentityCandidate::exactMatch)
                        .findFirst();
        if (jiraIdentity.isPresent()) {
            for (int index = 0; index < discovered.size(); index++) {
                var current = discovered.get(index);
                if (current.tool().equals("confluence")
                        && current.health().healthy()
                        && current.candidates().isEmpty()) {
                    var jira = jiraIdentity.get();
                    discovered.set(
                            index,
                            new Discovery(
                                    "confluence",
                                    current.health(),
                                    List.of(
                                            new IdentityCandidate(
                                                    jira.externalUserId(),
                                                    jira.username(),
                                                    email,
                                                    true))));
                }
            }
        }
        return discovered;
    }

    private String displayName(
            String email, List<Map.Entry<String, IdentityCandidate>> exactMatches) {
        return exactMatches.stream()
                .sorted(Comparator.comparing(match -> match.getKey().equals("jira") ? 0 : 1))
                .map(Map.Entry::getValue)
                .map(IdentityCandidate::username)
                .filter(value -> value != null && !value.isBlank())
                .map(this::humanizeIdentityName)
                .findFirst()
                .orElseGet(() -> humanizeEmail(email));
    }

    private String humanizeEmail(String email) {
        return humanizeIdentityName(email.substring(0, email.indexOf('@')));
    }

    private String humanizeIdentityName(String value) {
        if (!value.matches(".*[._-].*")) return value;
        return Arrays.stream(value.split("[._-]+"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((left, right) -> left + " " + right)
                .orElse(value);
    }

    @Transactional
    List<ExternalIdentity> autoConfirmExact(String email) {
        List<ExternalIdentity> confirmed = new ArrayList<>();
        for (var discovery : discover(email)) {
            var exact =
                    discovery.candidates().stream().filter(IdentityCandidate::exactMatch).toList();
            if (exact.size() == 1) {
                var candidate = exact.getFirst();
                confirmed.add(
                        confirm(
                                email,
                                discovery.tool(),
                                candidate.externalUserId(),
                                candidate.username(),
                                candidate.email()));
            }
        }
        return confirmed;
    }

    @Transactional
    ExternalIdentity confirm(
            String email, String tool, String externalId, String username, String matchedEmail) {
        var e =
                employees
                        .findByCanonicalEmailIgnoreCase(EvaluationService.normalize(email))
                        .orElseThrow();
        return confirm(e, tool, new IdentityCandidate(externalId, username, matchedEmail, true));
    }

    private ExternalIdentity confirm(Employee employee, String tool, IdentityCandidate candidate) {
        var i =
                identities
                        .findByEmployeeIdAndToolKey(employee.getId(), tool)
                        .orElseGet(ExternalIdentity::new);
        i.setEmployeeId(employee.getId());
        i.setToolKey(tool);
        i.setExternalUserId(candidate.externalUserId());
        i.setUsername(candidate.username());
        i.setMatchedEmail(EvaluationService.normalize(candidate.email()));
        i.setVerified(true);
        return identities.save(i);
    }
}
