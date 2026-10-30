import { CommitteeCase, EthicsCommittee } from '../../core/models/ethics.models';

/**
 * Presentación de Proyectos: un proyecto muestra como máximo una fila por tipo de comité.
 * Varios CommitteeCase del mismo tipo se conservan; esta capa elige el expediente activo
 * y la aprobación vigente. No cambia estados, permisos ni el workflow.
 */
const TERMINAL = new Set(['APPROVED', 'REJECTED', 'WITHDRAWN', 'EXEMPT']);

const OPEN_RANK: Record<string, number> = {
  PENDING: 10,
  TO_BE_DETERMINED: 10,
  INVALID: 15,
  PENDING_DOCUMENTS: 20,
  NOTIFIED: 30,
  RETURNED: 40,
  ADMISSIBILITY: 50,
  PENDING_ASSIGNMENT: 60,
  PENDING_ACCEPTANCE: 70,
  IN_REVIEW: 80,
  PENDING_SESSION: 90,
  WITH_OBSERVATIONS: 100,
  AWAITING_RESEARCHER: 110,
  PENDING_RESOLUTION: 120,
  PENDING_PRESIDENT_VALIDATION: 130
};

export interface CommitteePresentation {
  committee: EthicsCommittee;
  active: CommitteeCase;
  records: CommitteeCase[];
  /** Vencimiento de la aprobación vigente del comité, si existe. */
  expiresAt: string | null;
  /** Texto secundario cuando el estado visible ya no es esa aprobación. */
  approvalUntil: string | null;
  statusLabelKey: string | null;
}

export function presentCommittees(cases: CommitteeCase[]): CommitteePresentation[] {
  const order: EthicsCommittee[] = [];
  const grouped = new Map<EthicsCommittee, CommitteeCase[]>();
  for (const item of cases) {
    const current = grouped.get(item.committee);
    if (current) {
      current.push(item);
    } else {
      order.push(item.committee);
      grouped.set(item.committee, [item]);
    }
  }
  return order.map((committee) => presentCommittee(grouped.get(committee) ?? []));
}

export function operationalStatusKey(item: CommitteeCase): string | null {
  if (!item.processKind || item.processKind === 'INITIAL' || item.processStatus === 'RESOLVED') {
    return null;
  }
  if (item.processKind === 'AMENDMENT') {
    return item.processStatus === 'SUBMITTED' ? 'projects.amendmentSubmitted' : 'projects.amendmentDraft';
  }
  if (item.processKind === 'FOLLOW_UP' || item.processKind === 'CLOSURE_FOLLOW_UP') {
    return item.processStatus === 'SUBMITTED' ? 'projects.followUpInProgress' : 'projects.followUpDraft';
  }
  return null;
}

/** Etiqueta del proceso abierto. No crea procesos ni cambia el estado del expediente. */
export function currentProcessKey(item: CommitteeCase): string | null {
  if (item.processStatus === 'RESOLVED') {
    return null;
  }
  if (item.processKind === 'AMENDMENT') {
    return item.processStatus === 'SUBMITTED' ? 'projects.amendmentSubmitted' : 'projects.amendmentDraft';
  }
  if (item.processKind === 'FOLLOW_UP') {
    return item.processStatus === 'SUBMITTED' ? 'projects.followUpInProgress' : 'projects.followUpDraft';
  }
  if (item.processKind === 'CLOSURE_FOLLOW_UP') {
    return 'projects.closureFollowUp';
  }
  if ((item.processKind === 'INITIAL' || !item.processKind) && !TERMINAL.has(item.status)) {
    return 'projects.initialEvaluation';
  }
  return null;
}

function presentCommittee(records: CommitteeCase[]): CommitteePresentation {
  const active = selectActive(records);
  const approval = vigenteApproval(records);
  const expiresAt = approval?.approvalExpiresAt ?? null;
  const statusLabelKey = operationalStatusKey(active);
  const approvalUntil = expiresAt && (statusLabelKey || approval?.id !== active.id) ? expiresAt : null;
  return {
    committee: active.committee,
    active,
    records,
    expiresAt,
    approvalUntil,
    statusLabelKey
  };
}

function selectActive(records: CommitteeCase[]): CommitteeCase {
  const later = records
    .filter((item) => operationalStatusKey(item))
    .sort((a, b) => b.receivedAt.localeCompare(a.receivedAt));
  if (later[0]) {
    return later[0];
  }
  const open = records.filter((item) => !TERMINAL.has(item.status));
  const pool = open.length ? open : records;
  return pool.slice().sort((a, b) => {
    const rank = (OPEN_RANK[b.status] ?? 0) - (OPEN_RANK[a.status] ?? 0);
    return rank || b.receivedAt.localeCompare(a.receivedAt);
  })[0];
}

function vigenteApproval(records: CommitteeCase[]): CommitteeCase | undefined {
  return records
    .filter((item) => item.approvalExpiresAt)
    .sort((a, b) => (b.approvalExpiresAt ?? '').localeCompare(a.approvalExpiresAt ?? ''))[0];
}
