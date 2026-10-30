export const PROTOTYPE_RULE = {
  id: 'PROTOTYPE_RULE_TUTOR_VISIBILITY',
  description: 'Muestra patrocinante/tutor si el cargo del IR sugiere dependencia formativa. No es regla institucional definitiva.',
  irPositionsRequiringTutor: [
    'Estudiante de pregrado',
    'Estudiante de magíster',
    'Estudiante de magister',
    'Estudiante de doctorado',
    'Postdoctorante'
  ]
};

/** P16 y P17: la misma lista formativa, más asistente de investigación y personal técnico. */
export const SUPERVISED_POSITIONS = [
  ...PROTOTYPE_RULE.irPositionsRequiringTutor,
  'Asistente de Investigación',
  'Personal Técnico'
];

export function requiresTutor(position: string | null | undefined): boolean {
  return !!position && PROTOTYPE_RULE.irPositionsRequiringTutor.includes(position);
}

export function requiresSupervisedContext(position: string | null | undefined): boolean {
  return !!position && SUPERVISED_POSITIONS.includes(position);
}

export function questionVisible(
  rule: 'formative-ir' | 'supervised-ir' | undefined,
  position: string | null | undefined
): boolean {
  if (!rule) {
    return true;
  }
  return rule === 'formative-ir' ? requiresTutor(position) : requiresSupervisedContext(position);
}
