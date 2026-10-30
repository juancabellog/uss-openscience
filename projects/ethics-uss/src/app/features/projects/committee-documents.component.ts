import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, HostListener, computed, inject, input, signal } from '@angular/core';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { PermissionService } from '../../core/services/permission.service';
import { DocumentMetadata } from '../../core/models/ethics.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { DocumentService } from '../../core/services/document.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { PdfGenerationService } from '../../core/services/pdf-generation.service';
import { caseDocumentsEditable } from '../../core/workflow/committee-workspace.policy';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { IconComponent } from '../../shared/components/icon.component';
import { DocumentUploadActionComponent } from './document-upload-action.component';

interface DocumentRow {
  id: string;
  name: string;
  fileName: string | null;
  version: number | null;
  uploadedAt: string | null;
  statusKey: string;
  doc: DocumentMetadata | null;
  history: DocumentMetadata[];
  canUpload: boolean;
  canVersion: boolean;
  required: boolean;
  requirementKey?: string;
}

type CategoryFilter = 'all' | 'required' | 'optional';
type LoadFilter = 'all' | 'uploaded' | 'missing';

/** Contadores de la lista ya acotada por búsqueda. Todos = obligatorios + opcionales = cargados + sin cargar. */
export function documentListCounts(rows: ReadonlyArray<{ required: boolean; loaded: boolean }>): {
  all: number;
  required: number;
  optional: number;
  uploaded: number;
  missing: number;
} {
  const required = rows.filter((row) => row.required).length;
  const uploaded = rows.filter((row) => row.loaded).length;
  const all = rows.length;
  return {
    all,
    required,
    optional: all - required,
    uploaded,
    missing: all - uploaded
  };
}

type UploadPhase = 'idle' | 'loading' | 'success' | 'error';

@Component({
  selector: 'eth-committee-documents',
  imports: [NgTemplateOutlet, DocumentUploadActionComponent, IconComponent, ...I18N_IMPORTS],
  template: `
    @if (caseId()) {
      <div class="doc-toolbar">
        <label class="search">
          <span class="sr-only">{{ 'projects.searchDocuments' | translate }}</span>
          <input
            type="search"
            [value]="query()"
            [attr.placeholder]="'projects.searchDocuments' | translate"
            [attr.aria-label]="'projects.searchDocuments' | translate"
            (input)="query.set($any($event.target).value)"
          />
        </label>
        <div class="filter-field">
          <label class="filter-label" [attr.for]="'doc-category-' + caseId()">{{ 'projects.documentCategory' | translate }}</label>
          <select [id]="'doc-category-' + caseId()" [value]="categoryFilter()" (change)="categoryFilter.set($any($event.target).value)">
            <option value="all">{{ 'projects.filterAll' | translate }} ({{ counts().all }})</option>
            <option value="required">{{ 'projects.categoryRequired' | translate }} ({{ counts().required }})</option>
            <option value="optional">{{ 'projects.categoryOptional' | translate }} ({{ counts().optional }})</option>
          </select>
        </div>
        <div class="filter-field">
          <label class="filter-label" [attr.for]="'doc-load-' + caseId()">{{ 'projects.uploadStatus' | translate }}</label>
          <select [id]="'doc-load-' + caseId()" [value]="loadFilter()" (change)="loadFilter.set($any($event.target).value)">
            <option value="all">{{ 'projects.filterAll' | translate }} ({{ counts().all }})</option>
            <option value="uploaded">{{ 'projects.filterUploaded' | translate }} ({{ counts().uploaded }})</option>
            <option value="missing">{{ 'projects.notUploaded' | translate }} ({{ counts().missing }})</option>
          </select>
        </div>
        @if (filtersActive()) {
          <button type="button" class="ghost clear" (click)="clearFilters()">{{ 'projects.clearFilters' | translate }}</button>
        }
      </div>
      @if (formatHint()) {
        <p class="file-hint">{{ 'projects.fileHint' | translate }}</p>
      }
      @if (!visibleRows().length) {
        <p class="empty">{{ 'projects.noDocumentsFound' | translate }}</p>
        @if (filtersActive()) {
          <button type="button" class="ghost" (click)="clearFilters()">{{ 'projects.clearFilters' | translate }}</button>
        }
      } @else {
        <ng-container *ngTemplateOutlet="docTable; context: { rows: visibleRows(), showCategory: true }" />
      }
    } @else {
      <ng-container *ngTemplateOutlet="docTable; context: { rows: rows(), showCategory: false }" />
    }

    <ng-template #docTable let-rows="rows" let-showCategory="showCategory">
      <div class="table-wrap">
        <table>
          <caption class="sr-only">{{ 'projects.documentSection' | translate }}</caption>
          <thead>
            <tr>
              <th class="name">{{ 'projects.documentName' | translate }}</th>
              @if (showCategory) {
                <th class="category">{{ 'projects.documentCategory' | translate }}</th>
              }
              <th>{{ 'projects.version' | translate }}</th>
              <th>{{ 'projects.uploadedAt' | translate }}</th>
              <th>{{ 'cases.status' | translate }}</th>
              <th class="actions">{{ 'projects.actions' | translate }}</th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows; track row.id) {
              <tr [attr.aria-busy]="phase()[row.id] === 'loading' ? true : null">
                <td [attr.data-label]="'projects.documentName' | translate">
                  {{ row.name }}
                  @if (row.fileName) {
                    <span class="file">{{ row.fileName }}</span>
                  }
                </td>
                @if (showCategory) {
                  <td [attr.data-label]="'projects.documentCategory' | translate">
                    <span class="category-badge">{{ (row.required ? 'projects.categoryRequiredItem' : 'projects.categoryOptionalItem') | translate }}</span>
                  </td>
                }
                <td class="meta" [attr.data-label]="'projects.version' | translate">{{ row.version === null ? ('common.dash' | translate) : 'v' + row.version }}</td>
                <td class="meta" [attr.data-label]="'projects.uploadedAt' | translate">{{ row.uploadedAt ? (row.uploadedAt | localeDate) : ('common.dash' | translate) }}</td>
                <td [attr.data-label]="'cases.status' | translate" [class.is-empty]="!row.doc">
                  {{ row.statusKey | translate }}
                </td>
                <td [attr.data-label]="'projects.actions' | translate">
                  <div class="row-actions">
                    @if (row.doc) {
                      <button type="button" class="ghost icon" [attr.aria-label]="'projects.viewDocument' | translate" [attr.data-tooltip]="'projects.viewDocument' | translate" (click)="preview(row.doc)">
                        <eth-icon name="view" />
                      </button>
                      <button type="button" class="ghost icon" [attr.aria-label]="'projects.downloadDocument' | translate" [attr.data-tooltip]="'projects.downloadDocument' | translate" (click)="preview(row.doc)">
                        <eth-icon name="download" />
                      </button>
                    }
                    @if (row.canUpload && row.requirementKey && phase()[row.id] !== 'error') {
                      <eth-document-upload-action
                        labelKey="projects.upload"
                        tooltipKey="projects.uploadFile"
                        [compact]="true"
                        [accessibleName]="uploadName(row)"
                        [busy]="phase()[row.id] === 'loading'"
                        (selected)="upload(row, $event)"
                      />
                    }
                    @if (row.doc && (row.canVersion || row.history.length)) {
                      <div class="more">
                        <button
                          type="button"
                          class="ghost icon more-button"
                          [attr.aria-expanded]="menu() === row.id"
                          aria-haspopup="menu"
                          [attr.aria-controls]="'doc-menu-' + row.id"
                          [attr.aria-label]="'projects.moreActions' | translate"
                          [attr.data-tooltip]="'projects.moreActions' | translate"
                          (click)="toggleMenu(row.id, $event)"
                        >
                          <eth-icon name="more" />
                        </button>
                        @if (menu() === row.id) {
                          <div class="menu" [id]="'doc-menu-' + row.id" role="menu">
                            @if (row.canVersion && row.requirementKey && phase()[row.id] !== 'error') {
                              <div role="none">
                                <eth-document-upload-action
                                  labelKey="projects.uploadNewVersion"
                                  [accessibleName]="versionName(row)"
                                  [busy]="phase()[row.id] === 'loading'"
                                  (selected)="upload(row, $event)"
                                />
                              </div>
                            }
                            @if (row.history.length) {
                              <button type="button" class="ghost" role="menuitem" [attr.aria-expanded]="open()[row.id] || false" (click)="toggle(row.id)">
                                {{ 'projects.viewVersions' | translate }}
                              </button>
                            }
                          </div>
                        }
                      </div>
                    }
                  </div>
                  @if (phase()[row.id] === 'loading' && !row.canUpload) {
                    <p class="feedback" role="status">{{ 'projects.uploading' | translate }}</p>
                  }
                  @if (phase()[row.id] === 'success') {
                    <p class="feedback ok" role="status">{{ 'projects.documentUploaded' | translate }}</p>
                  }
                  @if (phase()[row.id] === 'error') {
                    <p class="feedback error" role="alert">{{ errors()[row.id] || ('projects.uploadFailed' | translate) }}</p>
                    <eth-document-upload-action
                      labelKey="projects.retry"
                      [accessibleName]="('projects.retry' | translate) + ': ' + row.name"
                      (selected)="upload(row, $event)"
                    />
                  }
                  @if (open()[row.id]) {
                    <ul class="history">
                      @for (item of versionsOf(row); track item.id) {
                        <li>
                          <span>
                            v{{ item.version }} · {{ item.uploadedAt | localeDate }}
                            · {{ versionNote(item, row) }}
                          </span>
                          <button type="button" class="ghost icon" [attr.aria-label]="'projects.viewDocument' | translate" [attr.data-tooltip]="'projects.viewDocument' | translate" (click)="preview(item)">
                            <eth-icon name="view" />
                          </button>
                          <button type="button" class="ghost icon" [attr.aria-label]="'projects.downloadDocument' | translate" [attr.data-tooltip]="'projects.downloadDocument' | translate" (click)="preview(item)">
                            <eth-icon name="download" />
                          </button>
                        </li>
                      }
                    </ul>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </ng-template>
  `,
  styles: `
    :host {
      display: block;
      container: doc-filters / inline-size;
    }
    .doc-toolbar {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      align-items: end;
      gap: 0.65rem 0.75rem;
      margin: 0 0 0.75rem;
    }
    .search input,
    .filter-field select {
      box-sizing: border-box;
      width: 100%;
      height: 2.5rem;
      padding: 0 0.75rem;
    }
    .filter-field {
      display: grid;
      gap: 0.25rem;
      min-width: 0;
    }
    .filter-label {
      color: var(--eth-muted);
      font-size: 0.75rem;
      font-weight: 700;
      line-height: 1.2;
    }
    .clear:focus-visible,
    .search input:focus-visible,
    .filter-field select:focus-visible {
      outline: 2px solid var(--eth-accent);
      outline-offset: 2px;
    }
    .clear {
      justify-self: start;
      height: 2.5rem;
      padding: 0 0.75rem;
    }
    @container doc-filters (min-width: 44rem) {
      .doc-toolbar {
        grid-template-columns: minmax(12rem, 1fr) auto auto;
      }
      .filter-field {
        min-width: 12.5rem;
      }
      .clear {
        grid-column: 2 / -1;
        justify-self: end;
      }
    }
    .file-hint,
    .empty {
      margin: 0 0 0.75rem;
      color: var(--eth-muted);
      font-size: 0.9rem;
    }
    table {
      width: 100%;
    }
    th,
    td {
      padding: 0.4rem 0.65rem;
      vertical-align: middle;
    }
    th.name {
      width: 34%;
    }
    th.category {
      width: 7.5rem;
    }
    th.actions {
      width: 1%;
    }
    .category-badge {
      display: inline-flex;
      align-items: center;
      min-height: 1.35rem;
      padding: 0 0.45rem;
      border-radius: 999px;
      background: #f3f1ec;
      color: var(--eth-muted);
      font-size: 0.75rem;
      font-weight: 600;
      white-space: nowrap;
    }
    .file {
      display: block;
      margin-top: 0.1rem;
      color: var(--eth-muted);
      font-size: 0.78rem;
      line-height: 1.25;
    }
    .is-empty {
      color: var(--eth-muted);
    }
    .meta {
      white-space: nowrap;
    }
    .row-actions {
      display: inline-flex;
      flex-wrap: nowrap;
      align-items: center;
      gap: 0.35rem;
      white-space: nowrap;
    }
    .row-actions > * {
      flex: 0 0 auto;
    }
    .history li {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem;
    }
    .history {
      list-style: none;
      margin: 0.55rem 0 0;
      padding: 0;
      white-space: normal;
    }
    .feedback {
      margin: 0.45rem 0 0;
      font-size: 0.85rem;
    }
    .feedback.ok {
      color: var(--eth-success);
    }
    .feedback.error {
      color: var(--eth-danger);
    }
    .more {
      position: relative;
    }
    .more-button {
      flex: 0 0 auto;
    }
    .menu {
      position: absolute;
      right: 0;
      z-index: 5;
      display: grid;
      gap: 0.25rem;
      min-width: 12rem;
      margin-top: 0.25rem;
      padding: 0.35rem;
      background: var(--eth-surface);
      border: 1px solid var(--eth-border);
      border-radius: 8px;
    }
    .menu button.ghost,
    .menu eth-document-upload-action {
      width: 100%;
    }
    .table-wrap {
      overflow: visible;
    }
    @media (max-width: 720px) {
      .doc-toolbar {
        grid-template-columns: minmax(0, 1fr);
      }
      .clear {
        grid-column: auto;
        justify-self: start;
      }
      .row-actions {
        flex-wrap: wrap;
        white-space: normal;
      }
      table,
      thead,
      tbody,
      tr,
      td {
        display: block;
      }
      thead {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
      }
      tr {
        padding: 0.75rem 0;
        border-bottom: 1px solid var(--eth-border);
      }
      td {
        width: auto;
        padding: 0.15rem 0;
        white-space: normal;
        border: 0;
      }
      td[data-label]::before {
        content: attr(data-label);
        display: block;
        color: var(--eth-muted);
        font-size: 0.72rem;
        font-weight: 700;
        letter-spacing: 0.03em;
        text-transform: uppercase;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CommitteeDocumentsComponent {
  private readonly documents = inject(DocumentService);
  private readonly cases = inject(CommitteeCaseService);
  private readonly store = inject(EthicsStore);
  private readonly auth = inject(DemoAuthService);
  private readonly permissions = inject(PermissionService);
  private readonly pdf = inject(PdfGenerationService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly timers = new Set<number>();
  readonly caseId = input('');
  readonly projectId = input('');
  readonly source = input<DocumentMetadata[] | null>(null);
  readonly query = signal('');
  readonly categoryFilter = signal<CategoryFilter>('all');
  readonly loadFilter = signal<LoadFilter>('all');
  readonly open = signal<Record<string, boolean>>({});
  readonly menu = signal<string | null>(null);
  readonly phase = signal<Record<string, UploadPhase>>({});
  readonly errors = signal<Record<string, string>>({});
  readonly rows = computed(() => {
    const source = this.source();
    if (source) {
      return this.fromFiles(source);
    }
    const caseId = this.caseId();
    if (!caseId) {
      return [];
    }
    const item = this.cases.get(caseId);
    const latest = this.documents.latest(caseId);
    const requirements = item ? this.cases.requiredDocs(item.committee) : [];
    const used = new Set<string>();
    const editable = item ? this.documentsEditable(item.projectId, item.status, item.packageFrozen) : false;
    const rows = requirements.map((requirement) => {
      const current = latest.find((doc) => doc.requirementKey === requirement.key) ?? null;
      if (current) {
        used.add(current.id);
      }
      const history = this.documents.versions(caseId, requirement.key).filter((doc) => doc.id !== current?.id);
      return this.row(
        requirement.key,
        this.docLabel(requirement.key, requirement.label),
        current,
        history,
        editable && !current,
        editable && !!current && requirement.versionable,
        requirement.required,
        requirement.key
      );
    });
    for (const doc of latest) {
      if (used.has(doc.id) || (doc.kind !== 'REQUIRED' && doc.kind !== 'SUPPORT')) {
        continue;
      }
      const history = doc.requirementKey
        ? this.documents.versions(caseId, doc.requirementKey).filter((entry) => entry.id !== doc.id)
        : [];
      rows.push(this.row(doc.id, doc.name, doc, history, false, false, doc.kind === 'REQUIRED'));
    }
    return rows;
  });
  readonly searchedRows = computed(() => {
    const term = this.query().trim().toLowerCase();
    if (!term) {
      return this.rows();
    }
    return this.rows().filter((row) =>
      row.name.toLowerCase().includes(term) || (row.fileName?.toLowerCase().includes(term) ?? false)
    );
  });
  readonly counts = computed(() =>
    documentListCounts(this.searchedRows().map((row) => ({ required: row.required, loaded: !!row.doc })))
  );
  readonly visibleRows = computed(() => {
    const category = this.categoryFilter();
    const load = this.loadFilter();
    return this.searchedRows().filter((row) => {
      if (category === 'required' && !row.required) {
        return false;
      }
      if (category === 'optional' && row.required) {
        return false;
      }
      if (load === 'uploaded' && !row.doc) {
        return false;
      }
      if (load === 'missing' && row.doc) {
        return false;
      }
      return true;
    });
  });
  readonly formatHint = computed(() => this.rows().some((row) => row.canUpload || row.canVersion));

  filtersActive(): boolean {
    return !!this.query().trim() || this.categoryFilter() !== 'all' || this.loadFilter() !== 'all';
  }

  clearFilters(): void {
    this.query.set('');
    this.categoryFilter.set('all');
    this.loadFilter.set('all');
  }

  constructor() {
    this.destroyRef.onDestroy(() => {
      for (const timer of this.timers) {
        window.clearTimeout(timer);
      }
    });
  }

  @HostListener('document:click', ['$event'])
  closeMenuFromOutside(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (!target?.closest('.more')) {
      this.menu.set(null);
    }
  }

  @HostListener('document:keydown.escape')
  closeMenuFromKeyboard(): void {
    this.menu.set(null);
  }

  toggle(id: string): void {
    this.open.update((current) => ({ ...current, [id]: !current[id] }));
    this.menu.set(null);
  }

  toggleMenu(id: string, event: MouseEvent): void {
    event.stopPropagation();
    this.menu.update((current) => (current === id ? null : id));
  }

  upload(row: DocumentRow, file: File): void {
    const caseId = this.caseId();
    if (!caseId || !row.requirementKey || this.phase()[row.id] === 'loading') {
      return;
    }
    this.menu.set(null);
    this.errors.update((current) => ({ ...current, [row.id]: '' }));
    this.phase.update((current) => ({ ...current, [row.id]: 'loading' }));
    const requirementKey = row.requirementKey;
    const projectId = this.projectId() || undefined;
    const timer = window.setTimeout(() => {
      this.timers.delete(timer);
      const result = this.documents.simulateUpload('CASE', caseId, file, 'REQUIRED', requirementKey, projectId);
      if ('error' in result) {
        this.phase.update((current) => ({ ...current, [row.id]: 'error' }));
        this.errors.update((current) => ({ ...current, [row.id]: result.error }));
        return;
      }
      this.phase.update((current) => ({ ...current, [row.id]: 'success' }));
      const done = window.setTimeout(() => {
        this.timers.delete(done);
        this.phase.update((current) => ({ ...current, [row.id]: 'idle' }));
      }, 2500);
      this.timers.add(done);
    }, 400);
    this.timers.add(timer);
  }

  uploadName(row: DocumentRow): string {
    return `${this.i18n.translate('projects.uploadFile')}: ${row.name}`;
  }

  versionName(row: DocumentRow): string {
    return `${this.i18n.translate('projects.uploadNewVersion')}: ${row.name}`;
  }

  versionsOf(row: DocumentRow): DocumentMetadata[] {
    return row.doc ? [row.doc, ...row.history] : row.history;
  }

  versionNote(item: DocumentMetadata, row: DocumentRow): string {
    if (item.responseCycle) {
      return this.i18n.translate('projects.responseRound', { n: item.responseCycle });
    }
    const versions = this.versionsOf(row);
    const response = versions.find((entry) => entry.responseCycle);
    const reviewed = versions
      .filter((entry) => !entry.responseCycle)
      .sort((a, b) => b.version - a.version)[0];
    if (response?.responseCycle && reviewed?.id === item.id) {
      return this.i18n.translate('projects.evaluatedRound', { n: response.responseCycle });
    }
    return this.i18n.translate(item.id === row.doc?.id ? 'projects.currentDocument' : 'projects.previousVersion');
  }

  preview(doc: DocumentMetadata): void {
    this.pdf.open({
      title: doc.name,
      disclaimer: this.i18n.translate('pdf.prototypeDisclaimer'),
      rows: [
        { label: this.i18n.translate('projects.version'), value: `v${doc.version}` },
        { label: this.i18n.translate('projects.uploadedAt'), value: doc.uploadedAt }
      ],
      body: doc.name,
      stamp: doc.stampStatus === 'PROTOTYPE' ? this.i18n.translate('cases.stampPrototype') : undefined
    });
  }

  private fromFiles(files: DocumentMetadata[]): DocumentRow[] {
    const grouped = new Map<string, DocumentMetadata[]>();
    for (const file of files) {
      const key = file.requirementKey ?? file.id;
      grouped.set(key, [...(grouped.get(key) ?? []), file]);
    }
    return [...grouped.values()].map((items) => {
      const ordered = [...items].sort((a, b) => b.version - a.version);
      const current = ordered[0];
      return this.row(
        current.requirementKey ?? current.id,
        this.docLabel(current.requirementKey ?? '', current.name),
        current,
        ordered.slice(1),
        false,
        false,
        true,
        current.requirementKey
      );
    });
  }

  private row(
    id: string,
    name: string,
    doc: DocumentMetadata | null,
    history: DocumentMetadata[],
    canUpload: boolean,
    canVersion: boolean,
    required: boolean,
    requirementKey?: string
  ): DocumentRow {
    return {
      id,
      name,
      fileName: doc && doc.name !== name ? doc.name : null,
      version: doc?.version ?? null,
      uploadedAt: doc?.uploadedAt ?? null,
      statusKey: doc ? 'projects.currentDocument' : 'projects.notUploaded',
      doc,
      history,
      canUpload,
      canVersion,
      required,
      requirementKey
    };
  }

  private documentsEditable(projectId: string, status: NonNullable<ReturnType<CommitteeCaseService['get']>>['status'], packageFrozen: boolean): boolean {
    const user = this.auth.currentUser();
    const project = this.store.snapshot().projects.find((entry) => entry.id === projectId);
    const item = this.store.snapshot().cases.find((entry) => entry.projectId === projectId && entry.status === status);
    return caseDocumentsEditable({
      canRespond: this.permissions.can(user, 'RESPOND_TO_OBSERVATIONS', {
        projectId,
        committeeCaseId: item?.id,
        committeeType: item?.committee,
        workflowStatus: status
      }),
      isOwner: !!user && project?.researcherId === user.id,
      status,
      packageFrozen
    });
  }

  private docLabel(key: string, fallback: string): string {
    const translationKey = `catalogs.requiredDocs.${key}`;
    if (key && this.i18n.has(translationKey)) {
      return this.i18n.translate(translationKey);
    }
    if (this.i18n.has(fallback)) {
      return this.i18n.translate(fallback);
    }
    return fallback;
  }
}
