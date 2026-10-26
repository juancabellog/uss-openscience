import { Injectable, inject } from '@angular/core';
import { I18nService } from '../i18n/i18n.service';
import { DocumentKind, DocumentMetadata, EthicsCommittee, ProcessKind } from '../models/ethics.models';
import { AuditService } from './audit.service';
import { EthicsStore } from './ethics-store.service';
import { nowIso, uid } from './ids';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly i18n = inject(I18nService);

  constructor(
    private readonly store: EthicsStore,
    private readonly audit: AuditService
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
    const current = this.list(ownerId);
    const total = current.reduce((sum, item) => sum + this.parseMb(item.sizeLabel), 0) + file.size / (1024 * 1024);
    if (total > 100) {
      return { error: this.i18n.translate('errors.fileLimit') };
    }
    const documentType = scope?.documentType;
    const committeeType = scope?.committeeType;
    const version = current.filter((item) =>
      item.requirementKey === requirementKey
      && item.documentType === documentType
      && item.committeeType === committeeType
    ).length + 1;
    const doc: DocumentMetadata = {
      id: uid('doc'),
      ownerType,
      ownerId,
      name: file.name,
      kind,
      requirementKey,
      documentType,
      committeeType,
      sizeLabel: `${(file.size / (1024 * 1024)).toFixed(1)} MB`,
      uploadedAt: nowIso(),
      version,
      processKind: processKind ?? 'INITIAL'
    };
    this.store.update((state) => ({ ...state, documents: [doc, ...state.documents] }));
    this.audit.record({
      action: 'DOCUMENT_UPLOADED',
      objectType: 'Document',
      objectId: doc.id,
      description: `Cargó ${doc.name}`,
      projectId,
      caseId: ownerType === 'CASE' ? ownerId : undefined
    });
    return doc;
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
}
