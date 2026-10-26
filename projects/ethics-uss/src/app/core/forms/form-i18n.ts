import { FormDefinition } from './form.types';

export type FormI18nNamespace = 'newProject' | 'validation';

export function optionKeyId(id: string): string {
  return id
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^A-Za-z0-9]+/g, '_')
    .replace(/^_|_$/g, '');
}

export function questionI18nBase(ns: FormI18nNamespace, questionId: string): string {
  return `forms.${ns}.questions.${questionId.replaceAll('-', '')}`;
}

export function attachI18nKeys(form: FormDefinition, ns: FormI18nNamespace): FormDefinition {
  return {
    ...form,
    titleKey: `forms.${ns}.title`,
    sections: form.sections.map((section) => ({
      ...section,
      titleKey: `forms.${ns}.sections.${section.id}`
    })),
    questions: form.questions.map((question) => {
      const base = questionI18nBase(ns, question.id);
      return {
        ...question,
        labelKey: `${base}.label`,
        helperTextKey: question.helperText ? `${base}.help` : undefined,
        options: question.options?.map((option) => ({
          ...option,
          labelKey: option.labelKey ?? `${base}.options.${optionKeyId(option.id)}`
        }))
      };
    })
  };
}
