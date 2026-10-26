import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { COMMITTEE_LABELS } from '../../mocks/catalogs';
import { applyNoneExclusive } from '../../mocks/classification-rules.mock';
import { FormAnswers, FormFileAnswer, FormOption, FormQuestion, asFile, asFiles, hasAnswer, isNotApplicable, selectedIds } from '../../core/forms';
import { I18nService } from '../../core/i18n/i18n.service';
import { I18N_IMPORTS } from '../i18n/i18n.imports';

@Component({
  selector: 'eth-ethics-form-fields',
  imports: [ReactiveFormsModule, ...I18N_IMPORTS],
  templateUrl: './ethics-form-fields.component.html',
  styleUrl: './ethics-form-fields.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EthicsFormFieldsComponent {
  private readonly i18n = inject(I18nService);
  readonly questions = input.required<FormQuestion[]>();
  readonly mode = input<'edit' | 'readonly'>('readonly');
  readonly form = input<FormGroup>();
  readonly answers = input<FormAnswers>({});
  readonly fileSelected = output<{ question: FormQuestion; file: File }>();
  readonly fileRemoved = output<{ question: FormQuestion; name: string }>();
  readonly preview = output<FormFileAnswer>();
  readonly labels = COMMITTEE_LABELS;

  labelOf(question: FormQuestion): string {
    return question.labelKey ? this.i18n.translate(question.labelKey) || question.label : question.label;
  }

  helpOf(question: FormQuestion): string {
    return question.helperTextKey ? this.i18n.translate(question.helperTextKey) || question.helperText || '' : question.helperText ?? '';
  }

  optionOf(option: FormOption): string {
    return option.labelKey ? this.i18n.translate(option.labelKey) || option.label : option.label;
  }

  selected(question: FormQuestion, optionId: string): boolean {
    if (this.mode() === 'edit') {
      const value = this.form()?.get(question.fieldKey)?.value;
      return Array.isArray(value) && value.includes(optionId);
    }
    return selectedIds(this.answers()[question.id]).includes(optionId);
  }

  declared(question: FormQuestion): boolean {
    if (this.mode() === 'edit') {
      return !!this.form()?.get(question.fieldKey)?.value;
    }
    return this.answers()[question.id] === true;
  }

  toggle(question: FormQuestion, optionId: string): void {
    const control = this.form()?.get(question.fieldKey);
    if (!control) {
      return;
    }
    const current = Array.isArray(control.value) ? control.value : [];
    control.setValue(question.exclusiveOptionId ? applyNoneExclusive(current, optionId, question.exclusiveOptionId) : toggleValue(current, optionId));
  }

  empty(question: FormQuestion): boolean {
    return !hasAnswer(this.answers()[question.id]);
  }

  notApplicable(question: FormQuestion): boolean {
    return isNotApplicable(question, this.answers());
  }

  textValue(question: FormQuestion): string {
    if (this.notApplicable(question)) {
      return this.i18n.translate('common.na');
    }
    const value = this.answers()[question.id];
    if (!hasAnswer(value)) {
      return this.i18n.translate('common.noAnswer');
    }
    if (typeof value === 'string') {
      const option = question.options?.find((item) => item.id === value);
      return option ? this.optionOf(option) : value;
    }
    return String(value);
  }

  selectedOptions(question: FormQuestion): FormOption[] {
    const ids = selectedIds(this.answers()[question.id]);
    return (question.options ?? []).filter((item) => ids.includes(item.id));
  }

  groupedOptions(question: FormQuestion): Array<{ title?: string; items: FormOption[] }> {
    const options = question.options ?? [];
    if (!question.showAllOptionsReadonly) {
      return [{ items: options }];
    }
    const order = ['CEC', 'CECUA', 'BIOSEGURIDAD', 'NONE'];
    return order
      .map((group) => ({
        title: groupTitle(group),
        items: options.filter((item) => item.group === group)
      }))
      .filter((group) => group.items.length);
  }

  files(question: FormQuestion): FormFileAnswer[] {
    const raw = this.mode() === 'edit' ? this.form()?.get(question.fieldKey)?.value : this.answers()[question.id];
    if (question.type === 'file') {
      const file = asFile(raw);
      return file ? [file] : [];
    }
    return asFiles(raw);
  }

  onFile(question: FormQuestion, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.fileSelected.emit({ question, file });
    }
    input.value = '';
  }
}

function toggleValue(current: string[], optionId: string): string[] {
  return current.includes(optionId) ? current.filter((item) => item !== optionId) : [...current, optionId];
}

function groupTitle(group: string): string | undefined {
  if (group === 'NONE') {
    return 'forms.noDerivation';
  }
  return COMMITTEE_LABELS[group];
}
