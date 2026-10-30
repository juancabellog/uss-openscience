import { attachI18nKeys } from './form-i18n';
import { FormDefinition, FormQuestion } from './form.types';

const QUESTIONS: FormQuestion[] = [
  {
    id: 'NP-001',
    number: 1,
    sectionId: 'A',
    fieldKey: 'title',
    type: 'text',
    label: 'Título de la Propuesta',
    helperText: 'El mismo título debe ser utilizado para todos los procesos con todos los comités.',
    required: true
  },
  {
    id: 'NP-002',
    number: 2,
    sectionId: 'A',
    fieldKey: 'fundingSource',
    type: 'select',
    label: 'Tipo de proyecto o fuente de financiamiento',
    required: true,
    options: [
      'ANID Anillo',
      'ANID Basal',
      'ANID Desafíos Públicos',
      'Centros FONDAP',
      'CORFO',
      'FONDECYT Exploración',
      'FONDECYT Iniciación',
      'FONDNECYT Postdoctoral',
      'FONDECYT Regular',
      'FONDEF IDeA I+D',
      'FONDEF IDeA I+T',
      'FONDEF VIU',
      'FONIS',
      'Gobierno Regional',
      'Internacional',
      'Núcleos Milenio',
      'Fondos Interno USS',
      'Fondos Interno USS: Inteligencia Artificial',
      'Fondo Interno USS: Proyectos de Vinculación con el Medio (VcM)',
      'Fondo Interno USS: Innovación e Investigación en Docencia Universitaria (CIES)',
      'Fondo Interno USS: Investigación en Políticas Públicas USS (IPSUSS)',
      'TESIS Pregrado',
      'TESIS Magister',
      'TESIS Doctorado',
      'Otros'
    ].map((label) => ({ id: label, label }))
  },
  {
    id: 'NP-003',
    number: 3,
    sectionId: 'A',
    fieldKey: 'fundingStatus',
    type: 'select',
    label: 'Estado del financiamiento',
    required: true,
    options: [
      { id: 'AWARDED', label: 'Adjudicado' },
      { id: 'APPLYING', label: 'En postulación' },
      { id: 'NOT_APPLICABLE', label: 'No aplica' }
    ]
  },
  {
    id: 'NP-004',
    number: 4,
    sectionId: 'B',
    fieldKey: 'irName',
    type: 'text',
    label: 'Nombre Responsable de la ejecución del Proyecto (IR)',
    required: true
  },
  {
    id: 'NP-005',
    number: 5,
    sectionId: 'B',
    fieldKey: 'irLastName',
    type: 'text',
    label: 'Apellidos Responsable de la ejecución del proyecto (IR)',
    required: true
  },
  {
    id: 'NP-006',
    number: 6,
    sectionId: 'B',
    fieldKey: 'irEmail',
    type: 'email',
    label: 'Correo Electrónico Responsable de la ejecución del proyecto (IR)',
    required: true
  },
  {
    id: 'NP-007',
    number: 7,
    sectionId: 'B',
    fieldKey: 'campus',
    type: 'select',
    label: 'Sede - Campus USS donde se ejecuta',
    required: true,
    options: [
      'Santiago - Bellavista',
      'Santiago - Los Leones',
      'Santiago - Ciudad Universitaria (Huechuraba)',
      'Concepción - Tres Pascualas',
      'Concepción - Paicaví',
      'Valdivia - Sede',
      'Valdivia - Centro de Estudios Científicos',
      'Patagonia'
    ].map((label) => ({ id: label, label }))
  },
  {
    id: 'NP-008',
    number: 8,
    sectionId: 'B',
    fieldKey: 'faculty',
    type: 'select',
    label: 'Facultad/Unidad',
    required: true,
    options: [
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
    ].map((label) => ({ id: label, label }))
  },
  {
    id: 'NP-009',
    number: 9,
    sectionId: 'B',
    fieldKey: 'program',
    type: 'text',
    label: 'Carrera, programa, otro',
    helperText: 'Si no aplica, indicar N/A.',
    required: true
  },
  {
    id: 'NP-010',
    number: 10,
    sectionId: 'B',
    fieldKey: 'irPosition',
    type: 'select',
    label: 'Tipo de Cargo',
    required: true,
    options: [
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
    ].map((label) => ({ id: label, label }))
  },
  {
    id: 'NP-011',
    number: 11,
    sectionId: 'C',
    fieldKey: 'sponsorName',
    type: 'text',
    label: 'Nombre del patrocinante o tutor',
    required: true,
    displayWhen: 'formative-ir'
  },
  {
    id: 'NP-012',
    number: 12,
    sectionId: 'C',
    fieldKey: 'sponsorLastName',
    type: 'text',
    label: 'Apellidos del patrocinante o tutor',
    required: true,
    displayWhen: 'formative-ir'
  },
  {
    id: 'NP-013',
    number: 13,
    sectionId: 'C',
    fieldKey: 'sponsorEmail',
    type: 'email',
    label: 'Correo electrónico del patrocinante o tutor',
    required: true,
    displayWhen: 'formative-ir'
  },
  {
    id: 'NP-014',
    number: 14,
    sectionId: 'C',
    fieldKey: 'sponsorCampus',
    type: 'select',
    label: 'Sede - Campus USS del patrocinante o tutor',
    required: true,
    displayWhen: 'formative-ir',
    options: [
      'Santiago - Bellavista',
      'Santiago - Los Leones',
      'Santiago - Ciudad Universitaria (Huechuraba)',
      'Concepción - Tres Pascualas',
      'Concepción - Paicaví',
      'Valdivia - Sede',
      'Valdivia - Centro de Estudios Científicos',
      'Patagonia'
    ].map((label) => ({ id: label, label }))
  },
  {
    id: 'NP-015',
    number: 15,
    sectionId: 'C',
    fieldKey: 'sponsorFaculty',
    type: 'select',
    label: 'Facultad/Unidad patrocinante o tutor',
    required: true,
    displayWhen: 'formative-ir',
    options: [
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
    ].map((label) => ({ id: label, label }))
  },
  {
    id: 'NP-016',
    number: 16,
    sectionId: 'C',
    fieldKey: 'sponsorPosition',
    type: 'select',
    label: 'Tipo de cargo patrocinante o tutor',
    required: true,
    displayWhen: 'supervised-ir',
    options: [
      { id: 'Docente', label: 'Docente' },
      { id: 'Docente Investigador', label: 'Docente Investigador' },
      { id: 'Investigador', label: 'Investigador' }
    ]
  },
  {
    id: 'NP-017',
    number: 17,
    sectionId: 'D',
    fieldKey: 'proposalFrame',
    type: 'select',
    label: 'Esta propuesta',
    required: true,
    displayWhen: 'supervised-ir',
    options: [
      'Es un proyecto original',
      'Se enmarca en un proyecto vigente de mi tutor o patrocinante, sin incorporar cambios',
      'Se enmarca en un proyecto vigente de mi tutor o patrocinante, incorporando cambios o nuevos elementos'
    ].map((label) => ({ id: label, label }))
  },
  {
    id: 'NP-018',
    number: 18,
    sectionId: 'D',
    fieldKey: 'activityTypes',
    type: 'multiselect',
    label: 'Tipo de Actividad',
    helperText: 'Marque todas las opciones que corresponda.',
    required: true,
    multiple: true,
    options: [
      'Actividad de graduación de pregrado',
      'Actividad de graduación de Magister',
      'Asesorías Técnicas Especializadas',
      'Curso proyecto de investigación',
      'Curso metodología de la investigación de pregrado',
      'Estudio de Caso',
      'Proyecto de Investigación',
      'Proyecto de Vinculación con el Medio',
      'Proyecto Piloto (pruebas de concepto, obtención de datos preliminares, pruebas de metodologías, etc.)',
      'Revisión Bibliográfica',
      'Trabajo entorno a necesidades del comercio, la industria y sociedad',
      'Tesis de Pregrado',
      'Tesis de Magister',
      'Tesis de Doctorado'
    ].map((label) => ({ id: label, label }))
  },
  {
    id: 'NP-019',
    number: 19,
    sectionId: 'D',
    fieldKey: 'activityFrame',
    type: 'select',
    label: 'Esta actividad',
    required: true,
    options: [
      'Es un proyecto original',
      'Se enmarca en un proyecto vigente que cuenta con certificaciones éticas y/o bioseguridad y NO incorpora cambios o nuevos elementos',
      'Se enmarca en un proyecto vigente que cuenta con certificaciones éticas y/o bioseguridad y SÍ incorpora cambios o nuevos elementos'
    ].map((label) => ({ id: label, label }))
  },
  {
    id: 'NP-020',
    number: 20,
    sectionId: 'D',
    fieldKey: 'ussPlace',
    type: 'text',
    label: 'Indique el lugar de ejecución en la USS',
    helperText: 'Dónde se obtendrán datos.',
    required: true
  },
  {
    id: 'NP-021',
    number: 21,
    sectionId: 'D',
    fieldKey: 'externalPlace',
    type: 'text',
    label: 'Indique el lugar externo de ejecución, no USS',
    helperText: 'Dónde se obtendrán datos.',
    required: true
  },
  {
    id: 'NP-022',
    number: 22,
    sectionId: 'E',
    fieldKey: 'team',
    type: 'multiselect',
    label: 'El proyecto será ejecutado por',
    helperText: 'Marque todas las opciones que corresponda.',
    required: true,
    multiple: true,
    options: [
      'Estudiantes de pregrado',
      'Estudiantes de postgrado',
      'Docentes',
      'Docentes investigadores',
      'Investigadores',
      'Personal Técnico de apoyo a la Investigación',
      'Personal Administrativo'
    ].map((label) => ({ id: label, label }))
  },
  {
    id: 'NP-023',
    number: 23,
    sectionId: 'E',
    fieldKey: 'products',
    type: 'multiselect',
    label: 'Producto(s) Esperado(s)',
    helperText: 'Marque todas las opciones que corresponda.',
    required: true,
    multiple: true,
    options: [
      'Envío/publicación en revista de especialidad',
      'Libro o capítulo de libro',
      'Informe final de curso de metodología de investigación de pregrado',
      'Informe final de curso proyecto de investigación de pregrado',
      'Tesis o seminario de grado',
      'Producto susceptible a protección de propiedad intelectual',
      'Postulación/presentación en congresos, simposios o seminarios',
      'Postulación a Fondos externos concursables',
      'Generación de Material/Metodología/otro para mejorar la docencia/aprendizaje en la USS',
      'Generación de Material/Metodología/otro para mejorar la docencia/aprendizaje general',
      'Generación de Bases de Datos',
      'Indicaciones para políticas públicas',
      'Material informativo para la comunidad'
    ].map((label) => ({ id: label, label }))
  },
  {
    id: 'NP-024',
    number: 24,
    sectionId: 'F',
    fieldKey: 'studyObjects',
    type: 'multiselect',
    label: 'La metodología de la propuesta incluye los siguientes Objetos/Sujetos de estudio',
    helperText: 'Marque todas las opciones que corresponda.',
    required: true,
    multiple: true,
    exclusiveOptionId: 'NONE',
    showAllOptionsReadonly: true,
    options: [
      {
        id: 'cec-samples',
        group: 'CEC',
        classification: 'CEC',
        label: 'Personas a las cuales se les extraerán muestras biológicas (sangre, tejido, secreciones, etc.)'
      },
      {
        id: 'cec-drugs',
        group: 'CEC',
        classification: 'CEC',
        label: 'Personas a las cuales se les administrarán fármacos o en los que se usarán dispositivos de uso médico'
      },
      {
        id: 'cec-surveys',
        group: 'CEC',
        classification: 'CEC',
        label: 'Personas a las cuales se les realizarán encuestas, entrevistas, cuestionarios, etc.'
      },
      {
        id: 'cec-records',
        group: 'CEC',
        classification: 'CEC',
        label:
          'Personas a las cuales se les realizarán registros de localización (GPS), registros gráficos y/o sonoros (registros en audio, video, fotografías)'
      },
      {
        id: 'cec-vulnerable',
        group: 'CEC',
        classification: 'CEC',
        label:
          'Personas menores de edad, personas provenientes de pueblos originarios, estudiantes, privados de libertad, personas con algún grado de discapacidad que requieren resguardos adicionales u otras personas que requieren resguardos adicionales'
      },
      {
        id: 'cec-records-clinical',
        group: 'CEC',
        classification: 'CEC',
        label: 'Fichas clínicas, sicológicas o sociales'
      },
      {
        id: 'cec-social',
        group: 'CEC',
        classification: 'CEC',
        label:
          'Manipulación del medio ambiente social y/o cultural (Ej. Estudios etnográficos, intervenciones urbanas, etc.)'
      },
      {
        id: 'cec-public-db-1',
        group: 'CEC',
        classification: 'CEC',
        label: 'Bases de datos de dominio público'
      },
      {
        id: 'cec-public-db-2',
        group: 'CEC',
        classification: 'CEC',
        label: 'Bases de datos de dominio público'
      },
      {
        id: 'cecua-vertebrates',
        group: 'CECUA',
        classification: 'CECUA',
        label: 'Animales vertebrados'
      },
      {
        id: 'cecua-samples',
        group: 'CECUA',
        classification: 'CECUA',
        label: 'Obtención de muestras biológicas de animales y/o piezas anatómicas derivadas de animales'
      },
      {
        id: 'cecua-prior',
        group: 'CECUA',
        classification: 'CECUA',
        label: 'Muestras biológicas de animales y/o piezas anatómicas derivadas de animales obtenidas previamente'
      },
      {
        id: 'cecua-commercial',
        group: 'CECUA',
        classification: 'CECUA',
        label: 'Muestras biológicas de origen comercial provenientes de animales'
      },
      {
        id: 'bio-reagents',
        group: 'BIOSEGURIDAD',
        classification: 'BIOSEGURIDAD',
        label: 'El uso de reactivos químicos, drogas, hormonas, radioisótopos'
      },
      {
        id: 'bio-human',
        group: 'BIOSEGURIDAD',
        classification: 'BIOSEGURIDAD',
        label: 'El uso de muestras biológicas proveniente de personas'
      },
      {
        id: 'bio-animal',
        group: 'BIOSEGURIDAD',
        classification: 'BIOSEGURIDAD',
        label: 'El uso de muestras biológicas provenientes de animales'
      },
      {
        id: 'bio-cell-commercial',
        group: 'BIOSEGURIDAD',
        classification: 'BIOSEGURIDAD',
        label: 'Uso de líneas celulares comerciales'
      },
      {
        id: 'bio-cell-generated',
        group: 'BIOSEGURIDAD',
        classification: 'BIOSEGURIDAD',
        label: 'Generación de líneas celulares desde muestras animales o humanas'
      },
      {
        id: 'bio-agents',
        group: 'BIOSEGURIDAD',
        classification: 'BIOSEGURIDAD',
        label:
          'El uso y/o generación de agentes biológicos (como bacterias, virus, hongos, parásitos, material infeccioso, organismos genéticamente modificados)'
      },
      {
        id: 'bio-env-samples',
        group: 'BIOSEGURIDAD',
        classification: 'BIOSEGURIDAD',
        label: 'Manipulación del medio ambiente natural: toma y uso de muestras ambientales'
      },
      {
        id: 'bio-env-apply',
        group: 'BIOSEGURIDAD',
        classification: 'BIOSEGURIDAD',
        label: 'Manipulación del medio ambiente natural: Aplicación de elementos externos al ambiente'
      },
      {
        id: 'NONE',
        group: 'NONE',
        classification: 'NO_REQUIERE_COMITE',
        label: 'Ninguno de los anteriores'
      }
    ]
  },
  {
    id: 'NP-025',
    number: 25,
    sectionId: 'G',
    fieldKey: 'truth',
    type: 'declaration',
    label:
      'Declaro que la información incluida en este formulario es veraz y corresponde a la totalidad de los antecedentes respecto de la actividad identificada.',
    required: true
  },
  {
    id: 'NP-026',
    number: 26,
    sectionId: 'G',
    fieldKey: 'singapore',
    type: 'declaration',
    label: 'Declaro conocer y adscribir a la Declaración de Singapur sobre Integridad en la Investigación.',
    required: true,
    linkHref: '/declaracion-singapur.pdf',
    linkLabelKey: 'forms.singaporePdf'
  }
];

export const NEW_PROJECT_FORM: FormDefinition = attachI18nKeys({
  id: 'NEW_PROJECT',
  version: 'NEW_PROJECT_V1',
  title: 'Nuevo proyecto',
  questions: QUESTIONS,
  sections: [
    { id: 'A', letter: 'A', title: 'Datos del proyecto', questionIds: ['NP-001', 'NP-002', 'NP-003'] },
    { id: 'B', letter: 'B', title: 'Investigador responsable', questionIds: ['NP-004', 'NP-005', 'NP-006', 'NP-007', 'NP-008', 'NP-009', 'NP-010'] },
    { id: 'C', letter: 'C', title: 'Patrocinante o tutor', questionIds: ['NP-011', 'NP-012', 'NP-013', 'NP-014', 'NP-015', 'NP-016'] },
    { id: 'D', letter: 'D', title: 'Características del proyecto', questionIds: ['NP-017', 'NP-018', 'NP-019', 'NP-020', 'NP-021'] },
    { id: 'E', letter: 'E', title: 'Equipo y resultados', questionIds: ['NP-022', 'NP-023'] },
    { id: 'F', letter: 'F', title: 'Metodología', questionIds: ['NP-024'] },
    { id: 'G', letter: 'G', title: 'Declaraciones', questionIds: ['NP-025', 'NP-026'] }
  ]
}, 'newProject');
