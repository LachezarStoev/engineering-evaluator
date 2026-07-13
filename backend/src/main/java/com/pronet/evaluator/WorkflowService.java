package com.pronet.evaluator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pronet.evaluator.domain.*;
import com.pronet.evaluator.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class WorkflowService {
    private final EvaluationRepository evaluations;
    private final CriterionResultRepository results;
    private final DisputeRepository disputes;
    private final AuditEventRepository audit;
    private final ObjectMapper mapper;

    @Transactional
    Dispute dispute(UUID evaluationId, UUID resultId, String actor, String message) {
        evaluations.findById(evaluationId).orElseThrow();
        var d = new Dispute();
        d.setEvaluationId(evaluationId);
        d.setCriterionResultId(resultId);
        d.setAuthorEmail(EvaluationService.normalize(actor));
        d.setMessage(message);
        record(
                actor,
                "DISPUTE_CREATED",
                "evaluation",
                evaluationId.toString(),
                Map.of("resultId", Objects.toString(resultId, "")));
        return disputes.save(d);
    }

    @Transactional
    CriterionResult decide(UUID resultId, ResultStatus decision, String note, String actor) {
        if (decision != ResultStatus.PASS && decision != ResultStatus.FAIL)
            throw new IllegalArgumentException("Decision must be PASS or FAIL");
        var r = results.findById(resultId).orElseThrow();
        if (r.getEvaluation().isFinalized())
            throw new IllegalStateException("Finalized evaluations are immutable");
        r.setManagerDecision(decision);
        r.setManagerNote(note);
        r.setResultStatus(decision);
        record(
                actor,
                "MANAGER_DECISION",
                "criterion_result",
                resultId.toString(),
                Map.of("decision", decision));
        return results.save(r);
    }

    List<Dispute> disputes(UUID id) {
        return disputes.findByEvaluationIdOrderByCreatedAtDesc(id);
    }

    List<AuditEvent> audit() {
        return audit.findTop200ByOrderByCreatedAtDesc();
    }

    void record(String actor, String action, String type, String id, Object details) {
        var a = new AuditEvent();
        a.setActorEmail(actor);
        a.setAction(action);
        a.setEntityType(type);
        a.setEntityId(id);
        try {
            a.setDetailsJson(mapper.writeValueAsString(details));
        } catch (Exception ignored) {
        }
        audit.save(a);
    }
}
