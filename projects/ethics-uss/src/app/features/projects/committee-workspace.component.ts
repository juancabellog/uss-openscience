import { KeyValuePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, afterEveryRender, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { COMMITTEE_LABELS, COMMITTEE_SHORT_LABELS, OFFICIAL_COMMITTEE_NAMES } from '../../mocks/catalogs';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { AuditEvent, DocumentMetadata, ResolutionKind, Review, ReviewerAssignment, User } from '../../core/models/ethics.models';
import { isOperationalReviewStatus } from '../../core/workflow/review-assignment-status';
import { AuditService } from '../../core/services/audit.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { DocumentService } from '../../core/services/document.service';
import { EthicsSubmissionService } from '../../core/services/ethics-submission.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { PermissionService } from '../../core/services/permission.service';
import { ObservationService } from '../../core/services/observation.service';
import { PdfGenerationService } from '../../core/services/pdf-generation.service';
import { ProjectService } from '../../core/services/project.service';
import { ResolutionService } from '../../core/services/resolution.service';
import { ReviewService } from '../../core/services/review.service';
import { ReviewerService } from '../../core/services/reviewer.service';
import { SessionService } from '../../core/services/session.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { CommitteeWorkspaceVisibility, historyEventVisible, researcherResponseOpen, workspaceVisibility } from '../../core/workflow/committee-workspace.policy';
import { operationalStatusKey } from './committee-presentation';
import { PrivacyBadgeComponent } from '../../shared/components/privacy-badge.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { CommitteeDocumentsComponent } from './committee-documents.component';
import { ReviewerComboboxComponent, ReviewerOption } from './reviewer-combobox.component';
import { CommitteeHistoryEventView, CommitteeHistoryTimelineComponent } from './committee-history-timeline.component';

interface WorkspaceAccess extends CommitteeWorkspaceVisibility {
  reviewDesk: boolean;
  reviewerOnly: boolean;
  canAssign: boolean;
  canReassign: boolean;
  assignedNote: boolean;
  canViewSavedConsolidation: boolean;
  canEditConsolidation: boolean;
  canSaveConsolidation: boolean;
  canPublish: boolean;
}

@Component({
  selector: 'eth-committee-workspace',
  imports: [RouterLink, KeyValuePipe, PrivacyBadgeComponent, StatusChipComponent, CommitteeDocumentsComponent, CommitteeHistoryTimelineComponent, ReviewerComboboxComponent, ...I18N_IMPORTS],
  templateUrl: './committee-workspace.component.html',
  styleUrl: './committee-workspace.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CommitteeWorkspaceComponent {
  readonly caseId = input.required<string>();
  /** En /expedientes un miembro no asignado sigue viendo el aviso de acceso restringido. */
  readonly enforceMembership = input(false);
  private readonly cases = inject(CommitteeCaseService);
  private readonly store = inject(EthicsStore);
  private readonly projects = inject(ProjectService);
  private readonly submissions = inject(EthicsSubmissionService);
  private readonly documents = inject(DocumentService);
  private readonly observations = inject(ObservationService);
  private readonly resolutions = inject(ResolutionService);
  private readonly reviews = inject(ReviewService);
  private readonly reviewers = inject(ReviewerService);
  private readonly sessions = inject(SessionService);
  private readonly audit = inject(AuditService);
  private readonly pdf = inject(PdfGenerationService);
  private readonly auth = inject(DemoAuthService);
  private readonly permissions = inject(PermissionService);
  readonly i18n = inject(I18nService);
  readonly committees = COMMITTEE_LABELS;
  readonly user = this.auth.currentUser;
  readonly rev1 = signal('');
  readonly rev2 = signal('');
  readonly reassigning = signal<1 | 2 | null>(null);
  readonly reassignCandidate = signal('');
  readonly reassignReason = signal('');
  readonly reassignError = signal<'' | 'invalid' | 'forbidden' | 'closed' | 'same' | 'duplicate'>('');
  readonly reassignNotice = signal('');
  readonly sessionId = signal('');
  readonly extraDate = signal('');
  readonly extraTime = signal('10:00');
  readonly methodology = signal('');
  readonly draftSaved = signal(false);
  readonly responseError = signal(false);
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
  readonly confirmValidate = signal(false);
  readonly confirmNotify = signal(false);
  readonly answer = signal<Record<string, string>>({});
  readonly item = computed(() => this.cases.get(this.caseId()));
  readonly operationalKey = computed(() => {
    const item = this.item();
    return item ? operationalStatusKey(item) : null;
  });
  readonly relatedRecords = computed(() => {
    const item = this.item();
    if (!item) {
      return [];
    }
    const user = this.user();
    return this.store.snapshot().cases
      .filter((other) => other.projectId === item.projectId && other.committee === item.committee && other.id !== item.id)
      .filter((other) => {
        if (!user || user.role !== 'REVIEWER') {
          return true;
        }
        return this.permissions.can(user, 'VIEW_COMMITTEE_WORKSPACE', {
          projectId: other.projectId,
          committeeCaseId: other.id,
          committeeType: other.committee,
          workflowStatus: other.status
        });
      })
      .sort((a, b) => b.receivedAt.localeCompare(a.receivedAt));
  });
  readonly project = computed(() => this.store.snapshot().projects.find((row) => row.id === this.item()?.projectId));
  readonly responseOpen = computed(() => {
    const item = this.item();
    const user = this.user();
    const project = this.project();
    if (!item || !user || !project) {
      return false;
    }
    return researcherResponseOpen({
      canRespond: this.permissions.can(user, 'RESPOND_TO_OBSERVATIONS', {
        projectId: item.projectId,
        committeeCaseId: item.id,
        committeeType: item.committee,
        workflowStatus: item.status
      }),
      isOwner: project.researcherId === user.id,
      status: item.status
    });
  });
  readonly responseSent = computed(() => {
    const item = this.item();
    const user = this.user();
    const project = this.project();
    if (!item || !user || !project || project.researcherId !== user.id || item.status !== 'IN_REVIEW') {
      return false;
    }
    return this.store.snapshot().observations.some((observation) =>
      observation.caseId === item.id && observation.privacy === 'RESEARCHER_VISIBLE' && observation.status === 'ANSWERED'
    );
  });
  readonly assignedMember = computed(() => {
    const user = this.user();
    const item = this.item();
    if (!user || !item) {
      return false;
    }
    return this.reviewers.currentBySlot(item.id).some((slot) =>
      !!slot.assignment && slot.assignment.reviewerId === user.id && isOperationalReviewStatus(slot.assignment.status)
    );
  });
  readonly denied = computed(() => {
    const user = this.user();
    const item = this.item();
    if (!user || !item) {
      return false;
    }
    const context = {
      projectId: item.projectId,
      committeeCaseId: item.id,
      committeeType: item.committee,
      workflowStatus: item.status
    };
    return !this.permissions.can(user, 'VIEW_COMMITTEE_WORKSPACE', context)
      && !this.permissions.can(user, 'VIEW_PROJECTS', context);
  });
  readonly visibility = computed<WorkspaceAccess | null>(() => {
    const item = this.item();
    const user = this.user();
    if (!item) {
      return null;
    }
    const base = workspaceVisibility(user?.role, item.status, this.assignedMember());
    if (!user) {
      return {
        ...base,
        reviewDesk: false,
        reviewerOnly: false,
        canAssign: false,
        canReassign: false,
        assignedNote: false,
        canViewSavedConsolidation: false,
        canEditConsolidation: false,
        canSaveConsolidation: false,
        canPublish: false
      };
    }
    const context = {
      projectId: item.projectId,
      committeeCaseId: item.id,
      committeeType: item.committee,
      workflowStatus: item.status
    };
    const can = (capability: string) => this.permissions.can(user, capability, context);
    const own = can('VIEW_OWN_REVIEW');
    const all = can('VIEW_ALL_REVIEWS');
    const published = can('VIEW_PUBLISHED_OBSERVATIONS');
    const desk = can('VIEW_REVIEWER_ASSIGNMENTS');
    return {
      ...base,
      admissibility: base.admissibility && can('VIEW_ADMISSIBILITY'),
      ownReview: own,
      internalReviews: all && base.internalReviews,
      answerObservations: can('RESPOND_TO_OBSERVATIONS') && this.project()?.researcherId === user.id,
      consolidate: can('CONSOLIDATE_OBSERVATIONS') && base.consolidate,
      prepareResolution: can('PREPARE_RESOLUTION') && base.prepareResolution,
      validateResolution: can('VALIDATE_RESOLUTION') && base.validateResolution,
      draftResolution: can('PREPARE_RESOLUTION') || can('VALIDATE_RESOLUTION'),
      history: desk || all ? 'operational' : 'external',
      reviewDesk: desk,
      reviewerOnly: own && !all && !published && !desk,
      canAssign: can('ASSIGN_REVIEWERS'),
      canReassign: can('REASSIGN_REVIEWER'),
      assignedNote: own && !desk,
      canViewSavedConsolidation: can('VIEW_SAVED_CONSOLIDATED_OBSERVATIONS'),
      canEditConsolidation: can('EDIT_CONSOLIDATED_OBSERVATIONS'),
      canSaveConsolidation: can('SAVE_CONSOLIDATED_OBSERVATIONS'),
      canPublish: can('PUBLISH_OBSERVATIONS')
    };
  });
  readonly completion = computed(() => this.cases.completion(this.caseId()));
  readonly expiry = computed(() => {
    const item = this.item();
    return item ? this.projects.expiryInfo(item) : null;
  });
  readonly cycles = computed(() =>
    this.store.snapshot().cycles
      .filter((item) => item.caseId === this.caseId())
      .sort((a, b) => b.cycleNumber - a.cycleNumber)
  );
  readonly openCycle = computed(() => {
    const current = this.item()?.currentCycle;
    return this.cycles().find((item) => item.cycleNumber === current && item.status !== 'PUBLISHED');
  });
  readonly publishedCycles = computed(() => this.cycles().filter((item) => item.status === 'PUBLISHED' || !!item.closedAt));
  readonly publishedObservations = computed(() =>
    [...this.observations.publishedForResearcher(this.caseId())].sort((a, b) => b.cycleNumber - a.cycleNumber)
  );
  readonly currentReviews = computed(() => {
    const user = this.user();
    const item = this.item();
    return user && item ? this.reviews.listForCase(item.id, user.id, user.role, item.currentCycle) : [];
  });
  readonly ownAssignment = computed(() => {
    const user = this.user();
    if (!user) {
      return undefined;
    }
    return this.reviewers.currentBySlot(this.caseId()).find((slot) =>
      slot.assignment?.reviewerId === user.id && slot.assignment.status !== 'REJECTED' && slot.assignment.status !== 'CONFLICT'
    )?.assignment;
  });
  readonly reviewSlots = computed(() => {
    this.store.state();
    const item = this.item();
    if (!item) {
      return [];
    }
    const users = this.store.snapshot().users;
    const reviews = this.store.snapshot().reviews.filter((review) =>
      review.caseId === item.id && review.cycleNumber === item.currentCycle && !!review.submittedAt
    );
    return this.reviewers.currentBySlot(item.id).map((slot) => {
      const assignment = slot.assignment;
      const person = users.find((user) => user.id === assignment?.reviewerId);
      const review = assignment
        ? reviews.find((itemReview) => itemReview.assignmentId === assignment.id || itemReview.reviewerId === assignment.reviewerId)
        : undefined;
      return {
        slot: slot.slot,
        name: person?.displayName ?? '',
        statusKey: this.slotStatusKey(assignment?.status),
        submittedAt: review?.submittedAt,
        review
      };
    });
  });
  readonly reviewsReady = computed(() => {
    const item = this.item();
    if (!item || item.status === 'AWAITING_RESEARCHER') {
      return false;
    }
    const state = this.store.state();
    const assignments = this.reviewers.currentBySlot(item.id)
      .map((slot) => slot.assignment)
      .filter((assignment): assignment is ReviewerAssignment => !!assignment && assignment.status !== 'REJECTED' && assignment.status !== 'CONFLICT');
    if (!assignments.length) {
      return false;
    }
    const reviews = state.reviews.filter((review) => review.caseId === item.id && review.cycleNumber === item.currentCycle && review.submittedAt);
    return assignments.every((assignment) => reviews.some((review) => review.assignmentId === assignment.id || review.reviewerId === assignment.reviewerId));
  });
  readonly roundConsolidated = computed(() => !!this.openCycle()?.consolidatedAt);
  private hydratedDraft = '';
  readonly resolution = computed(() => this.resolutions.byCase(this.caseId()));
  readonly issuedResolution = computed(() => {
    const resolution = this.resolution();
    return resolution?.workflow === 'ISSUED' ? resolution : undefined;
  });
  readonly canManageUsers = computed(() => this.permissions.can(this.auth.currentUser(), 'MANAGE_USERS'));

  reviewerAffiliation(person: User): string {
    if (person.personKind === 'EXTERNAL' || person.externalReviewer) {
      const place = person.institution?.trim();
      const external = this.i18n.translate('users.external');
      return place ? `${place} · ${external}` : external;
    }
    return this.i18n.translate('users.uss');
  }
  readonly reviewerChoices = computed<ReviewerOption[]>(() => {
    this.i18n.currentLanguage();
    const committee = this.item()?.committee;
    if (!committee) {
      return [];
    }
    return this.reviewers.candidates(committee).map((person) => {
      const count = this.reviewers.activeReviewCount(person.id);
      return {
        id: person.id,
        name: person.displayName,
        email: person.email,
        specialty: person.specialty,
        detail: this.reviewerAffiliation(person),
        meta: this.i18n.translate(count === 1 ? 'reviewers.activeOne' : 'reviewers.activeMany', { count })
      };
    });
  });
  readonly slotRows = computed(() => {
    this.i18n.currentLanguage();
    const users = this.store.snapshot().users;
    return this.reviewers.currentBySlot(this.caseId()).map((slot) => {
      const assignment = slot.assignment;
      const person = users.find((user) => user.id === assignment?.reviewerId);
      const canChange = this.reviewers.canReassign(this.caseId(), assignment);
      const closed = !!assignment && !canChange && (assignment.status === 'COMPLETED' || this.reviewers.reviewWasSubmitted(assignment.id));
      return {
        slot: slot.slot,
        assignment,
        name: person?.displayName ?? '',
        detail: person ? this.reviewerAffiliation(person) : '',
        statusKey: this.slotStatusKey(assignment?.status),
        canChange,
        advanced: this.reviewers.isAdvancedReassignment(this.caseId(), assignment),
        closed,
        replacement: assignment?.status === 'REJECTED' || assignment?.status === 'CONFLICT'
      };
    });
  });
  readonly rejectedSlots = computed(() => this.slotRows().filter((slot) => slot.assignment?.status === 'REJECTED').length);
  readonly hasAssignedReviewers = computed(() => this.slotRows().some((slot) => !!slot.assignment));
  readonly showAssignForm = computed(() => {
    const access = this.visibility();
    const item = this.item();
    if (!access?.canAssign || !item) {
      return false;
    }
    const rows = this.slotRows();
    const open = rows.filter((slot) =>
      slot.assignment && slot.assignment.status !== 'REJECTED' && slot.assignment.status !== 'CONFLICT'
    );
    if (rows.every((slot) => !!slot.assignment) && rows.some((slot) => slot.canChange)) {
      return false;
    }
    if (item.status === 'PENDING_ASSIGNMENT') {
      return true;
    }
    if (item.status === 'PENDING_ACCEPTANCE') {
      return open.length < 2;
    }
    return access.assignment && open.length < 2;
  });
  readonly sessionOptions = computed(() => {
    const committee = this.item()?.committee;
    return committee ? this.sessions.list(committee) : [];
  });
  readonly latestDocuments = computed(() => this.documents.latest(this.caseId()).filter((item) => item.kind !== 'OBSERVATION'));
  readonly committeeSubmissionDocs = computed(() => {
    const item = this.item();
    if (!item) {
      return [];
    }
    const submission = this.submissions.byProject(item.projectId);
    return submission ? this.documents.list(submission.id).filter((doc) => doc.committeeType === item.committee) : [];
  });
  readonly history = computed(() => {
    const visibility = this.visibility();
    const caseId = this.caseId();
    if (!visibility) {
      return [];
    }
    const user = this.user();
    const desk = visibility.history === 'operational';
    return this.audit.list()
      .filter((event) => event.caseId === caseId || event.objectId === caseId)
      .filter((event) => historyEventVisible(event.action, visibility.history))
      .filter((event) => desk || !this.namesAnotherReviewer(event, user));
  });
  readonly historyItems = computed(() => {
    const canDraft = this.visibility()?.draftResolution ?? false;
    const state = this.store.snapshot();
    return this.history().map((event) => this.presentHistory(event, state, canDraft));
  });
  readonly sectionNav = computed(() => {
    const access = this.visibility();
    if (!access || !this.item()) {
      return [];
    }
    const items: { id: string; key: string }[] = [];
    const add = (suffix: string, key: string) => items.push({ id: this.sid(suffix), key });
    if (access.reviewerOnly) {
      add('documents', 'projects.documentSection');
      if (access.ownReview) {
        add('my-review', 'projects.myReview');
      }
      return items;
    }
    if (access.admissibility) {
      add('admissibility', 'cases.admissibility');
    }
    if (access.reviewDesk) {
      add('review', 'projects.reviewSection');
    } else if (access.assignment) {
      add('assignment', 'cases.assignReviewers');
    }
    add('documents', 'projects.documentSection');
    if (access.internalReviews) {
      add('reviews', 'projects.internalReviews');
    }
    if (access.ownReview) {
      add('my-review', 'projects.myReview');
    }
    if (access.answerObservations) {
      add('observations', 'projects.observations');
    } else if (this.publishedCycles().length) {
      add('published', 'projects.publishedObservations');
    } else if (!access.internalReviews && !access.ownReview) {
      add('observations', 'projects.observations');
    }
    add('resolution', 'projects.resolutionTitle');
    add('post', 'projects.postProcesses');
    add('history', 'projects.history');
    return items;
  });
  readonly activeSection = signal('');
  private spy: IntersectionObserver | null = null;
  private spyKey = '';
  private suppressSpyUntil = 0;

  private readonly host = inject(ElementRef<HTMLElement>);

  constructor() {
    const destroyRef = inject(DestroyRef);
    const onResize = () => this.syncStickyOffset();
    window.addEventListener('resize', onResize);
    afterEveryRender(() => {
      this.syncStickyOffset();
      this.syncSpy();
    });
    effect(() => {
      const sections = this.sectionNav();
      if (sections.length && !sections.some((item) => item.id === this.activeSection())) {
        this.activeSection.set(sections[0].id);
      }
    });
    destroyRef.onDestroy(() => {
      this.spy?.disconnect();
      window.removeEventListener('resize', onResize);
    });
    effect(() => {
      const cycle = this.openCycle();
      const marker = cycle
        ? `${cycle.id}|${cycle.consolidatedMethodology ?? ''}|${cycle.consolidatedObservations ?? ''}|${cycle.consolidatedRisks ?? ''}`
        : '';
      if (!cycle || marker === this.hydratedDraft) {
        return;
      }
      this.hydratedDraft = marker;
      if (!cycle.consolidatedMethodology && !cycle.consolidatedObservations && !cycle.consolidatedRisks) {
        return;
      }
      this.methodology.set(cycle.consolidatedMethodology ?? '');
      this.text.set(cycle.consolidatedObservations ?? '');
      this.risks.set(cycle.consolidatedRisks ?? '');
    });
  }

  responses(observationId: string) {
    return this.observations.responses(observationId);
  }

  admit(ok: boolean): void {
    this.cases.admissibility(this.caseId(), ok);
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
      this.resolutions.returnToSecretary(this.caseId(), reason);
    } else {
      this.cases.admissibility(this.caseId(), false, reason);
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
    this.reviewers.assign(this.caseId(), this.rev1(), this.rev2(), this.sessionId() || undefined);
  }

  optionsFor(slot: 1 | 2): ReviewerOption[] {
    const other = slot === 1 ? this.rev2() : this.rev1();
    return this.reviewerChoices().filter((person) => person.id !== other);
  }

  replacementOptions(): ReviewerOption[] {
    return this.reviewerChoices();
  }

  startReassignment(slot: 1 | 2): void {
    if (this.reassigning() === slot) {
      this.cancelReassignment(slot);
      return;
    }
    this.reassignCandidate.set('');
    this.reassignReason.set('');
    this.reassignError.set('');
    this.reassignNotice.set('');
    this.reassigning.set(slot);
    setTimeout(() => document.getElementById(`reassign-slot-${slot}-input`)?.focus());
  }

  selectReplacement(reviewerId: string): void {
    this.reassignCandidate.set(reviewerId);
    if (this.reassignError() === 'duplicate' || this.reassignError() === 'same' || this.reassignError() === 'invalid') {
      this.reassignError.set('');
    }
  }

  confirmReassignment(slot: 1 | 2): void {
    const outcome = this.reviewers.reassign(this.caseId(), slot, this.reassignCandidate(), this.reassignReason());
    if (outcome !== 'ok') {
      this.reassignError.set(outcome);
      return;
    }
    this.reassigning.set(null);
    this.reassignCandidate.set('');
    this.reassignReason.set('');
    this.reassignError.set('');
    this.reassignNotice.set(this.i18n.translate('reviewers.updatedNotified'));
    setTimeout(() => document.getElementById('reassign-notice')?.focus());
  }

  cancelReassignment(slot: 1 | 2): void {
    this.reassigning.set(null);
    this.reassignCandidate.set('');
    this.reassignReason.set('');
    this.reassignError.set('');
    setTimeout(() => document.getElementById(`reassign-toggle-${slot}`)?.focus());
  }

  reasonDescribedBy(slot: 1 | 2): string {
    const ids = [`reason-help-${slot}`];
    if (this.reassignError() === 'invalid') {
      ids.push(`reason-error-${slot}`);
    }
    return ids.join(' ');
  }

  slotStatusKey(status?: string): string {
    if (status === 'PENDING') {
      return 'reviewers.pendingAcceptance';
    }
    if (status === 'ACCEPTED') {
      return 'reviewers.accepted';
    }
    if (status === 'REJECTED') {
      return 'reviewers.rejected';
    }
    if (status === 'CONFLICT') {
      return 'catalogs.assignmentStatus.CONFLICT';
    }
    if (status === 'COMPLETED') {
      return 'catalogs.assignmentStatus.COMPLETED';
    }
    return '';
  }

  consolidate(): void {
    const draft = this.observations.incorporateSubmitted(this.caseId());
    this.methodology.set(draft.methodology);
    this.text.set(draft.text);
    this.risks.set(draft.risks);
  }

  saveConsolidation(): void {
    this.observations.saveDraft(this.caseId(), {
      methodology: this.methodology(),
      text: this.text(),
      risks: this.risks()
    });
    this.draftSaved.set(true);
  }

  askPublish(): void {
    if (!this.publishNotice()) {
      this.publishNotice.set(this.i18n.translate('cases.publishDefault'));
    }
    this.confirmPublish.set(true);
  }

  publish(): void {
    const notice = this.publishNotice().trim();
    if (!notice || !this.text().trim() || !this.reviewsReady()) {
      return;
    }
    this.observations.publish(this.caseId(), notice, {
      methodology: this.methodology(),
      text: this.text(),
      risks: this.risks()
    });
    this.methodology.set('');
    this.text.set('');
    this.risks.set('');
    this.confirmPublish.set(false);
  }

  displayDate(value?: string | null): string {
    if (!value) {
      return '';
    }
    const normalized = /^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T12:00:00` : value;
    return this.i18n.formatDate(normalized);
  }

  cycleDate(cycleNumber: number): string {
    const cycle = this.cycles().find((item) => item.cycleNumber === cycleNumber);
    return this.displayDate(cycle?.closedAt || cycle?.openedAt);
  }

  previewCycle(cycleNumber: number): void {
    const cycle = this.cycles().find((item) => item.cycleNumber === cycleNumber);
    const observation = this.observations.byCase(this.caseId()).find((item) => item.cycleNumber === cycleNumber && item.status !== 'DRAFT');
    this.pdf.open({
      title: this.i18n.translate('cases.roundLabel', { n: cycleNumber }),
      disclaimer: this.i18n.translate('pdf.prototypeDisclaimer'),
      rows: [{ label: this.i18n.translate('cases.committee'), value: this.item()?.code ?? '' }],
      body: observation?.text || cycle?.consolidatedObservations || ''
    });
  }

  previewObservation(): void {
    const item = this.item();
    this.pdf.open({
      title: this.i18n.translate('cases.roundLabel', { n: item?.currentCycle ?? 1 }),
      disclaimer: this.i18n.translate('pdf.prototypeDisclaimer'),
      rows: [
        { label: this.i18n.translate('cases.committee'), value: this.i18n.translate(COMMITTEE_LABELS[item?.committee ?? 'CEC']) },
        { label: this.i18n.translate('classification.project'), value: this.project()?.title ?? '' }
      ],
      body: [this.methodology(), this.text(), this.risks()].filter(Boolean).join('\n\n')
    });
  }

  setAnswer(id: string, value: string): void {
    this.answer.update((current) => ({ ...current, [id]: value }));
  }

  draftAnswer(observationId: string): string {
    const draft = this.answer()[observationId];
    if (draft !== undefined) {
      return draft;
    }
    return this.responses(observationId).find((item) => item.submitted === false)?.text ?? '';
  }

  reply(observationId: string): void {
    const text = this.draftAnswer(observationId);
    this.observations.saveResponseDraft(observationId, text, []);
    this.answer.update((current) => ({ ...current, [observationId]: text }));
    this.responseError.set(false);
  }

  sendAnswers(): void {
    const drafts: Record<string, string> = {};
    for (const observation of this.publishedObservations()) {
      drafts[observation.id] = this.draftAnswer(observation.id);
    }
    const sent = this.observations.submitAnswers(this.caseId(), drafts);
    this.responseError.set(!sent);
  }

  toggleDoc(id: string, checked: boolean): void {
    this.selectedDocs.update((current) => ({ ...current, [id]: checked }));
  }

  prepare(): void {
    const selected = Object.entries(this.selectedDocs()).filter(([, on]) => on).map(([id]) => id);
    this.resolutions.prepare(this.caseId(), this.kind(), this.resolutionText(), selected);
  }

  previewResolution(): void {
    const item = this.item();
    const resolution = this.resolution();
    this.pdf.open({
      title: resolution?.correlative ?? this.i18n.translate('cases.prepareResolution'),
      disclaimer: this.i18n.translate('pdf.prototypeDisclaimer'),
      rows: [
        { label: this.i18n.translate('classification.project'), value: this.project()?.title ?? '' },
        { label: this.i18n.translate('cases.committee'), value: OFFICIAL_COMMITTEE_NAMES[item?.committee ?? 'CEC'] }
      ],
      body: this.resolutionText() || resolution?.text || '',
      signature: resolution?.signatureStatus === 'PROTOTYPE' ? this.i18n.translate('cases.signaturePrototype') : undefined,
      stamp: resolution?.stampStatus === 'PROTOTYPE' || resolution?.workflow === 'ISSUED'
        ? this.i18n.translate('cases.stampPrototype')
        : undefined
    });
  }

  downloadResolution(): void {
    const resolution = this.issuedResolution();
    const project = this.project();
    const item = this.item();
    if (!resolution || !project || !item) {
      return;
    }
    const researcher = this.store.snapshot().users.find((user) => user.id === project.researcherId);
    const html = this.resolutions.printableHtml(resolution, project.title, researcher?.displayName ?? '', OFFICIAL_COMMITTEE_NAMES[item.committee]);
    const url = URL.createObjectURL(new Blob([html], { type: 'text/html' }));
    window.open(url, '_blank');
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

  askValidate(): void {
    this.confirmValidate.set(true);
  }

  validate(): void {
    this.confirmValidate.set(false);
    this.resolutions.validate(this.caseId());
  }

  notify(): void {
    this.cases.notifyCommittee(this.caseId());
    this.confirmNotify.set(false);
  }

  reviewerName(reviewerId: string): string {
    return this.store.snapshot().users.find((user) => user.id === reviewerId)?.displayName ?? '';
  }

  reviewLabel(review: Review, index: number): string {
    const name = this.reviewerName(review.reviewerId);
    const slot = this.i18n.translate('cases.reviewerN', { n: index + 1 });
    return name ? `${slot} · ${name}` : slot;
  }

  sid(suffix: string): string {
    return `c-${this.caseId()}-${suffix}`;
  }

  goTo(id: string, event: Event): void {
    event.preventDefault();
    const target = document.getElementById(id);
    if (!target) {
      return;
    }
    this.suppressSpyUntil = performance.now() + 800;
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
    target.scrollIntoView({ behavior: motion, block: 'start' });
    this.activeSection.set(id);
    if (target.tabIndex < 0) {
      target.tabIndex = -1;
    }
    target.focus({ preventScroll: true });
    history.replaceState(null, '', `#${id}`);
  }

  private markActive(): void {
    if (performance.now() < this.suppressSpyUntil) {
      return;
    }
    const navBottom = this.host.nativeElement.querySelector('.section-nav')?.getBoundingClientRect().bottom ?? 0;
    const marker = Math.max(120, navBottom + 8);
    const candidates = this.sectionNav()
      .map((item) => ({ id: item.id, rect: document.getElementById(item.id)?.getBoundingClientRect() }))
      .filter((item): item is { id: string; rect: DOMRect } => !!item.rect && item.rect.bottom > marker && item.rect.top < window.innerHeight);
    const best = [...candidates].sort((a, b) => Math.abs(a.rect.top - marker) - Math.abs(b.rect.top - marker))[0];
    if (best) {
      this.activeSection.set(best.id);
    }
  }

  private syncStickyOffset(): void {
    const host = this.host.nativeElement;
    const sidebar = document.querySelector('.sidebar');
    const sidebarSticky = !!sidebar && getComputedStyle(sidebar).position === 'sticky';
    const top = sidebarSticky ? sidebar.getBoundingClientRect().height : 0;
    host.style.setProperty('--eth-sticky-top', `${top}px`);
    const nav = host.querySelector('.section-nav');
    if (nav) {
      host.style.setProperty('--committee-nav-block', `${nav.getBoundingClientRect().height}px`);
    }
  }

  private syncSpy(): void {
    const ids = this.sectionNav().map((item) => item.id);
    const key = ids.join('|');
    if (!ids.length || key === this.spyKey) {
      return;
    }
    const firstBind = this.spyKey === '';
    this.spyKey = key;
    this.spy?.disconnect();
    this.spy = new IntersectionObserver(() => this.markActive(), {
      root: null,
      rootMargin: '-96px 0px -40% 0px',
      threshold: [0, 0.1, 0.5]
    });
    for (const id of ids) {
      const element = document.getElementById(id);
      if (element) {
        this.spy.observe(element);
      }
    }
    if (firstBind) {
      const hash = location.hash.slice(1);
      if (ids.includes(hash)) {
        this.activeSection.set(hash);
        document.getElementById(hash)?.scrollIntoView({ block: 'start' });
      }
    }
  }

  private namesAnotherReviewer(event: AuditEvent, user: User | null): boolean {
    const aboutAssignment = event.action.includes('REVIEWER') || event.action === 'ASSIGN_REVIEWERS' || event.action === 'REVIEW_SUBMITTED';
    if (!aboutAssignment || !user) {
      return false;
    }
    const text = [event.actorId, event.actorName, event.description, event.oldValue, event.newValue, event.objectId].filter(Boolean).join(' ');
    return !text.includes(user.id) && !text.includes(user.displayName);
  }

  openHistory(event: CommitteeHistoryEventView): void {
    if (event.link === 'document' && event.objectId) {
      const doc = this.store.snapshot().documents.find((item) => item.id === event.objectId);
      if (doc) {
        this.previewDocument(doc);
      }
      return;
    }
    if (event.link === 'observation' && event.cycle) {
      this.previewCycle(event.cycle);
      return;
    }
    if (event.link === 'resolution') {
      this.previewResolution();
    }
  }

  private presentHistory(
    event: AuditEvent,
    state: ReturnType<EthicsStore['snapshot']>,
    canDraft: boolean
  ): CommitteeHistoryEventView {
    const item: CommitteeHistoryEventView = {
      id: event.id,
      date: event.date,
      action: event.action,
      fallback: event.description,
      actorName: event.actorName,
      detail: event.reason?.trim() || undefined
    };
    if (event.action === 'REVIEWER_REASSIGNED') {
      const previous = this.reviewerAuditLabel(event.oldValue);
      const next = this.reviewerAuditLabel(event.newValue);
      if (previous) {
        item.title = this.i18n.translate('reviewers.reassignedEvent', { slot: previous.slot });
        item.previousName = previous.name;
      }
      if (next) {
        item.nextName = next.name;
      }
      if (event.reason?.trim()) {
        item.detail = this.i18n.translate('audit.reason', { reason: event.reason.trim() });
      }
    }
    if (event.objectType === 'Document') {
      const doc = state.documents.find((row) => row.id === event.objectId);
      if (doc) {
        item.objectLabel = `${doc.name} · v${doc.version}`;
        item.link = 'document';
        item.linkKey = 'projects.viewDocument';
        item.objectId = doc.id;
      }
    } else if (event.objectType === 'Observation') {
      const observation = state.observations.find((row) => row.id === event.objectId);
      if (observation && observation.status !== 'DRAFT') {
        item.objectLabel = this.i18n.translate('cases.roundLabel', { n: observation.cycleNumber });
        item.link = 'observation';
        item.linkKey = 'projects.viewObservations';
        item.cycle = observation.cycleNumber;
      }
    } else if (event.objectType === 'Resolution') {
      const resolution = state.resolutions.find((row) => row.id === event.objectId);
      if (resolution && (resolution.workflow === 'ISSUED' || canDraft)) {
        item.objectLabel = resolution.correlative;
        item.link = 'resolution';
        item.linkKey = 'projects.viewResolution';
      }
    }
    return item;
  }

  private reviewerAuditLabel(value?: string): { slot: string; name: string } | undefined {
    const match = value?.match(/^([12]):\s+(.+)$/);
    if (!match) {
      return undefined;
    }
    return { slot: match[1], name: match[2] };
  }
}
