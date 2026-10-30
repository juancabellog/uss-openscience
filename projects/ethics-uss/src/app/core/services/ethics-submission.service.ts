import { Injectable } from '@angular/core';
import { formForType, normalizeTextPayload, payloadToAnswers, asTextOr } from '../forms/form-answers';
import { capturePersonSnapshot } from '../forms/person-snapshot';
import { ClassificationCode, EthicsSubmission, EthicsSubmissionType, Project } from '../models/ethics.models';
import { DemoAuthService } from '../auth/demo-auth.service';
import { PermissionService } from './permission.service';
import { hasScope } from '../workflow/role-capabilities';
import { AuditService } from './audit.service';
import { EthicsClassificationService } from './ethics-classification.service';
import { EthicsStore } from './ethics-store.service';
import { NotificationService } from './notification.service';
import { nowIso, uid } from './ids';

@Injectable({ providedIn: 'root' })
export class EthicsSubmissionService {
  constructor(
    private readonly store: EthicsStore,
    private readonly auth: DemoAuthService,
    private readonly audit: AuditService,
    private readonly notifications: NotificationService,
    private readonly classification: EthicsClassificationService,
    private readonly permissions: PermissionService
  ) {}

  get(id: string): EthicsSubmission | undefined {
    return this.store.snapshot().submissions.find((item) => item.id === id);
  }

  byProject(projectId: string): EthicsSubmission | undefined {
    return this.store.snapshot().submissions.find((item) => item.projectId === projectId);
  }

  pendingClassification(): EthicsSubmission[] {
    return this.store.snapshot().submissions.filter((item) => item.status === 'SUBMITTED' && !item.classification.validated);
  }

  saveDraft(type: EthicsSubmissionType, payload: Record<string, unknown>, existingId?: string): EthicsSubmission {
    const user = this.auth.currentUser();
    this.assertInstitutionalResearcher(user?.id);
    const title = asTextOr(payload['title'], 'Borrador sin título');
    return this.upsert(type, payload, existingId, 'DRAFT', title, user?.id ?? 'u-inv-1', false);
  }

  submit(type: EthicsSubmissionType, payload: Record<string, unknown>, existingId?: string): EthicsSubmission {
    const user = this.auth.currentUser();
    this.assertInstitutionalResearcher(user?.id);
    const existing = existingId ? this.get(existingId) : undefined;
    if (!this.permissions.can(user, 'SUBMIT_REQUEST', { projectId: existing?.projectId })) {
      throw new Error('La solicitud no puede enviarse en este contexto');
    }
    const title = asTextOr(payload['title'], 'Solicitud sin título');
    const submission = this.upsert(type, payload, existingId, 'SUBMITTED', title, user?.id ?? 'u-inv-1', true);
    this.audit.record({
      action: 'SUBMIT_REQUEST',
      objectType: 'EthicsSubmission',
      objectId: submission.id,
      description: 'Envió solicitud ética',
      projectId: submission.projectId
    });
    this.store.snapshot().users
      .filter((item) => hasScope(item, 'GLOBAL'))
      .forEach((admin) => this.notifications.notify(admin.id, {
        type: 'SUBMISSION_RECEIVED',
        title: 'Solicitud recibida',
        body: title,
        params: { projectTitle: title },
        link: `/solicitudes/${submission.id}`
      }));
    if (user) {
      this.notifications.notify(user.id, {
        type: 'SUBMISSION_SENT',
        title: 'Solicitud enviada',
        body: 'DISEI validará la clasificación preliminar.',
        link: `/proyectos/${submission.projectId}`
      });
    }
    return submission;
  }

  validateClassification(submissionId: string, validated: ClassificationCode[], justification?: string): EthicsSubmission {
    const current = this.get(submissionId);
    if (!current) {
      throw new Error('Solicitud no encontrada');
    }
    const changed = JSON.stringify(current.classification.automatic) !== JSON.stringify(validated);
    this.store.update((state) => ({
      ...state,
      submissions: state.submissions.map((item) =>
        item.id === submissionId
          ? {
              ...item,
              status: 'CLASSIFIED',
              classification: {
                ...item.classification,
                validated,
                justification,
                validatedBy: this.auth.currentUser()?.id,
                validatedAt: nowIso()
              },
              updatedAt: nowIso()
            }
          : item
      )
    }));
    this.audit.record({
      action: 'VALIDATE_CLASSIFICATION',
      objectType: 'EthicsClassification',
      objectId: submissionId,
      description: changed ? 'Modificó clasificación automática' : 'Confirmó clasificación automática',
      oldValue: current.classification.automatic.join(', '),
      newValue: validated.join(', '),
      reason: justification,
      projectId: current.projectId
    });
    return this.get(submissionId)!;
  }

  private assertInstitutionalResearcher(userId: string | undefined): void {
    const user = this.store.snapshot().users.find((item) => item.id === userId);
    if (user?.personKind === 'EXTERNAL' || user?.externalReviewer) {
      throw new Error('EXTERNAL_RESEARCHER_DENIED');
    }
  }

  private upsert(
    type: EthicsSubmissionType,
    payload: Record<string, unknown>,
    existingId: string | undefined,
    status: EthicsSubmission['status'],
    title: string,
    researcherId: string,
    submitted: boolean
  ): EthicsSubmission {
    const form = formForType(type);
    const stored = normalizeTextPayload(form, payload);
    const automatic = type === 'NEW_PROJECT'
      ? this.classification.suggest((stored['studyObjects'] as string[]) ?? [])
      : ((stored['committees'] as ClassificationCode[]) ?? []);
    const existing = existingId ? this.get(existingId) : undefined;
    const projectId = existing?.projectId ?? uid('prj');
    const submissionId = existing?.id ?? uid('sub');
    const stamp = nowIso();
    this.store.update((state) => {
      const project: Project = state.projects.find((item) => item.id === projectId) ?? {
        id: projectId,
        code: `ETI-2026-${String(state.projects.length + 1).padStart(3, '0')}`,
        title,
        fundingSource: asTextOr(payload['fundingSource'], 'Otros'),
        fundingStatus: (stored['fundingStatus'] as Project['fundingStatus']) ?? undefined,
        fundingCode: typeof stored['fundingCode'] === 'string' && stored['fundingCode'] ? stored['fundingCode'] : undefined,
        researcherId,
        type,
        createdAt: stamp,
        updatedAt: stamp
      };
      const submission: EthicsSubmission = {
        id: submissionId,
        projectId,
        type,
        status,
        formVersion: form.version,
        answers: payloadToAnswers(form, stored),
        payload: stored,
        personSnapshot: submitted ? capturePersonSnapshot(stored, stamp) : existing?.personSnapshot,
        classification: {
          automatic,
          validated: existing?.classification.validated ?? null
        },
        submittedAt: submitted ? stamp : existing?.submittedAt,
        createdAt: existing?.createdAt ?? stamp,
        updatedAt: stamp
      };
      return {
        ...state,
        projects: [
          { ...project, title, fundingSource: asTextOr(payload['fundingSource'], project.fundingSource), updatedAt: stamp },
          ...state.projects.filter((item) => item.id !== projectId)
        ],
        submissions: [submission, ...state.submissions.filter((item) => item.id !== submissionId)]
      };
    });
    if (!existing) {
      this.audit.record({
        action: 'CREATE_SUBMISSION',
        objectType: 'EthicsSubmission',
        objectId: submissionId,
        description: 'Creó solicitud',
        projectId
      });
    }
    return this.get(submissionId)!;
  }
}
