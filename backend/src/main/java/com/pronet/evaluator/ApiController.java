package com.pronet.evaluator;

import com.pronet.evaluator.config.AppProperties;
import com.pronet.evaluator.config.ConnectorsProperties;
import com.pronet.evaluator.domain.*;
import com.pronet.evaluator.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class ApiController {
    private final AppProperties appProperties;
    private final ConnectorsProperties connectorProperties;
    private final EmployeeRepository employees;
    private final LevelRepository levels;
    private final CriterionRepository criteria;
    private final EvidenceRepository evidence;
    private final EvaluationRepository evaluations;
    private final ConnectorConfigRepository connectors;
    private final EvaluationService service;
    private final IdentityDiscoveryService identityDiscovery;
    private final EvidenceSynchronizationService synchronization;
    private final WorkflowService workflows;
    private final ExportService exports;
    private final AiSummaryService ai;
    private final DataReadinessService readiness;
    private final LevelFitService levelFit;
    private final EngineeringTrackRepository tracks;
    private final CompetencyExpectationRepository competencies;

    @GetMapping("/employees/by-email/{email}")
    @PreAuthorize("@access.canViewEmployee(#email, authentication)")
    Employee employee(@PathVariable String email) {
        return lookup(email);
    }

    @PostMapping("/employees/resolve/{email}")
    @PreAuthorize("@access.canViewEmployee(#email, authentication)")
    Employee resolveEmployee(@PathVariable String email) {
        return identityDiscovery.findOrProvision(email);
    }

    private Employee lookup(String email) {
        return employees
                .findByCanonicalEmailIgnoreCase(EvaluationService.normalize(email))
                .orElseThrow(() -> new NoSuchElementException("Employee not found: " + email));
    }

    @GetMapping("/employees")
    @PreAuthorize(
            "hasAnyRole('TEAM_LEAD','ENGINEERING_MANAGER','HR','ORGANIZATION_ADMIN','AUDITOR')")
    List<Employee> employees() {
        return employees.findAll();
    }

    @PostMapping("/employees")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    Employee createEmployee(@Valid @RequestBody EmployeeRequest r) {
        var e = new Employee();
        e.setCanonicalEmail(EvaluationService.normalize(r.email()));
        e.setDisplayName(r.displayName());
        e.setTeam(r.team());
        e.setManagerEmail(EvaluationService.normalize(r.managerEmail()));
        e.setCurrentLevelCode(r.currentLevelCode());
        e.setTargetLevelCode(r.targetLevelCode());
        var trackCode = normalizeCode(r.trackCode(), "GENERAL");
        tracks.findFirstByCodeIgnoreCaseAndStatusOrderByVersionDesc(
                        trackCode, ConfigStatus.PUBLISHED)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Published engineering track not found: " + trackCode));
        e.setTrackCode(trackCode);
        e.setEmploymentStart(r.employmentStart());
        if (r.aliases() != null)
            r.aliases().forEach(a -> e.getAliases().add(EvaluationService.normalize(a)));
        return employees.save(e);
    }

    @PatchMapping("/employees/{email}/track")
    @PreAuthorize("hasAnyRole('EVALUATOR_ADMIN','ENGINEERING_MANAGER','ORGANIZATION_ADMIN')")
    Employee assignTrack(@PathVariable String email, @RequestBody TrackAssignmentRequest request) {
        var employee = lookup(email);
        var trackCode = normalizeCode(request.trackCode(), "GENERAL");
        tracks.findFirstByCodeIgnoreCaseAndStatusOrderByVersionDesc(
                        trackCode, ConfigStatus.PUBLISHED)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Published engineering track not found: " + trackCode));
        employee.setTrackCode(trackCode);
        return employees.save(employee);
    }

    @GetMapping("/employees/{email}/evaluations")
    @PreAuthorize("@access.canViewEmployee(#email, authentication)")
    List<Evaluation> evaluations(@PathVariable String email) {
        var e = lookup(email);
        return evaluations.findByEmployeeIdOrderByCreatedAtDesc(e.getId());
    }

    @PostMapping("/evaluations/recalculate")
    @PreAuthorize("hasAnyRole('EVALUATOR_ADMIN','ENGINEERING_MANAGER')")
    Evaluation evaluate(@Valid @RequestBody EvaluationRequest r) {
        if (r.from() != null && r.to() != null) {
            var zoneName =
                    Optional.ofNullable(r.timezone())
                            .filter(s -> !s.isBlank())
                            .orElse(appProperties.timeZone());
            var zone = ZoneId.of(zoneName);
            var from = r.from().atStartOfDay(zone).toInstant();
            var to = r.to().plusDays(1).atStartOfDay(zone).toInstant();
            var label =
                    Optional.ofNullable(r.period())
                            .filter(s -> !s.isBlank())
                            .orElse(r.from() + ".." + r.to());
            return service.calculate(
                    r.email(),
                    label,
                    r.levelCode(),
                    r.ruleVersion(),
                    from,
                    to,
                    zoneName,
                    Optional.ofNullable(r.mode()).orElse(EvaluationMode.ASSESSMENT));
        }
        return service.calculate(r.email(), r.period(), r.levelCode(), r.ruleVersion());
    }

    @PostMapping("/employees/{email}/prepare-report")
    @PreAuthorize(
            "hasAnyRole('EVALUATOR_ADMIN','ENGINEERING_MANAGER','INTEGRATION_ADMIN','ORGANIZATION_ADMIN')")
    PreparedReport prepareReport(
            @PathVariable String email, @Valid @RequestBody PrepareReportRequest request) {
        var employee = lookup(email);
        var zone = ZoneId.of(request.timezone());
        var from = request.from().atStartOfDay(zone).toInstant();
        var to = request.to().plusDays(1).atStartOfDay(zone).toInstant();
        if (!from.isBefore(to)) throw new IllegalArgumentException("from must be before to");
        var identities = identityDiscovery.autoConfirmExact(email);
        int evidenceProcessed = synchronization.syncEmployee(employee.getId(), from, to);
        var levelFits =
                levelFit.evaluate(
                        employee.getId(), from, to, request.timezone(), request.ruleVersion());
        var levelCode = selectReportLevel(employee, levelFits, request.ruleVersion());
        var label =
                Optional.ofNullable(request.period())
                        .filter(value -> !value.isBlank())
                        .orElse(request.from() + ".." + request.to());
        var evaluation =
                service.calculate(
                        email,
                        label,
                        levelCode,
                        request.ruleVersion(),
                        from,
                        to,
                        request.timezone(),
                        EvaluationMode.ASSESSMENT);
        return new PreparedReport(evaluation, identities, evidenceProcessed, levelFits);
    }

    static String selectReportLevel(
            Employee employee, List<LevelFitService.LevelFit> levelFits, int frameworkVersion) {
        var supported =
                levelFits.stream()
                        .filter(LevelFitService.LevelFit::recommended)
                        .map(LevelFitService.LevelFit::code)
                        .findFirst();
        if (supported.isPresent()) return supported.get();

        var assigned =
                Optional.ofNullable(employee.getTargetLevelCode())
                        .filter(value -> !value.isBlank())
                        .or(
                                () ->
                                        Optional.ofNullable(employee.getCurrentLevelCode())
                                                .filter(value -> !value.isBlank()));
        if (assigned.isPresent()) return assigned.get();

        return levelFits.stream()
                .filter(fit -> fit.automaticCriteria() > fit.incompleteCriteria())
                .filter(fit -> fit.score() > 0)
                .max(
                        Comparator.comparingInt(LevelFitService.LevelFit::score)
                                .thenComparingInt(LevelFitService.LevelFit::ordinal))
                .map(LevelFitService.LevelFit::code)
                .orElseGet(
                        () ->
                                levelFits.stream()
                                        .min(
                                                Comparator.comparingInt(
                                                        LevelFitService.LevelFit::ordinal))
                                        .map(LevelFitService.LevelFit::code)
                                        .orElse(frameworkVersion >= 2 ? "JUNIOR_I" : "JUNIOR"));
    }

    @PostMapping("/evaluations/{id}/finalize")
    @PreAuthorize("hasRole('ENGINEERING_MANAGER')")
    Evaluation finalizeEvaluation(@PathVariable UUID id) {
        return service.finalizeEvaluation(id);
    }

    @GetMapping("/evaluations/{id}/evidence")
    @PreAuthorize("@access.canViewEvaluation(#id, authentication)")
    List<Evidence> evaluationEvidence(@PathVariable UUID id) {
        var e = evaluations.findById(id).orElseThrow();
        if (e.getPeriodFrom() != null && e.getPeriodTo() != null)
            return evidence.findByEmployeeIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                    e.getEmployeeId(), e.getPeriodFrom(), e.getPeriodTo());
        return evidence.findByEmployeeIdOrderByOccurredAtDesc(e.getEmployeeId());
    }

    @GetMapping("/evaluations/{id}/readiness")
    @PreAuthorize("@access.canViewEvaluation(#id, authentication)")
    List<DataReadinessService.Readiness> readiness(@PathVariable UUID id) {
        return readiness.forEvaluation(id);
    }

    @GetMapping("/levels")
    List<EngineeringLevel> levels() {
        return levels.findAllByOrderByOrdinalValueAscVersionDesc();
    }

    @GetMapping("/tracks")
    List<EngineeringTrack> tracks() {
        return tracks.findAllByOrderByOrdinalValueAscVersionDesc();
    }

    @PostMapping("/tracks")
    @PreAuthorize("hasRole('EVALUATOR_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    EngineeringTrack createTrack(@Valid @RequestBody TrackRequest request) {
        var track = new EngineeringTrack();
        track.setCode(normalizeCode(request.code(), null));
        track.setName(request.name());
        track.setDescription(request.description());
        track.setIconKey(Optional.ofNullable(request.iconKey()).orElse("code"));
        track.setOrdinalValue(request.ordinal());
        track.setVersion(request.version());
        track.setStatus(Optional.ofNullable(request.status()).orElse(ConfigStatus.DRAFT));
        track.setEffectiveFrom(
                Optional.ofNullable(request.effectiveFrom()).orElse(LocalDate.now()));
        return tracks.save(track);
    }

    @GetMapping("/frameworks/{version}")
    FrameworkDefinition framework(@PathVariable int version) {
        return new FrameworkDefinition(
                version,
                tracks.findAllByOrderByOrdinalValueAscVersionDesc(),
                levels.findByVersionAndStatusOrderByOrdinalValueAsc(
                        version, ConfigStatus.PUBLISHED),
                criteria.findAll().stream().filter(item -> item.getVersion() == version).toList(),
                competencies.findByVersionOrderByLevelCodeAscCompetencyKeyAsc(version));
    }

    @PostMapping("/levels")
    @PreAuthorize("hasRole('EVALUATOR_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    EngineeringLevel createLevel(@Valid @RequestBody LevelRequest r) {
        var l = new EngineeringLevel();
        l.setCode(r.code().toUpperCase());
        l.setName(r.name());
        l.setOrdinalValue(r.ordinal());
        l.setVersion(r.version());
        l.setStatus(r.status());
        l.setEffectiveFrom(r.effectiveFrom());
        return levels.save(l);
    }

    @GetMapping("/criteria")
    List<Criterion> criteria() {
        return criteria.findAll();
    }

    @PostMapping("/criteria")
    @PreAuthorize("hasRole('EVALUATOR_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    Criterion createCriterion(@Valid @RequestBody CriterionRequest r) {
        if ("BETWEEN".equals(r.operator())
                && (r.threshold() == null
                        || r.thresholdMax() == null
                        || r.threshold().compareTo(r.thresholdMax()) > 0)) {
            throw new IllegalArgumentException(
                    "BETWEEN requires a minimum threshold not greater than thresholdMax");
        }
        if (r.scope() == CriterionScope.TRACK
                && (r.trackCode() == null || r.trackCode().isBlank())) {
            throw new IllegalArgumentException("TRACK criteria require trackCode");
        }
        if (r.scope() == CriterionScope.TEAM && (r.teamKey() == null || r.teamKey().isBlank())) {
            throw new IllegalArgumentException("TEAM criteria require teamKey");
        }
        var c = new Criterion();
        c.setCode(r.code());
        c.setName(r.name());
        c.setDescription(r.description());
        c.setSourceTool(r.sourceTool());
        c.setMetricKey(r.metricKey());
        c.setEvaluationType(r.evaluationType());
        c.setPeriodType(r.periodType());
        c.setMinimumCoverage(
                Optional.ofNullable(r.minimumCoverage())
                        .filter(v -> !v.isBlank())
                        .orElse("COMPLETE"));
        c.setCustomPeriodAllowed(r.customPeriodAllowed());
        c.setOperator(r.operator());
        c.setThresholdValue(r.threshold());
        c.setThresholdMaxValue(r.thresholdMax());
        c.setAggregation(Optional.ofNullable(r.aggregation()).orElse(Aggregation.SUM));
        c.setDenominatorMetricKey(r.denominatorMetricKey());
        c.setLevelCode(r.levelCode());
        c.setScope(Optional.ofNullable(r.scope()).orElse(CriterionScope.COMMON));
        c.setTrackCode(normalizeCode(r.trackCode(), null));
        c.setTeamKey(r.teamKey());
        c.setProrationPolicy(
                Optional.ofNullable(r.prorationPolicy()).orElse(ProrationPolicy.PROGRESS_ONLY));
        c.setMandatory(r.mandatory() == null || r.mandatory());
        c.setRubric(r.rubric());
        c.setVisualization(Optional.ofNullable(r.visualization()).orElse("PROGRESS"));
        c.setVersion(r.version());
        c.setStatus(r.status());
        c.setEffectiveFrom(r.effectiveFrom());
        return criteria.save(c);
    }

    @PostMapping("/evidence")
    @PreAuthorize("hasAnyRole('EVALUATOR_ADMIN','INTEGRATION_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    Evidence addEvidence(@Valid @RequestBody EvidenceRequest r) {
        var emp = lookup(r.email());
        var e = new Evidence();
        e.setEmployeeId(emp.getId());
        e.setToolKey(r.toolKey());
        e.setMetricKey(r.metricKey());
        e.setExternalId(r.externalId());
        e.setOccurredAt(r.occurredAt());
        e.setNumericValue(r.value());
        e.setTitle(r.title());
        e.setUrl(r.url());
        e.setAttributesJson("{}");
        return evidence.save(e);
    }

    @GetMapping("/integrations/health")
    @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','AUDITOR')")
    List<ConnectorConfig> health() {
        return connectors.findAll();
    }

    @GetMapping("/integrations/onboarding")
    @PreAuthorize("hasAnyRole('INTEGRATION_ADMIN','AUDITOR')")
    List<IntegrationOnboarding> integrationOnboarding() {
        var gitlab = connectorProperties.gitlab();
        var jira = connectorProperties.jira();
        var confluence = connectorProperties.confluence();
        String gl = gitlab.url().replaceAll("/+$", "");
        return List.of(
                new IntegrationOnboarding(
                        "gitlab",
                        "GitLab",
                        gl,
                        gl.isBlank() ? "" : gl + "/-/user_settings/personal_access_tokens",
                        List.of("GITLAB_URL", "GITLAB_TOKEN"),
                        "Create a personal access token with read_api scope. The search is global across every repository visible to this user.",
                        !gl.isBlank() && !gitlab.token().isBlank()),
                new IntegrationOnboarding(
                        "jira",
                        "Jira",
                        jira.url(),
                        "https://id.atlassian.com/manage-profile/security/api-tokens",
                        List.of("JIRA_URL", "JIRA_EMAIL", "JIRA_TOKEN"),
                        "Create an Atlassian API token. The search is global across every Jira project visible to this account.",
                        !jira.url().isBlank()
                                && !jira.email().isBlank()
                                && !jira.token().isBlank()),
                new IntegrationOnboarding(
                        "confluence",
                        "Confluence",
                        confluence.url(),
                        "https://id.atlassian.com/manage-profile/security/api-tokens",
                        List.of("CONFLUENCE_URL", "CONFLUENCE_EMAIL", "CONFLUENCE_TOKEN"),
                        "The same Atlassian identity may be used. Search covers every Confluence space visible to this account.",
                        !confluence.url().isBlank()
                                && !confluence.email().isBlank()
                                && !confluence.token().isBlank()));
    }

    @PostMapping("/employees/{email}/discover-identities")
    @PreAuthorize("@access.canViewEmployee(#email, authentication)")
    List<IdentityDiscoveryService.Discovery> discover(@PathVariable String email) {
        return identityDiscovery.discover(email);
    }

    @PostMapping("/employees/{email}/identities")
    @PreAuthorize("@access.canManageIdentity(#email, authentication)")
    ExternalIdentity confirmIdentity(@PathVariable String email, @RequestBody IdentityRequest r) {
        return identityDiscovery.confirm(
                email, r.toolKey(), r.externalUserId(), r.username(), r.matchedEmail());
    }

    @PostMapping("/integrations/sync")
    @PreAuthorize("hasRole('INTEGRATION_ADMIN')")
    Map<String, Integer> sync(@RequestParam Instant from, @RequestParam Instant to) {
        return Map.of("evidenceProcessed", synchronization.syncAll(from, to));
    }

    @PostMapping("/integrations")
    @PreAuthorize("hasRole('INTEGRATION_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    ConnectorConfig connector(@RequestBody ConnectorRequest r) {
        var c = connectors.findByToolKey(r.toolKey()).orElseGet(ConnectorConfig::new);
        c.setToolKey(r.toolKey());
        c.setDisplayName(r.displayName());
        c.setBaseUrl(r.baseUrl());
        c.setAllowedScopes(r.allowedScopes());
        c.setEnabled(r.enabled());
        c.setHealthStatus("NOT_TESTED");
        return connectors.save(c);
    }

    @PostMapping("/evaluations/{id}/disputes")
    @PreAuthorize("@access.canViewEvaluation(#id, authentication)")
    Dispute dispute(@PathVariable UUID id, @RequestBody DisputeRequest r, Authentication auth) {
        return workflows.dispute(id, r.criterionResultId(), auth.getName(), r.message());
    }

    @GetMapping("/evaluations/{id}/disputes")
    @PreAuthorize("@access.canViewEvaluation(#id, authentication)")
    List<Dispute> disputes(@PathVariable UUID id) {
        return workflows.disputes(id);
    }

    @PostMapping("/criterion-results/{id}/decision")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','ENGINEERING_MANAGER')")
    CriterionResult decision(
            @PathVariable UUID id, @RequestBody DecisionRequest r, Authentication auth) {
        return workflows.decide(id, r.decision(), r.note(), auth.getName());
    }

    @GetMapping(
            value = "/evaluations/{id}/export.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("@access.canViewEvaluation(#id, authentication)")
    byte[] xlsx(@PathVariable UUID id) {
        return exports.xlsx(id);
    }

    @GetMapping(value = "/evaluations/{id}/export.pdf", produces = "application/pdf")
    @PreAuthorize("@access.canViewEvaluation(#id, authentication)")
    byte[] pdf(@PathVariable UUID id) {
        return exports.pdf(id);
    }

    @GetMapping("/evaluations/{id}/ai-summary")
    @PreAuthorize("@access.canViewEvaluation(#id, authentication)")
    AiSummaryService.Summary summary(@PathVariable UUID id) {
        return ai.summarize(id);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('AUDITOR')")
    List<AuditEvent> audit() {
        return workflows.audit();
    }

    record EmployeeRequest(
            @Email @NotBlank String email,
            @NotBlank String displayName,
            String team,
            String managerEmail,
            String currentLevelCode,
            String targetLevelCode,
            String trackCode,
            LocalDate employmentStart,
            Set<String> aliases) {}

    record TrackAssignmentRequest(@NotBlank String trackCode) {}

    record EvaluationRequest(
            @Email String email,
            String period,
            LocalDate from,
            LocalDate to,
            String timezone,
            EvaluationMode mode,
            @NotBlank String levelCode,
            @Min(1) int ruleVersion) {}

    record PrepareReportRequest(
            String period,
            @NotNull LocalDate from,
            @NotNull LocalDate to,
            @NotBlank String timezone,
            EvaluationMode mode,
            String levelCode,
            @Min(1) int ruleVersion) {}

    record PreparedReport(
            Evaluation evaluation,
            List<ExternalIdentity> identities,
            int evidenceProcessed,
            List<LevelFitService.LevelFit> levelFits) {}

    record LevelRequest(
            @NotBlank String code,
            @NotBlank String name,
            int ordinal,
            @Min(1) int version,
            ConfigStatus status,
            LocalDate effectiveFrom) {}

    record TrackRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            String iconKey,
            int ordinal,
            @Min(1) int version,
            ConfigStatus status,
            LocalDate effectiveFrom) {}

    record CriterionRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotBlank String sourceTool,
            @NotBlank String metricKey,
            EvaluationType evaluationType,
            String periodType,
            String minimumCoverage,
            boolean customPeriodAllowed,
            @Pattern(regexp = ">=|<=|>|<|=|BETWEEN") String operator,
            BigDecimal threshold,
            BigDecimal thresholdMax,
            Aggregation aggregation,
            String denominatorMetricKey,
            @NotBlank String levelCode,
            CriterionScope scope,
            String trackCode,
            String teamKey,
            ProrationPolicy prorationPolicy,
            Boolean mandatory,
            String rubric,
            String visualization,
            @Min(1) int version,
            ConfigStatus status,
            LocalDate effectiveFrom) {}

    record FrameworkDefinition(
            int version,
            List<EngineeringTrack> tracks,
            List<EngineeringLevel> levels,
            List<Criterion> criteria,
            List<CompetencyExpectation> competencies) {}

    record EvidenceRequest(
            @Email String email,
            String toolKey,
            String metricKey,
            String externalId,
            Instant occurredAt,
            BigDecimal value,
            String title,
            String url) {}

    record ConnectorRequest(
            String toolKey,
            String displayName,
            String baseUrl,
            String allowedScopes,
            boolean enabled) {}

    record IdentityRequest(
            String toolKey, String externalUserId, String username, String matchedEmail) {}

    record DisputeRequest(UUID criterionResultId, @NotBlank String message) {}

    record DecisionRequest(ResultStatus decision, @NotBlank String note) {}

    record IntegrationOnboarding(
            String key,
            String name,
            String serviceUrl,
            String tokenUrl,
            List<String> environmentVariables,
            String instructions,
            boolean configured) {}

    private static String normalizeCode(String value, String fallback) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_"))
                .orElse(fallback);
    }
}
