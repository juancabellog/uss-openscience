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

export function requiresTutor(position: string | null | undefined): boolean {
  return !!position && PROTOTYPE_RULE.irPositionsRequiringTutor.includes(position);
}
