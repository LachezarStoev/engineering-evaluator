import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import type {
  AiSummary,
  ConnectorConfig,
  Criterion,
  DataReadiness,
  Employee,
  EngineeringLevel,
  EngineeringTrack,
  FrameworkDefinition,
  Evaluation,
  Evidence,
  IdentityCandidate,
  IdentityDiscovery,
  IntegrationOnboarding,
  PreparedReport,
  SyncResponse,
} from './models';

@Injectable({ providedIn: 'root' })
export class EvaluatorApi {
  private readonly http = inject(HttpClient);

  employee(email: string) {
    return this.http.get<Employee>(`/api/v1/employees/by-email/${encodeURIComponent(email)}`);
  }
  resolveEmployee(email: string) {
    return this.http.post<Employee>(`/api/v1/employees/resolve/${encodeURIComponent(email)}`, {});
  }
  levels() {
    return this.http.get<EngineeringLevel[]>('/api/v1/levels');
  }
  tracks() {
    return this.http.get<EngineeringTrack[]>('/api/v1/tracks');
  }
  framework(version = 2) {
    return this.http.get<FrameworkDefinition>(`/api/v1/frameworks/${version}`);
  }
  criteria() {
    return this.http.get<Criterion[]>('/api/v1/criteria');
  }
  evaluations(email: string) {
    return this.http.get<Evaluation[]>(
      `/api/v1/employees/${encodeURIComponent(email)}/evaluations`,
    );
  }
  evidence(evaluationId: string) {
    return this.http.get<Evidence[]>(`/api/v1/evaluations/${evaluationId}/evidence`);
  }
  readiness(evaluationId: string) {
    return this.http.get<DataReadiness[]>(`/api/v1/evaluations/${evaluationId}/readiness`);
  }
  aiSummary(evaluationId: string) {
    return this.http.get<AiSummary>(`/api/v1/evaluations/${evaluationId}/ai-summary`);
  }
  createEmployee(request: object) {
    return this.http.post<Employee>('/api/v1/employees', request);
  }
  createLevel(request: object) {
    return this.http.post('/api/v1/levels', request);
  }
  createTrack(request: object) {
    return this.http.post<EngineeringTrack>('/api/v1/tracks', request);
  }
  assignTrack(email: string, trackCode: string) {
    return this.http.patch<Employee>(`/api/v1/employees/${encodeURIComponent(email)}/track`, {
      trackCode,
    });
  }
  createCriterion(request: object) {
    return this.http.post('/api/v1/criteria', request);
  }
  calculate(request: object) {
    return this.http.post<Evaluation>('/api/v1/evaluations/recalculate', request);
  }
  prepareReport(email: string, request: object) {
    return this.http.post<PreparedReport>(
      `/api/v1/employees/${encodeURIComponent(email)}/prepare-report`,
      request,
    );
  }
  decide(resultId: string, decision: string, note: string) {
    return this.http.post(`/api/v1/criterion-results/${resultId}/decision`, { decision, note });
  }
  dispute(evaluationId: string, resultId: string, message: string) {
    return this.http.post(`/api/v1/evaluations/${evaluationId}/disputes`, {
      criterionResultId: resultId,
      message,
    });
  }
  integrationHealth() {
    return this.http.get<ConnectorConfig[]>('/api/v1/integrations/health');
  }
  onboarding() {
    return this.http.get<IntegrationOnboarding[]>('/api/v1/integrations/onboarding');
  }
  sync(from: Date, to: Date) {
    const query = new URLSearchParams({ from: from.toISOString(), to: to.toISOString() });
    return this.http.post<SyncResponse>(`/api/v1/integrations/sync?${query}`, {});
  }
  discoverIdentities(email: string) {
    return this.http.post<IdentityDiscovery[]>(
      `/api/v1/employees/${encodeURIComponent(email)}/discover-identities`,
      {},
    );
  }
  confirmIdentity(email: string, tool: string, candidate: IdentityCandidate) {
    return this.http.post(`/api/v1/employees/${encodeURIComponent(email)}/identities`, {
      toolKey: tool,
      externalUserId: candidate.externalUserId,
      username: candidate.username,
      matchedEmail: candidate.email,
    });
  }
}
