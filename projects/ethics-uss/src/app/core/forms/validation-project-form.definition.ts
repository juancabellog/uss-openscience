import { attachI18nKeys } from './form-i18n';
import { FormDefinition, FormQuestion, optionsOf } from './form.types';
import { NEW_PROJECT_FORM } from './new-project-form.definition';

const QUESTIONS: FormQuestion[] = [
  {
    id: 'VAL-001',
    number: 1,
    sectionId: 'A',
    fieldKey: 'irName',
    type: 'text',
    label: 'Nombre Responsable de la ejecución del Proyecto (IR)',
    required: true
  },
  {
    id: 'VAL-002',
    number: 2,
    sectionId: 'A',
    fieldKey: 'irLastName',
    type: 'text',
    label: 'Apellidos Responsable de la ejecución del proyecto (IR)',
    required: true
  },
  {
    id: 'VAL-003',
    number: 3,
    sectionId: 'A',
    fieldKey: 'irEmail',
    type: 'email',
    label: 'Correo Electrónico Responsable de la ejecución del proyecto (IR)',
    required: true
  },
  {
    id: 'VAL-004',
    number: 4,
    sectionId: 'A',
    fieldKey: 'category',
    type: 'select',
    label: 'Categoría',
    required: false,
    options: [
      { id: 'Docente', label: 'Docente' },
      { id: 'Docente Investigador', label: 'Docente Investigador' },
      { id: 'Investigador', label: 'Investigador' }
    ]
  },
  {
    id: 'VAL-005',
    number: 5,
    sectionId: 'A',
    fieldKey: 'faculty',
    type: 'select',
    label: 'Facultad/Unidad',
    required: false,
    options: optionsOf(NEW_PROJECT_FORM, 'NP-008')
  },
  {
    id: 'VAL-006',
    number: 6,
    sectionId: 'A',
    fieldKey: 'campus',
    type: 'select',
    label: 'Sede - Campus USS donde se ejecutará',
    required: false,
    options: optionsOf(NEW_PROJECT_FORM, 'NP-007')
  },
  {
    id: 'VAL-007',
    number: 7,
    sectionId: 'B',
    fieldKey: 'fundingSource',
    type: 'select',
    label: 'Tipo de proyecto o fuente de financiamiento',
    required: false,
    options: optionsOf(NEW_PROJECT_FORM, 'NP-002')
  },
  {
    id: 'VAL-008',
    number: 8,
    sectionId: 'B',
    fieldKey: 'title',
    type: 'text',
    label: 'Título de la propuesta',
    required: true
  },
  {
    id: 'VAL-009',
    number: 9,
    sectionId: 'B',
    fieldKey: 'fundingCode',
    type: 'text',
    label: 'Código asignado por la fuente de financiamiento',
    required: false
  },
  {
    id: 'VAL-010',
    number: 10,
    sectionId: 'C',
    fieldKey: 'committees',
    type: 'multiselect',
    label: '¿Qué comité(s) debe(n) validar?',
    required: true,
    multiple: true,
    options: [
      { id: 'CEC', label: 'Comité Ético Científico' },
      { id: 'CECUA', label: 'Comité Institucional de Ética en Cuidado y Uso de Animales' },
      { id: 'BIOSEGURIDAD', label: 'Comité de Bioseguridad' }
    ]
  },
  {
    id: 'VAL-011',
    number: 11,
    sectionId: 'D',
    fieldKey: 'institution',
    type: 'text',
    label: 'Institución que emitió la aprobación',
    required: false
  },
  {
    id: 'VAL-012',
    number: 12,
    sectionId: 'D',
    fieldKey: 'sameTitle',
    type: 'select',
    label: '¿El título del acta de aprobación es idéntico al declarado arriba?',
    required: false,
    options: [
      { id: 'yes', label: 'Sí' },
      { id: 'no', label: 'No, cambió' }
    ]
  },
  {
    id: 'VAL-013',
    number: 13,
    sectionId: 'D',
    fieldKey: 'teamChanged',
    type: 'select',
    label: '¿Cambia el equipo que ejecuta?',
    required: false,
    options: [
      { id: 'yes', label: 'Sí' },
      { id: 'no', label: 'No' }
    ]
  },
  {
    id: 'VAL-014',
    number: 14,
    sectionId: 'D',
    fieldKey: 'procedureChanged',
    type: 'select',
    label: '¿Cambia algún procedimiento respecto del protocolo aprobado?',
    required: false,
    options: [
      { id: 'yes', label: 'Sí' },
      { id: 'no', label: 'No' }
    ]
  },
  {
    id: 'VAL-015',
    number: 15,
    sectionId: 'D',
    fieldKey: 'continuesOrigin',
    type: 'select',
    label: '¿La ejecución del proyecto continúa también en la institución de origen?',
    required: false,
    options: [
      { id: 'yes', label: 'Sí' },
      { id: 'no', label: 'No' }
    ]
  },
  {
    id: 'VAL-016',
    number: 16,
    sectionId: 'D',
    fieldKey: 'startDate',
    type: 'date',
    label: 'Fecha estimada de inicio de actividades en USS',
    required: false
  },
  {
    id: 'VAL-017',
    number: 17,
    sectionId: 'E',
    fieldKey: 'carta',
    type: 'file',
    label: 'Carta de solicitud de validación',
    helperText: 'Solo PDF. Límite visual del conjunto: 100 MB.',
    required: true
  },
  {
    id: 'VAL-018',
    number: 18,
    sectionId: 'E',
    fieldKey: 'supportDocs',
    type: 'multiselect',
    label: 'Documentación de respaldo',
    required: true,
    multiple: true,
    options: [
      { id: 'acta', label: 'Acta o certificado de aprobación emitida por comité de origen', required: true },
      { id: 'protocolo', label: 'Protocolo completo y/o documentación aprobada', required: true },
      { id: 'adjudicacion', label: 'Carta de adjudicación del proyecto' },
      { id: 'seguimiento', label: 'Informe de seguimiento del comité de origen' },
      { id: 'enmiendas', label: 'Aprobación de enmiendas al proyecto emitidas por comité de origen' },
      { id: 'otros', label: 'Otros' }
    ]
  },
  {
    id: 'VAL-019',
    number: 19,
    sectionId: 'E',
    fieldKey: 'uploads',
    type: 'file-list',
    label: 'Carga de documentación',
    helperText: 'Los archivos se muestran por nombre, tipo y tamaño. No se expone la ruta interna.',
    required: false
  }
];

export const VALIDATION_FORM: FormDefinition = attachI18nKeys({
  id: 'VALIDATION',
  version: 'VALIDATION_V1',
  title: 'Validación de proyecto',
  questions: QUESTIONS,
  sections: [
    { id: 'A', letter: 'A', title: 'Investigador responsable', questionIds: ['VAL-001', 'VAL-002', 'VAL-003', 'VAL-004', 'VAL-005', 'VAL-006'] },
    { id: 'B', letter: 'B', title: 'Proyecto', questionIds: ['VAL-007', 'VAL-008', 'VAL-009'] },
    { id: 'C', letter: 'C', title: 'Comités a validar', questionIds: ['VAL-010'] },
    { id: 'D', letter: 'D', title: 'Aprobación externa', questionIds: ['VAL-011', 'VAL-012', 'VAL-013', 'VAL-014', 'VAL-015', 'VAL-016'] },
    { id: 'E', letter: 'E', title: 'Documentación', questionIds: ['VAL-017', 'VAL-018', 'VAL-019'] }
  ]
}, 'validation');
