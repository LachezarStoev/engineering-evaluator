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
    private final AppProperties properties;

    record Summary(String text, boolean aiGenerated, String model, String disclaimer) {}

    Summary summarize(UUID id) {
        var ai = properties.ai();
        var e = evaluations.findById(id).orElseThrow();
        long
                pass =
                        e.getResults().stream()
                                .filter(r -> r.getResultStatus() == ResultStatus.PASS)
                                .count(),
                review =
                        e.getResults().stream()
                                .filter(r -> r.getResultStatus() == ResultStatus.NEEDS_REVIEW)
                                .count();
        String facts =
                "Period "
                        + e.getPeriod()
                        + ", level "
                        + e.getLevelCode()
                        + ", "
                        + pass
                        + " passed, "
                        + review
                        + " need human review. Results: "
                        + e.getResults().stream()
                                .map(r -> r.getFormula() + " => " + r.getResultStatus())
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
