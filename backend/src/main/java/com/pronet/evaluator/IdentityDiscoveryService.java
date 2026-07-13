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
        var i =
                identities
                        .findByEmployeeIdAndToolKey(e.getId(), tool)
                        .orElseGet(ExternalIdentity::new);
        i.setEmployeeId(e.getId());
        i.setToolKey(tool);
        i.setExternalUserId(externalId);
        i.setUsername(username);
        i.setMatchedEmail(EvaluationService.normalize(matchedEmail));
        i.setVerified(true);
        return identities.save(i);
    }
}
