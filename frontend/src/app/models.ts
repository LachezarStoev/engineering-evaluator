export type Tab = 'report' | 'admin' | 'integrations';
export type Decision = 'PASS' | 'FAIL';

export interface Employee {
  id: string;
  canonicalEmail: string;
  displayName: string;
  team?: string;
  currentLevelCode?: string;
  targetLevelCode?: string;
  trackCode: string;
}

export interface EngineeringTrack {
  code: string;
  name: string;
  description?: string;
  iconKey: string;
  ordinalValue: number;
  version: number;
  status: string;
}

export interface EngineeringLevel {
  code: string;
  name: string;
  version: number;
  status: string;
  ordinalValue: number;
}

export interface Criterion {
  code: string;
  name: string;
  sourceTool: string;
  metricKey: string;
  aggregation: string;
  operator: string;
  thresholdValue: number | null;
  thresholdMaxValue?: number | null;
  levelCode: string;
  scope: 'COMMON' | 'TRACK' | 'TEAM';
  trackCode?: string;
  teamKey?: string;
  prorationPolicy: 'ALLOWED' | 'PROGRESS_ONLY' | 'FORBIDDEN';
  mandatory: boolean;
  rubric?: string;
  visualization?: string;
  evaluationType?: string;
  periodType?: string;
  version: number;
  status: string;
}

export interface CriterionResult {
  id: string;
  criterionName?: string;
  resultStatus: string;
  measuredValue: number | null;
  thresholdValue: number | null;
  periodTargetValue: number | null;
  thresholdMaxValue?: number | null;
  periodTargetMaxValue?: number | null;
  formula: string;
  coverage: string;
  cadence?: string;
  managerNote?: string;
}

export interface Evaluation {
  id: string;
  period: string;
  periodTimezone?: string;
  evaluationMode: 'SNAPSHOT' | 'ASSESSMENT';
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
  exactMatch: boolean;
  matchType: 'EXACT_EMAIL' | 'UNIQUE_NAME' | 'REUSED_ATLASSIAN' | 'UNVERIFIED';
  confidence: number;
}

export interface IdentityDiscovery {
  tool: string;
  health: { healthy: boolean; message: string };
  candidates: IdentityCandidate[];
  verified: boolean;
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
  levelFits: LevelFit[];
}

export interface LevelFit {
  code: string;
  name: string;
  ordinal: number;
  score: number;
  passedAutomaticCriteria: number;
  automaticCriteria: number;
  humanReviewCriteria: number;
  incompleteCriteria: number;
  recommended: boolean;
}

export interface DataReadiness {
  tool: string;
  connectionStatus: string;
  identityStatus: string;
  dataStatus: string;
  evidenceCount: number;
  message: string;
}

export interface CompetencyExpectation {
  competencyKey: string;
  levelCode: string;
  trackCode?: string;
  expectation: string;
  rubricText?: string;
  version: number;
}

export interface FrameworkDefinition {
  version: number;
  tracks: EngineeringTrack[];
  levels: EngineeringLevel[];
  criteria: Criterion[];
  competencies: CompetencyExpectation[];
}
