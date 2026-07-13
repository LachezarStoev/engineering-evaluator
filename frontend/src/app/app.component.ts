import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EvaluatorApi } from './evaluator-api.service';
import type {
  ConnectorConfig,
  Decision,
  Employee,
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
      <div>
        <small>ENGINEERING INTELLIGENCE</small>
        <h1>Career Matrix</h1>
      </div>
      <nav>
        <button [class.active]="tab === 'report'" (click)="tab = 'report'">Report</button
        ><button [class.active]="tab === 'admin'" (click)="tab = 'admin'">Administration</button
        ><button
          [class.active]="tab === 'integrations'"
          (click)="tab = 'integrations'; loadHealth(); loadOnboarding()"
        >
          Integrations
        </button>
      </nav>
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
        <section *ngFor="let evaluation of evaluations" class="panel">
          <div class="panel-title">
            <div>
              <small>{{ evaluation.period }} · {{ evaluation.periodTimezone || 'UTC' }}</small>
              <h2>{{ evaluation.levelCode }} evaluation</h2>
            </div>
            <div class="actions">
              <a [href]="'/api/v1/evaluations/' + evaluation.id + '/export.pdf'">PDF</a
              ><a [href]="'/api/v1/evaluations/' + evaluation.id + '/export.xlsx'">Excel</a
              ><button (click)="summary(evaluation.id)">AI summary</button
              ><span class="status">{{ evaluation.status }}</span>
            </div>
          </div>
          <article *ngFor="let result of evaluation.results">
            <span
              class="dot"
              [class.pass]="result.resultStatus === 'PASS'"
              [class.fail]="result.resultStatus === 'FAIL'"
            ></span>
            <div class="metric">
              <strong>{{ result.formula }}</strong
              ><small
                >Coverage: {{ result.coverage }}
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
              ><span>{{ result.resultStatus }}</span>
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
            <div class="evidence-tags">
              <span *ngFor="let metric of metricSummary(evidenceByEvaluation[evaluation.id] || [])"
                ><b>{{ metric.key }}:</b> {{ metric.value }}</span
              >
            </div>
            <article *ngFor="let item of evidenceByEvaluation[evaluation.id]">
              <div class="metric">
                <small
                  >{{ item.occurredAt | date: 'medium' }} · {{ item.toolKey | uppercase }} ·
                  {{ item.metricKey }}</small
                ><strong>{{ item.title }}</strong>
                <p *ngIf="detail(item) as d">{{ d }}</p>
                <div class="evidence-tags">
                  <span *ngFor="let pair of attributes(item)"
                    ><b>{{ pair[0] }}:</b> {{ pair[1] }}</span
                  >
                </div>
                <a *ngIf="item.url" [href]="item.url" target="_blank" rel="noopener"
                  >Open source ↗</a
                >
              </div>
              <div class="value">
                <strong>{{ item.numericValue }}</strong>
              </div>
            </article>
            <div *ngIf="evidenceByEvaluation[evaluation.id]?.length === 0" class="empty">
              No source activity in this period.
            </div>
          </section>
        </section>
        <section *ngIf="employee && !evaluations.length" class="empty">
          <h2>No evaluation yet</h2>
          <p>Publish criteria and calculate the first quarterly report from Administration.</p>
        </section>
      </ng-container>

      <ng-container *ngIf="tab === 'admin'">
        <div class="grid">
          <section class="panel">
            <h2>Add employee</h2>
            <label>Email<input [(ngModel)]="employeeForm.email" /></label
            ><label>Name<input [(ngModel)]="employeeForm.displayName" /></label
            ><label>Team<input [(ngModel)]="employeeForm.team" /></label
            ><label>Manager email<input [(ngModel)]="employeeForm.managerEmail" /></label
            ><label>Current level<input [(ngModel)]="employeeForm.currentLevelCode" /></label
            ><button (click)="createEmployee()">Create employee</button>
          </section>
          <section class="panel">
            <h2>Add or version level</h2>
            <label>Code<input [(ngModel)]="levelForm.code" /></label
            ><label>Name<input [(ngModel)]="levelForm.name" /></label
            ><label>Order<input type="number" [(ngModel)]="levelForm.ordinal" /></label
            ><label>Version<input type="number" [(ngModel)]="levelForm.version" /></label
            ><button (click)="createLevel()">Publish level</button>
          </section>
          <section class="panel">
            <h2>Add criterion</h2>
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
          <section class="panel">
            <h2>Calculate report</h2>
            <label>Email<input [(ngModel)]="evaluationForm.email" /></label
            ><label
              >Optional quarter/label<input
                [(ngModel)]="evaluationForm.period"
                placeholder="2026-Q3" /></label
            ><label>From<input type="date" [(ngModel)]="evaluationForm.from" /></label
            ><label>To, inclusive<input type="date" [(ngModel)]="evaluationForm.to" /></label
            ><label>Timezone<input [(ngModel)]="evaluationForm.timezone" /></label
            ><label>Level<input [(ngModel)]="evaluationForm.levelCode" /></label
            ><label
              >Rule version<input type="number" [(ngModel)]="evaluationForm.ruleVersion" /></label
            ><button (click)="calculate()">Calculate</button>
          </section>
        </div>
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
          <article *ngFor="let item of onboarding">
            <div class="metric">
              <strong>{{ item.name }}</strong
              ><small>{{ item.instructions }}</small>
              <div class="evidence-tags">
                <code *ngFor="let variable of item.environmentVariables">{{ variable }}</code>
              </div>
              <div class="inline-actions">
                <a *ngIf="item.serviceUrl" [href]="item.serviceUrl">Open service</a
                ><a *ngIf="item.tokenUrl" [href]="item.tokenUrl">Create token</a>
              </div>
            </div>
            <span class="status">{{ item.configured ? 'CONFIGURED' : 'SETUP REQUIRED' }}</span>
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
                {{ item.lastSyncAt || 'never' }}</small
              >
            </div>
            <span class="status">{{ item.healthStatus }}</span>
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
    from: '',
    to: '',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    levelCode: 'MID',
    ruleVersion: 1,
  };

  load(): void {
    this.error = '';
    this.api.employee(this.email).subscribe({
      next: (employee) => {
        this.employee = employee;
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
  calculate(): void {
    this.api.calculate(this.evaluationForm).subscribe({
      next: () => this.ok('Evaluation calculated'),
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
    return new Date().toISOString().slice(0, 10);
  }
}
