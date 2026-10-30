import { Injectable, inject } from '@angular/core';
import { DemoAuthService } from '../auth/demo-auth.service';
import { DATA_MANAGEMENT_PLAN, isDataManagementPlan, planAttach, planVersions } from '../documents/data-management-plan';
import { I18nService } from '../i18n/i18n.service';
import { DocumentKind, DocumentMetadata, EthicsCommittee, ProcessKind } from '../models/ethics.models';
import { caseDocumentsEditable, researcherResponseOpen } from '../workflow/committee-workspace.policy';
import { PermissionService } from './permission.service';
import { AuditService } from './audit.service';
import { EthicsStore } from './ethics-store.service';
import { nowIso, uid } from './ids';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly i18n = inject(I18nService);
  private readonly auth = inject(DemoAuthService);

  constructor(
    private readonly store: EthicsStore,
    private readonly audit: AuditService,
    private readonly permissions: PermissionService
  ) {}

  list(ownerId: string): DocumentMetadata[] {
    return this.store.snapshot().documents.filter((item) => item.ownerId === ownerId);
  }

  latest(ownerId: string): DocumentMetadata[] {
    const grouped = new Map<string, DocumentMetadata>();
    for (const item of this.list(ownerId)) {
      const key = `${item.committeeType ?? ''}:${item.documentType ?? ''}:${item.requirementKey ?? item.id}`;
      const current = grouped.get(key);
      if (!current || item.version > current.version) {
        grouped.set(key, item);
      }
    }
    return [...grouped.values()];
  }

  versions(ownerId: string, requirementKey: string): DocumentMetadata[] {
    return this.list(ownerId)
      .filter((item) => item.requirementKey === requirementKey)
      .sort((a, b) => b.version - a.version);
  }

  simulateUpload(
    ownerType: DocumentMetadata['ownerType'],
    ownerId: string,
    file: File,
    kind: DocumentKind,
    requirementKey?: string,
    projectId?: string,
    processKind?: ProcessKind,
    scope?: { documentType?: string; committeeType?: EthicsCommittee }
  ): DocumentMetadata | { error: string } {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      return { error: this.i18n.translate('errors.pdfOnly') };
    }
    const responseCycle = this.responseCycleFor(ownerType, ownerId);
    if (responseCycle === false) {
      return { error: this.i18n.translate('errors.documentsClosed') };
    }
    const current = this.list(ownerId);
    const total = current.reduce((sum, item) => sum + this.parseMb(item.sizeLabel), 0) + file.size / (1024 * 1024);
    if (total > 100) {
      return { error: this.i18n.translate('errors.fileLimit') };
    }
    const plan = scope?.documentType === DATA_MANAGEMENT_PLAN ? planAttach(current, file.name) : null;
    const documentType = plan?.documentType ?? scope?.documentType;
    const committeeType = plan ? undefined : scope?.committeeType;
    const resolvedKey = plan?.requirementKey ?? requirementKey;
    const matches = plan
      ? current.filter((item) => isDataManagementPlan(item.documentType))
      : current.filter((item) =>
        item.requirementKey === resolvedKey
        && item.documentType === documentType
        && item.committeeType === committeeType
      );
    const version = plan?.version ?? matches.reduce((max, item) => Math.max(max, item.version), 0) + 1;
    const doc: DocumentMetadata = {
      id: uid('doc'),
      ownerType,
      ownerId,
      name: file.name,
      kind,
      requirementKey: resolvedKey,
      documentType,
      committeeType,
      sizeLabel: `${(file.size / (1024 * 1024)).toFixed(1)} MB`,
      uploadedAt: nowIso(),
      uploadedBy: this.auth.currentUser()?.id,
      version,
      responseCycle: responseCycle || undefined,
      processKind: processKind ?? 'INITIAL'
    };
    this.store.update((state) => ({ ...state, documents: [doc, ...state.documents] }));
    this.audit.record({
      action: version > 1 ? 'DOCUMENT_VERSION_UPLOADED' : 'DOCUMENT_UPLOADED',
      objectType: 'Document',
      objectId: doc.id,
      description: version > 1 ? `Cargó la versión ${version} de ${doc.name}` : `Cargó ${doc.name}`,
      projectId,
      caseId: ownerType === 'CASE' ? ownerId : undefined
    });
    return doc;
  }

  /** Plan vigente de la solicitud. El tipo es la única clave; el nombre del archivo no participa. */
  dataManagementPlan(ownerId: string): { current: DocumentMetadata | null; history: DocumentMetadata[] } {
    const versions = planVersions(this.list(ownerId));
    return { current: versions[0] ?? null, history: versions.slice(1) };
  }

  uploadDataManagementPlan(ownerId: string, file: File, projectId?: string): DocumentMetadata | { error: string } {
    return this.simulateUpload('SUBMISSION', ownerId, file, 'SUPPORT', DATA_MANAGEMENT_PLAN, projectId, undefined, {
      documentType: DATA_MANAGEMENT_PLAN
    });
  }

  uploadOther(ownerId: string, file: File, projectId?: string): DocumentMetadata | { error: string } {
    return this.simulateUpload('SUBMISSION', ownerId, file, 'SUPPORT', uid('other'), projectId);
  }

  remove(id: string): void {
    this.store.update((state) => ({ ...state, documents: state.documents.filter((item) => item.id !== id) }));
  }

  /** Reasigna el borrador a la solicitud. La carta sigue siendo un único documento. */
  attachDraft(draftOwnerId: string, submissionId: string): void {
    this.store.update((state) => ({
      ...state,
      documents: state.documents.map((item) => (
        item.ownerId === draftOwnerId ? { ...item, ownerId: submissionId, ownerType: 'SUBMISSION' as const } : item
      ))
    }));
  }

  /** Retira los documentos asociados a un comité. La carta transversal no tiene committeeType. */
  removeCommittee(ownerId: string, committee: EthicsCommittee): void {
    this.store.update((state) => ({
      ...state,
      documents: state.documents.filter((item) => !(item.ownerId === ownerId && item.committeeType === committee))
    }));
  }

  private parseMb(label: string): number {
    return Number.parseFloat(label) || 0;
  }

  /** false bloquea la carga. Un número marca la ronda que se está respondiendo. */
  private responseCycleFor(ownerType: DocumentMetadata['ownerType'], ownerId: string): number | false | undefined {
    if (ownerType !== 'CASE') {
      return undefined;
    }
    const state = this.store.snapshot();
    const item = state.cases.find((entry) => entry.id === ownerId);
    if (!item) {
      return undefined;
    }
    const user = this.auth.currentUser();
    const project = state.projects.find((entry) => entry.id === item.projectId);
    const isOwner = !!user && project?.researcherId === user.id;
    const canRespond = this.permissions.can(user, 'RESPOND_TO_OBSERVATIONS', {
      projectId: item.projectId,
      committeeCaseId: item.id,
      committeeType: item.committee,
      workflowStatus: item.status
    });
    const context = { canRespond, isOwner, status: item.status, packageFrozen: item.packageFrozen };
    if (!caseDocumentsEditable(context)) {
      return false;
    }
    if (!researcherResponseOpen(context)) {
      return undefined;
    }
    return Math.max(1, item.currentCycle - 1);
  }
}
