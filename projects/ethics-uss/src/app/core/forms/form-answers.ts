import { FormControl, FormGroup, Validators } from '@angular/forms';
import { EthicsSubmission, EthicsSubmissionType } from '../models/ethics.models';
import { NEW_PROJECT_FORM } from './new-project-form.definition';
import { requiresTutor } from './form-rules';
import { VALIDATION_FORM } from './validation-project-form.definition';
import { FormAnswerValue, FormAnswers, FormDefinition, FormFileAnswer, FormQuestion } from './form.types';

export function formForType(type: EthicsSubmissionType): FormDefinition {
  return type === 'VALIDATION' ? VALIDATION_FORM : NEW_PROJECT_FORM;
}

export function payloadToAnswers(form: FormDefinition, payload: Record<string, unknown> = {}): FormAnswers {
  const answers: FormAnswers = {};
  for (const question of form.questions) {
    answers[question.id] = normalizeValue(payload[question.fieldKey]);
  }
  return answers;
}

export function answersToPayload(form: FormDefinition, answers: FormAnswers): Record<string, unknown> {
  const payload: Record<string, unknown> = {};
  for (const question of form.questions) {
    payload[question.fieldKey] = answers[question.id] ?? defaultValue(question);
  }
  return payload;
}

/**
 * Adapter temporal: el prototipo sigue persistiendo `payload` plano (fieldKey)
 * para no romper servicios existentes. `answers` + `formVersion` son canónicos.
 */
export function resolveAnswers(submission: EthicsSubmission): FormAnswers {
  const form = formForType(submission.type);
  const fromPayload = payloadToAnswers(form, submission.payload);
  return { ...fromPayload, ...(submission.answers as FormAnswers | undefined) };
}

export function hasAnswer(value: FormAnswerValue | undefined): boolean {
  if (value === null || value === undefined) {
    return false;
  }
  if (typeof value === 'string') {
    return value.trim().length > 0;
  }
  if (typeof value === 'boolean') {
    return value;
  }
  if (Array.isArray(value)) {
    return value.length > 0;
  }
  return !!value.name;
}

export function isNotApplicable(question: FormQuestion, answers: FormAnswers): boolean {
  if (question.notApplicableWhen !== 'tutor-not-required') {
    return false;
  }
  if (hasAnswer(answers[question.id])) {
    return false;
  }
  const position = answers['NP-010'];
  return typeof position === 'string' ? !requiresTutor(position) : false;
}

export function completeness(form: FormDefinition, answers: FormAnswers): { answered: number; total: number } {
  const answered = form.questions.filter((question) => hasAnswer(answers[question.id]) || isNotApplicable(question, answers)).length;
  return { answered, total: form.questions.length };
}

export function selectedIds(value: FormAnswerValue | undefined): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

export function asFile(value: FormAnswerValue | undefined): FormFileAnswer | undefined {
  return value && typeof value === 'object' && !Array.isArray(value) && 'name' in value ? value : undefined;
}

export function asFiles(value: FormAnswerValue | undefined): FormFileAnswer[] {
  return Array.isArray(value) ? value.filter((item): item is FormFileAnswer => typeof item === 'object' && !!item && 'name' in item) : [];
}

export function optionLabel(question: FormQuestion, id: string): string {
  return question.options?.find((item) => item.id === id)?.label ?? id;
}

export function buildFormGroup(form: FormDefinition): FormGroup {
  const controls: Record<string, FormControl<FormAnswerValue>> = {};
  for (const question of form.questions) {
    const validators = [];
    if (question.required && (question.type === 'text' || question.type === 'email' || question.type === 'select' || question.type === 'date')) {
      validators.push(Validators.required);
    }
    if (question.type === 'email') {
      validators.push(Validators.email);
    }
    if (question.type === 'declaration' && question.required) {
      validators.push(Validators.requiredTrue);
    }
    controls[question.fieldKey] = new FormControl<FormAnswerValue>(defaultValue(question), { nonNullable: true, validators });
  }
  return new FormGroup(controls);
}

function normalizeValue(value: unknown): FormAnswerValue {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === 'string' || typeof value === 'boolean') {
    return value;
  }
  if (Array.isArray(value)) {
    return value as FormAnswerValue;
  }
  if (typeof value === 'object' && value && 'name' in value) {
    return value as FormFileAnswer;
  }
  return null;
}

function defaultValue(question: FormQuestion): FormAnswerValue {
  if (question.fieldKey === 'program') {
    return 'N/A';
  }
  if (question.type === 'multiselect' || question.type === 'file-list') {
    return [];
  }
  if (question.type === 'declaration') {
    return false;
  }
  if (question.type === 'file') {
    return null;
  }
  return '';
}
