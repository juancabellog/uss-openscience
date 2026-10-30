import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { isDataManagementPlan } from '../../core/documents/data-management-plan';
import { I18nService } from '../../core/i18n/i18n.service';
import { DocumentMetadata } from '../../core/models/ethics.models';
import { DocumentService } from '../../core/services/document.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { EthicsSubmissionService } from '../../core/services/ethics-submission.service';
import { PdfGenerationService } from '../../core/services/pdf-generation.service';
import { PermissionService } from '../../core/services/permission.service';
import { caseDocumentsEditable } from '../../core/workflow/committee-workspace.policy';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { DocumentUploadActionComponent } from './document-upload-action.component';

@Component({
  selector: 'eth-request-documents',
  imports: [DocumentUploadActionComponent, ...I18N_IMPORTS],
  template: `
    @if (visible(); as view) {
      <section class="request-docs" aria-labelledby="request-documents-heading">
        <h2 id="request-documents-heading">{{ 'documents.transversal' | translate }}</h2>

        <section class="block" aria-labelledby="data-management-plan-heading">
          <h3 id="data-management-plan-heading">{{ 'documents.dataManagementPlan' | translate }}</h3>
          @if (view.current; as current) {
            <p class="status" role="status">{{ 'documents.loadedVersion' | translate: { version: current.version } }}</p>
            <p class="file">{{ current.name }}</p>
            <p class="meta">
              {{ 'documents.version' | translate }} {{ current.version }}
              · {{ 'documents.uploadedOn' | translate: { date: (current.uploadedAt | localeDate) } }}
              @if (personName(current.uploadedBy); as person) {
                · {{ 'documents.uploadedBy' | translate: { name: person } }}
              }
            </p>
            <div class="actions">
              @if (view.canView) {
                <button type="button" class="ghost" (click)="preview(current)">{{ 'common.view' | translate }}</button>
              }
              @if (view.canDownload) {
                <button type="button" class="ghost" (click)="preview(current)">{{ 'documents.download' | translate }}</button>
              }
              @if (view.canVersion) {
                <eth-document-upload-action
                  labelKey="documents.uploadNewVersion"
                  [accessibleName]="'documents.uploadNewVersion' | translate"
                  [busy]="planPhase() === 'loading'"
                  [error]="planError()"
                  (selected)="uploadPlan($event)"
                />
              }
            </div>
            @if (view.canHistory && view.history.length) {
              <h4>{{ 'documents.history' | translate }}</h4>
              <ul class="history">
                @for (item of view.history; track item.id) {
                  <li>
                    <p class="file">{{ item.name }}</p>
                    <p class="meta">
                      {{ 'documents.version' | translate }} {{ item.version }}
                      · {{ 'documents.uploadedOn' | translate: { date: (item.uploadedAt | localeDate) } }}
                      @if (personName(item.uploadedBy); as person) {
                        · {{ 'documents.uploadedBy' | translate: { name: person } }}
                      }
                    </p>
                    <div class="actions">
                      @if (view.canView) {
                        <button type="button" class="ghost" (click)="preview(item)">{{ 'common.view' | translate }}</button>
                      }
                      @if (view.canDownload) {
                        <button type="button" class="ghost" (click)="preview(item)">{{ 'documents.download' | translate }}</button>
                      }
                    </div>
                  </li>
                }
              </ul>
            }
          } @else {
            <p class="status" role="status">{{ 'documents.noneUploaded' | translate }}</p>
            @if (view.canUpload) {
              <eth-document-upload-action
                labelKey="documents.uploadDataManagementPlan"
                [accessibleName]="'documents.uploadDataManagementPlan' | translate"
                [busy]="planPhase() === 'loading'"
                [error]="planError()"
                (selected)="uploadPlan($event)"
              />
            }
          }
        </section>

        <section class="block" aria-labelledby="other-documents-heading">
          <h3 id="other-documents-heading">{{ 'documents.other' | translate }}</h3>
          @if (!view.others.length) {
            <p class="status">{{ 'documents.noneUploaded' | translate }}</p>
          }
          @if (view.others.length) {
            <ul class="history">
              @for (item of view.others; track item.id) {
                <li>
                  <p class="file">{{ item.name }}</p>
                  <p class="meta">
                    {{ 'documents.version' | translate }} {{ item.version }}
                    · {{ 'documents.uploadedOn' | translate: { date: (item.uploadedAt | localeDate) } }}
                  </p>
                </li>
              }
            </ul>
          }
          @if (view.canUploadOther) {
            <eth-document-upload-action
              labelKey="documents.attachOther"
              [accessibleName]="'documents.attachOther' | translate"
              [busy]="otherPhase() === 'loading'"
              [error]="otherError()"
              (selected)="uploadOther($event)"
            />
          }
        </section>
      </section>
    }
  `,
  styles: `
    :host { display: block; }
    .request-docs { margin: 0 0 1rem; }
    h2 {
      margin: 0 0 0.45rem;
      font-size: 0.78rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--eth-muted);
    }
    h3 { margin: 0 0 0.35rem; font-size: 1.05rem; }
    h4 { margin: 0.85rem 0 0.35rem; font-size: 0.92rem; }
    .block + .block { margin-top: 1rem; }
    .status, .file, .meta { margin: 0.15rem 0; }
    .file { font-weight: 650; }
    .meta { color: var(--eth-muted); }
    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem;
      margin-top: 0.45rem;
    }
    .history { margin: 0.35rem 0 0; padding: 0; list-style: none; }
    .history li { margin: 0 0 0.7rem; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RequestDocumentsComponent {
  readonly projectId = input.required<string>();
  private readonly documents = inject(DocumentService);
  private readonly submissions = inject(EthicsSubmissionService);
  private readonly store = inject(EthicsStore);
  private readonly auth = inject(DemoAuthService);
  private readonly permissions = inject(PermissionService);
  private readonly pdf = inject(PdfGenerationService);
  private readonly i18n = inject(I18nService);
  private readonly timers = new Set<number>();
  readonly planPhase = signal<'idle' | 'loading' | 'error'>('idle');
  readonly planError = signal('');
  readonly otherPhase = signal<'idle' | 'loading' | 'error'>('idle');
  readonly otherError = signal('');

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      for (const timer of this.timers) {
        window.clearTimeout(timer);
      }
    });
  }

  readonly visible = computed(() => {
    const projectId = this.projectId();
    const user = this.auth.currentUser();
    const submission = this.submissions.byProject(projectId);
    if (!user || !submission || !this.permissions.can(user, 'VIEW_DOCUMENTS', { projectId })) {
      return null;
    }
    const plan = this.documents.dataManagementPlan(submission.id);
    const editable = this.documentsOpen(projectId);
    const canUpload = editable && this.permissions.can(user, 'UPLOAD_DOCUMENTS', { projectId });
    const canVersion = editable && this.permissions.can(user, 'UPLOAD_DOCUMENT_VERSION', { projectId });
    return {
      current: plan.current,
      history: plan.history,
      others: this.documents.latest(submission.id).filter((item) => !isDataManagementPlan(item.documentType)),
      canView: true,
      canDownload: this.permissions.can(user, 'DOWNLOAD_DOCUMENTS', { projectId }),
      canHistory: this.permissions.can(user, 'VIEW_DOCUMENT_VERSIONS', { projectId }),
      canUpload: canUpload && !plan.current,
      canVersion: canVersion && !!plan.current,
      canUploadOther: canUpload
    };
  });

  personName(userId: string | undefined): string | null {
    if (!userId) {
      return null;
    }
    return this.store.snapshot().users.find((user) => user.id === userId)?.displayName ?? null;
  }

  preview(doc: DocumentMetadata): void {
    this.pdf.open({
      title: doc.name,
      disclaimer: this.i18n.translate('pdf.prototypeDisclaimer'),
      rows: [
        { label: this.i18n.translate('documents.version'), value: String(doc.version) },
        { label: this.i18n.translate('projects.uploadedAt'), value: doc.uploadedAt }
      ],
      body: doc.name
    });
  }

  uploadPlan(file: File): void {
    this.upload(file, 'plan');
  }

  uploadOther(file: File): void {
    this.upload(file, 'other');
  }

  private upload(file: File, channel: 'plan' | 'other'): void {
    const submission = this.submissions.byProject(this.projectId());
    const phase = channel === 'plan' ? this.planPhase : this.otherPhase;
    const error = channel === 'plan' ? this.planError : this.otherError;
    if (!submission || phase() === 'loading') {
      return;
    }
    error.set('');
    phase.set('loading');
    const timer = window.setTimeout(() => {
      this.timers.delete(timer);
      const result = channel === 'plan'
        ? this.documents.uploadDataManagementPlan(submission.id, file, this.projectId())
        : this.documents.uploadOther(submission.id, file, this.projectId());
      if ('error' in result) {
        phase.set('error');
        error.set(result.error);
        return;
      }
      phase.set('idle');
    }, 400);
    this.timers.add(timer);
  }

  private documentsOpen(projectId: string): boolean {
    const user = this.auth.currentUser();
    const state = this.store.snapshot();
    const project = state.projects.find((item) => item.id === projectId);
    const cases = state.cases.filter((item) => item.projectId === projectId);
    if (!cases.length) {
      return true;
    }
    return cases.some((item) => caseDocumentsEditable({
      canRespond: this.permissions.can(user, 'RESPOND_TO_OBSERVATIONS', {
        projectId,
        committeeCaseId: item.id,
        committeeType: item.committee,
        workflowStatus: item.status
      }),
      isOwner: !!user && project?.researcherId === user.id,
      status: item.status,
      packageFrozen: item.packageFrozen
    }));
  }
}
