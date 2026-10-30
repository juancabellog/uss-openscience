import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { I18nService } from '../../core/i18n/i18n.service';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { CASE_STATUS_LABELS, COMMITTEE_LABELS, COMMITTEE_SHORT_LABELS } from '../../mocks/catalogs';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { CommitteeCase } from '../../core/models/ethics.models';
import { hasScope } from '../../core/workflow/role-capabilities';
import { PermissionService } from '../../core/services/permission.service';
import { ProjectRow, ProjectService } from '../../core/services/project.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { ProjectCardComponent } from './project-card.component';
import { ProjectFilterValue, ProjectFiltersComponent, ProjectStatusOption } from './project-filters.component';
import { ProjectGroupComponent } from './project-group.component';
import { presentCommittees } from './committee-presentation';
import { ProjectViewModel } from './project-view.model';
import { projectHasStatus, projectHasValidity } from './project-inventory';
import { projectBelongsToCommittees } from '../../core/workflow/committee-projects';
import { ProjectViewToggleComponent } from './project-view-toggle.component';

type ViewMode = 'cards' | 'list';

@Component({
  selector: 'eth-project-list-page',
  imports: [
    RouterLink,
    EmptyStateComponent,
    ProjectCardComponent,
    ProjectFiltersComponent,
    ProjectGroupComponent,
    ProjectViewToggleComponent,
    ...I18N_IMPORTS
  ],
  template: `
    <section>
      <header class="projects-toolbar">
        <h1>{{ 'projects.title' | translate }}</h1>
        <div class="projects-toolbar__actions">
          <eth-project-view-toggle [mode]="mode()" (modeChange)="setMode($event)" />
          @if (canCreate()) {
            <a class="btn" routerLink="/proyectos/nuevo">
              <span class="button-icon" aria-hidden="true">+</span>
              <span>{{ 'dashboard.newEthicsApplication' | translate }}</span>
            </a>
          }
        </div>
      </header>
      @if (showCommitteeScope()) {
        <div class="scope-filter">
          <span class="label-caption" id="project-scope-label">{{ 'projects.scopeLabel' | translate }}</span>
          <div class="status-chips" role="group" aria-labelledby="project-scope-label">
            <button type="button" [attr.aria-pressed]="!committeeScope()" (click)="setCommitteeScope(false)">
              {{ 'reviews.filterAll' | translate }}
            </button>
            <button type="button" [attr.aria-pressed]="committeeScope()" (click)="setCommitteeScope(true)">
              {{ 'dashboard.committeeProjects' | translate }}
            </button>
          </div>
        </div>
      }
      @if (authorized().length) {
        <eth-project-filters
          [showResearcher]="showResearcher()"
          [researchers]="researchers()"
          [statuses]="statusOptions()"
          [statusTotal]="statusTotal()"
          [value]="filters()"
          (valueChange)="onFilters($event)"
        />
        <div class="result-bar">
          <p class="result-count">{{ countLabel() }}</p>
          @if (mode() === 'list' && projects().length > 1) {
            <button type="button" class="text-action" (click)="expandAll()">{{ 'projects.expandAll' | translate }}</button>
            <button type="button" class="text-action" (click)="collapseAll()">{{ 'projects.collapseAll' | translate }}</button>
          }
        </div>
      }
      @if (!authorized().length) {
        <eth-empty-state title="projects.emptyTitle" message="projects.emptyMessage" />
      } @else if (!projects().length) {
        <eth-empty-state title="projects.noResults" message="projects.noResultsDetail">
          <button type="button" class="ghost clear-filters" (click)="clearFilters()">{{ 'projects.clearFilters' | translate }}</button>
        </eth-empty-state>
      } @else if (mode() === 'cards') {
        <div class="project-board">
          @for (project of projects(); track project.id) {
            <eth-project-card [project]="project" />
          }
        </div>
      } @else {
        <div class="project-groups">
          @for (project of projects(); track project.id) {
            <eth-project-group
              [project]="project"
              [expanded]="isOpen(project.id)"
              (expandedChange)="setOpen(project.id, $event)"
            />
          }
        </div>
      }
    </section>
  `,
  styles: `
    .projects-toolbar {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 1.15rem;
    }
    h1 {
      margin: 0;
    }
    .projects-toolbar__actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      flex-wrap: wrap;
      gap: 0.75rem;
    }
    .project-board {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(20.5rem, 1fr));
      gap: 1rem;
      align-items: stretch;
    }
    .project-groups {
      display: grid;
      gap: 0.9rem;
    }
    .result-bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.35rem 0.9rem;
      margin: 0 0 0.85rem;
    }
    .result-count {
      margin: 0;
      color: var(--eth-muted);
      font-size: 0.85rem;
    }
    .text-action {
      height: auto;
      padding: 0.15rem 0;
      border: 0;
      background: transparent;
      color: var(--eth-primary);
      font-size: 0.85rem;
      font-weight: 600;
    }
    .text-action:focus-visible {
      outline: 2px solid var(--eth-accent);
      outline-offset: 2px;
    }
    .clear-filters {
      margin-top: 0.85rem;
    }
    .scope-filter {
      display: grid;
      gap: 0.35rem;
      margin: 0 0 0.85rem;
    }
    .status-chips { display: flex; flex-wrap: wrap; gap: 0.35rem; }
    .status-chips button {
      height: 2.15rem;
      padding: 0 0.75rem;
      border: 1px solid var(--eth-border);
      border-radius: 999px;
      background: #f7f6f3;
      color: var(--eth-ink);
      font-weight: 600;
    }
    .status-chips button[aria-pressed='true'] {
      border-width: 2px;
      border-color: var(--eth-primary);
      background: #fff;
      color: var(--eth-primary);
      font-weight: 800;
    }
    .status-chips button:focus-visible { outline: 2px solid var(--eth-primary); outline-offset: 2px; }
    @media (max-width: 720px) {
      .projects-toolbar {
        flex-direction: column;
      }
      .projects-toolbar__actions {
        justify-content: flex-start;
      }
      .project-board {
        grid-template-columns: 1fr;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectListPage {
  private readonly projectsApi = inject(ProjectService);
  private readonly auth = inject(DemoAuthService);
  private readonly permissions = inject(PermissionService);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly query = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  private readonly choice = signal<{ userId: string; mode: ViewMode } | null>(null);
  private readonly emptyFilters: ProjectFilterValue = { query: '', researcherId: '', from: '', to: '', status: '', validity: '' };
  private readonly statuses = new Set<string>(this.projectsApi.globalStatusOrder);
  private readonly validities = new Set(['VIGENTE', 'PROXIMO', 'VENCIDO']);
  readonly filters = signal<ProjectFilterValue>(this.filtersFrom(this.route.snapshot.queryParamMap));
  private readonly openIds = signal<ReadonlySet<string>>(new Set());
  readonly canCreate = computed(() => this.permissions.can(this.auth.currentUser(), 'CREATE_REQUEST'));
  readonly showResearcher = computed(() => {
    const user = this.auth.currentUser();
    return !!user && !hasScope(user, 'OWN_PROJECT');
  });
  readonly authorized = computed(() => this.projectsApi.listVisible());
  readonly committeeScope = computed(() => this.query().get('scope') === 'committee');
  readonly showCommitteeScope = computed(() => {
    const membership = new Set(this.auth.currentUser()?.committees ?? []);
    if (!membership.size) {
      return false;
    }
    const subset = this.authorized().filter((row) =>
      projectBelongsToCommittees(row.cases.map((item) => item.committee), membership)
    ).length;
    return this.committeeScope() || subset !== this.authorized().length;
  });
  readonly researchers = computed(() => {
    const people = new Map<string, { name: string; email?: string }>();
    for (const row of this.authorized()) {
      if (row.researcher) {
        people.set(row.researcher.id, { name: row.researcher.displayName, email: row.researcher.email });
      }
    }
    return [...people.entries()]
      .map(([id, person]) => ({ id, name: person.name, email: person.email }))
      .sort((a, b) => a.name.localeCompare(b.name, 'es'));
  });
  readonly mode = computed<ViewMode>(() => {
    const user = this.auth.currentUser();
    const id = user?.id ?? '';
    const chosen = this.choice();
    if (chosen?.userId === id) {
      return chosen.mode;
    }
    return this.read(id) ?? (hasScope(user, 'OWN_PROJECT') ? 'cards' : 'list');
  });
  readonly projects = computed(() => this.filtered().map((row) => this.toView(row)));

  constructor() {
    effect(() => {
      const params = this.query();
      const status = this.known(params.get('status'), this.statuses);
      const validity = this.known(params.get('validity'), this.validities);
      const current = this.filters();
      if (current.status === status && current.validity === validity) {
        return;
      }
      this.filters.set({ ...current, status, validity });
    });
  }

  private readonly narrowed = computed(() => {
    const filters = this.filters();
    const query = filters.query.trim().toLocaleLowerCase();
    const includeResearcher = this.showResearcher();
    const expiry = (item: CommitteeCase) => this.projectsApi.expiryInfo(item);
    const membership = new Set(this.auth.currentUser()?.committees ?? []);
    const committeeOnly = this.committeeScope();
    return this.authorized().filter((row) => {
      if (committeeOnly && !projectBelongsToCommittees(row.cases.map((item) => item.committee), membership)) {
        return false;
      }
      if (!projectHasValidity(row, filters.validity as '' | 'VIGENTE' | 'PROXIMO' | 'VENCIDO', expiry)) {
        return false;
      }
      const title = row.project.title.toLocaleLowerCase();
      const code = row.project.code.toLocaleLowerCase();
      const researcher = includeResearcher ? (row.researcher?.displayName ?? '').toLocaleLowerCase() : '';
      if (query && !title.includes(query) && !code.includes(query) && !researcher.includes(query)) {
        return false;
      }
      if (filters.researcherId && row.project.researcherId !== filters.researcherId) {
        return false;
      }
      const created = row.project.createdAt.slice(0, 10);
      if (filters.from && created < filters.from) {
        return false;
      }
      if (filters.to && created > filters.to) {
        return false;
      }
      return true;
    });
  });
  readonly statusTotal = computed(() => this.narrowed().length);
  readonly statusOptions = computed<ProjectStatusOption[]>(() => {
    const counts = new Map<string, number>();
    for (const row of this.narrowed()) {
      counts.set(row.globalStatus, (counts.get(row.globalStatus) ?? 0) + 1);
    }
    const selected = this.filters().status;
    return this.projectsApi.globalStatusOrder
      .filter((status) => (counts.get(status) ?? 0) > 0 || status === selected)
      .map((status) => ({
        value: status,
        labelKey: CASE_STATUS_LABELS[status] ?? '',
        count: counts.get(status) ?? 0
      }))
      .filter((item) => item.labelKey);
  });
  private readonly filtered = computed(() => {
    const status = this.filters().status;
    return this.narrowed().filter((row) => projectHasStatus(row, status));
  });

  setCommitteeScope(committee: boolean): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { scope: committee ? 'committee' : null },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  onFilters(next: ProjectFilterValue): void {
    this.filters.set(next);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        status: next.status || null,
        validity: next.validity || null
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  setMode(mode: ViewMode): void {
    const userId = this.auth.currentUser()?.id ?? '';
    if (userId) {
      localStorage.setItem(this.key(userId), mode);
    }
    this.choice.set({ userId, mode });
  }

  clearFilters(): void {
    this.onFilters(this.emptyFilters);
  }

  isOpen(id: string): boolean {
    return this.openIds().has(id);
  }

  setOpen(id: string, open: boolean): void {
    const next = new Set(this.openIds());
    if (open) {
      next.add(id);
    } else {
      next.delete(id);
    }
    this.openIds.set(next);
  }

  expandAll(): void {
    this.openIds.set(new Set(this.projects().map((project) => project.id)));
  }

  collapseAll(): void {
    this.openIds.set(new Set());
  }

  countLabel(): string {
    const total = this.authorized().length;
    const shown = this.filtered().length;
    const filters = this.filters();
    const active = !!(filters.query.trim() || filters.researcherId || filters.from || filters.to || filters.status || filters.validity || this.committeeScope());
    if (active) {
      return this.i18n.translate('projects.countFiltered', { shown, total });
    }
    if (shown === 1) {
      return this.i18n.translate('projects.countOne', { count: shown });
    }
    return this.i18n.translate('projects.count', { count: shown });
  }

  private toView(row: ProjectRow): ProjectViewModel {
    const visible = this.visibleCases(row);
    const showOverall = visible.length === row.cases.length;
    const role = this.auth.currentUser()?.role;
    const committees = presentCommittees(visible).map((group) => {
      const approval = visible.find((item) => item.committee === group.committee && item.approvalExpiresAt === group.expiresAt);
      const expiry = approval ? this.projectsApi.expiryInfo(approval) : null;
      return {
        id: group.active.id,
        shortNameKey: COMMITTEE_SHORT_LABELS[group.committee] ?? COMMITTEE_LABELS[group.committee] ?? group.committee,
        fullNameKey: COMMITTEE_LABELS[group.committee] ?? group.committee,
        code: group.active.code,
        status: group.active.status,
        statusLabelKey: group.statusLabelKey,
        receivedAt: group.active.receivedAt,
        expiresAt: group.expiresAt,
        daysLeft: expiry?.daysLeft ?? null,
        approvalUntil: group.approvalUntil
      };
    });
    const next = committees
      .filter((item) => item.expiresAt && item.daysLeft !== null && item.daysLeft >= 0)
      .sort((a, b) => (a.expiresAt ?? '').localeCompare(b.expiresAt ?? ''))[0];
    return {
      id: row.project.id,
      title: row.project.title,
      code: row.project.code,
      funding: row.project.fundingSource,
      researcherName: role && role !== 'RESEARCHER' ? row.researcher?.displayName ?? null : null,
      submittedAt: row.project.createdAt,
      overallStatus: showOverall ? row.globalStatus : null,
      committees,
      nextExpiration: next?.expiresAt ? { date: next.expiresAt, committeeKey: next.shortNameKey } : null
    };
  }

  private filtersFrom(params: ParamMap): ProjectFilterValue {
    return {
      ...this.emptyFilters,
      status: this.known(params.get('status'), this.statuses),
      validity: this.known(params.get('validity'), this.validities)
    };
  }

  private known(value: string | null, allowed: Set<string>): string {
    return value && allowed.has(value) ? value : '';
  }

  private visibleCases(row: ProjectRow): CommitteeCase[] {
    const user = this.auth.currentUser();
    if (!user || hasScope(user, 'GLOBAL') || hasScope(user, 'OWN_PROJECT')) {
      return row.cases;
    }
    if (hasScope(user, 'COMMITTEE')) {
      return row.cases.filter((item) => user.committees.includes(item.committee));
    }
    return row.cases.filter((item) =>
      this.permissions.can(user, 'VIEW_COMMITTEE_WORKSPACE', {
        projectId: item.projectId,
        committeeCaseId: item.id,
        committeeType: item.committee,
        workflowStatus: item.status
      })
    );
  }

  private read(userId: string): ViewMode | null {
    if (!userId) {
      return null;
    }
    const value = localStorage.getItem(this.key(userId));
    return value === 'cards' || value === 'list' ? value : null;
  }

  private key(userId: string): string {
    return `ethics.projects.viewMode.${userId}`;
  }
}
