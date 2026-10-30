import type { ClassificationCode, EthicsSubmissionType } from '../models/ethics.models';

export type FormVersion = 'NEW_PROJECT_V1' | 'VALIDATION_V1';

export type FormFieldType =
  | 'text'
  | 'email'
  | 'date'
  | 'select'
  | 'multiselect'
  | 'declaration'
  | 'file'
  | 'file-list';

export interface FormFileAnswer {
  name: string;
  type: string;
  sizeLabel: string;
  key?: string;
}

export type FormAnswerValue = string | string[] | boolean | FormFileAnswer | FormFileAnswer[] | null;

export type FormAnswers = Record<string, FormAnswerValue>;

export interface FormOption {
  id: string;
  label: string;
  labelKey?: string;
  helperText?: string;
  helperTextKey?: string;
  required?: boolean;
  classification?: ClassificationCode;
  group?: 'CEC' | 'CECUA' | 'BIOSEGURIDAD' | 'NONE';
}

export interface FormQuestion {
  id: string;
  number: number;
  sectionId: string;
  fieldKey: string;
  type: FormFieldType;
  label: string;
  labelKey?: string;
  helperText?: string;
  helperTextKey?: string;
  required: boolean;
  multiple?: boolean;
  options?: FormOption[];
  exclusiveOptionId?: string;
  showAllOptionsReadonly?: boolean;
  /** Oculta la pregunta hasta que el cargo del IR cumple la condición. */
  displayWhen?: 'formative-ir' | 'supervised-ir';
  notApplicableWhen?: 'tutor-not-required';
  /** Enlace asociado, por ejemplo el PDF de la Declaración de Singapur. */
  linkHref?: string;
  linkLabelKey?: string;
}

export interface FormSection {
  id: string;
  letter: string;
  title: string;
  titleKey?: string;
  questionIds: string[];
}

export interface FormDefinition {
  id: EthicsSubmissionType;
  version: FormVersion;
  title: string;
  titleKey?: string;
  sections: FormSection[];
  questions: FormQuestion[];
}

export function questionOf(form: FormDefinition, id: string): FormQuestion {
  const question = form.questions.find((item) => item.id === id);
  if (!question) {
    throw new Error(`Pregunta ${id} no encontrada en ${form.version}`);
  }
  return question;
}

export function optionsOf(form: FormDefinition, id: string): FormOption[] {
  return questionOf(form, id).options ?? [];
}

export function optionLabels(form: FormDefinition, id: string): string[] {
  return optionsOf(form, id).map((item) => item.label);
}

export function questionsOfSection(form: FormDefinition, sectionId: string): FormQuestion[] {
  const section = form.sections.find((item) => item.id === sectionId);
  if (!section) {
    return [];
  }
  return section.questionIds.map((id) => questionOf(form, id));
}
