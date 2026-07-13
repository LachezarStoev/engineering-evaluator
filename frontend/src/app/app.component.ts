import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EvaluatorApi } from './evaluator-api.service';
import type {
  ConnectorConfig,
  Criterion,
  CriterionResult,
  CompetencyExpectation,
  DataReadiness,
  Decision,
  Employee,
  EngineeringLevel,
  EngineeringTrack,
  Evaluation,
  Evidence,
  IdentityCandidate,
  IdentityDiscovery,
  IntegrationOnboarding,
  LevelFit,
  Tab,
} from './models';

interface VisualMetric {
  label: string;
  value: string;
  detail: string;
  tone: 'positive' | 'neutral' | 'warning';
}

interface ActivityBucket {
  label: string;
  value: number;
  height: number;
}

interface HeatmapDay {
  date: string;
  label: string;
  value: number;
  intensity: number;
}

interface SourceShare {
  key: string;
  label: string;
  value: number;
  percentage: number;
}

interface ComparisonRow {
  label: string;
  current: number;
  previous: number;
  delta: number;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: ` <header>
      <div class="header-inner">
        <div class="brand">
          <span class="brand-mark">CM</span>
          <div>
            <small>ENGINEERING INTELLIGENCE · FRAMEWORK V2</small>
            <h1>Career Matrix</h1>
          </div>
        </div>
        <nav>
          <button [class.active]="tab === 'report'" (click)="tab = 'report'">Report</button
          ><button [class.active]="tab === 'admin'" (click)="openAdmin()">Administration</button
          ><button
            [class.active]="tab === 'integrations'"
            (click)="tab = 'integrations'; loadHealth(); loadOnboarding()"
          >
            Integrations
          </button>
        </nav>
      </div>
    </header>
    <main>
      <section *ngIf="notice" class="notice">{{ notice }}</section>
      <section *ngIf="error" class="error">{{ error }}</section>

      <ng-container *ngIf="tab === 'report'">
        <section class="search">
          <label for="employee-email">Employee email</label>
          <div>
            <input
              id="employee-email"
              [(ngModel)]="email"
              type="email"
              placeholder="developer@company.com"
            /><button (click)="load()">Open report</button>
          </div>
          <p>Every result includes its formula, coverage, source evidence, and human decisions.</p>
        </section>
        <section *ngIf="hasPendingIdentityConfirmation()" class="panel identity-confirmation">
          <div class="identity-confirmation-heading">
            <div>
              <small>STEP 2 · VERIFY EMPLOYEE</small>
              <h2>Confirm the matching company profiles</h2>
              <p>
                We found possible accounts for <strong>{{ employee?.canonicalEmail }}</strong
                >. Confirm the correct profiles before viewing or calculating the report.
              </p>
            </div>
            <span class="status status-warning">CONFIRMATION REQUIRED</span>
          </div>
          <article *ngFor="let tool of unresolvedDiscoveries()" class="identity-source">
            <b>{{ tool.tool | uppercase }}</b>
            <span *ngIf="!tool.candidates.length" class="identity-empty">
              No candidate was found. This source will be shown as incomplete.
            </span>
            <div *ngFor="let candidate of tool.candidates" class="candidate">
              <span class="candidate-person">
                <strong>{{ candidate.username }}</strong>
                <small>{{ candidate.email || candidate.externalUserId }}</small>
              </span>
              <span class="status">{{ identityMatchLabel(candidate) }}</span>
              <button (click)="confirmForEmployee(tool.tool, candidate)">
                Confirm {{ candidate.username }}
              </button>
            </div>
          </article>
        </section>
        <section *ngIf="employee && !hasPendingIdentityConfirmation()" class="profile">
          <div>
            <span class="avatar">{{ employee.displayName[0] }}</span>
            <div>
              <h2>{{ employee.displayName }}</h2>
              <p>{{ employee.canonicalEmail }} · {{ employee.team || 'No team' }}</p>
            </div>
          </div>
          <div class="profile-context">
            <span class="track-badge">{{ trackLabel(employee.trackCode) }}</span>
            <strong>{{ profileLevelLabel() }}</strong>
          </div>
        </section>
        <section *ngIf="readiness.length" class="panel readiness-panel">
          <div class="panel-title">
            <div>
              <small>DATA READINESS</small>
              <h2>Source coverage for this report</h2>
            </div>
          </div>
          <article *ngFor="let source of readiness" class="readiness-row">
            <strong>{{ source.tool | uppercase }}</strong>
            <span
              class="status"
              [class.status-ok]="source.connectionStatus === 'CONNECTED'"
              [attr.data-state]="source.connectionStatus"
              >{{ displayLabel(source.connectionStatus) }}</span
            >
            <span
              class="status"
              [class.status-ok]="source.identityStatus === 'IDENTITY_VERIFIED'"
              [attr.data-state]="source.identityStatus"
              >{{ displayLabel(source.identityStatus) }}</span
            >
            <span
              class="status"
              [class.status-ok]="source.dataStatus === 'SYNCED'"
              [attr.data-state]="source.dataStatus"
              >{{ displayLabel(source.dataStatus) }}</span
            >
            <small class="readiness-detail">
              <b>{{ source.evidenceCount }} evidence records</b>
              <span>{{ source.message }}</span>
            </small>
          </article>
        </section>
        <section *ngFor="let evaluation of evaluations" class="panel evaluation-panel">
          <div class="panel-title">
            <div>
              <small
                >{{ displayPeriod(evaluation.period) }} ·
                {{ evaluation.periodTimezone || 'UTC' }}</small
              >
              <h2>Evidence-based level assessment</h2>
              <span class="status status-neutral"
                >Assessment criteria: {{ displayLevelCode(evaluation.levelCode) }}</span
              >
            </div>
            <div class="actions">
              <a [href]="'/api/v1/evaluations/' + evaluation.id + '/export.pdf'">PDF</a
              ><a [href]="'/api/v1/evaluations/' + evaluation.id + '/export.xlsx'">Excel</a
              ><button (click)="summary(evaluation.id)">AI summary</button
              ><span class="status status-neutral">{{ displayLabel(evaluation.status) }}</span>
            </div>
          </div>
          <section class="report-hero">
            <div class="report-hero-copy">
              <small>ENGINEERING SIGNAL OVERVIEW</small>
              <h3>{{ employee?.displayName }} · {{ trackLabel(employee?.trackCode) }}</h3>
              <p>
                A traceable view of delivery, quality, collaboration and growth. Every score below
                remains linked to its source evidence and review status.
              </p>
              <div class="source-strip" *ngIf="sourceShares(evaluation).length">
                <span
                  *ngFor="let source of sourceShares(evaluation)"
                  [style.flex-grow]="source.percentage"
                  [attr.title]="source.label + ': ' + source.value + ' records'"
                >
                  <i></i>{{ source.label }} {{ source.percentage }}%
                </span>
              </div>
            </div>
            <div *ngIf="highlightedFit() as fit" class="score-orbit-wrap">
              <div class="score-orbit" [style.background]="scoreGradient(fit.score)">
                <span
                  ><b>{{ fit.score }}%</b><small>evidence fit</small></span
                >
              </div>
              <strong>{{ displayLevelCode(fit.code) }}</strong>
              <small>{{
                fit.recommended ? 'Fully supported level' : 'Provisional closest fit'
              }}</small>
            </div>
          </section>

          <section class="executive-metrics" aria-label="Assessment overview">
            <article
              *ngFor="let metric of reportMetrics(evaluation)"
              class="executive-metric"
              [attr.data-tone]="metric.tone"
            >
              <small>{{ metric.label }}</small>
              <strong>{{ metric.value }}</strong>
              <span>{{ metric.detail }}</span>
            </article>
          </section>

          <section class="signal-grid">
            <article class="visual-panel signal-panel">
              <div class="visual-heading">
                <div>
                  <small>{{ employee?.trackCode || 'GENERAL' }} SIGNALS</small>
                  <h3>Work visible in this period</h3>
                </div>
                <span>Observed, not inferred</span>
              </div>
              <div class="role-signals">
                <div *ngFor="let signal of roleSignals(evaluation)">
                  <span>{{ signal.label }}</span>
                  <b>{{ signal.value }}</b>
                  <i [style.width.%]="signal.value ? 100 : 0"></i>
                </div>
              </div>
            </article>
            <article class="visual-panel trend-panel">
              <div class="visual-heading">
                <div>
                  <small>ACTIVITY RHYTHM</small>
                  <h3>Evidence over time</h3>
                </div>
                <span>{{ displayPeriod(evaluation.period) }}</span>
              </div>
              <div class="activity-chart" aria-label="Evidence activity over time">
                <div
                  *ngFor="let bucket of activityTrend(evaluation)"
                  [attr.title]="bucket.label + ': ' + bucket.value + ' records'"
                >
                  <b>{{ bucket.value }}</b>
                  <i [style.height.%]="bucket.height"></i>
                  <small>{{ bucket.label }}</small>
                </div>
              </div>
            </article>
          </section>

          <section class="signal-grid secondary-visuals">
            <article class="visual-panel heatmap-panel">
              <div class="visual-heading">
                <div>
                  <small>CONTRIBUTION MAP</small>
                  <h3>Daily engineering footprint</h3>
                </div>
                <span>Up to the latest 12 weeks</span>
              </div>
              <div class="activity-heatmap" aria-label="Daily evidence heatmap">
                <span
                  *ngFor="let day of activityHeatmap(evaluation)"
                  [attr.data-intensity]="day.intensity"
                  [attr.title]="day.label + ': ' + day.value + ' records'"
                  [attr.aria-label]="day.label + ', ' + day.value + ' evidence records'"
                ></span>
              </div>
              <div class="heatmap-legend">
                <span>Less</span><i></i><i></i><i></i><i></i><span>More</span>
              </div>
            </article>
            <article class="visual-panel comparison-panel">
              <div class="visual-heading">
                <div>
                  <small>PERIOD COMPARISON</small>
                  <h3>Change from previous report</h3>
                </div>
                <span *ngIf="previousEvaluation(evaluation) as previous">
                  {{ displayPeriod(previous.period) }}
                </span>
              </div>
              <div
                *ngIf="comparisonRows(evaluation).length; else firstBaseline"
                class="comparison-list"
              >
                <div *ngFor="let row of comparisonRows(evaluation)">
                  <span>{{ row.label }}</span>
                  <small>{{ row.previous }} → {{ row.current }}</small>
                  <b [class.negative]="row.delta < 0">
                    {{ row.delta > 0 ? '+' : '' }}{{ row.delta }}
                  </b>
                </div>
              </div>
              <ng-template #firstBaseline>
                <div class="baseline-empty">
                  <b>First comparable baseline</b>
                  <span
                    >The next report will show metric-by-metric movement without mixing
                    periods.</span
                  >
                </div>
              </ng-template>
            </article>
          </section>
          <section *ngIf="levelFits.length" class="level-fit-panel">
            <div class="level-fit-heading">
              <div>
                <small>DETERMINISTIC LEVEL PROXIMITY</small>
                <h3>{{ recommendedLevelLabel() }}</h3>
              </div>
              <span
                >Score = average proportional progress across automatic criteria. Missing data
                contributes 0. This is not an assigned level.</span
              >
            </div>
            <div class="level-track">
              <article
                *ngFor="let fit of levelFits"
                class="level-fit-card"
                [class.recommended]="fit.recommended"
                [class.closest]="isClosestFit(fit)"
                [class.assessed]="fit.code === evaluation.levelCode"
              >
                <div class="level-fit-title">
                  <span>{{ fit.ordinal }}</span>
                  <div>
                    <strong>{{ displayLevelCode(fit.code) }}</strong>
                    <small>{{ fit.name }}</small>
                  </div>
                  <b>{{ levelFitValue(fit) }}</b>
                </div>
                <div class="level-progress" [class.incomplete]="!hasMeasurableLevelFit(fit)">
                  <i [style.width.%]="fit.score"></i>
                </div>
                <div class="level-fit-facts">
                  <span
                    ><b>{{ fit.passedAutomaticCriteria }}/{{ fit.automaticCriteria }}</b> on
                    pace</span
                  >
                  <span *ngIf="fit.humanReviewCriteria"
                    ><b>{{ fit.humanReviewCriteria }}</b> human review</span
                  >
                  <span *ngIf="fit.incompleteCriteria"
                    ><b>{{ fit.incompleteCriteria }}</b> incomplete</span
                  >
                </div>
                <div class="level-fit-badges">
                  <span *ngIf="fit.code === evaluation.levelCode" class="assessment-badge"
                    >Assessment criteria</span
                  >
                  <span *ngIf="isClosestFit(fit) && !fit.recommended" class="closest-badge"
                    >Closest evidence fit</span
                  >
                </div>
                <span *ngIf="fit.recommended" class="level-recommendation">Fully supported</span>
              </article>
            </div>
          </section>
          <section *ngIf="expectationsFor(evaluation.levelCode).length" class="competency-panel">
            <div class="competency-heading">
              <div>
                <small>COMPETENCY BASELINE</small>
                <h3>{{ displayLevelCode(evaluation.levelCode) }} expectations</h3>
              </div>
              <span>Qualitative evidence · confirmed during manager review</span>
            </div>
            <div class="competency-grid">
              <article *ngFor="let competency of expectationsFor(evaluation.levelCode)">
                <span class="competency-icon">{{ competency.competencyKey[0] }}</span>
                <div>
                  <small>{{ displayLabel(competency.competencyKey) }}</small>
                  <strong>{{ competency.expectation }}</strong>
                </div>
              </article>
            </div>
          </section>
          <div class="result-overview">
            <span *ngFor="let item of resultSummary(evaluation)">
              <b>{{ item.value }}</b
              ><small>{{ item.label }}</small>
            </span>
          </div>
          <div *ngIf="hasResultStatus(evaluation, 'NOT_COMPARABLE')" class="comparison-note">
            <strong>Proportional progress</strong>
            <span>
              For an incomplete month or quarter, measured results are compared with the
              proportional expected value for the selected calendar days. Formal pass/fail remains
              available only for a complete criterion period.
            </span>
          </div>
          <article *ngFor="let result of evaluation.results" class="result-row">
            <span
              class="dot"
              [class.pass]="result.resultStatus === 'PASS'"
              [class.fail]="result.resultStatus === 'FAIL'"
              [class.no-data]="result.resultStatus === 'NO_DATA'"
            ></span>
            <div class="metric">
              <strong class="criterion-title">{{ result.criterionName || 'Criterion' }}</strong>
              <code class="formula">{{ prettyFormula(result.formula) }}</code
              ><small class="coverage"
                >Coverage · {{ displayLabel(result.coverage) }}
                <span *ngIf="result.managerNote">· Manager: {{ result.managerNote }}</span></small
              >
              <div class="inline-actions" *ngIf="!evaluation.finalized">
                <ng-container *ngIf="result.resultStatus !== 'NOT_COMPARABLE'">
                  <button (click)="decide(result.id, 'PASS')">Approve</button>
                  <button (click)="decide(result.id, 'FAIL')">Reject</button>
                </ng-container>
                <button (click)="dispute(evaluation.id, result.id)">Dispute evidence</button>
              </div>
            </div>
            <div class="value">
              <strong>{{ criterionValue(result) }}</strong>
              <small>{{ criterionContext(result) }}</small>
              <span class="result-state" [attr.data-state]="result.resultStatus">{{
                resultStatusLabel(result)
              }}</span>
            </div>
          </article>
          <div *ngIf="aiSummary[evaluation.id]" class="summary">
            <strong>Advisory summary</strong>
            <p>{{ aiSummary[evaluation.id] }}</p>
          </div>
          <div class="evidence-actions">
            <button (click)="toggleEvidence(evaluation.id)">
              {{
                evidenceByEvaluation[evaluation.id]
                  ? evidenceExpanded[evaluation.id]
                    ? 'Hide activity details'
                    : 'Show activity details'
                  : 'Load activity details'
              }}
            </button>
          </div>
          <section *ngIf="evidenceExpanded[evaluation.id]" class="evidence-list">
            <div class="metric-cards">
              <button
                *ngFor="let metric of metricSummary(evidenceByEvaluation[evaluation.id] || [])"
                class="metric-card"
                [class.active]="evidenceMetricFilter === metric.key"
                [attr.aria-pressed]="evidenceMetricFilter === metric.key"
                (click)="filterEvidenceByMetric(metric.key)"
              >
                <small>{{ displayLabel(metric.key) }}</small>
                <b>{{ metric.value }}</b>
                <span>{{
                  evidenceMetricFilter === metric.key ? 'Showing activity' : 'View activity'
                }}</span>
              </button>
            </div>
            <div
              *ngIf="dataQualitySummary(evidenceByEvaluation[evaluation.id] || []).length"
              class="data-quality"
            >
              <strong>Data quality</strong>
              <button
                *ngFor="let metric of dataQualitySummary(evidenceByEvaluation[evaluation.id] || [])"
                class="data-quality-filter"
                [class.active]="evidenceMetricFilter === metric.key"
                (click)="filterEvidenceByMetric(metric.key)"
              >
                {{ displayLabel(metric.key) }}: <b>{{ metric.value }}</b>
              </button>
            </div>
            <div class="activity-toolbar">
              <label
                >Source<select [(ngModel)]="evidenceToolFilter">
                  <option value="ALL">All sources</option>
                  <option value="jira">Jira</option>
                  <option value="gitlab">GitLab</option>
                  <option value="confluence">Confluence</option>
                </select></label
              ><label
                >Activity<select [(ngModel)]="evidenceMetricFilter">
                  <option value="ALL">All activity</option>
                  <option
                    *ngFor="
                      let metric of evidenceMetrics(evidenceByEvaluation[evaluation.id] || [])
                    "
                    [value]="metric"
                  >
                    {{ displayLabel(metric) }}
                  </option>
                </select></label
              ><span
                >{{ filteredEvidence(evidenceByEvaluation[evaluation.id] || []).length }} visible
                records
                <button
                  *ngIf="evidenceMetricFilter !== 'ALL' || evidenceToolFilter !== 'ALL'"
                  class="clear-filter"
                  (click)="clearEvidenceFilters()"
                >
                  Clear filters
                </button></span
              >
            </div>
            <details
              *ngFor="let item of filteredEvidence(evidenceByEvaluation[evaluation.id] || [])"
              class="evidence-row"
            >
              <summary>
                <div class="metric">
                  <div class="evidence-meta">
                    <time>{{ item.occurredAt | date: 'medium' }}</time>
                    <span class="tool-badge">{{ item.toolKey | uppercase }}</span>
                    <span>{{ displayLabel(item.metricKey) }}</span>
                  </div>
                  <strong class="evidence-title">{{ item.title }}</strong>
                  <p *ngIf="detail(item) as d" class="evidence-preview">{{ d }}</p>
                </div>
                <span class="details-label">Details</span>
              </summary>
              <div class="evidence-expanded">
                <p *ngIf="detail(item) as d" class="evidence-detail">{{ d }}</p>
                <div class="attribute-grid">
                  <span *ngFor="let pair of attributes(item)">
                    <b>{{ pair[0] }}:</b> {{ pair[1] }}
                  </span>
                </div>
                <div class="evidence-footer">
                  <a *ngIf="item.url" [href]="item.url" target="_blank" rel="noopener"
                    >Open source ↗</a
                  >
                  <span *ngIf="item.numericValue !== 1" class="evidence-value"
                    >Value: <b>{{ item.numericValue }}</b></span
                  >
                </div>
              </div>
            </details>
            <div
              *ngIf="filteredEvidence(evidenceByEvaluation[evaluation.id] || []).length === 0"
              class="empty"
            >
              No source activity in this period.
            </div>
          </section>
        </section>
        <section *ngIf="employee" class="empty">
          <h2>{{ evaluations.length ? 'Assess another period' : 'Create level assessment' }}</h2>
          <p>
            Select any period. The system synchronizes the evidence, calculates proportional
            progress for incomplete months or quarters, and recommends the closest evidence-based
            level across the full career matrix.
          </p>
          <div class="search-row">
            <label>From<input type="date" [(ngModel)]="evaluationForm.from" /></label
            ><label>To, inclusive<input type="date" [(ngModel)]="evaluationForm.to" /></label
            ><button (click)="prepareReport()">Run level assessment</button>
          </div>
          <p class="mode-help">
            A complete criterion period produces formal pass/fail results. A partial period shows
            transparent pace against a proportional expected value and still requires manager
            confirmation.
          </p>
        </section>
      </ng-container>

      <ng-container *ngIf="tab === 'admin'">
        <section class="info-banner">
          Framework v2 contains seven engineering levels and reusable tracks. Common rules apply to
          every engineer; Backend, Frontend and future tracks can layer versioned criteria without
          changing the calculation engine.
        </section>
        <section class="track-showcase">
          <article
            *ngFor="let track of activeTracks()"
            [class.draft-track]="track.status !== 'PUBLISHED'"
          >
            <span class="track-symbol">{{ track.code.slice(0, 2) }}</span>
            <div>
              <small>{{ track.status }} · VERSION {{ track.version }}</small>
              <h3>{{ track.name }}</h3>
              <p>{{ track.description }}</p>
            </div>
          </article>
        </section>
        <section class="panel">
          <div class="panel-title">
            <div>
              <small>CURRENT CONFIGURATION</small>
              <h2>Published career matrix</h2>
            </div>
            <span class="status">{{ levels.length }} LEVELS · {{ criteria.length }} CRITERIA</span>
          </div>
          <article *ngFor="let level of levels" class="level-row">
            <div class="metric">
              <strong>{{ level.code }} · {{ level.name }}</strong
              ><small>Version {{ level.version }} · {{ level.status }}</small>
              <div class="criteria-grid">
                <div *ngFor="let criterion of criteriaFor(level.code)" class="criterion-card">
                  <div class="criterion-card-tags">
                    <span class="tool-badge">{{ criterion.sourceTool | uppercase }}</span>
                    <span class="scope-badge"
                      >{{ criterion.scope
                      }}{{ criterion.trackCode ? ' · ' + criterion.trackCode : '' }}</span
                    >
                  </div>
                  <b>{{ criterion.name }}</b>
                  <code
                    >{{ criterion.aggregation }}({{ criterion.metricKey }})
                    {{ prettyOperator(criterion.operator) }} {{ criterion.thresholdValue
                    }}<ng-container *ngIf="criterion.operator === 'BETWEEN'"
                      >–{{ criterion.thresholdMaxValue }}</ng-container
                    ></code
                  >
                </div>
              </div>
            </div>
          </article>
        </section>
        <details class="admin-group">
          <summary>
            <span><b>People onboarding</b><small>Organization administrators only</small></span>
            <span class="status">ADVANCED</span>
          </summary>
          <section class="panel admin-form">
            <h2>Add employee manually</h2>
            <p class="setup-note">
              Use only when an employee cannot be provisioned from SSO or the company directory.
              Source-system identities are still discovered automatically by corporate email.
            </p>
            <div class="form-grid">
              <label>Email<input [(ngModel)]="employeeForm.email" /></label
              ><label>Name<input [(ngModel)]="employeeForm.displayName" /></label
              ><label>Team<input [(ngModel)]="employeeForm.team" /></label
              ><label>Manager email<input [(ngModel)]="employeeForm.managerEmail" /></label
              ><label>Current level<input [(ngModel)]="employeeForm.currentLevelCode" /></label>
              <label
                >Engineering track<select [(ngModel)]="employeeForm.trackCode">
                  <option *ngFor="let track of publishedTracks()" [value]="track.code">
                    {{ track.name }}
                  </option>
                </select></label
              >
            </div>
            <button (click)="createEmployee()">Create employee</button>
            <div class="assignment-box">
              <h3>Assign or change an existing employee track</h3>
              <div class="form-grid">
                <label>Employee email<input [(ngModel)]="trackAssignmentForm.email" /></label
                ><label
                  >Track<select [(ngModel)]="trackAssignmentForm.trackCode">
                    <option *ngFor="let track of publishedTracks()" [value]="track.code">
                      {{ track.name }}
                    </option>
                  </select></label
                >
              </div>
              <button (click)="assignTrackForAdmin()">Assign track</button>
            </div>
          </section>
        </details>

        <details class="admin-group">
          <summary>
            <span
              ><b>Matrix versioning</b
              ><small>Evaluator administrators only · changes are auditable</small></span
            >
            <span class="status">ADVANCED</span>
          </summary>
          <div class="grid admin-content">
            <section class="panel">
              <h2>Create an engineering track</h2>
              <p class="setup-note">
                Tracks are generic. Create Data, QA Automation, Platform or another discipline
                without adding application code.
              </p>
              <label>Code<input [(ngModel)]="trackForm.code" placeholder="DATA" /></label
              ><label
                >Name<input [(ngModel)]="trackForm.name" placeholder="Data Engineering" /></label
              ><label>Description<input [(ngModel)]="trackForm.description" /></label
              ><label>Icon key<input [(ngModel)]="trackForm.iconKey" /></label
              ><label>Order<input type="number" [(ngModel)]="trackForm.ordinal" /></label
              ><button (click)="createTrack()">Create track draft</button>
            </section>
            <section class="panel">
              <h2>Publish a level version</h2>
              <label>Code<input [(ngModel)]="levelForm.code" /></label
              ><label>Name<input [(ngModel)]="levelForm.name" /></label
              ><label>Order<input type="number" [(ngModel)]="levelForm.ordinal" /></label
              ><label>Version<input type="number" [(ngModel)]="levelForm.version" /></label
              ><button (click)="createLevel()">Publish level</button>
            </section>
            <section class="panel">
              <h2>Publish a criterion version</h2>
              <label>Code<input [(ngModel)]="criterionForm.code" /></label
              ><label>Name<input [(ngModel)]="criterionForm.name" /></label
              ><label
                >Evidence source<input
                  list="evidence-sources"
                  [(ngModel)]="criterionForm.sourceTool"
                />
                <datalist id="evidence-sources">
                  <option value="gitlab"></option>
                  <option value="jira"></option>
                  <option value="confluence"></option>
                  <option value="human"></option>
                  <option value="sonarqube"></option>
                  <option value="sentry"></option>
                  <option value="lighthouse"></option>
                </datalist> </label
              ><label>Metric key<input [(ngModel)]="criterionForm.metricKey" /></label
              ><label
                >Aggregation<select [(ngModel)]="criterionForm.aggregation">
                  <option>SUM</option>
                  <option>COUNT</option>
                  <option>RATIO</option>
                  <option>AVERAGE</option>
                </select></label
              ><label *ngIf="criterionForm.aggregation === 'RATIO'"
                >Denominator metric<input [(ngModel)]="criterionForm.denominatorMetricKey" /></label
              ><label
                >Cadence<select [(ngModel)]="criterionForm.periodType">
                  <option>MONTH</option>
                  <option>QUARTER</option>
                  <option>CUSTOM</option>
                </select></label
              ><label>Minimum coverage<input [(ngModel)]="criterionForm.minimumCoverage" /></label
              ><label class="checkbox-label"
                ><input type="checkbox" [(ngModel)]="criterionForm.customPeriodAllowed" />Allow
                custom-period assessment</label
              ><label
                >Proration policy<select [(ngModel)]="criterionForm.prorationPolicy">
                  <option>PROGRESS_ONLY</option>
                  <option>ALLOWED</option>
                  <option>FORBIDDEN</option>
                </select></label
              ><label
                >Scope<select [(ngModel)]="criterionForm.scope">
                  <option>COMMON</option>
                  <option>TRACK</option>
                  <option>TEAM</option>
                </select></label
              ><label *ngIf="criterionForm.scope === 'TRACK'"
                >Track<select [(ngModel)]="criterionForm.trackCode">
                  <option *ngFor="let track of publishedTracks()" [value]="track.code">
                    {{ track.name }}
                  </option>
                </select></label
              ><label *ngIf="criterionForm.scope === 'TEAM'"
                >Team key<input [(ngModel)]="criterionForm.teamKey" /></label
              ><label>Level<input [(ngModel)]="criterionForm.levelCode" /></label
              ><label
                >Operator<select [(ngModel)]="criterionForm.operator">
                  <option>>=</option>
                  <option><=</option>
                  <option>></option>
                  <option><</option>
                  <option>=</option>
                  <option>BETWEEN</option>
                </select></label
              ><label>Threshold<input type="number" [(ngModel)]="criterionForm.threshold" /></label
              ><label *ngIf="criterionForm.operator === 'BETWEEN'"
                >Maximum threshold<input
                  type="number"
                  [(ngModel)]="criterionForm.thresholdMax" /></label
              ><label
                >Type<select [(ngModel)]="criterionForm.evaluationType">
                  <option>AUTOMATIC</option>
                  <option>AUTOMATIC_WITH_REVIEW</option>
                  <option>MANAGER_REVIEWED</option>
                  <option>EVIDENCE_ONLY</option>
                </select></label
              ><label class="checkbox-label"
                ><input type="checkbox" [(ngModel)]="criterionForm.mandatory" />Mandatory
                criterion</label
              ><label
                >Human rubric<input
                  [(ngModel)]="criterionForm.rubric"
                  placeholder="What evidence should a reviewer confirm?" /></label
              ><button (click)="createCriterion()">Publish criterion</button>
            </section>
          </div>
        </details>
      </ng-container>

      <ng-container *ngIf="tab === 'integrations'"
        ><section class="panel">
          <div class="panel-title">
            <div>
              <small>FIRST-TIME SETUP</small>
              <h2>Connect your company accounts</h2>
            </div>
          </div>
          <p class="setup-note">
            In local development, tokens are read only by the backend from the project-root
            <code>.env</code>. They are never sent to or stored by this browser. In production,
            provide the same variables through the deployment secret manager.
          </p>
          <article *ngFor="let item of onboarding" class="integration-row">
            <div class="metric">
              <strong>{{ item.name }}</strong
              ><small>{{ item.instructions }}</small>
              <div class="env-tags">
                <code *ngFor="let variable of item.environmentVariables">{{ variable }}</code>
              </div>
              <div class="inline-actions">
                <a *ngIf="item.serviceUrl" [href]="item.serviceUrl">Open service</a
                ><a *ngIf="item.tokenUrl" [href]="item.tokenUrl">Create token</a>
              </div>
            </div>
            <span
              class="status"
              [class.status-ok]="item.configured"
              [class.status-warning]="!item.configured"
              >{{ item.configured ? 'CONFIGURED' : 'SETUP REQUIRED' }}</span
            >
          </article>
        </section>
        <section class="panel">
          <div class="panel-title">
            <div>
              <small>READ-ONLY CONNECTORS</small>
              <h2>Integration health</h2>
            </div>
            <button (click)="sync()">Synchronize last 100 days</button>
          </div>
          <article *ngFor="let item of health">
            <div class="metric">
              <strong>{{ item.displayName }}</strong
              ><small
                >{{ item.baseUrl || 'Configured through environment' }} · last sync
                {{ item.lastSyncAt ? (item.lastSyncAt | date: 'medium') : 'never' }}</small
              >
            </div>
            <span class="status" [class.status-ok]="item.healthStatus === 'HEALTHY'">{{
              displayLabel(item.healthStatus)
            }}</span>
          </article>
          <div class="empty" *ngIf="!health.length">
            No synchronization has run. Configure URLs and tokens in <code>.env</code>, confirm
            employee identities, then synchronize.
          </div>
        </section></ng-container
      >
    </main>`,
  styles: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  private readonly api = inject(EvaluatorApi);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly summaryMetrics = new Set([
    'completed_tasks',
    'story_points',
    'qa_tested_completed_tasks',
    'qa_defects',
    'review_comments',
    'merged_merge_requests',
    'documentation_updates',
  ]);
  private readonly dataQualityMetrics = new Set([
    'completed_tasks_without_sp',
    'completed_tasks_excluded_resolution',
    'qa_defects_needs_review',
  ]);

  tab: Tab = 'report';
  email = '';
  employee?: Employee;
  evaluations: Evaluation[] = [];
  evaluationHistory: Evaluation[] = [];
  health: ConnectorConfig[] = [];
  onboarding: IntegrationOnboarding[] = [];
  error = '';
  notice = '';
  aiSummary: Record<string, string> = {};
  evidenceByEvaluation: Record<string, Evidence[] | undefined> = {};
  evidenceExpanded: Record<string, boolean> = {};
  discoveries: IdentityDiscovery[] = [];
  readiness: DataReadiness[] = [];
  levelFits: LevelFit[] = [];
  evidenceToolFilter = 'ALL';
  evidenceMetricFilter = 'ALL';
  levels: EngineeringLevel[] = [];
  tracks: EngineeringTrack[] = [];
  competencies: CompetencyExpectation[] = [];
  criteria: Criterion[] = [];
  readonly frameworkVersion = 2;

  readonly employeeForm = {
    email: '',
    displayName: '',
    team: '',
    managerEmail: '',
    currentLevelCode: 'MID_I',
    targetLevelCode: 'MID_I',
    trackCode: 'GENERAL',
    employmentStart: this.today(),
    aliases: [] as string[],
  };
  readonly levelForm = {
    code: '',
    name: '',
    ordinal: 1,
    version: 2,
    status: 'PUBLISHED',
    effectiveFrom: this.today(),
  };
  readonly trackForm = {
    code: '',
    name: '',
    description: '',
    iconKey: 'code',
    ordinal: 10,
    version: 1,
    status: 'DRAFT',
    effectiveFrom: this.today(),
  };
  readonly trackAssignmentForm = { email: '', trackCode: 'GENERAL' };
  readonly criterionForm = {
    code: '',
    name: '',
    description: '',
    sourceTool: 'jira',
    metricKey: '',
    aggregation: 'SUM',
    denominatorMetricKey: '',
    evaluationType: 'AUTOMATIC',
    periodType: 'QUARTER',
    minimumCoverage: 'COMPLETE',
    customPeriodAllowed: false,
    prorationPolicy: 'PROGRESS_ONLY',
    scope: 'COMMON',
    trackCode: '',
    teamKey: '',
    mandatory: true,
    rubric: '',
    visualization: 'PROGRESS',
    operator: '>=',
    threshold: 1,
    thresholdMax: null as number | null,
    levelCode: 'MID_I',
    version: 2,
    status: 'PUBLISHED',
    effectiveFrom: this.today(),
  };
  readonly evaluationForm = {
    email: '',
    period: '',
    from: this.quarterStart(),
    to: this.today(),
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    mode: 'ASSESSMENT',
    levelCode: 'MID_I',
    ruleVersion: 2,
  };

  load(): void {
    this.loadFramework();
    this.error = '';
    this.notice = '';
    this.employee = undefined;
    this.evaluations = [];
    this.evaluationHistory = [];
    this.discoveries = [];
    this.readiness = [];
    this.levelFits = [];
    this.aiSummary = {};
    this.evidenceByEvaluation = {};
    this.evidenceExpanded = {};
    this.evidenceToolFilter = 'ALL';
    this.evidenceMetricFilter = 'ALL';
    this.changeDetector.markForCheck();
    this.api.resolveEmployee(this.email).subscribe({
      next: (employee) => {
        this.employee = employee;
        this.evaluationForm.email = employee.canonicalEmail;
        this.evaluationForm.levelCode =
          employee.targetLevelCode || employee.currentLevelCode || 'MID_I';
        this.changeDetector.markForCheck();
        this.loadIdentityReadiness(employee.canonicalEmail);
        this.loadEvaluationHistory(employee.canonicalEmail);
      },
      error: (error) => this.fail(error),
    });
  }

  private loadReadiness(evaluationId: string): void {
    this.api.readiness(evaluationId).subscribe({
      next: (readiness) => {
        this.readiness = readiness;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.readiness = [];
        this.changeDetector.markForCheck();
      },
    });
  }

  private loadIdentityReadiness(email: string): void {
    this.api.discoverIdentities(email).subscribe({
      next: (discoveries) => {
        this.discoveries = discoveries;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.discoveries = [];
        this.changeDetector.markForCheck();
      },
    });
  }

  unresolvedDiscoveries(): IdentityDiscovery[] {
    if (!this.employee) return [];
    return this.discoveries.filter((discovery) => !discovery.verified);
  }

  hasPendingIdentityConfirmation(): boolean {
    return this.unresolvedDiscoveries().some((discovery) => discovery.candidates.length > 0);
  }

  openAdmin(): void {
    this.tab = 'admin';
    this.loadFramework();
  }

  private loadFramework(): void {
    this.api.framework(this.frameworkVersion).subscribe({
      next: (framework) => {
        this.levels = framework.levels;
        this.tracks = framework.tracks;
        this.criteria = framework.criteria;
        this.competencies = framework.competencies;
        this.changeDetector.markForCheck();
      },
      error: (error) => this.fail(error),
    });
  }

  criteriaFor(levelCode: string): Criterion[] {
    return this.criteria.filter(
      (criterion) => criterion.levelCode === levelCode && criterion.status === 'PUBLISHED',
    );
  }

  createEmployee(): void {
    this.api
      .createEmployee(this.employeeForm)
      .subscribe({ next: () => this.ok('Employee created'), error: (error) => this.fail(error) });
  }
  createLevel(): void {
    this.api
      .createLevel(this.levelForm)
      .subscribe({ next: () => this.ok('Level published'), error: (error) => this.fail(error) });
  }
  createTrack(): void {
    this.api.createTrack(this.trackForm).subscribe({
      next: () => {
        this.ok('Track draft created');
        this.openAdmin();
      },
      error: (error) => this.fail(error),
    });
  }
  createCriterion(): void {
    this.api.createCriterion(this.criterionForm).subscribe({
      next: () => this.ok('Criterion published'),
      error: (error) => this.fail(error),
    });
  }

  assignTrackForAdmin(): void {
    if (!this.trackAssignmentForm.email) return;
    this.api
      .assignTrack(this.trackAssignmentForm.email, this.trackAssignmentForm.trackCode)
      .subscribe({
        next: () => this.ok('Employee track updated'),
        error: (error) => this.fail(error),
      });
  }
  prepareReport(): void {
    if (!this.employee) return;
    this.error = '';
    this.notice = 'Discovering identities and synchronizing evidence…';
    this.api.prepareReport(this.employee.canonicalEmail, this.evaluationForm).subscribe({
      next: (result) => {
        const tools = result.identities.map((identity) => identity.toolKey).join(', ') || 'none';
        this.evaluations = [result.evaluation];
        this.levelFits = result.levelFits;
        this.evidenceByEvaluation = {};
        this.evidenceExpanded = {};
        this.readiness = [];
        this.loadReadiness(result.evaluation.id);
        this.loadEvidenceOverview(result.evaluation.id);
        this.loadEvaluationHistory(this.employee!.canonicalEmail);
        this.ok(
          `Report created · ${result.evidenceProcessed} evidence records · identities: ${tools}`,
        );
        this.changeDetector.markForCheck();
      },
      error: (error) => this.fail(error),
    });
  }

  decide(id: string, decision: Decision): void {
    const note = prompt(`Reason for ${decision}`);
    if (!note) return;
    this.api.decide(id, decision, note).subscribe({
      next: () => {
        this.ok('Decision recorded');
        this.load();
      },
      error: (error) => this.fail(error),
    });
  }

  dispute(evaluationId: string, resultId: string): void {
    const message = prompt('Describe the missing or incorrect evidence');
    if (!message) return;
    this.api
      .dispute(evaluationId, resultId, message)
      .subscribe({ next: () => this.ok('Dispute submitted'), error: (error) => this.fail(error) });
  }

  summary(id: string): void {
    this.api.aiSummary(id).subscribe({
      next: (result) => {
        this.aiSummary[id] = result.text;
        this.changeDetector.markForCheck();
      },
      error: (error) => this.fail(error),
    });
  }

  toggleEvidence(id: string): void {
    if (this.evidenceByEvaluation[id]) {
      this.evidenceExpanded[id] = !this.evidenceExpanded[id];
      return;
    }
    this.api.evidence(id).subscribe({
      next: (evidence) => {
        this.evidenceByEvaluation[id] = evidence;
        this.evidenceExpanded[id] = true;
        this.changeDetector.markForCheck();
      },
      error: (error) => this.fail(error),
    });
  }

  private loadEvidenceOverview(evaluationId: string): void {
    this.api.evidence(evaluationId).subscribe({
      next: (evidence) => {
        this.evidenceByEvaluation[evaluationId] = evidence;
        this.evidenceExpanded[evaluationId] = false;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.evidenceByEvaluation[evaluationId] = [];
        this.changeDetector.markForCheck();
      },
    });
  }

  private loadEvaluationHistory(email: string): void {
    this.api.evaluations(email).subscribe({
      next: (evaluations) => {
        this.evaluationHistory = evaluations;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.evaluationHistory = [];
        this.changeDetector.markForCheck();
      },
    });
  }

  detail(item: Evidence): string {
    const attributes = this.parsed(item);
    return String(
      attributes['comment'] ||
        attributes['text'] ||
        (attributes['from'] || attributes['to']
          ? `${attributes['field'] || 'value'}: ${attributes['from'] || '—'} → ${attributes['to'] || '—'}`
          : '') ||
        '',
    );
  }

  attributes(item: Evidence): [string, string][] {
    const hidden = new Set(['comment', 'text', 'field', 'from', 'to', 'project_id']);
    return Object.entries(this.parsed(item))
      .filter(([key, value]) => !hidden.has(key) && value !== '' && value != null)
      .map(([key, value]) => [key.replaceAll('_', ' '), String(value)]);
  }

  metricSummary(items: Evidence[]): { key: string; value: number }[] {
    return this.summarizeMetrics(items, this.summaryMetrics);
  }

  dataQualitySummary(items: Evidence[]): { key: string; value: number }[] {
    return this.summarizeMetrics(items, this.dataQualityMetrics);
  }

  reportMetrics(evaluation: Evaluation): VisualMetric[] {
    const evidence = this.evidenceByEvaluation[evaluation.id] || [];
    const recommended = this.levelFits.find((fit) => fit.recommended);
    const closest = recommended || this.closestFit();
    const readySources = this.readiness.filter((source) => source.dataStatus === 'SYNCED').length;
    return [
      {
        label: 'Evidence records',
        value: String(evidence.length),
        detail: `${new Set(evidence.map((item) => item.toolKey)).size} active sources`,
        tone: evidence.length ? 'positive' : 'warning',
      },
      {
        label: 'Closest level',
        value: closest ? this.displayLevelCode(closest.code) : 'Insufficient data',
        detail: closest
          ? `${closest.score}% ${recommended ? 'fully supported' : 'provisional evidence fit'}`
          : 'No measurable automatic criteria',
        tone: recommended ? 'positive' : 'warning',
      },
      {
        label: 'Source readiness',
        value: this.readiness.length ? `${readySources}/${this.readiness.length}` : '—',
        detail: 'Connected, verified and synchronized',
        tone:
          this.readiness.length > 0 && readySources === this.readiness.length
            ? 'positive'
            : 'warning',
      },
      {
        label: 'Human review',
        value: String(
          evaluation.results.filter((result) => result.resultStatus === 'NEEDS_REVIEW').length,
        ),
        detail: 'Qualitative criteria awaiting confirmation',
        tone: 'neutral',
      },
    ];
  }

  recommendedFit(): LevelFit | undefined {
    return this.levelFits.find((fit) => fit.recommended);
  }

  closestFit(): LevelFit | undefined {
    return this.levelFits
      .filter((fit) => this.hasMeasurableLevelFit(fit) && fit.score > 0)
      .sort((left, right) => right.score - left.score || right.ordinal - left.ordinal)[0];
  }

  highlightedFit(): LevelFit | undefined {
    return this.recommendedFit() || this.closestFit();
  }

  isClosestFit(fit: LevelFit): boolean {
    return this.closestFit()?.code === fit.code;
  }

  profileLevelLabel(): string {
    if (this.employee?.targetLevelCode)
      return `Target: ${this.displayLevelCode(this.employee.targetLevelCode)}`;
    if (this.employee?.currentLevelCode)
      return `Current: ${this.displayLevelCode(this.employee.currentLevelCode)}`;
    return 'Level not assigned';
  }

  hasMeasurableLevelFit(fit: LevelFit): boolean {
    return fit.automaticCriteria > fit.incompleteCriteria;
  }

  levelFitValue(fit: LevelFit): string {
    return this.hasMeasurableLevelFit(fit) ? `${fit.score}%` : '—';
  }

  scoreGradient(score: number): string {
    const bounded = Math.max(0, Math.min(100, score));
    return `conic-gradient(#d5fb72 0 ${bounded}%, #ffffff24 ${bounded}% 100%)`;
  }

  roleSignals(evaluation: Evaluation): { key: string; label: string; value: number }[] {
    const totals = new Map(
      this.summarizeMetrics(
        this.evidenceByEvaluation[evaluation.id] || [],
        new Set([...this.summaryMetrics, 'cross_project_contributions', 'commits', 'pipelines']),
      ).map((metric) => [metric.key, metric.value]),
    );
    const byTrack: Record<string, { key: string; label: string }[]> = {
      FRONTEND: [
        { key: 'merged_merge_requests', label: 'Merged UI changes' },
        { key: 'review_comments', label: 'Review contributions' },
        { key: 'cross_project_contributions', label: 'Product areas' },
        { key: 'documentation_updates', label: 'Documentation' },
      ],
      BACKEND: [
        { key: 'completed_tasks', label: 'Completed delivery' },
        { key: 'story_points', label: 'Story points' },
        { key: 'review_comments', label: 'Review contributions' },
        { key: 'qa_defects', label: 'QA defects' },
      ],
      GENERAL: [
        { key: 'completed_tasks', label: 'Completed delivery' },
        { key: 'story_points', label: 'Story points' },
        { key: 'review_comments', label: 'Review contributions' },
        { key: 'documentation_updates', label: 'Documentation' },
      ],
    };
    return (byTrack[this.employee?.trackCode || 'GENERAL'] || byTrack['GENERAL']).map((signal) => ({
      ...signal,
      value: totals.get(signal.key) || 0,
    }));
  }

  activityTrend(evaluation: Evaluation): ActivityBucket[] {
    const [fromText, toText] = evaluation.period.split('..');
    const from = this.utcDate(fromText);
    const to = this.utcDate(toText);
    const totalDays = Math.max(1, this.daysBetween(from, to) + 1);
    const bucketCount = Math.min(12, totalDays);
    const values = Array.from({ length: bucketCount }, () => 0);
    for (const item of this.evidenceByEvaluation[evaluation.id] || []) {
      const occurred = this.utcDate(
        this.dateKeyInTimezone(item.occurredAt, evaluation.periodTimezone || 'UTC'),
      );
      const offset = this.daysBetween(from, occurred);
      if (offset < 0 || offset >= totalDays) continue;
      const bucketIndex = Math.min(bucketCount - 1, Math.floor((offset * bucketCount) / totalDays));
      values[bucketIndex] += 1;
    }
    const maximum = Math.max(1, ...values);
    return values.map((value, index) => {
      // Match the same boundaries used above when assigning evidence to a bucket.
      // floor(offset * buckets / days) = index implies these inclusive offsets.
      const startOffset = Math.ceil((index * totalDays) / bucketCount);
      const endOffset = Math.ceil(((index + 1) * totalDays) / bucketCount) - 1;
      const bucketDate = new Date(from);
      bucketDate.setUTCDate(bucketDate.getUTCDate() + startOffset);
      const bucketEndDate = new Date(from);
      bucketEndDate.setUTCDate(bucketEndDate.getUTCDate() + Math.max(startOffset, endOffset));
      const dateOptions: Intl.DateTimeFormatOptions = {
        day: '2-digit',
        month: 'short',
        timeZone: 'UTC',
      };
      const startLabel = bucketDate.toLocaleDateString('en-GB', dateOptions);
      const endLabel = bucketEndDate.toLocaleDateString('en-GB', dateOptions);
      return {
        value,
        height: value ? Math.max(8, Math.round((value / maximum) * 100)) : 3,
        label: startLabel === endLabel ? startLabel : `${startLabel}–${endLabel}`,
      };
    });
  }

  activityHeatmap(evaluation: Evaluation): HeatmapDay[] {
    const [fromText, toText] = evaluation.period.split('..');
    const to = this.utcDate(toText);
    const originalFrom = this.utcDate(fromText);
    const from = new Date(Math.max(originalFrom.getTime(), to.getTime() - 83 * 86_400_000));
    const counts = new Map<string, number>();
    for (const item of this.evidenceByEvaluation[evaluation.id] || []) {
      const date = this.dateKeyInTimezone(item.occurredAt, evaluation.periodTimezone || 'UTC');
      counts.set(date, (counts.get(date) || 0) + 1);
    }
    const values = [...counts.values()];
    const maximum = Math.max(1, ...values);
    const days: HeatmapDay[] = [];
    for (const cursor = new Date(from); cursor <= to; cursor.setUTCDate(cursor.getUTCDate() + 1)) {
      const date = cursor.toISOString().slice(0, 10);
      const value = counts.get(date) || 0;
      days.push({
        date,
        value,
        intensity: value === 0 ? 0 : Math.max(1, Math.ceil((value / maximum) * 4)),
        label: cursor.toLocaleDateString('en-GB', {
          day: 'numeric',
          month: 'short',
          year: 'numeric',
          timeZone: 'UTC',
        }),
      });
    }
    return days;
  }

  sourceShares(evaluation: Evaluation): SourceShare[] {
    const counts = new Map<string, number>();
    for (const item of this.evidenceByEvaluation[evaluation.id] || [])
      counts.set(item.toolKey, (counts.get(item.toolKey) || 0) + 1);
    const total = Math.max(
      1,
      [...counts.values()].reduce((sum, value) => sum + value, 0),
    );
    return [...counts.entries()]
      .sort((left, right) => right[1] - left[1])
      .map(([key, value]) => ({
        key,
        label: this.displayLabel(key),
        value,
        percentage: Math.round((value / total) * 100),
      }));
  }

  previousEvaluation(current: Evaluation): Evaluation | undefined {
    const [currentFrom, currentTo] = current.period.split('..').map((date) => this.utcDate(date));
    const currentDays = this.daysBetween(currentFrom, currentTo) + 1;
    return this.evaluationHistory.find((evaluation) => {
      if (
        evaluation.id === current.id ||
        evaluation.period === current.period ||
        evaluation.levelCode !== current.levelCode
      ) {
        return false;
      }
      const [from, to] = evaluation.period.split('..').map((date) => this.utcDate(date));
      return this.daysBetween(from, to) + 1 === currentDays;
    });
  }

  comparisonRows(current: Evaluation): ComparisonRow[] {
    const previous = this.previousEvaluation(current);
    if (!previous) return [];
    const previousByName = new Map(
      previous.results
        .filter((result) => result.criterionName && result.measuredValue != null)
        .map((result) => [result.criterionName!, result.measuredValue!]),
    );
    return current.results
      .filter(
        (result) =>
          result.criterionName &&
          result.measuredValue != null &&
          previousByName.has(result.criterionName),
      )
      .slice(0, 6)
      .map((result) => {
        const previousValue = previousByName.get(result.criterionName!) || 0;
        return {
          label: result.criterionName!,
          current: result.measuredValue!,
          previous: previousValue,
          delta: result.measuredValue! - previousValue,
        };
      });
  }

  private utcDate(value: string): Date {
    return new Date(`${value}T00:00:00Z`);
  }

  private daysBetween(from: Date, to: Date): number {
    return Math.floor((to.getTime() - from.getTime()) / 86_400_000);
  }

  private dateKeyInTimezone(value: string, timezone: string): string {
    const parts = new Intl.DateTimeFormat('en-GB', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      timeZone: timezone,
    }).formatToParts(new Date(value));
    const part = (type: string): string => parts.find((item) => item.type === type)?.value || '';
    return `${part('year')}-${part('month')}-${part('day')}`;
  }

  private summarizeMetrics(
    items: Evidence[],
    accepted: ReadonlySet<string>,
  ): { key: string; value: number }[] {
    const totals = new Map<string, number>();
    for (const item of items) {
      if (accepted.has(item.metricKey))
        totals.set(
          item.metricKey,
          (totals.get(item.metricKey) || 0) + Number(item.numericValue || 0),
        );
    }
    return [...totals].map(([key, value]) => ({ key, value }));
  }

  resultSummary(evaluation: Evaluation): { label: string; value: number }[] {
    const groups = [
      { label: 'Passed', statuses: ['PASS'] },
      { label: 'Failed', statuses: ['FAIL'] },
      { label: 'Human review', statuses: ['NEEDS_REVIEW'] },
      {
        label: 'Incomplete data',
        statuses: [
          'NO_DATA',
          'NO_ACTIVITY',
          'IDENTITY_UNRESOLVED',
          'SOURCE_UNAVAILABLE',
          'PARTIAL_DATA',
        ],
      },
    ];
    const summary = groups
      .map((group) => ({
        label: group.label,
        value: evaluation.results.filter((result) => group.statuses.includes(result.resultStatus))
          .length,
      }))
      .filter((group) => group.value > 0);
    const paceResults = evaluation.results.filter(
      (result) => result.resultStatus === 'NOT_COMPARABLE',
    );
    const onPace = paceResults.filter(
      (result) => this.resultStatusLabel(result) === 'On pace',
    ).length;
    const behindPace = paceResults.length - onPace;
    if (onPace) summary.push({ label: 'On pace', value: onPace });
    if (behindPace) summary.push({ label: 'Behind pace', value: behindPace });
    return summary;
  }

  evidenceMetrics(items: Evidence[]): string[] {
    return [...new Set(items.map((item) => item.metricKey))].sort();
  }

  filterEvidenceByMetric(metricKey: string): void {
    this.evidenceToolFilter = 'ALL';
    this.evidenceMetricFilter = this.evidenceMetricFilter === metricKey ? 'ALL' : metricKey;
  }

  clearEvidenceFilters(): void {
    this.evidenceToolFilter = 'ALL';
    this.evidenceMetricFilter = 'ALL';
  }

  hasResultStatus(evaluation: Evaluation, status: string): boolean {
    return evaluation.results.some((result) => result.resultStatus === status);
  }

  recommendedLevelLabel(): string {
    const recommended = this.levelFits.find((fit) => fit.recommended);
    if (recommended)
      return `${this.displayLevelCode(recommended.code)} is fully supported by automatic evidence`;
    const closest = this.closestFit();
    return closest
      ? `Closest evidence signal: ${this.displayLevelCode(closest.code)} at ${closest.score}% — provisional`
      : 'Not enough automatic evidence to calculate level proximity';
  }

  resultStatusLabel(result: CriterionResult): string {
    if (result.resultStatus !== 'NOT_COMPARABLE') return this.displayLabel(result.resultStatus);
    if (result.measuredValue == null || result.periodTargetValue == null) return 'Pace unavailable';
    const lowerIsBetter = result.formula.includes('<');
    const range = result.formula.includes('BETWEEN');
    const onPace = range
      ? result.measuredValue >= result.periodTargetValue &&
        (result.periodTargetMaxValue == null || result.measuredValue <= result.periodTargetMaxValue)
      : lowerIsBetter
        ? result.measuredValue < result.periodTargetValue
        : result.measuredValue >= result.periodTargetValue;
    return onPace ? 'On pace' : 'Behind pace';
  }

  metricValue(value: number | null, result: CriterionResult): string {
    if (value == null) return '—';
    return `${this.formatNumber(value)}${result.formula.includes('* 100') ? '%' : ''}`;
  }

  targetValue(result: CriterionResult, proportional: boolean): string {
    const minimum = proportional
      ? (result.periodTargetValue ?? result.thresholdValue)
      : result.thresholdValue;
    const maximum = proportional ? result.periodTargetMaxValue : result.thresholdMaxValue;
    if (maximum == null || !result.formula.includes('BETWEEN'))
      return this.metricValue(minimum, result);
    return `${this.metricValue(minimum, result)}–${this.metricValue(maximum, result)}`;
  }

  criterionValue(result: CriterionResult): string {
    if (this.isHumanReviewed(result)) {
      return `${this.formatNumber(result.measuredValue || 0)} linked evidence`;
    }
    if (result.measuredValue == null) return 'Not calculated';
    return `${this.metricValue(result.measuredValue, result)} observed`;
  }

  criterionContext(result: CriterionResult): string {
    if (this.isHumanReviewed(result)) return 'Manager review required; no automatic score';
    if (result.measuredValue == null) return this.coverageExplanation(result.coverage);
    const comparator = this.formulaComparator(result.formula);
    if (result.resultStatus === 'NOT_COMPARABLE') {
      if (result.formula.includes('* 100')) {
        return `Target ${comparator} ${this.targetValue(result, false)} for the full ${this.cadenceLabel(result.cadence)}`;
      }
      return `Expected to date: ${this.targetValue(result, true)} · full ${this.cadenceLabel(result.cadence)} target: ${this.targetValue(result, false)}`;
    }
    return `Target ${comparator} ${this.targetValue(result, false)} per ${this.cadenceLabel(result.cadence)}`;
  }

  private isHumanReviewed(result: CriterionResult): boolean {
    return (
      result.resultStatus === 'NEEDS_REVIEW' &&
      ['HUMAN_REVIEW', 'EVIDENCE_AVAILABLE'].includes(result.coverage)
    );
  }

  private coverageExplanation(coverage: string): string {
    const explanations: Record<string, string> = {
      IDENTITY_UNRESOLVED: 'Identity is not verified; source evidence was not synchronized',
      SOURCE_UNAVAILABLE: 'The source connection is unavailable; check Integrations',
      PARTIAL_DATA: 'Only part of the required source evidence is available',
      NO_DATA: 'The source was synchronized, but the metric cannot be calculated',
      NO_ACTIVITY: 'The source was synchronized and no matching activity was found',
    };
    return explanations[coverage] || this.displayLabel(coverage);
  }

  private formulaComparator(formula: string): string {
    if (formula.includes('BETWEEN')) return 'between';
    if (formula.includes('<=')) return '≤';
    if (formula.includes('>=')) return '≥';
    if (formula.includes('<')) return '<';
    if (formula.includes('>')) return '>';
    return '=';
  }

  private cadenceLabel(cadence?: string): string {
    const labels: Record<string, string> = {
      MONTH: 'month',
      QUARTER: 'quarter',
      YEAR: 'year',
      CUSTOM: 'selected period',
    };
    return labels[cadence || ''] || 'criterion period';
  }

  private formatNumber(value: number): string {
    return new Intl.NumberFormat('en-GB', {
      maximumFractionDigits: 2,
      minimumFractionDigits: 0,
    }).format(value);
  }

  filteredEvidence(items: Evidence[]): Evidence[] {
    const explicitStatusChanges = new Set(
      items
        .filter((item) => item.metricKey === 'jira_status_changes')
        .map((item) => `${item.occurredAt}|${String(this.parsed(item)['issue'] || item.title)}`),
    );
    return items.filter((item) => {
      if (this.evidenceToolFilter !== 'ALL' && item.toolKey !== this.evidenceToolFilter)
        return false;
      if (this.evidenceMetricFilter !== 'ALL' && item.metricKey !== this.evidenceMetricFilter)
        return false;
      if (
        item.metricKey === 'jira_field_changes' &&
        String(this.parsed(item)['field']).toLowerCase() === 'status'
      ) {
        const key = `${item.occurredAt}|${String(this.parsed(item)['issue'] || item.title)}`;
        if (explicitStatusChanges.has(key)) return false;
      }
      return true;
    });
  }

  displayLabel(value: string): string {
    return value
      .replaceAll('_', ' ')
      .toLowerCase()
      .replace(/\b\w/g, (letter) => letter.toUpperCase());
  }

  displayLevelCode(code: string): string {
    const labels: Record<string, string> = {
      JUNIOR_I: 'Junior I',
      JUNIOR_II: 'Junior II',
      MID_I: 'Mid I',
      MID_II: 'Mid II',
      SENIOR_I: 'Senior I',
      SENIOR_II: 'Senior II',
      PRINCIPAL: 'Principal',
    };
    return labels[code] || this.displayLabel(code);
  }

  expectationsFor(levelCode: string): CompetencyExpectation[] {
    return this.competencies.filter(
      (item) =>
        item.levelCode === levelCode &&
        (!item.trackCode || item.trackCode === this.employee?.trackCode),
    );
  }

  activeTracks(): EngineeringTrack[] {
    const latest = new Map<string, EngineeringTrack>();
    for (const track of this.tracks) {
      if (!latest.has(track.code)) latest.set(track.code, track);
    }
    return [...latest.values()];
  }

  publishedTracks(): EngineeringTrack[] {
    return this.activeTracks().filter((track) => track.status === 'PUBLISHED');
  }

  trackLabel(code?: string): string {
    return (
      this.tracks.find((track) => track.code === code)?.name || this.displayLabel(code || 'GENERAL')
    );
  }

  displayPeriod(period: string): string {
    return period.replace('..', ' → ');
  }

  prettyOperator(operator: string): string {
    return operator.replace('>=', '≥').replace('<=', '≤');
  }

  prettyFormula(formula: string): string {
    const humanMetric = (key: string): string =>
      key
        .replace(/^\w+\./, '')
        .replaceAll('_', ' ')
        .replace(/\bqa\b/gi, 'QA')
        .replace(/\bsp\b/gi, 'SP');
    return this.prettyOperator(
      formula
        .replace(/COUNT\(([^)]+)\)/g, (_match, key: string) => `Count of ${humanMetric(key)}`)
        .replace(/SUM\(([^)]+)\)/g, (_match, key: string) => `Total ${humanMetric(key)}`)
        .replace(/AVERAGE\(([^)]+)\)/g, (_match, key: string) => `Average ${humanMetric(key)}`)
        .replaceAll(' / ', ' ÷ ')
        .replaceAll(' * ', ' × ')
        .replace(/(\d+)\.0{1,4}\b/g, '$1'),
    );
  }

  loadHealth(): void {
    this.api.integrationHealth().subscribe({
      next: (health) => {
        this.health = health;
        this.changeDetector.markForCheck();
      },
      error: (error) => this.fail(error),
    });
  }
  loadOnboarding(): void {
    this.api.onboarding().subscribe({
      next: (onboarding) => {
        this.onboarding = onboarding;
        this.changeDetector.markForCheck();
      },
      error: (error) => this.fail(error),
    });
  }

  sync(): void {
    const to = new Date();
    const from = new Date(to.getTime() - 100 * 86_400_000);
    this.api.sync(from, to).subscribe({
      next: (result) => {
        this.ok(`${result.evidenceProcessed} evidence records processed`);
        this.loadHealth();
      },
      error: (error) => this.fail(error),
    });
  }

  confirmForEmployee(tool: string, candidate: IdentityCandidate): void {
    if (!this.employee) return;
    this.api.confirmIdentity(this.employee.canonicalEmail, tool, candidate).subscribe({
      next: () => {
        this.ok(`${tool} identity confirmed`);
        this.loadIdentityReadiness(this.employee!.canonicalEmail);
      },
      error: (error) => this.fail(error),
    });
  }

  identityMatchLabel(candidate: IdentityCandidate): string {
    if (candidate.matchType === 'EXACT_EMAIL') return 'Exact email';
    if (candidate.matchType === 'UNIQUE_NAME')
      return `Unique name match · ${candidate.confidence}%`;
    if (candidate.matchType === 'REUSED_ATLASSIAN') {
      return `Reused Atlassian account · ${candidate.confidence}%`;
    }
    return 'Needs confirmation';
  }

  private parsed(item: Evidence): Record<string, unknown> {
    try {
      return JSON.parse(item.attributesJson || '{}') as Record<string, unknown>;
    } catch {
      return {};
    }
  }

  private ok(message: string): void {
    this.notice = message;
    this.error = '';
    this.changeDetector.markForCheck();
    setTimeout(() => {
      this.notice = '';
      this.changeDetector.markForCheck();
    }, 4_000);
  }

  private fail(error: unknown): void {
    if (error instanceof HttpErrorResponse) {
      const serverMessage =
        typeof error.error?.error === 'string'
          ? error.error.error
          : typeof error.error?.message === 'string'
            ? error.error.message
            : '';
      this.error = serverMessage || `Request failed (${error.status || 'backend unavailable'})`;
    } else {
      this.error = 'Request failed unexpectedly';
    }
    this.changeDetector.markForCheck();
  }

  private today(): string {
    return this.localDate(new Date());
  }

  private quarterStart(): string {
    const now = new Date();
    const month = Math.floor(now.getMonth() / 3) * 3;
    return this.localDate(new Date(now.getFullYear(), month, 1));
  }

  private localDate(value: Date): string {
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
