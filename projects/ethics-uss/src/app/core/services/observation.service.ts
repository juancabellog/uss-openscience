import { Injectable } from '@angular/core';
import { Observation, ObservationResponse } from '../models/ethics.models';
import { AuditService } from './audit.service';
import { CommitteeCaseService } from './committee-case.service';
import { CommunicationService } from './communication.service';
import { EthicsStore } from './ethics-store.service';
import { NotificationService } from './notification.service';
import { ReviewerService } from './reviewer.service';
import { holds, hasScope } from '../workflow/role-capabilities';
import { nowIso, uid } from './ids';

export interface ConsolidatedDraft {
  methodology: string;
  text: string;
  risks: string;
}

@Injectable({ providedIn: 'root' })
export class ObservationService {
  constructor(
    private readonly store: EthicsStore,
    private readonly audit: AuditService,
    private readonly notifications: NotificationService,
    private readonly cases: CommitteeCaseService,
    private readonly communications: CommunicationService,
    private readonly reviewers: ReviewerService
  ) {}

  byCase(caseId: string): Observation[] {
    return this.store.snapshot().observations.filter((item) => item.caseId === caseId);
  }

  publishedForResearcher(caseId: string): Observation[] {
    return this.byCase(caseId).filter((item) => item.privacy === 'RESEARCHER_VISIBLE' && item.status !== 'DRAFT');
  }

  responses(observationId: string): ObservationResponse[] {
    return this.store.snapshot().responses.filter((item) => item.observationId === observationId);
  }

  /**
   * Junta el texto de las revisiones enviadas. No publica ni cambia el estado.
   * Una revisión sin submittedAt no entra. La autoría original no se toca.
   */
  incorporateSubmitted(caseId: string): ConsolidatedDraft {
    const current = this.cases.get(caseId);
    const cycle = current?.currentCycle ?? 1;
    const state = this.store.snapshot();
    const assignments = state.assignments
      .filter((item) => item.caseId === caseId && !item.supersededAt)
      .sort((a, b) => a.slot - b.slot);
    const reviews = state.reviews.filter((item) => item.caseId === caseId && item.cycleNumber === cycle && item.submittedAt);
    const methodology: string[] = [];
    const text: string[] = [];
    const risks: string[] = [];
    assignments.forEach((assignment) => {
      const review = reviews.find((item) => item.assignmentId === assignment.id || item.reviewerId === assignment.reviewerId);
      if (!review) {
        return;
      }
      pushBlock(methodology, review.criteria['metodologia']);
      pushBlock(text, review.criteria['observaciones']);
      pushBlock(risks, review.criteria['riesgos']);
    });
    return {
      methodology: methodology.join('\n\n'),
      text: text.join('\n\n'),
      risks: risks.join('\n\n')
    };
  }

  /**
   * Persiste el borrador de consolidación. No publica, no notifica
   * y no mueve el expediente a espera de respuesta del investigador.
   */
  saveDraft(caseId: string, draft: ConsolidatedDraft): void {
    const current = this.cases.get(caseId);
    const cycle = current?.currentCycle ?? 1;
    this.store.update((currentState) => ({
      ...currentState,
      cycles: currentState.cycles.map((item) => (
        item.caseId === caseId && item.cycleNumber === cycle && item.status !== 'PUBLISHED'
          ? {
            ...item,
            consolidatedMethodology: draft.methodology,
            consolidatedObservations: draft.text,
            consolidatedRisks: draft.risks
          }
          : item
      ))
    }));
    this.audit.record({
      action: 'OBSERVATIONS_DRAFT_SAVED',
      objectType: 'ReviewCycle',
      objectId: caseId,
      description: `Guardó el borrador de la ronda ${cycle}`,
      projectId: current?.projectId
    });
  }

  publish(caseId: string, notice: string, draft: ConsolidatedDraft): void {
    const current = this.cases.get(caseId);
    if (!current || !draft.text.trim() || !this.bothReviewsSubmitted(caseId)) {
      return;
    }
    const cycle = current.currentCycle;
    const pdfId = uid('doc');
    const observationId = uid('obs');
    const nextCycle = cycle + 1;
    const stamp = nowIso();
    const observation: Observation = {
      id: observationId,
      code: `OBS-${String(cycle).padStart(3, '0')}`,
      caseId,
      cycleNumber: cycle,
      topic: '',
      text: draft.text,
      methodology: draft.methodology,
      risks: draft.risks,
      relatedDocuments: [],
      status: 'PENDING_RESPONSE',
      privacy: 'RESEARCHER_VISIBLE',
      pdfDocumentId: pdfId,
      notice: notice.trim()
    };
    this.store.update((state) => ({
      ...state,
      documents: [
        {
          id: pdfId,
          ownerType: 'OBSERVATION' as const,
          ownerId: observationId,
          name: `observaciones-ronda-${cycle}.html`,
          kind: 'OBSERVATION' as const,
          sizeLabel: '0.1 MB',
          uploadedAt: stamp,
          version: cycle,
          templateId: 'PROTOTYPE_PDF_TEMPLATE' as const
        },
        ...state.documents
      ],
      observations: [...state.observations, observation],
      cases: state.cases.map((item) =>
        item.id === caseId ? { ...item, currentCycle: nextCycle, status: 'AWAITING_RESEARCHER' as const } : item
      ),
      cycles: [
        ...state.cycles.map((item) =>
          item.caseId === caseId && item.cycleNumber === cycle
            ? {
                ...item,
                status: 'PUBLISHED' as const,
                closedAt: stamp,
                consolidatedMethodology: draft.methodology,
                consolidatedObservations: draft.text,
                consolidatedRisks: draft.risks,
                pdfDocumentId: pdfId
              }
            : item
        ),
        { id: uid('cyc'), caseId, cycleNumber: nextCycle, openedAt: stamp, status: 'OPEN' as const }
      ]
    }));
    const project = this.store.snapshot().projects.find((item) => item.id === current.projectId);
    this.audit.record({
      action: 'OBSERVATIONS_PUBLISHED',
      objectType: 'Observation',
      objectId: observationId,
      caseId,
      description: `Publicó observaciones de la ronda ${cycle}`,
      projectId: current.projectId
    });
    if (project) {
      this.notifications.notify(project.researcherId, {
        type: 'OBSERVATIONS_AVAILABLE',
        title: 'Observaciones disponibles',
        body: notice.trim() || 'El comité registró observaciones.',
        link: `/proyectos/${project.id}`
      });
      this.communications.record({
        caseId,
        projectId: project.id,
        kind: 'OBSERVATION_NOTICE',
        body: notice.trim(),
        auditAction: 'OBSERVATION_NOTICE',
        auditDescription: `Comunicó observaciones de la ronda ${cycle}`,
        notifyUserIds: []
      });
    }
  }

  saveResponseDraft(observationId: string, text: string, documentIds: string[] = []): void {
    const observation = this.store.snapshot().observations.find((item) => item.id === observationId);
    const current = observation ? this.cases.get(observation.caseId) : undefined;
    if (!observation || current?.status !== 'AWAITING_RESEARCHER' || observation.status === 'ANSWERED') {
      return;
    }
    const existing = this.responses(observationId).find((item) => item.submitted === false);
    if (existing) {
      this.store.update((state) => ({
        ...state,
        responses: state.responses.map((item) => (
          item.id === existing.id ? { ...item, text, documentIds, answeredAt: nowIso(), submitted: false } : item
        ))
      }));
      return;
    }
    const response: ObservationResponse = {
      id: uid('ans'),
      observationId,
      text,
      documentIds,
      answeredAt: nowIso(),
      submitted: false
    };
    this.store.update((state) => ({
      ...state,
      responses: [...state.responses, response]
    }));
  }

  answer(observationId: string, text: string, documentIds: string[]): void {
    const observation = this.store.snapshot().observations.find((item) => item.id === observationId);
    const current = observation ? this.cases.get(observation.caseId) : undefined;
    const existing = [...this.responses(observationId)].at(-1);
    if (existing && current?.status === 'AWAITING_RESEARCHER') {
      this.store.update((state) => ({
        ...state,
        responses: state.responses.map((item) => (
          item.id === existing.id ? { ...item, text, documentIds, answeredAt: nowIso() } : item
        )),
        observations: state.observations.map((item) => (item.id === observationId ? { ...item, status: 'ANSWERED' } : item))
      }));
      return;
    }
    const response: ObservationResponse = {
      id: uid('ans'),
      observationId,
      text,
      documentIds,
      answeredAt: nowIso()
    };
    this.store.update((state) => ({
      ...state,
      responses: [...state.responses, response],
      observations: state.observations.map((item) => (item.id === observationId ? { ...item, status: 'ANSWERED' } : item))
    }));
  }

  submitAnswers(caseId: string, drafts: Record<string, string> = {}): boolean {
    const current = this.cases.get(caseId);
    if (!current || current.status !== 'AWAITING_RESEARCHER') {
      return false;
    }
    const pending = this.publishedForResearcher(caseId).filter((item) => item.status !== 'ANSWERED');
    if (!pending.length) {
      return false;
    }
    const texts = new Map<string, string>();
    for (const observation of pending) {
      const stored = this.responses(observation.id).find((item) => item.submitted === false)?.text ?? '';
      const text = (drafts[observation.id] ?? stored).trim();
      if (!text) {
        return false;
      }
      texts.set(observation.id, text);
    }
    const stamp = nowIso();
    this.store.update((state) => {
      const responses = state.responses.map((item) => ({ ...item }));
      for (const [observationId, text] of texts) {
        const draft = responses.find((item) => item.observationId === observationId && item.submitted === false);
        if (draft) {
          draft.text = text;
          draft.answeredAt = stamp;
          draft.submitted = true;
        } else {
          responses.push({
            id: uid('ans'),
            observationId,
            text,
            documentIds: [],
            answeredAt: stamp,
            submitted: true
          });
        }
      }
      return {
        ...state,
        responses,
        observations: state.observations.map((item) => (
          texts.has(item.id) ? { ...item, status: 'ANSWERED' as const } : item
        ))
      };
    });
    this.cases.receiveResearcherResponse(caseId);
    this.reviewers.openAssignmentsForNewRound(caseId);
    this.audit.record({
      action: 'RESEARCHER_RESPONSE_SUBMITTED',
      objectType: 'ReviewCycle',
      objectId: caseId,
      caseId,
      description: `Envió respuestas de la ronda ${current.currentCycle - 1}`,
      projectId: current.projectId
    });
    this.store
      .snapshot()
      .users.filter((user) => hasScope(user, 'COMMITTEE') && holds(user, 'PUBLISH_OBSERVATIONS') && user.committees.includes(current.committee))
      .forEach((user) => this.notifications.notify(user.id, {
        type: 'RESPONSE_RECEIVED',
        title: 'Respuesta recibida',
        body: `El IR respondió ${current.code}.`,
        params: { observationCode: current.code },
        link: `/expedientes/${caseId}`
      }));
    return true;
  }

  private bothReviewsSubmitted(caseId: string): boolean {
    const current = this.cases.get(caseId);
    if (!current) {
      return false;
    }
    const assignments = this.store.snapshot().assignments.filter((item) =>
      item.caseId === caseId && !item.supersededAt && item.status !== 'REJECTED' && item.status !== 'CONFLICT'
    );
    if (assignments.length < 2) {
      return false;
    }
    const reviews = this.store.snapshot().reviews.filter((review) =>
      review.caseId === caseId && review.cycleNumber === current.currentCycle && review.submittedAt
    );
    return assignments.every((assignment) =>
      reviews.some((review) => review.assignmentId === assignment.id || review.reviewerId === assignment.reviewerId)
    );
  }
}

function pushBlock(target: string[], value?: string): void {
  const text = value?.trim();
  if (text) {
    target.push(text);
  }
}
