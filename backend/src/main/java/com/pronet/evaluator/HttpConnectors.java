package com.pronet.evaluator;

import com.pronet.evaluator.config.ConnectorsProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

abstract class ReadOnlyHttpConnector implements EngineeringConnector {
    final String baseUrl, token;
    final RestClient client;

    ReadOnlyHttpConnector(String baseUrl, String email, String token) {
        this.baseUrl = strip(baseUrl);
        this.token = token;
        this.client =
                RestClient.builder()
                        .baseUrl(this.baseUrl)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, authorization(email, token))
                        .build();
    }

    public ConnectorHealth testConnection() {
        if (baseUrl.isBlank() || token.isBlank())
            return new ConnectorHealth(false, "Missing URL or token", "NOT_CONFIGURED");
        try {
            healthRequest();
            return new ConnectorHealth(true, "Connected read-only", "CONNECTED");
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401)
                return new ConnectorHealth(
                        false,
                        "The configured credentials were rejected (HTTP 401)",
                        "AUTHENTICATION_FAILED");
            if (status == 403)
                return new ConnectorHealth(
                        false,
                        "The account is authenticated but cannot access this service (HTTP 403)",
                        "ACCESS_DENIED");
            if (status == 429)
                return new ConnectorHealth(
                        false, "The service rate limit was reached (HTTP 429)", "RATE_LIMITED");
            return new ConnectorHealth(
                    false, "The service returned HTTP " + status, "SOURCE_UNAVAILABLE");
        } catch (Exception e) {
            return new ConnectorHealth(
                    false, "The service could not be reached", "SOURCE_UNAVAILABLE");
        }
    }

    public List<EvidenceInput> syncEvidence(String id, Instant from, Instant to) {
        return List.of();
    }

    abstract void healthRequest();

    static String strip(String s) {
        return s == null ? "" : s.replaceAll("/+$", "");
    }

    static String authorization(String email, String t) {
        if (t == null || t.isBlank()) return "Bearer missing";
        if (t.startsWith("Bearer ") || t.startsWith("Basic ")) return t;
        if (email != null && !email.isBlank())
            return "Basic "
                    + Base64.getEncoder()
                            .encodeToString(
                                    (email + ":" + t)
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "Bearer " + t;
    }

    static Instant instant(Object... values) {
        for (Object value : values)
            if (value != null)
                try {
                    return Instant.parse(value.toString());
                } catch (Exception ignored) {
                }
        return Instant.now();
    }

    static BigDecimal number(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}

@Component
class GitLabConnector extends ReadOnlyHttpConnector {
    GitLabConnector(ConnectorsProperties properties) {
        super(properties.gitlab().url(), "", properties.gitlab().token());
    }

    public String key() {
        return "gitlab";
    }

    void healthRequest() {
        client.get().uri("/api/v4/user").retrieve().toBodilessEntity();
    }

    public List<IdentityCandidate> discoverUsers(String email) {
        List<?> rows = searchUsers(email);
        String emailLocalPart =
                email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        if (rows == null || rows.isEmpty()) rows = searchUsers(emailLocalPart);
        if (rows == null) return List.of();
        List<IdentityCandidate> out = new ArrayList<>();
        for (Object row : rows) {
            Map<?, ?> m = (Map<?, ?>) row;
            String username = Objects.toString(m.get("username"), "");
            String discoveredEmail =
                    Objects.toString(m.get("email"), Objects.toString(m.get("public_email"), ""));
            boolean exact =
                    email.equalsIgnoreCase(discoveredEmail)
                            || emailLocalPart.equalsIgnoreCase(username);
            out.add(
                    new IdentityCandidate(
                            String.valueOf(m.get("id")),
                            username,
                            exact && discoveredEmail.isBlank() ? email : discoveredEmail,
                            exact));
        }
        return out;
    }

    private List<?> searchUsers(String query) {
        return client.get()
                .uri(u -> u.path("/api/v4/users").queryParam("search", query).build())
                .retrieve()
                .body(List.class);
    }

    public List<EvidenceInput> syncEvidence(String id, Instant from, Instant to) {
        List<EvidenceInput> out = new ArrayList<>();
        Map<String, Map<?, ?>> projects = new HashMap<>();
        List<?> mrs =
                client.get()
                        .uri(
                                u ->
                                        u.path("/api/v4/merge_requests")
                                                .queryParam("scope", "all")
                                                .queryParam("author_id", id)
                                                .queryParam("state", "merged")
                                                .queryParam("updated_after", from.toString())
                                                .queryParam("updated_before", to.toString())
                                                .queryParam("per_page", 100)
                                                .build())
                        .retrieve()
                        .body(List.class);
        if (mrs != null)
            for (Object x : mrs) {
                Map<?, ?> m = (Map<?, ?>) x;
                Instant when = instant(m.get("merged_at"), m.get("updated_at"));
                if (!when.isBefore(from) && !when.isAfter(to)) {
                    String project = Objects.toString(m.get("project_id"), "");
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("project_id", project);
                    a.put(
                            "project",
                            Objects.toString(
                                    project(project, projects).get("path_with_namespace"),
                                    project));
                    a.put("source_branch", Objects.toString(m.get("source_branch"), ""));
                    a.put("target_branch", Objects.toString(m.get("target_branch"), ""));
                    a.put("iid", m.get("iid"));
                    a.put("description", clip(Objects.toString(m.get("description"), "")));
                    out.add(
                            new EvidenceInput(
                                    "merged_merge_requests",
                                    "mr:" + m.get("id"),
                                    when,
                                    BigDecimal.ONE,
                                    Objects.toString(m.get("title"), "Merge request"),
                                    Objects.toString(m.get("web_url"), ""),
                                    a));
                }
            }
        var fromDate = from.atZone(java.time.ZoneOffset.UTC).toLocalDate().minusDays(1);
        var toDate = to.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(1);
        List<?> events =
                client.get()
                        .uri(
                                u ->
                                        u.path("/api/v4/users/" + id + "/events")
                                                .queryParam("after", fromDate)
                                                .queryParam("before", toDate)
                                                .queryParam("per_page", 100)
                                                .build())
                        .retrieve()
                        .body(List.class);
        if (events != null)
            for (Object x : events) {
                Map<?, ?> m = (Map<?, ?>) x;
                Instant when = instant(m.get("created_at"));
                if (when.isBefore(from) || when.isAfter(to)) continue;
                String action = Objects.toString(m.get("action_name"), "");
                String title = Objects.toString(m.get("target_title"), "GitLab activity");
                String project = Objects.toString(m.get("project_id"), "");
                Map<?, ?> p = project(project, projects);
                String projectName = Objects.toString(p.get("path_with_namespace"), project),
                        projectUrl = Objects.toString(p.get("web_url"), "");
                if (action.startsWith("commented")) {
                    Map<?, ?> note = m.get("note") instanceof Map<?, ?> n ? n : Map.of();
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("project_id", project);
                    a.put("project", projectName);
                    a.put("target_type", Objects.toString(m.get("target_type"), ""));
                    a.put("target_iid", m.get("target_iid"));
                    a.put("comment", clip(Objects.toString(note.get("body"), "")));
                    String url = projectUrl.isBlank() ? "" : projectUrl + targetPath(m);
                    out.add(
                            new EvidenceInput(
                                    "review_comments",
                                    "event:" + m.get("id"),
                                    when,
                                    BigDecimal.ONE,
                                    title,
                                    url,
                                    a));
                }
                if (action.startsWith("pushed")) {
                    Map<?, ?> push = m.get("push_data") instanceof Map<?, ?> pd ? pd : Map.of();
                    BigDecimal count =
                            Optional.ofNullable(number(push.get("commit_count")))
                                    .orElse(BigDecimal.ONE);
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("project_id", project);
                    a.put("project", projectName);
                    a.put("branch", Objects.toString(push.get("ref"), ""));
                    a.put("commit_count", count);
                    a.put("commit_from", Objects.toString(push.get("commit_from"), ""));
                    a.put("commit_to", Objects.toString(push.get("commit_to"), ""));
                    String url =
                            projectUrl.isBlank()
                                    ? ""
                                    : projectUrl
                                            + "/-/commits/"
                                            + Objects.toString(push.get("ref"), "");
                    out.add(
                            new EvidenceInput(
                                    "commits",
                                    "event:" + m.get("id"),
                                    when,
                                    count,
                                    Objects.toString(push.get("commit_title"), title),
                                    url,
                                    a));
                }
            }
        return out;
    }

    private Map<?, ?> project(String id, Map<String, Map<?, ?>> cache) {
        if (id.isBlank()) return Map.of();
        return cache.computeIfAbsent(
                id,
                k -> {
                    try {
                        var x =
                                client.get()
                                        .uri("/api/v4/projects/" + k)
                                        .retrieve()
                                        .body(Map.class);
                        return x == null ? Map.of() : x;
                    } catch (Exception e) {
                        return Map.of();
                    }
                });
    }

    private static String targetPath(Map<?, ?> event) {
        String type = Objects.toString(event.get("target_type"), "").toLowerCase(),
                iid = Objects.toString(event.get("target_iid"), "");
        if (iid.isBlank()) return "";
        if (type.contains("merge")) return "/-/merge_requests/" + iid;
        if (type.contains("issue")) return "/-/issues/" + iid;
        return "";
    }

    private static String clip(String s) {
        return s.length() > 1000 ? s.substring(0, 1000) + "…" : s;
    }
}

@Component
class JiraConnector extends ReadOnlyHttpConnector {
    private static final Set<String> MAIN_PROJECTS = Set.of("STM", "PAYMENTS", "DEL", "PMO");

    JiraConnector(ConnectorsProperties properties) {
        super(properties.jira().url(), properties.jira().email(), properties.jira().token());
    }

    public String key() {
        return "jira";
    }

    void healthRequest() {
        client.get().uri("/rest/api/2/myself").retrieve().toBodilessEntity();
    }

    public List<IdentityCandidate> discoverUsers(String email) {
        List<?> rows =
                client.get()
                        .uri(
                                u ->
                                        u.path("/rest/api/3/user/search")
                                                .queryParam("query", email)
                                                .build())
                        .retrieve()
                        .body(List.class);
        if (rows == null) return List.of();
        List<IdentityCandidate> out = new ArrayList<>();
        for (Object row : rows) {
            Map<?, ?> m = (Map<?, ?>) row;
            out.add(
                    new IdentityCandidate(
                            Objects.toString(m.get("accountId"), ""),
                            Objects.toString(m.get("displayName"), ""),
                            Objects.toString(m.get("emailAddress"), ""),
                            email.equalsIgnoreCase(Objects.toString(m.get("emailAddress"), ""))));
        }
        return out;
    }

    public List<EvidenceInput> syncEvidence(String id, Instant from, Instant to) {
        String start = from.atZone(java.time.ZoneOffset.UTC).toLocalDate().minusDays(1).toString(),
                end = to.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(1).toString(),
                safe = id.replace("\"", "");
        List<String> storyFields = storyPointFields();
        Set<String> doneStatuses = doneStatusIds();
        String fields =
                String.join(
                        ",",
                        new LinkedHashSet<>(
                                concat(
                                        List.of(
                                                "summary",
                                                "created",
                                                "resolution",
                                                "resolutiondate",
                                                "labels",
                                                "comment",
                                                "worklog",
                                                "reporter",
                                                "assignee",
                                                "project",
                                                "issuetype",
                                                "status",
                                                "parent",
                                                "subtasks",
                                                "issuelinks",
                                                "priority"),
                                        storyFields)));
        Map<String, Map<?, ?>> issues = new LinkedHashMap<>();
        search(
                        "issuekey in updatedBy(\""
                                + safe
                                + "\", \""
                                + start
                                + "\", \""
                                + end
                                + "\") ORDER BY updated DESC",
                        fields)
                .forEach(i -> issues.put(Objects.toString(i.get("key")), i));
        search(
                        "assignee WAS \""
                                + safe
                                + "\" DURING (\""
                                + start
                                + "\", \""
                                + end
                                + "\") AND updated >= \""
                                + start
                                + "\" AND updated <= \""
                                + end
                                + "\" ORDER BY updated DESC",
                        fields)
                .forEach(i -> issues.put(Objects.toString(i.get("key")), i));
        List<EvidenceInput> out = new ArrayList<>();
        Map<String, Map<?, ?>> cache = new HashMap<>(issues);
        List<Completed> completed = new ArrayList<>();
        for (Map<?, ?> issue : issues.values()) {
            Map<?, ?> f = map(issue.get("fields"));
            String issueKey = Objects.toString(issue.get("key")),
                    title = Objects.toString(f.get("summary"), "Jira issue"),
                    url = baseUrl + "/browse/" + issueKey;
            Map<String, Object> context = context(issue);
            Instant created = parse(f.get("created"));
            if (id.equals(Objects.toString(map(f.get("reporter")).get("accountId")))
                    && created != null
                    && inRange(created, from, to))
                out.add(
                        new EvidenceInput(
                                "jira_issue_creations",
                                issueKey + ":created",
                                created,
                                BigDecimal.ONE,
                                title,
                                url,
                                context));
            collectAuthored(
                    out,
                    f.get("comment"),
                    "comments",
                    "comments",
                    id,
                    issueKey,
                    title,
                    url,
                    from,
                    to,
                    context);
            collectAuthored(
                    out,
                    f.get("worklog"),
                    "worklogs",
                    "worklogs",
                    id,
                    issueKey,
                    title,
                    url,
                    from,
                    to,
                    context);
            for (Object h : list(map(issue.get("changelog")).get("histories"))) {
                Map<?, ?> history = map(h);
                if (!id.equals(Objects.toString(map(history.get("author")).get("accountId"))))
                    continue;
                Instant when = parse(history.get("created"));
                if (when == null || !inRange(when, from, to)) continue;
                for (Object i : list(history.get("items"))) {
                    Map<?, ?> item = map(i);
                    String field = Objects.toString(item.get("field"), "field");
                    Map<String, Object> a = new LinkedHashMap<>(context);
                    a.put("field", field);
                    a.put("from", Objects.toString(item.get("fromString"), ""));
                    a.put("to", Objects.toString(item.get("toString"), ""));
                    out.add(
                            new EvidenceInput(
                                    "jira_field_changes",
                                    issueKey + ":change:" + history.get("id") + ":" + field,
                                    when,
                                    BigDecimal.ONE,
                                    field + " changed on " + title,
                                    url,
                                    a));
                    if ("status".equalsIgnoreCase(field))
                        out.add(
                                new EvidenceInput(
                                        "jira_status_changes",
                                        issueKey + ":status:" + history.get("id"),
                                        when,
                                        BigDecimal.ONE,
                                        title,
                                        url,
                                        a));
                }
            }
            Instant completedAt = completionForAssignee(issue, id, doneStatuses, from, to);
            String project = projectKey(issue);
            if (completedAt != null && !"QA".equals(project) && countsForVelocity(f)) {
                BigDecimal points = storyPoints(f, storyFields);
                Map<String, Object> a = new LinkedHashMap<>(context);
                a.put("story_points", points == null ? 0 : points);
                a.put(
                        "story_points_present",
                        points != null && points.compareTo(BigDecimal.ZERO) > 0);
                a.put("completion_source", "statusCategory=Done transition");
                out.add(
                        new EvidenceInput(
                                "completed_tasks",
                                issueKey,
                                completedAt,
                                BigDecimal.ONE,
                                title,
                                url,
                                a));
                if (points == null || points.compareTo(BigDecimal.ZERO) == 0)
                    out.add(
                            new EvidenceInput(
                                    "completed_tasks_without_sp",
                                    issueKey + ":without-sp",
                                    completedAt,
                                    BigDecimal.ONE,
                                    title,
                                    url,
                                    a));
                else
                    out.add(
                            new EvidenceInput(
                                    "completed_tasks_with_sp",
                                    issueKey + ":with-sp",
                                    completedAt,
                                    BigDecimal.ONE,
                                    title,
                                    url,
                                    a));
                out.add(
                        new EvidenceInput(
                                "story_points",
                                issueKey + ":sp",
                                completedAt,
                                points == null ? BigDecimal.ZERO : points,
                                title,
                                url,
                                a));
                completed.add(new Completed(issue, completedAt));
            } else if (completedAt != null && !"QA".equals(project)) {
                Map<String, Object> a = new LinkedHashMap<>(context);
                a.put("exclusion_reason", "resolution is not Done");
                out.add(
                        new EvidenceInput(
                                "completed_tasks_excluded_resolution",
                                issueKey + ":excluded-resolution",
                                completedAt,
                                BigDecimal.ONE,
                                title,
                                url,
                                a));
            }
        }
        collectQaEvidence(out, completed, id, cache, fields);
        return out;
    }

    private void collectQaEvidence(
            List<EvidenceInput> out,
            List<Completed> completed,
            String userId,
            Map<String, Map<?, ?>> cache,
            String fields) {
        for (Completed c : completed) {
            Map<?, ?> impl = c.issue();
            String implKey = Objects.toString(impl.get("key")),
                    title = Objects.toString(map(impl.get("fields")).get("summary"), "Jira task");
            Set<String> qaTasks = new LinkedHashSet<>(), mainKeys = new LinkedHashSet<>();
            if (MAIN_PROJECTS.contains(projectKey(impl))) mainKeys.add(implKey);
            for (String key : linkedKeys(impl)) {
                Map<?, ?> linked = issue(key, cache, fields);
                if ("QA".equals(projectKey(linked)) && !isDefect(linked)) qaTasks.add(key);
                else if (!"QA".equals(projectKey(linked))) mainKeys.add(key);
            }
            for (String mainKey : mainKeys)
                for (String key : linkedKeys(issue(mainKey, cache, fields))) {
                    Map<?, ?> linked = issue(key, cache, fields);
                    if ("QA".equals(projectKey(linked)) && !isDefect(linked)) qaTasks.add(key);
                }
            if (qaTasks.isEmpty()) continue;
            Map<String, Object> tested = context(impl);
            tested.put("qa_tasks", qaTasks);
            out.add(
                    new EvidenceInput(
                            "qa_tested_completed_tasks",
                            implKey + ":qa-tested",
                            c.when(),
                            BigDecimal.ONE,
                            title,
                            baseUrl + "/browse/" + implKey,
                            tested));
            for (String qaKey : qaTasks) {
                Map<?, ?> qa = issue(qaKey, cache, fields);
                for (String defectKey : linkedKeys(qa)) {
                    Map<?, ?> defect = issue(defectKey, cache, fields);
                    if (!"QA".equals(projectKey(defect)) || !isDefect(defect)) continue;
                    String attribution = attribution(defect, impl, mainKeys, userId, cache, fields);
                    Instant when =
                            Optional.ofNullable(parse(map(defect.get("fields")).get("created")))
                                    .orElse(c.when());
                    Map<String, Object> a = context(defect);
                    a.put("qa_task", qaKey);
                    a.put("implementation_task", implKey);
                    a.put("attribution", attribution);
                    String
                            defectTitle =
                                    Objects.toString(
                                            map(defect.get("fields")).get("summary"), "QA defect"),
                            url = baseUrl + "/browse/" + defectKey;
                    if ("UNRESOLVED".equals(attribution)) {
                        out.add(
                                new EvidenceInput(
                                        "qa_defects_needs_review",
                                        defectKey + ":" + userId + ":review",
                                        when,
                                        BigDecimal.ONE,
                                        defectTitle,
                                        url,
                                        a));
                        continue;
                    }
                    out.add(
                            new EvidenceInput(
                                    "qa_defects",
                                    defectKey + ":" + userId,
                                    when,
                                    BigDecimal.ONE,
                                    defectTitle,
                                    url,
                                    a));
                    String priority =
                            Objects.toString(
                                    map(map(defect.get("fields")).get("priority")).get("name"),
                                    "Unspecified");
                    out.add(
                            new EvidenceInput(
                                    "qa_defects_" + slug(priority),
                                    defectKey + ":" + userId + ":" + slug(priority),
                                    when,
                                    BigDecimal.ONE,
                                    defectTitle,
                                    url,
                                    a));
                }
            }
        }
    }

    private String attribution(
            Map<?, ?> defect,
            Map<?, ?> impl,
            Set<String> mainKeys,
            String userId,
            Map<String, Map<?, ?>> cache,
            String fields) {
        String implKey = Objects.toString(impl.get("key"));
        if (linkedKeys(defect).contains(implKey)) return "DIRECT";
        Set<String> assignees = new LinkedHashSet<>();
        for (String main : mainKeys)
            for (String key : linkedKeys(issue(main, cache, fields))) {
                Map<?, ?> x = issue(key, cache, fields);
                if ("QA".equals(projectKey(x)) || mainKeys.contains(key)) continue;
                String assignee =
                        Objects.toString(
                                map(map(x.get("fields")).get("assignee")).get("accountId"), "");
                if (!assignee.isBlank()) assignees.add(assignee);
            }
        return assignees.size() == 1 && assignees.contains(userId) ? "INFERRED" : "UNRESOLVED";
    }

    private Map<?, ?> issue(String key, Map<String, Map<?, ?>> cache, String fields) {
        return cache.computeIfAbsent(
                key,
                k -> {
                    try {
                        Map<?, ?> x =
                                client.get()
                                        .uri(
                                                u ->
                                                        u.path("/rest/api/3/issue/" + k)
                                                                .queryParam("fields", fields)
                                                                .queryParam("expand", "changelog")
                                                                .build())
                                        .retrieve()
                                        .body(Map.class);
                        return x == null ? Map.of() : x;
                    } catch (Exception e) {
                        return Map.of();
                    }
                });
    }

    private List<Map<?, ?>> search(String jql, String fields) {
        List<Map<?, ?>> out = new ArrayList<>();
        String next = null;
        do {
            String token = next;
            Map<?, ?> body =
                    client.get()
                            .uri(
                                    u -> {
                                        var b =
                                                u.path("/rest/api/3/search/jql")
                                                        .queryParam("jql", jql)
                                                        .queryParam("maxResults", 100)
                                                        .queryParam("expand", "changelog")
                                                        .queryParam("fields", fields);
                                        if (token != null) b.queryParam("nextPageToken", token);
                                        return b.build();
                                    })
                            .retrieve()
                            .body(Map.class);
            if (body == null) break;
            for (Object x : list(body.get("issues"))) out.add(map(x));
            next = Objects.toString(body.get("nextPageToken"), null);
        } while (next != null && !next.isBlank());
        return out;
    }

    private List<String> storyPointFields() {
        try {
            List<?> rows = client.get().uri("/rest/api/3/field").retrieve().body(List.class);
            if (rows == null) return List.of("customfield_10016", "customfield_10048");
            return rows.stream()
                    .map(JiraConnector::map)
                    .filter(
                            f -> {
                                String name = Objects.toString(f.get("name"), "").toLowerCase();
                                String custom =
                                        Objects.toString(map(f.get("schema")).get("custom"), "");
                                return name.contains("story point")
                                        || custom.contains("story-points");
                            })
                    .map(f -> Objects.toString(f.get("id")))
                    .distinct()
                    .toList();
        } catch (Exception e) {
            return List.of("customfield_10016", "customfield_10048");
        }
    }

    private Set<String> doneStatusIds() {
        try {
            List<?> rows = client.get().uri("/rest/api/3/status").retrieve().body(List.class);
            if (rows == null) return Set.of();
            Set<String> out = new HashSet<>();
            for (Object x : rows) {
                Map<?, ?> m = map(x);
                if ("done"
                        .equalsIgnoreCase(
                                Objects.toString(map(m.get("statusCategory")).get("key"))))
                    out.add(Objects.toString(m.get("id")));
            }
            return out;
        } catch (Exception e) {
            return Set.of("5", "6", "10001");
        }
    }

    private static Instant completionForAssignee(
            Map<?, ?> issue, String id, Set<String> done, Instant from, Instant to) {
        List<Map<?, ?>> histories =
                list(map(issue.get("changelog")).get("histories")).stream()
                        .map(JiraConnector::map)
                        .sorted(
                                Comparator.comparing(
                                        h ->
                                                Optional.ofNullable(parse(h.get("created")))
                                                        .orElse(Instant.EPOCH)))
                        .toList();
        String assignee = initialAssignee(issue, histories);
        Instant result = null;
        for (Map<?, ?> h : histories) {
            Instant when = parse(h.get("created"));
            for (Object x : list(h.get("items"))) {
                Map<?, ?> item = map(x);
                if ("assignee".equalsIgnoreCase(Objects.toString(item.get("field"))))
                    assignee =
                            Objects.toString(
                                    item.get("to"),
                                    Objects.toString(item.get("tmpToAccountId"), ""));
                if ("status".equalsIgnoreCase(Objects.toString(item.get("field")))
                        && done.contains(Objects.toString(item.get("to")))
                        && id.equals(assignee)
                        && when != null
                        && inRange(when, from, to)) result = when;
            }
        }
        return result;
    }

    private static String initialAssignee(Map<?, ?> issue, List<Map<?, ?>> histories) {
        for (Map<?, ?> h : histories)
            for (Object x : list(h.get("items"))) {
                Map<?, ?> i = map(x);
                if ("assignee".equalsIgnoreCase(Objects.toString(i.get("field"))))
                    return Objects.toString(
                            i.get("from"), Objects.toString(i.get("tmpFromAccountId"), ""));
            }
        return Objects.toString(map(map(issue.get("fields")).get("assignee")).get("accountId"), "");
    }

    private static BigDecimal storyPoints(Map<?, ?> fields, List<String> keys) {
        for (String k : keys) {
            BigDecimal n = number(fields.get(k));
            if (n != null) return n;
        }
        return null;
    }

    static boolean countsForVelocity(Map<?, ?> fields) {
        return "done"
                .equalsIgnoreCase(Objects.toString(map(fields.get("resolution")).get("name"), ""));
    }

    private static Set<String> linkedKeys(Map<?, ?> issue) {
        Set<String> keys = new LinkedHashSet<>();
        Map<?, ?> f = map(issue.get("fields"));
        String parent = Objects.toString(map(f.get("parent")).get("key"), "");
        if (!parent.isBlank()) keys.add(parent);
        for (Object s : list(f.get("subtasks"))) {
            String k = Objects.toString(map(s).get("key"), "");
            if (!k.isBlank()) keys.add(k);
        }
        for (Object l : list(f.get("issuelinks"))) {
            Map<?, ?> link = map(l);
            for (String side : List.of("inwardIssue", "outwardIssue")) {
                String k = Objects.toString(map(link.get(side)).get("key"), "");
                if (!k.isBlank()) keys.add(k);
            }
        }
        return keys;
    }

    private static boolean isDefect(Map<?, ?> issue) {
        String type =
                Objects.toString(map(map(issue.get("fields")).get("issuetype")).get("name"), "")
                        .toLowerCase();
        return type.contains("bug") || type.contains("defect");
    }

    private static String projectKey(Map<?, ?> issue) {
        return Objects.toString(
                map(map(issue.get("fields")).get("project")).get("key"),
                Objects.toString(issue.get("key"), "").replaceAll("-.*", ""));
    }

    private static Map<String, Object> context(Map<?, ?> issue) {
        Map<?, ?> f = map(issue.get("fields"));
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("issue", Objects.toString(issue.get("key")));
        c.put("project", projectKey(issue));
        c.put("issue_type", Objects.toString(map(f.get("issuetype")).get("name"), ""));
        c.put("current_status", Objects.toString(map(f.get("status")).get("name"), ""));
        c.put("resolution", Objects.toString(map(f.get("resolution")).get("name"), ""));
        c.put("priority", Objects.toString(map(f.get("priority")).get("name"), ""));
        return c;
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    static void collectAuthored(
            List<EvidenceInput> out,
            Object container,
            String array,
            String metric,
            String id,
            String key,
            String title,
            String url,
            Instant from,
            Instant to,
            Map<String, Object> context) {
        for (Object o : list(map(container).get(array))) {
            Map<?, ?> m = map(o);
            if (!id.equals(Objects.toString(map(m.get("author")).get("accountId")))) continue;
            Instant when = parse(m.get("created"), m.get("updated"));
            if (when != null && inRange(when, from, to)) {
                Map<String, Object> a = new LinkedHashMap<>(context);
                a.put("text", clip(adfText(m.get("body"))));
                if ("worklogs".equals(metric)) {
                    a.put("time_spent", Objects.toString(m.get("timeSpent"), ""));
                    a.put("time_spent_seconds", m.get("timeSpentSeconds"));
                }
                out.add(
                        new EvidenceInput(
                                "jira_" + metric,
                                key + ":" + metric + ":" + m.get("id"),
                                when,
                                BigDecimal.ONE,
                                title,
                                url,
                                a));
            }
        }
    }

    static String adfText(Object o) {
        if (o == null) return "";
        if (o instanceof String s) return s;
        if (o instanceof Map<?, ?> m) {
            StringBuilder b = new StringBuilder();
            if ("text".equals(m.get("type"))) b.append(Objects.toString(m.get("text"), ""));
            for (Object c : list(m.get("content"))) {
                String x = adfText(c);
                if (!x.isBlank()) {
                    if (!b.isEmpty()) b.append(' ');
                    b.append(x);
                }
            }
            return b.toString();
        }
        if (o instanceof List<?> l)
            return l.stream()
                    .map(JiraConnector::adfText)
                    .filter(s -> !s.isBlank())
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");
        return Objects.toString(o, "");
    }

    static String clip(String s) {
        return s.length() > 1000 ? s.substring(0, 1000) + "…" : s;
    }

    static Map<?, ?> map(Object o) {
        return o instanceof Map<?, ?> m ? m : Map.of();
    }

    static List<?> list(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    static Instant parse(Object... xs) {
        for (Object x : xs)
            if (x != null) {
                String s = x.toString();
                try {
                    return Instant.parse(s);
                } catch (Exception ignored) {
                }
                try {
                    return java.time.OffsetDateTime.parse(
                                    s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
                            .toInstant();
                } catch (Exception ignored) {
                }
            }
        return null;
    }

    static boolean inRange(Instant x, Instant a, Instant b) {
        return !x.isBefore(a) && x.isBefore(b);
    }

    record Completed(Map<?, ?> issue, Instant when) {}
}

@Component
class ConfluenceConnector extends ReadOnlyHttpConnector {
    ConfluenceConnector(ConnectorsProperties properties) {
        super(
                properties.confluence().url(),
                properties.confluence().email(),
                properties.confluence().token());
    }

    public String key() {
        return "confluence";
    }

    void healthRequest() {
        client.get().uri("/rest/api/space?limit=1").retrieve().toBodilessEntity();
    }

    public List<IdentityCandidate> discoverUsers(String email) {
        // Atlassian Cloud no longer permits Confluence lookup by username/email. The shared
        // Atlassian accountId discovered through Jira is reused by IdentityDiscoveryService.
        return List.of();
    }

    public List<EvidenceInput> syncEvidence(String id, Instant from, Instant to) {
        var searchFrom = from.atZone(java.time.ZoneOffset.UTC).toLocalDate().minusDays(1);
        var searchTo = to.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(1);
        String cql =
                "type=page AND contributor=\""
                        + id.replace("\"", "")
                        + "\" AND lastModified >= \""
                        + searchFrom
                        + "\" AND lastModified < \""
                        + searchTo
                        + "\"";
        Map<?, ?> body =
                client.get()
                        .uri(
                                u ->
                                        u.path("/rest/api/content/search")
                                                .queryParam("cql", cql)
                                                .queryParam("limit", 100)
                                                .queryParam("expand", "version")
                                                .build())
                        .retrieve()
                        .body(Map.class);
        List<EvidenceInput> out = new ArrayList<>();
        if (body != null && body.get("results") instanceof List<?> rows)
            for (Object x : rows) {
                Map<?, ?> m = (Map<?, ?>) x;
                Map<?, ?> links = m.get("_links") instanceof Map<?, ?> l ? l : Map.of();
                Map<?, ?> version = m.get("version") instanceof Map<?, ?> v ? v : Map.of();
                Instant occurredAt = instant(version.get("when"));
                if (occurredAt.isBefore(from) || !occurredAt.isBefore(to)) continue;
                out.add(
                        new EvidenceInput(
                                "documentation_updates",
                                Objects.toString(m.get("id")),
                                occurredAt,
                                BigDecimal.ONE,
                                Objects.toString(m.get("title"), "Confluence page"),
                                baseUrl + Objects.toString(links.get("webui"), ""),
                                Map.of("version", Objects.toString(version.get("number"), ""))));
            }
        return out;
    }
}
