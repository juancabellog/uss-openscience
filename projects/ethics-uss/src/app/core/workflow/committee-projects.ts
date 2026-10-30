/**
 * Un proyecto del comité es el que tiene al menos un expediente en un comité
 * del que la persona es miembro. Se cuenta una vez por projectId.
 * Una ReviewerAssignment no agrega el proyecto a este conjunto.
 */
export function projectBelongsToCommittees(
  caseCommittees: readonly string[],
  membership: ReadonlySet<string>
): boolean {
  return caseCommittees.some((committee) => membership.has(committee));
}
