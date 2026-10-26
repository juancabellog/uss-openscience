import { KeyValuePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { DocumentService } from '../../core/services/document.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { PdfGenerationService } from '../../core/services/pdf-generation.service';
import { ReviewService } from '../../core/services/review.service';
import { ReviewerService } from '../../core/services/reviewer.service';
import { PrivacyBadgeComponent } from '../../shared/components/privacy-badge.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-review-detail-page',
  imports: [PrivacyBadgeComponent, KeyValuePipe, ...I18N_IMPORTS],
  template: `
    @if (allowed() && assignment(); as asg) {
      <section class="panel">
        <h1>{{ title() }}</h1>
        <p>{{ code() }} · <span>{{ ('catalogs.assignmentStatus.' + asg.status) | translate }}</span></p>
        <p>{{ 'reviews.responsibleInvestigator' | translate }}: {{ investigator() }}</p>
        <p>{{ 'reviews.fundingSource' | translate }}: {{ funding() }}</p>
        <eth-privacy-badge level="COMMITTEE_ONLY" />
        <h2>{{ 'reviews.documents' | translate }}</h2>
        @for (doc of docs(); track doc.id) {
          <p>{{ doc.name }} · v{{ doc.version }} · {{ doc.uploadedAt | localeDate }}
            <button type="button" class="ghost" (click)="download(doc.name, doc.version, doc.uploadedAt)">{{ 'projects.download' | translate }}</button>
          </p>
        }
        <p class="hint">{{ 'reviews.formProvisional' | translate }}</p>
        @if (asg.status === 'PENDING') {
          <div class="actions">
            <button type="button" class="btn" (click)="accepting.set(true)">{{ 'reviews.accept' | translate }}</button>
            <button type="button" class="secondary" (click)="rejecting.set(true)">{{ 'reviews.reject' | translate }}</button>
          </div>
        }
        @if (isAssignee() && (asg.status === 'ACCEPTED' || asg.status === 'COMPLETED') && !currentReview()?.submittedAt) {
          <label class="field"><span class="label-caption">{{ 'reviews.methodology' | translate }}</span><textarea [value]="methodology()" (input)="methodology.set($any($event.target).value)"></textarea></label>
          <label class="field"><span class="label-caption">{{ 'reviews.risks' | translate }}</span><textarea [value]="risks()" (input)="risks.set($any($event.target).value)"></textarea></label>
          <label class="field"><span class="label-caption">{{ 'reviews.notes' | translate }}</span><textarea [value]="notes()" (input)="notes.set($any($event.target).value)"></textarea></label>
          <div class="actions">
            <button type="button" class="secondary" (click)="save(false)">{{ 'reviews.saveDraft' | translate }}</button>
            <button type="button" class="btn" (click)="save(true)">{{ 'reviews.submitReview' | translate }}</button>
          </div>
        }
        @if (currentReview()?.submittedAt) {
          <p class="alert">{{ 'reviews.submittedReadonly' | translate }}</p>
          @for (entry of currentReview()?.criteria | keyvalue; track entry.key) {
            <p><strong>{{ ('reviews.criteria.' + entry.key) | translate }}</strong>: {{ entry.value }}</p>
          }
        }
      </section>
    }
    @if (rejecting()) {
      <div class="modal"><div class="modal-card">
        <p>{{ 'reviews.rejectReason' | translate }}</p>
        <textarea [value]="reason()" (input)="reason.set($any($event.target).value)"></textarea>
        <div class="actions">
          <button type="button" class="btn" (click)="reject()">{{ 'reviews.confirmReject' | translate }}</button>
          <button type="button" class="ghost" (click)="rejecting.set(false)">{{ 'common.cancel' | translate }}</button>
        </div>
      </div></div>
    }
    @if (accepting()) {
      <div class="modal"><div class="modal-card">
        <p>{{ 'reviews.declare' | translate }}</p>
        <div class="actions">
          <button type="button" class="btn" (click)="accept(false)">{{ 'reviews.noConflict' | translate }}</button>
          <button type="button" class="secondary" (click)="accept(true)">{{ 'reviews.hasConflict' | translate }}</button>
        </div>
      </div></div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReviewDetailPage {
  readonly id = input.required<string>();
  private readonly assignments = inject(ReviewerService);
  private readonly reviews = inject(ReviewService);
  private readonly cases = inject(CommitteeCaseService);
  private readonly store = inject(EthicsStore);
  private readonly documents = inject(DocumentService);
  private readonly pdf = inject(PdfGenerationService);
  private readonly auth = inject(DemoAuthService);
  private readonly i18n = inject(I18nService);
  readonly rejecting = signal(false);
  readonly accepting = signal(false);
  readonly reason = signal('');
  readonly methodology = signal('');
  readonly risks = signal('');
  readonly notes = signal('');
  readonly assignment = computed(() => this.assignments.getAssignment(this.id()));
  readonly allowed = computed(() => {
    const user = this.auth.currentUser();
    const assignment = this.assignment();
    if (!user || !assignment) {
      return false;
    }
    if (user.role === 'COMMITTEE_MEMBER') {
      return assignment.reviewerId === user.id;
    }
    return user.role === 'EXECUTIVE_SECRETARY' || user.role === 'COMMITTEE_PRESIDENT' || user.role === 'ADMINISTRATOR';
  });
  readonly isAssignee = computed(() => {
    const user = this.auth.currentUser();
    return !!user && user.id === this.assignment()?.reviewerId;
  });
  readonly currentReview = computed(() => {
    const user = this.auth.currentUser();
    const assignment = this.assignment();
    if (!user || !assignment || !this.allowed()) {
      return undefined;
    }
    const cycle = this.cases.get(assignment.caseId)?.currentCycle ?? 1;
    const review = this.store.state().reviews.find((item) => item.assignmentId === assignment.id && item.cycleNumber === cycle);
    if (!review || (user.role === 'COMMITTEE_MEMBER' && review.reviewerId !== user.id)) {
      return undefined;
    }
    return review;
  });
  readonly project = computed(() => {
    const item = this.cases.get(this.assignment()?.caseId ?? '');
    return this.store.snapshot().projects.find((row) => row.id === item?.projectId);
  });
  readonly title = computed(() => this.project()?.title ?? this.i18n.translate('reviews.defaultTitle'));
  readonly code = computed(() => this.cases.get(this.assignment()?.caseId ?? '')?.code ?? '');
  readonly investigator = computed(() => {
    const project = this.project();
    return this.store.snapshot().users.find((user) => user.id === project?.researcherId)?.displayName ?? '';
  });
  readonly funding = computed(() => this.project()?.fundingSource ?? '');
  readonly docs = computed(() => this.documents.latest(this.assignment()?.caseId ?? ''));

  accept(conflict: boolean): void {
    if (!this.allowed()) {
      return;
    }
    this.assignments.decide(this.id(), true, undefined, conflict);
    this.accepting.set(false);
  }

  reject(): void {
    if (!this.allowed() || !this.reason().trim()) {
      return;
    }
    this.assignments.decide(this.id(), false, this.reason());
    this.rejecting.set(false);
  }

  download(name: string, version: number, uploadedAt: string): void {
    this.pdf.open({
      title: name,
      disclaimer: this.i18n.translate('pdf.prototypeDisclaimer'),
      rows: [
        { label: this.i18n.translate('projects.version'), value: `v${version}` },
        { label: this.i18n.translate('projects.uploadedAt'), value: uploadedAt }
      ],
      body: name
    });
  }

  save(submit: boolean): void {
    if (!this.allowed() || !this.isAssignee() || this.currentReview()?.submittedAt) {
      return;
    }
    this.reviews.save(this.id(), {
      metodologia: this.methodology(),
      riesgos: this.risks(),
      observaciones: this.notes()
    }, submit);
  }
}
