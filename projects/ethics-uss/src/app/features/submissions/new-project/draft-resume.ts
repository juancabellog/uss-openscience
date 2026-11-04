export type DraftEditDecision = 'ok' | 'missing' | 'closed' | 'forbidden';

export function draftEditDecision(input: {
  found: boolean;
  status?: string | null;
  ownerId?: string | null;
  actorId?: string | null;
}): DraftEditDecision {
  if (!input.found) {
    return 'missing';
  }
  if (input.status !== 'DRAFT') {
    return 'closed';
  }
  if (!input.actorId || input.ownerId !== input.actorId) {
    return 'forbidden';
  }
  return 'ok';
}

/** El primer paso incompleto. Si todos están completos, el último. */
export function firstPendingStep(complete: readonly boolean[]): number {
  const pending = complete.findIndex((item) => item !== true);
  if (pending < 0) {
    return Math.max(complete.length, 1);
  }
  return pending + 1;
}

export function draftIdentity(
  existing: { id: string; projectId: string } | undefined,
  create: () => { submissionId: string; projectId: string }
): { submissionId: string; projectId: string; created: boolean } {
  if (existing) {
    return { submissionId: existing.id, projectId: existing.projectId, created: false };
  }
  return { ...create(), created: true };
}
