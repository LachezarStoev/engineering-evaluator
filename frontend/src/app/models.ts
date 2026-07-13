export type Tab = 'report' | 'admin' | 'integrations';
export type Decision = 'PASS' | 'FAIL';

export interface Employee {
  id: string;
  canonicalEmail: string;
  displayName: string;
  team?: string;
  currentLevelCode?: string;
  targetLevelCode?: string;
}

export interface EngineeringLevel {
  code: string;
  name: string;
  version: number;
  status: string;
}

export interface Criterion {
  code: string;
  name: string;
  sourceTool: string;
  metricKey: string;
  aggregation: string;
  operator: string;
  thresholdValue: number | null;
  levelCode: string;
  version: number;
  status: string;
}

export interface CriterionResult {
  id: string;
  resultStatus: string;
  measuredValue: number | null;
  thresholdValue: number | null;
  formula: string;
  coverage: string;
  managerNote?: string;
}

export interface Evaluation {
  id: string;
  period: string;
  periodTimezone?: string;
  levelCode: string;
  status: string;
  finalized: boolean;
  results: CriterionResult[];
}

export interface Evidence {
  id: string;
  toolKey: string;
  metricKey: string;
  occurredAt: string;
  numericValue: number;
  title: string;
  url?: string;
  attributesJson: string;
}

export interface ConnectorConfig {
  displayName: string;
  baseUrl?: string;
  lastSyncAt?: string;
  healthStatus: string;
}

export interface IntegrationOnboarding {
  key: string;
  name: string;
  serviceUrl?: string;
  tokenUrl?: string;
  environmentVariables: string[];
  instructions: string;
  configured: boolean;
}

export interface IdentityCandidate {
  externalUserId: string;
  username: string;
  email?: string;
}

export interface IdentityDiscovery {
  tool: string;
  health: { healthy: boolean; message: string };
  candidates: IdentityCandidate[];
}

export interface AiSummary {
  text: string;
}
export interface SyncResponse {
  evidenceProcessed: number;
}

export interface PreparedReport {
  evaluation: Evaluation;
  identities: { toolKey: string; username?: string; matchedEmail?: string }[];
  evidenceProcessed: number;
}
