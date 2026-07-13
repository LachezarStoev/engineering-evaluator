package com.pronet.evaluator;

import com.pronet.evaluator.config.AppProperties;
import com.pronet.evaluator.domain.*;
import com.pronet.evaluator.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
class AiSummaryService {
    private final EvaluationRepository evaluations;
    private final EmployeeRepository employees;
    private final AppProperties properties;

    record Summary(String text, boolean aiGenerated, String model, String disclaimer) {}

    Summary summarize(UUID id) {
        var ai = properties.ai();
        var e = evaluations.findById(id).orElseThrow();
        var employee = employees.findById(e.getEmployeeId()).orElseThrow();
        var counts =
                e.getResults().stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        CriterionResult::getResultStatus,
                                        () -> new EnumMap<>(ResultStatus.class),
                                        java.util.stream.Collectors.counting()));
        String facts =
                "Period "
                        + e.getPeriod()
                        + ", engineering track "
                        + employee.getTrackCode()
                        + ", level "
                        + e.getLevelCode()
                        + ", mode "
                        + e.getEvaluationMode()
                        + ". Status counts: "
                        + counts
                        + ". Results: "
                        + e.getResults().stream()
                                .map(
                                        r ->
                                                r.getFormula()
                                                        + " measured="
                                                        + r.getMeasuredValue()
                                                        + ", expected-for-period="
                                                        + r.getPeriodTargetValue()
                                                        + (r.getPeriodTargetMaxValue() == null
                                                                ? ""
                                                                : ".."
                                                                        + r
                                                                                .getPeriodTargetMaxValue())
                                                        + ", full-target="
                                                        + r.getThresholdValue()
                                                        + (r.getThresholdMaxValue() == null
                                                                ? ""
                                                                : ".." + r.getThresholdMaxValue())
                                                        + " => "
                                                        + EvaluationService.displayStatus(r))
                                .toList();
        if (ai.baseUrl().isBlank() || ai.apiKey().isBlank() || ai.model().isBlank())
            return new Summary(
                    facts, false, "none", "Deterministic fallback; no AI decision was made.");
        try {
            var client =
                    RestClient.builder()
                            .baseUrl(ai.baseUrl())
                            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ai.apiKey())
                            .build();
            Map<?, ?> response =
                    client.post()
                            .uri("/chat/completions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(
                                    Map.of(
                                            "model",
                                            ai.model(),
                                            "temperature",
                                            0,
                                            "messages",
                                            List.of(
                                                    Map.of(
                                                            "role",
                                                            "system",
                                                            "content",
                                                            "Summarize only the supplied engineering evidence. Do not assign a level or change any result."),
                                                    Map.of("role", "user", "content", facts))))
                            .retrieve()
                            .body(Map.class);
            var choices = (List<?>) response.get("choices");
            var msg = (Map<?, ?>) ((Map<?, ?>) choices.getFirst()).get("message");
            return new Summary(
                    Objects.toString(msg.get("content")),
                    true,
                    ai.model(),
                    "Advisory AI summary; formal results remain deterministic.");
        } catch (Exception ex) {
            return new Summary(
                    facts, false, ai.model(), "AI unavailable; deterministic fallback used.");
        }
    }
}
