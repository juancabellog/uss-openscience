import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { COMMITTEE_LABELS, OFFICIAL_COMMITTEE_NAMES } from '../../mocks/catalogs';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { AuditService } from '../../core/services/audit.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { DocumentService } from '../../core/services/document.service';
import { ObservationService } from '../../core/services/observation.service';
import { ProjectService } from '../../core/services/project.service';
import { ResolutionService } from '../../core/services/resolution.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { PrivacyBadgeComponent } from '../../shared/components/privacy-badge.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-project-detail-page',
  imports: [RouterLink, FormsModule, StatusChipComponent, PrivacyBadgeComponent, ...I18N_IMPORTS],
  templateUrl: './project-detail.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectDetailPage {
  readonly id = input.required<string>();
  private readonly projects = inject(ProjectService);
  private readonly cases = inject(CommitteeCaseService);
  private readonly documents = inject(DocumentService);
  private readonly observations = inject(ObservationService);
  private readonly resolutions = inject(ResolutionService);
  private readonly audit = inject(AuditService);
  private readonly auth = inject(DemoAuthService);
  readonly i18n = inject(I18nService);
  readonly tab = signal<'resumen' | 'docs' | 'CEC' | 'CECUA' | 'BIOSEGURIDAD' | 'historial'>('resumen');
  readonly confirmCase = signal<string | null>(null);
  readonly answer = signal<Record<string, string>>({});
  readonly row = computed(() => this.projects.get(this.id()));
  readonly user = this.auth.currentUser;
  readonly committees = COMMITTEE_LABELS;
  readonly history = computed(() => this.audit.list(this.id()));
  readonly timelineKeys = [
    'projects.timeline.application',
    'projects.timeline.documents',
    'projects.timeline.send',
    'projects.timeline.admissibility',
    'projects.timeline.review',
    'projects.timeline.comments',
    'projects.timeline.response',
    'projects.timeline.session',
    'projects.timeline.resolution'
  ];

  docLabel(key: string, fallback: string): string {
    const translationKey = `catalogs.requiredDocs.${key}`;
    return this.i18n.has(translationKey) ? this.i18n.translate(translationKey) : fallback;
  }

  semaphoreLabel(code: string): string {
    return this.i18n.catalog('semaphore', code);
  }

  docs(caseId: string) {
    return this.documents.list(caseId);
  }

  required(caseId: string) {
    const item = this.cases.get(caseId);
    return item ? this.cases.requiredDocs(item.committee) : [];
  }

  completion(caseId: string) {
    return this.cases.completion(caseId);
  }

  obs(caseId: string) {
    return this.observations.byCase(caseId).filter((item) => item.status !== 'DRAFT');
  }

  resolution(caseId: string) {
    return this.resolutions.byCase(caseId);
  }

  expiry(caseId: string) {
    const item = this.cases.get(caseId);
    return item ? this.projects.expiryInfo(item) : null;
  }

  upload(caseId: string, key: string, event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      this.documents.simulateUpload('CASE', caseId, file, 'REQUIRED', key, this.id());
    }
  }

  askNotify(caseId: string): void {
    this.confirmCase.set(caseId);
  }

  notify(): void {
    const caseId = this.confirmCase();
    if (caseId) {
      this.cases.notifyCommittee(caseId);
    }
    this.confirmCase.set(null);
  }

  setAnswer(id: string, value: string): void {
    this.answer.update((current) => ({ ...current, [id]: value }));
  }

  reply(observationId: string): void {
    const text = this.answer()[observationId]?.trim();
    if (!text) {
      return;
    }
    this.observations.answer(observationId, text, []);
  }

  sendAnswers(caseId: string): void {
    const pending = this.obs(caseId).some((item) => item.status !== 'ANSWERED');
    if (!pending) {
      this.observations.submitAnswers(caseId);
    }
  }

  download(caseId: string): void {
    const resolution = this.resolution(caseId);
    const row = this.row();
    if (!resolution || !row) {
      return;
    }
    const html = this.resolutions.printableHtml(
      resolution,
      row.project.title,
      row.researcher?.displayName ?? '',
      OFFICIAL_COMMITTEE_NAMES[this.cases.get(caseId)?.committee ?? 'CEC']
    );
    const blob = new Blob([html], { type: 'text/html' });
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
  }
}
