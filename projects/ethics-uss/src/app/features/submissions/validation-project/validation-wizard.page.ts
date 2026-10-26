import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { VALIDATION_FORM, buildFormGroup, questionsOfSection } from '../../../core/forms';
import { FormFileAnswer, FormQuestion } from '../../../core/forms/form.types';
import { DocumentService } from '../../../core/services/document.service';
import { EthicsSubmissionService } from '../../../core/services/ethics-submission.service';
import { EthicsFormFieldsComponent } from '../../../shared/forms/ethics-form-fields.component';
import { I18nService } from '../../../core/i18n/i18n.service';
import { I18N_IMPORTS } from '../../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-validation-wizard-page',
  imports: [ReactiveFormsModule, EthicsFormFieldsComponent, ...I18N_IMPORTS],
  templateUrl: './validation-wizard.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ValidationWizardPage {
  private readonly submissions = inject(EthicsSubmissionService);
  private readonly documents = inject(DocumentService);
  private readonly router = inject(Router);
  readonly i18n = inject(I18nService);
  readonly definition = VALIDATION_FORM;
  readonly step = signal(1);
  readonly message = signal<string | null>(null);
  readonly steps = this.definition.sections.map((section) => section.title);
  readonly form = buildFormGroup(this.definition);

  questions(sectionId: string) {
    return questionsOfSection(this.definition, sectionId);
  }

  onFile(event: { question: FormQuestion; file: File }): void {
    const result = this.documents.simulateUpload('SUBMISSION', 'draft-validation', event.file, 'SUPPORT', event.question.fieldKey);
    if ('error' in result) {
      this.message.set(result.error);
      return;
    }
    const file: FormFileAnswer = { name: result.name, type: 'PDF', sizeLabel: result.sizeLabel, key: event.question.fieldKey };
    if (event.question.type === 'file') {
      this.form.controls[event.question.fieldKey].setValue(file);
      return;
    }
    const current = this.form.controls[event.question.fieldKey].value;
    const files = Array.isArray(current) ? current : [];
    this.form.controls[event.question.fieldKey].setValue([...files, file]);
  }

  removeFile(event: { question: FormQuestion; name: string }): void {
    const current = this.form.controls[event.question.fieldKey].value;
    if (event.question.type === 'file') {
      this.form.controls[event.question.fieldKey].setValue(null);
      return;
    }
    const files = Array.isArray(current) ? current.filter((item) => !(typeof item === 'object' && item && 'name' in item && item.name === event.name)) : [];
    this.form.controls[event.question.fieldKey].setValue(files);
  }

  next(): void {
    this.step.update((value) => Math.min(this.steps.length, value + 1));
  }

  back(): void {
    this.step.update((value) => Math.max(1, value - 1));
  }

  submit(): void {
    const support = this.form.controls['supportDocs'].value;
    const selected = Array.isArray(support) ? support : [];
    if (!selected.includes('acta') || !selected.includes('protocolo')) {
      this.message.set(this.i18n.translate('submission.validation.needActa'));
      return;
    }
    const submission = this.submissions.submit('VALIDATION', this.form.getRawValue());
    void this.router.navigate(['/proyectos', submission.projectId]);
  }
}
