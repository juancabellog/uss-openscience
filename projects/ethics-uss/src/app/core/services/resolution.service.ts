import { Injectable } from '@angular/core';
import { Resolution, ResolutionKind } from '../models/ethics.models';
import { AuditService } from './audit.service';
import { CommitteeCaseService } from './committee-case.service';
import { EthicsStore } from './ethics-store.service';
import { NotificationService } from './notification.service';
import { holds, hasScope } from '../workflow/role-capabilities';
import { addYears, correlative, nowIso, uid } from './ids';

@Injectable({ providedIn: 'root' })
export class ResolutionService {
  constructor(
    private readonly store: EthicsStore,
    private readonly audit: AuditService,
    private readonly notifications: NotificationService,
    private readonly cases: CommitteeCaseService
  ) {}

  byCase(caseId: string): Resolution | undefined {
    return this.listByCase(caseId)[0];
  }

  listByCase(caseId: string): Resolution[] {
    return this.store.snapshot().resolutions.filter((item) => item.caseId === caseId);
  }

  prepare(
    caseId: string,
    kind: ResolutionKind,
    text: string,
    selectedDocumentIds: string[] = [],
    notes?: string
  ): Resolution {
    const current = this.cases.get(caseId);
    if (!current) {
      throw new Error('Expediente no encontrado');
    }
    const existing = this.byCase(caseId);
    let seq = this.store.snapshot().nextResolutionSeq;
    const resolution: Resolution = {
      id: existing?.id ?? uid('res'),
      correlative: existing?.correlative ?? correlative(seq, current.receivedAt),
      caseId,
      kind,
      decisionCatalog: kind,
      text,
      notes,
      validityNote: 'Vigencia de 12 meses desde la emisión.',
      workflow: 'PENDING_PRESIDENT',
      preparedBy: this.store.snapshot().users.find(() => true)?.id,
      intakeDate: current.receivedAt.slice(0, 10),
      selectedDocumentIds,
      signatureStatus: 'NONE',
      stampStatus: 'NONE',
      processKind: current.processKind ?? 'INITIAL'
    };
    this.store.update((state) => ({
      ...state,
      nextResolutionSeq: existing ? state.nextResolutionSeq : state.nextResolutionSeq + 1,
      resolutions: [resolution, ...state.resolutions.filter((item) => item.id !== resolution.id)],
      cases: state.cases.map((item) =>
        item.id === caseId ? { ...item, status: 'PENDING_PRESIDENT_VALIDATION', resolutionId: resolution.id } : item
      )
    }));
    this.audit.record({
      action: 'RESOLUTION_PREPARED',
      objectType: 'Resolution',
      objectId: resolution.id,
      description: `Preparó resolución ${resolution.correlative}`,
      projectId: current.projectId
    });
    return resolution;
  }

  validate(caseId: string): Resolution {
    const resolution = this.byCase(caseId);
    const current = this.cases.get(caseId);
    if (!resolution || !current) {
      throw new Error('Resolución no encontrada');
    }
    const issuedAt = nowIso();
    const expiresAt = addYears(issuedAt, 1);
    const issued: Resolution = {
      ...resolution,
      workflow: 'ISSUED',
      validatedBy: 'current',
      issuedAt,
      signatureStatus: 'PROTOTYPE',
      stampStatus: 'PROTOTYPE'
    };
    const selected = new Set(resolution.selectedDocumentIds ?? []);
    this.store.update((state) => ({
      ...state,
      resolutions: state.resolutions.map((item) => (item.id === resolution.id ? issued : item)),
      documents: state.documents.map((item) =>
        selected.has(item.id) ? { ...item, stampStatus: 'PROTOTYPE' as const } : item
      ),
      cases: state.cases.map((item) =>
        item.id === caseId
          ? {
              ...item,
              status: resolution.kind === 'APPROVED' ? 'APPROVED' as const : resolution.kind === 'REJECTED' ? 'REJECTED' as const : 'EXEMPT' as const,
              processStatus: 'RESOLVED' as const,
              approvalIssuedAt: issuedAt,
              approvalExpiresAt: expiresAt,
              packageFrozen: true
            }
          : item
      )
    }));
    this.audit.record({
      action: 'RESOLUTION_VALIDATED',
      objectType: 'Resolution',
      objectId: resolution.id,
      description: `Validó y emitió ${resolution.correlative}`,
      projectId: current.projectId
    });
    const project = this.store.snapshot().projects.find((item) => item.id === current.projectId);
    if (project) {
      this.notifications.notify(project.researcherId, {
        type: 'RESOLUTION_ISSUED',
        title: 'Resolución emitida',
        body: `${resolution.correlative} ya está disponible.`,
        params: { correlative: resolution.correlative },
        link: `/proyectos/${project.id}`
      });
    }
    return issued;
  }

  returnToSecretary(caseId: string, comment: string): void {
    const resolution = this.byCase(caseId);
    const current = this.cases.get(caseId);
    if (!resolution || !current || !comment.trim()) {
      return;
    }
    this.store.update((state) => ({
      ...state,
      resolutions: state.resolutions.map((item) =>
        item.id === resolution.id ? { ...item, workflow: 'DRAFT', returnedComment: comment.trim() } : item
      ),
      cases: state.cases.map((item) => (item.id === caseId ? { ...item, status: 'PENDING_RESOLUTION' } : item))
    }));
    this.audit.record({
      action: 'RESOLUTION_RETURNED_TO_SECRETARY',
      objectType: 'Resolution',
      objectId: resolution.id,
      description: 'Devolvió la resolución a la secretaría ejecutiva',
      reason: comment.trim(),
      projectId: current.projectId
    });
    this.store.snapshot().users
      .filter((user) => hasScope(user, 'COMMITTEE') && holds(user, 'PREPARE_RESOLUTION') && !holds(user, 'VALIDATE_RESOLUTION') && user.committees.includes(current.committee))
      .forEach((user) => this.notifications.notify(user.id, {
        type: 'RESOLUTION_RETURNED',
        title: 'Resolución devuelta',
        body: comment.trim(),
        link: `/expedientes/${caseId}`
      }));
  }

  printableHtml(resolution: Resolution, title: string, researcher: string, committee: string): string {
    return `<!doctype html><html><head><meta charset="utf-8"><title>${resolution.correlative}</title>
      <style>body{font-family:Georgia,serif;padding:48px;color:#1a2433}h1{font-size:22px}p{line-height:1.5}</style></head>
      <body><h1>Resolución ${resolution.correlative}</h1>
      <p><strong>Comité:</strong> ${committee}</p>
      <p><strong>Proyecto:</strong> ${title}</p>
      <p><strong>Investigador/a:</strong> ${researcher}</p>
      <p><strong>Decisión:</strong> ${resolution.decisionCatalog}</p>
      <p>${resolution.text}</p>
      <p>${resolution.validityNote}</p>
      <p>Firma simulada: validada por presidencia de comité. No constituye firma electrónica.</p>
      </body></html>`;
  }
}
