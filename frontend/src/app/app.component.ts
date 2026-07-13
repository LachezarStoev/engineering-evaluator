import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EvaluatorApi } from './evaluator-api.service';
import type {
  ConnectorConfig,
  Criterion,
  Decision,
  Employee,
  EngineeringLevel,
  Evaluation,
  Evidence,
  IdentityCandidate,
  IdentityDiscovery,
  IntegrationOnboarding,
  Tab,
} from './models';
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: ` <header>
      <div class="header-inner">
        <div class="brand">
          <span class="brand-mark">CM</span>
          <div>
            <small>ENGINEERING INTELLIGENCE</small>
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
        <section *ngIf="employee" class="profile">
          <div>
            <span class="avatar">{{ employee.displayName[0] }}</span>
            <div>
              <h2>{{ employee.displayName }}</h2>
              <p>{{ employee.canonicalEmail }} · {{ employee.team || 'No team' }}</p>
            </div>
          </div>
          <strong>{{ employee.targetLevelCode || employee.currentLevelCode }}</strong>
        </section>
        <section *ngFor="let evaluation of evaluations" class="panel evaluation-panel">
          <div class="panel-title">
            <div>
              <small
                >{{ displayPeriod(evaluation.period) }} ·
                {{ evaluation.periodTimezone || 'UTC' }}</small
              >
              <h2>{{ evaluation.levelCode }} evaluation</h2>
            </div>
            <div class="actions">
              <a [href]="'/api/v1/evaluations/' + evaluation.id + '/export.pdf'">PDF</a
              ><a [href]="'/api/v1/evaluations/' + evaluation.id + '/export.xlsx'">Excel</a
              ><button (click)="summary(evaluation.id)">AI summary</button
              ><span class="status status-neutral">{{ displayLabel(evaluation.status) }}</span>
            </div>
          </div>
          <article *ngFor="let result of evaluation.results" class="result-row">
            <span
              class="dot"
              [class.pass]="result.resultStatus === 'PASS'"
              [class.fail]="result.resultStatus === 'FAIL'"
              [class.no-data]="result.resultStatus === 'NO_DATA'"
            ></span>
            <div class="metric">
              <code class="formula">{{ prettyFormula(result.formula) }}</code
              ><small class="coverage"
                >Coverage · {{ displayLabel(result.coverage) }}
                <span *ngIf="result.managerNote">· Manager: {{ result.managerNote }}</span></small
              >
              <div class="inline-actions" *ngIf="!evaluation.finalized">
                <button (click)="decide(result.id, 'PASS')">Approve</button
                ><button (click)="decide(result.id, 'FAIL')">Reject</button
                ><button (click)="dispute(evaluation.id, result.id)">Dispute</button>
              </div>
            </div>
            <div class="value">
              <strong>{{ result.measuredValue ?? '—' }} / {{ result.thresholdValue ?? '—' }}</strong
              ><span class="result-state" [attr.data-state]="result.resultStatus">{{
                displayLabel(result.resultStatus)
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
                  ? 'Hide activity details'
                  : 'Show activity details'
              }}
            </button>
          </div>
          <section *ngIf="evidenceByEvaluation[evaluation.id]" class="evidence-list">
            <div class="metric-cards">
              <span *ngFor="let metric of metricSummary(evidenceByEvaluation[evaluation.id] || [])"
                ><small>{{ displayLabel(metric.key) }}</small
                ><b>{{ metric.value }}</b></span
              >
            </div>
            <article *ngFor="let item of evidenceByEvaluation[evaluation.id]" class="evidence-row">
              <div class="metric">
                <div class="evidence-meta">
                  <time>{{ item.occurredAt | date: 'medium' }}</time>
                  <span class="tool-badge">{{ item.toolKey | uppercase }}</span>
                  <span>{{ displayLabel(item.metricKey) }}</span>
                </div>
                <strong class="evidence-title">{{ item.title }}</strong>
                <p *ngIf="detail(item) as d" class="evidence-detail">{{ d }}</p>
                <div class="attribute-grid">
                  <span *ngFor="let pair of attributes(item)"
                    ><b>{{ pair[0] }}:</b> {{ pair[1] }}</span
                  >
                </div>
                <a *ngIf="item.url" [href]="item.url" target="_blank" rel="noopener"
                  >Open source ↗</a
                >
              </div>
              <div class="evidence-value">
                <small>VALUE</small><strong>{{ item.numericValue }}</strong>
              </div>
            </article>
            <div *ngIf="evidenceByEvaluation[evaluation.id]?.length === 0" class="empty">
              No source activity in this period.
            </div>
          </section>
        </section>
        <section *ngIf="employee" class="empty">
          <h2>
            {{ evaluations.length ? 'Create report for another period' : 'No evaluation yet' }}
          </h2>
          <p>
            The standard career-matrix criteria are already published. Select a period and the
            system will discover exact Jira, GitLab, and Confluence identities, synchronize their
            evidence, and calculate the report.
          </p>
          <div class="search-row">
            <label>From<input type="date" [(ngModel)]="evaluationForm.from" /></label
            ><label>To, inclusive<input type="date" [(ngModel)]="evaluationForm.to" /></label
            ><label
              >Level<select [(ngModel)]="evaluationForm.levelCode">
                <option>JUNIOR</option>
                <option>MID</option>
                <option>MID_II</option>
                <option>SENIOR</option>
                <option>PRINCIPAL</option>
              </select></label
            ><button (click)="prepareReport()">Collect evidence and create report</button>
          </div>
        </section>
      </ng-container>

      <ng-container *ngIf="tab === 'admin'">
        <section class="info-banner">
          The five engineering levels and their standard criteria are preloaded. Use this screen
          only to add employees or deliberately publish a new version of a level or criterion.
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
                  <span class="tool-badge">{{ criterion.sourceTool | uppercase }}</span>
                  <b>{{ criterion.name }}</b>
                  <code
                    >{{ criterion.aggregation }}({{ criterion.metricKey }})
                    {{ prettyOperator(criterion.operator) }} {{ criterion.thresholdValue }}</code
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
            </div>
            <button (click)="createEmployee()">Create employee</button>
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
                >Tool<select [(ngModel)]="criterionForm.sourceTool">
                  <option>gitlab</option>
                  <option>jira</option>
                  <option>confluence</option>
                </select></label
              ><label>Metric key<input [(ngModel)]="criterionForm.metricKey" /></label
              ><label
                >Aggregation<select [(ngModel)]="criterionForm.aggregation">
                  <option>SUM</option>
                  <option>COUNT</option>
                  <option>RATIO</option>
                </select></label
              ><label *ngIf="criterionForm.aggregation === 'RATIO'"
                >Denominator metric<input [(ngModel)]="criterionForm.denominatorMetricKey" /></label
              ><label>Level<input [(ngModel)]="criterionForm.levelCode" /></label
              ><label
                >Operator<select [(ngModel)]="criterionForm.operator">
                  <option>>=</option>
                  <option><=</option>
                  <option>></option>
                  <option><</option>
                  <option>=</option>
                </select></label
              ><label>Threshold<input type="number" [(ngModel)]="criterionForm.threshold" /></label
              ><label
                >Type<select [(ngModel)]="criterionForm.evaluationType">
                  <option>AUTOMATIC</option>
                  <option>AUTOMATIC_WITH_REVIEW</option>
                  <option>MANAGER_REVIEWED</option>
                  <option>EVIDENCE_ONLY</option>
                </select></label
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
        </section>
        <section class="panel">
          <h2>Discover identities by email</h2>
          <p class="setup-note">
            Discovery searches globally across all projects, repositories and spaces visible to the
            configured account.
          </p>
          <div class="search-row">
            <input [(ngModel)]="identityEmail" placeholder="developer@company.com" /><button
              (click)="discover()"
            >
              Search systems
            </button>
          </div>
          <article *ngFor="let tool of discoveries">
            <div class="metric">
              <strong>{{ tool.tool }} · {{ tool.health.message }}</strong
              ><small *ngIf="!tool.candidates.length">No exact candidate found</small>
              <div *ngFor="let candidate of tool.candidates" class="candidate">
                {{ candidate.username }} · {{ candidate.email || candidate.externalUserId }}
                <button (click)="confirm(tool.tool, candidate)">Confirm mapping</button>
              </div>
            </div>
          </article>
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
    'completed_tasks_with_sp',
    'completed_tasks_without_sp',
    'completed_tasks_excluded_resolution',
    'story_points',
    'qa_tested_completed_tasks',
    'qa_defects',
    'qa_defects_needs_review',
  ]);

  tab: Tab = 'report';
  email = '';
  employee?: Employee;
  evaluations: Evaluation[] = [];
  health: ConnectorConfig[] = [];
  onboarding: IntegrationOnboarding[] = [];
  error = '';
  notice = '';
  aiSummary: Record<string, string> = {};
  evidenceByEvaluation: Record<string, Evidence[] | undefined> = {};
  identityEmail = '';
  discoveries: IdentityDiscovery[] = [];
  levels: EngineeringLevel[] = [];
  criteria: Criterion[] = [];

  readonly employeeForm = {
    email: '',
    displayName: '',
    team: '',
    managerEmail: '',
    currentLevelCode: 'MID',
    targetLevelCode: 'MID',
    employmentStart: this.today(),
    aliases: [] as string[],
  };
  readonly levelForm = {
    code: '',
    name: '',
    ordinal: 1,
    version: 1,
    status: 'PUBLISHED',
    effectiveFrom: this.today(),
  };
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
    operator: '>=',
    threshold: 1,
    levelCode: 'MID',
    version: 1,
    status: 'PUBLISHED',
    effectiveFrom: this.today(),
  };
  readonly evaluationForm = {
    email: '',
    period: '',
    from: this.quarterStart(),
    to: this.today(),
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    levelCode: 'MID',
    ruleVersion: 1,
  };

  load(): void {
    this.error = '';
    this.api.resolveEmployee(this.email).subscribe({
      next: (employee) => {
        this.employee = employee;
        this.evaluationForm.email = employee.canonicalEmail;
        this.evaluationForm.levelCode =
          employee.targetLevelCode || employee.currentLevelCode || 'MID';
        this.changeDetector.markForCheck();
        this.api.evaluations(this.email).subscribe({
          next: (evaluations) => {
            this.evaluations = evaluations;
            this.changeDetector.markForCheck();
          },
          error: (error) => this.fail(error),
        });
      },
      error: (error) => this.fail(error),
    });
  }

  openAdmin(): void {
    this.tab = 'admin';
    this.api.levels().subscribe({
      next: (levels) => {
        this.levels = levels;
        this.changeDetector.markForCheck();
      },
      error: (error) => this.fail(error),
    });
    this.api.criteria().subscribe({
      next: (criteria) => {
        this.criteria = criteria;
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
  createCriterion(): void {
    this.api.createCriterion(this.criterionForm).subscribe({
      next: () => this.ok('Criterion published'),
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
        this.ok(
          `Report created · ${result.evidenceProcessed} evidence records · identities: ${tools}`,
        );
        this.load();
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
      delete this.evidenceByEvaluation[id];
      return;
    }
    this.api.evidence(id).subscribe({
      next: (evidence) => {
        this.evidenceByEvaluation[id] = evidence;
        this.changeDetector.markForCheck();
      },
      error: (error) => this.fail(error),
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
    const totals = new Map<string, number>();
    for (const item of items) {
      if (this.summaryMetrics.has(item.metricKey))
        totals.set(
          item.metricKey,
          (totals.get(item.metricKey) || 0) + Number(item.numericValue || 0),
        );
    }
    return [...totals].map(([key, value]) => ({ key: key.replaceAll('_', ' '), value }));
  }

  displayLabel(value: string): string {
    return value
      .replaceAll('_', ' ')
      .toLowerCase()
      .replace(/\b\w/g, (letter) => letter.toUpperCase());
  }

  displayPeriod(period: string): string {
    return period.replace('..', ' → ');
  }

  prettyOperator(operator: string): string {
    return operator.replace('>=', '≥').replace('<=', '≤');
  }

  prettyFormula(formula: string): string {
    return this.prettyOperator(formula.replace(/(\d+)\.0{1,4}\b/g, '$1'));
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

  discover(): void {
    this.api.discoverIdentities(this.identityEmail).subscribe({
      next: (discoveries) => {
        this.discoveries = discoveries;
        this.changeDetector.markForCheck();
      },
      error: (error) => this.fail(error),
    });
  }
  confirm(tool: string, candidate: IdentityCandidate): void {
    this.api.confirmIdentity(this.identityEmail, tool, candidate).subscribe({
      next: () => this.ok(`${tool} identity confirmed`),
      error: (error) => this.fail(error),
    });
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
