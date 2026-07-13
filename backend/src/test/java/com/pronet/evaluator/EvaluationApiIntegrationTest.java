package com.pronet.evaluator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "app.security.dev-mode=true",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri="
        })
@AutoConfigureMockMvc
class EvaluationApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void createsTransparentQuarterlyEvaluationAndKeepsMissingDataDistinctFromFailure()
            throws Exception {
        mvc.perform(
                        post("/api/v1/employees")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
      {"email":"Dev@Example.com","displayName":"Test Developer","team":"Core","currentLevelCode":"MID","targetLevelCode":"MID","employmentStart":"2026-01-01","aliases":["old@example.com"]}
      """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.canonicalEmail").value("dev@example.com"));
        mvc.perform(
                        post("/api/v1/criteria")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
      {"code":"TEST_VELOCITY","name":"Quarterly velocity","sourceTool":"jira","metricKey":"story_points","evaluationType":"AUTOMATIC","periodType":"QUARTER","operator":">=","threshold":250,"aggregation":"SUM","levelCode":"MID","version":1,"status":"PUBLISHED","effectiveFrom":"2026-01-01"}
      """))
                .andExpect(status().isCreated());
        mvc.perform(
                        post("/api/v1/criteria")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
      {"code":"TEST_QA_RATIO","name":"QA ratio","sourceTool":"jira","metricKey":"qa_defects","denominatorMetricKey":"qa_tested_completed_tasks","evaluationType":"AUTOMATIC","periodType":"QUARTER","operator":"<","threshold":15,"aggregation":"RATIO","levelCode":"MID","version":1,"status":"PUBLISHED","effectiveFrom":"2026-01-01"}
      """))
                .andExpect(status().isCreated());
        mvc.perform(
                        post("/api/v1/evidence")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
      {"email":"dev@example.com","toolKey":"jira","metricKey":"story_points","externalId":"ENG-1","occurredAt":"2026-07-10T10:00:00Z","value":260,"title":"Delivered feature","url":"https://jira/ENG-1"}
      """))
                .andExpect(status().isCreated());
        mvc.perform(
                        post("/api/v1/evidence")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
      {"email":"dev@example.com","toolKey":"jira","metricKey":"qa_tested_completed_tasks","externalId":"ENG-1-QA","occurredAt":"2026-07-10T10:00:00Z","value":1,"title":"QA tested feature","url":"https://jira/ENG-1"}
      """))
                .andExpect(status().isCreated());
        var calculated =
                mvc.perform(
                                post("/api/v1/evaluations/recalculate")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
      {"email":"DEV@example.com","period":"2026-Q3-sofia","from":"2026-07-01","to":"2026-09-30","timezone":"Europe/Sofia","levelCode":"MID","ruleVersion":1}
      """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.periodTimezone").value("Europe/Sofia"))
                        .andExpect(jsonPath("$.periodFrom").value("2026-06-30T21:00:00Z"))
                        .andExpect(jsonPath("$.results").isArray())
                        .andReturn();
        Map<?, ?> evaluation =
                json.readValue(calculated.getResponse().getContentAsByteArray(), Map.class);
        String evaluationId = evaluation.get("id").toString();
        var results = (java.util.List<?>) evaluation.get("results");
        Map<?, ?> passed =
                results.stream()
                        .map(x -> (Map<?, ?>) x)
                        .filter(x -> "PASS".equals(x.get("resultStatus")))
                        .findFirst()
                        .orElseThrow();
        Map<?, ?> qa =
                results.stream()
                        .map(x -> (Map<?, ?>) x)
                        .filter(x -> Objects.toString(x.get("formula")).contains("qa_defects"))
                        .filter(x -> x.get("measuredValue") != null)
                        .findFirst()
                        .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("PASS", qa.get("resultStatus"));
        org.junit.jupiter.api.Assertions.assertEquals(
                0,
                new java.math.BigDecimal(qa.get("measuredValue").toString())
                        .compareTo(java.math.BigDecimal.ZERO));
        String resultId = passed.get("id").toString();
        mvc.perform(
                        post("/api/v1/criterion-results/" + resultId + "/decision")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\":\"PASS\",\"note\":\"Evidence verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerNote").value("Evidence verified"));
        mvc.perform(
                        post("/api/v1/evaluations/" + evaluationId + "/disputes")
                                .header("X-User-Email", "dev@example.com")
                                .header("X-Roles", "DEVELOPER")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"criterionResultId\":\""
                                                + resultId
                                                + "\",\"message\":\"Please verify linked work\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(get("/api/v1/evaluations/" + evaluationId + "/export.xlsx"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .contentType(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mvc.perform(get("/api/v1/evaluations/" + evaluationId + "/export.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void developerCannotUseAdministrativeEndpoints() throws Exception {
        mvc.perform(
                        post("/api/v1/levels")
                                .header("X-User-Email", "person@example.com")
                                .header("X-Roles", "DEVELOPER")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\"X\",\"name\":\"X\",\"ordinal\":1,\"version\":1,\"status\":\"DRAFT\",\"effectiveFrom\":\"2026-01-01\"}"))
                .andExpect(status().isForbidden());
    }
}
