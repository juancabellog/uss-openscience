import { KeyValuePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { COMMITTEE_LABELS, OFFICIAL_COMMITTEE_NAMES } from '../../mocks/catalogs';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { DocumentService } from '../../core/services/document.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { ObservationService } from '../../core/services/observation.service';
import { PdfGenerationService } from '../../core/services/pdf-generation.service';
import { ResolutionService } from '../../core/services/resolution.service';
import { ReviewService } from '../../core/services/review.service';
import { ReviewerService } from '../../core/services/reviewer.service';
import { SessionService } from '../../core/services/session.service';
import { DocumentMetadata, ResolutionKind, Review } from '../../core/models/ethics.models';
import {
  canAssignReviewers,
  canConsolidate,
  canPrepareResolution,
  canValidateResolution,
  sectionsFor,
  seesInternalReviews
} from '../../core/workflow/case-workflow';
import { PrivacyBadgeComponent } from '../../shared/components/privacy-badge.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';

@Component({
  selector: 'eth-case-detail-page',
  imports: [FormsModule, RouterLink, PrivacyBadgeComponent, StatusChipComponent, KeyValuePipe, ...I18N_IMPORTS],
  templateUrl: './case-detail.page.html',
  styleUrl: './case-detail.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CaseDetailPage {
  readonly id = input.required<string>();
  private readonly cases = inject(CommitteeCaseService);
  private readonly store = inject(EthicsStore);
  private readonly reviewers = inject(ReviewerService);
  private readonly reviews = inject(ReviewService);
  private readonly observations = inject(ObservationService);
  private readonly sessions = inject(SessionService);
  private readonly resolutionApi = inject(ResolutionService);
  private readonly documents = inject(DocumentService);
  private readonly pdf = inject(PdfGenerationService);
  private readonly auth = inject(DemoAuthService);
  readonly i18n = inject(I18nService);
  readonly user = this.auth.currentUser;
  readonly committees = COMMITTEE_LABELS;
  readonly rev1 = signal('');
  readonly rev2 = signal('');
  readonly sessionId = signal('');
  readonly extraDate = signal('');
  readonly extraTime = signal('10:00');
  readonly methodology = signal('');
  readonly text = signal('');
  readonly risks = signal('');
  readonly kind = signal<ResolutionKind>('APPROVED');
  readonly resolutionText = signal('');
  readonly selectedDocs = signal<Record<string, boolean>>({});
  readonly confirmPublish = signal(false);
  readonly publishNotice = signal('');
  readonly confirmReturn = signal(false);
  readonly returnReason = signal('');
  readonly returnError = signal(false);
  readonly presidentReturn = signal(false);
  readonly previewOpen = signal(false);
  readonly item = computed(() => this.cases.get(this.id()));
  readonly project = computed(() => this.store.snapshot().projects.find((row) => row.id === this.item()?.projectId));
  readonly diseiCode = computed(() => this.project()?.code ?? '');
  readonly sections = computed(() => {
    const item = this.item();
    return item ? sectionsFor(item.status) : null;
  });
  readonly internal = computed(() => seesInternalReviews(this.user()?.role));
  readonly mayAssign = computed(() => canAssignReviewers(this.user()?.role));
  readonly mayConsolidate = computed(() => canConsolidate(this.user()?.role));
  readonly mayPrepare = computed(() => canPrepareResolution(this.user()?.role));
  readonly mayValidate = computed(() => canValidateResolution(this.user()?.role));
  readonly researcherView = computed(() => this.user()?.role === 'RESEARCHER');
  readonly officerView = computed(() => {
    const role = this.user()?.role;
    return role === 'EXECUTIVE_SECRETARY' || role === 'COMMITTEE_PRESIDENT' || role === 'ADMINISTRATOR';
  });
  readonly assignedMember = computed(() => {
    const user = this.user();
    const item = this.item();
    return !!user && user.role === 'COMMITTEE_MEMBER' && !!item && item.reviewerIds.includes(user.id);
  });
  readonly denied = computed(() => {
    const user = this.user();
    const item = this.item();
    return !!user && !!item && user.role === 'COMMITTEE_MEMBER' && !item.reviewerIds.includes(user.id);
  });
  readonly waitingResearcher = computed(() => this.item()?.status === 'AWAITING_RESEARCHER');
  readonly openCycle = computed(() => {
    const current = this.item()?.currentCycle;
    return this.cycles().find((item) => item.cycleNumber === current && item.status !== 'PUBLISHED');
  });
  readonly reviewsReady = computed(() => {
    const item = this.item();
    if (!item || item.status === 'AWAITING_RESEARCHER') {
      return false;
    }
    const state = this.store.state();
    const assignments = state.assignments.filter((assignment) => assignment.caseId === item.id && assignment.status !== 'REJECTED');
    if (!assignments.length) {
      return false;
    }
    const reviews = state.reviews.filter((review) => review.caseId === item.id && review.cycleNumber === item.currentCycle && review.submittedAt);
    return assignments.every((assignment) => reviews.some((review) => review.assignmentId === assignment.id || review.reviewerId === assignment.reviewerId));
  });
  readonly roundConsolidated = computed(() => !!this.openCycle()?.consolidatedAt);
  readonly publishedCycles = computed(() => this.cycles().filter((item) => item.status === 'PUBLISHED' || !!item.closedAt));
  readonly reviewerOptions = computed(() => {
    const committee = this.item()?.committee;
    return committee ? this.reviewers.catalog(committee) : [];
  });
  readonly sessionOptions = computed(() => {
    const committee = this.item()?.committee;
    return committee ? this.sessions.list(committee) : [];
  });
  readonly currentReviews = computed(() => {
    const user = this.user();
    const cycle = this.item()?.currentCycle;
    return user ? this.reviews.listForCase(this.id(), user.id, user.role, cycle) : [];
  });
  readonly cycles = computed(() =>
    this.store.snapshot().cycles
      .filter((item) => item.caseId === this.id())
      .sort((a, b) => b.cycleNumber - a.cycleNumber)
  );
  readonly published = computed(() => this.observations.publishedForResearcher(this.id()));
  readonly resolution = computed(() => this.resolutionApi.byCase(this.id()));
  readonly resolutions = computed(() => this.resolutionApi.listByCase(this.id()));
  readonly latestDocuments = computed(() => this.documents.latest(this.id()).filter((item) => item.kind !== 'OBSERVATION'));

  constructor() {
    effect(() => {
      const cycle = this.openCycle();
      if (!cycle?.consolidatedAt) {
        return;
      }
      this.methodology.set(cycle.consolidatedMethodology ?? '');
      this.text.set(cycle.consolidatedObservations ?? '');
      this.risks.set(cycle.consolidatedRisks ?? '');
    });
  }

  admit(ok: boolean): void {
    this.cases.admissibility(this.id(), ok);
  }

  askReturn(): void {
    this.returnReason.set('');
    this.returnError.set(false);
    this.confirmReturn.set(true);
  }

  cancelReturn(): void {
    this.confirmReturn.set(false);
    this.presidentReturn.set(false);
    this.returnError.set(false);
  }

  confirmAdmitReturn(): void {
    const reason = this.returnReason().trim();
    if (!reason) {
      this.returnError.set(true);
      return;
    }
    if (this.presidentReturn()) {
      this.resolutionApi.returnToSecretary(this.id(), reason);
    } else {
      this.cases.admissibility(this.id(), false, reason);
    }
    this.confirmReturn.set(false);
    this.presidentReturn.set(false);
  }

  askPresidentReturn(): void {
    this.presidentReturn.set(true);
    this.askReturn();
  }

  createExtra(): void {
    const item = this.item();
    if (!item || !this.extraDate()) {
      return;
    }
    const session = this.sessions.create(item.committee, this.extraDate(), this.extraTime(), 'EXTRAORDINARY');
    this.sessionId.set(session.id);
  }

  assign(): void {
    if (!this.rev1() || !this.rev2() || this.rev1() === this.rev2()) {
      return;
    }
    this.reviewers.assign(this.id(), this.rev1(), this.rev2(), this.sessionId() || undefined);
  }

  consolidate(): void {
    const draft = this.observations.consolidate(this.id());
    this.methodology.set(draft.methodology);
    this.text.set(draft.text);
    this.risks.set(draft.risks);
  }

  askPublish(): void {
    if (!this.publishNotice()) {
      this.publishNotice.set(this.i18n.translate('cases.publishDefault'));
    }
    this.confirmPublish.set(true);
  }

  publish(): void {
    const notice = this.publishNotice().trim();
    if (!notice || !this.text().trim()) {
      return;
    }
    this.observations.publish(this.id(), notice, {
      methodology: this.methodology(),
      text: this.text(),
      risks: this.risks()
    });
    this.methodology.set('');
    this.text.set('');
    this.risks.set('');
    this.confirmPublish.set(false);
  }

  previewObservation(): void {
    const item = this.item();
    const project = this.project();
    this.pdf.open({
      title: this.i18n.translate('cases.roundLabel', { n: item?.currentCycle ?? 1 }),
      disclaimer: this.i18n.translate('pdf.prototypeDisclaimer'),
      rows: [
        { label: this.i18n.translate('cases.committee'), value: this.i18n.translate(COMMITTEE_LABELS[item?.committee ?? 'CEC']) },
        { label: this.i18n.translate('classification.project'), value: project?.title ?? '' }
      ],
      body: [this.methodology(), this.text(), this.risks()].filter(Boolean).join('\n\n')
    });
  }

  previewCycle(cycleNumber: number): void {
    const cycle = this.cycles().find((item) => item.cycleNumber === cycleNumber);
    const observation = this.observations.byCase(this.id()).find((item) => item.cycleNumber === cycleNumber && item.status !== 'DRAFT');
    this.pdf.open({
      title: this.i18n.translate('cases.roundLabel', { n: cycleNumber }),
      disclaimer: this.i18n.translate('pdf.prototypeDisclaimer'),
      rows: [{ label: this.i18n.translate('cases.committee'), value: this.item()?.code ?? '' }],
      body: observation?.text || cycle?.consolidatedObservations || ''
    });
  }

  toggleDoc(id: string, checked: boolean): void {
    this.selectedDocs.update((current) => ({ ...current, [id]: checked }));
  }

  prepare(): void {
    const selected = Object.entries(this.selectedDocs()).filter(([, on]) => on).map(([id]) => id);
    this.resolutionApi.prepare(this.id(), this.kind(), this.resolutionText(), selected);
  }

  previewResolution(): void {
    const item = this.item();
    const project = this.project();
    const resolution = this.resolution();
    this.pdf.open({
      title: resolution?.correlative ?? this.i18n.translate('cases.prepareResolution'),
      disclaimer: this.i18n.translate('pdf.prototypeDisclaimer'),
      rows: [
        { label: this.i18n.translate('classification.project'), value: project?.title ?? '' },
        { label: this.i18n.translate('cases.committee'), value: OFFICIAL_COMMITTEE_NAMES[item?.committee ?? 'CEC'] }
      ],
      body: this.resolutionText() || resolution?.text || '',
      signature: resolution?.signatureStatus === 'PROTOTYPE' ? this.i18n.translate('cases.signaturePrototype') : undefined,
      stamp: resolution?.stampStatus === 'PROTOTYPE' || resolution?.workflow === 'ISSUED'
        ? this.i18n.translate('cases.stampPrototype')
        : undefined
    });
  }

  previewDocument(doc: DocumentMetadata): void {
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

  validate(): void {
    this.resolutionApi.validate(this.id());
  }

  reviewLabel(review: Review, index: number): string {
    return this.i18n.translate('cases.reviewN', { n: index + 1 });
  }
}
