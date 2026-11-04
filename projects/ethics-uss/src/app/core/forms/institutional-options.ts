/** Catálogo único de sede, facultad y cargo. Lo usan el formulario de proyecto y la activación. */
export const INSTITUTIONAL_CAMPUSES = [
  'Santiago - Bellavista',
  'Santiago - Los Leones',
  'Santiago - Ciudad Universitaria (Huechuraba)',
  'Concepción - Tres Pascualas',
  'Concepción - Paicaví',
  'Valdivia - Sede',
  'Valdivia - Centro de Estudios Científicos',
  'Patagonia'
] as const;

export const INSTITUTIONAL_FACULTIES = [
  'Arquitectura, Arte y Diseño',
  'Ciencias',
  'Ciencias de la Rehabilitación y Calidad de Vida',
  'Ciencias para el cuidado de la Salud',
  'Derecho y Ciencias Sociales',
  'Economía, Negocios y Gobierno',
  'Educación',
  'Ingeniería',
  'Medicina',
  'Medicina Veterinaria',
  'Odontología'
] as const;

export const INSTITUTIONAL_POSITIONS = [
  'Estudiante de pregrado',
  'Estudiante de magister',
  'Estudiante de doctorado',
  'Postdoctorante',
  'Asistente de Investigación',
  'Personal Técnico',
  'Docente',
  'Co-Investigador',
  'Investigador',
  'Director Unidad Administrativa'
] as const;

export function institutionalSelectOptions(values: readonly string[]): { id: string; label: string }[] {
  return values.map((label) => ({ id: label, label }));
}
