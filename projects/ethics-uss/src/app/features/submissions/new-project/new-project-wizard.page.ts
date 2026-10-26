import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { COMMITTEE_LABELS, requiresTutor } from '../../../mocks/catalogs';
import { NEW_PROJECT_FORM, buildFormGroup, questionsOfSection } from '../../../core/forms';
import { EthicsClassificationService } from '../../../core/services/ethics-classification.service';
import { EthicsSubmissionService } from '../../../core/services/ethics-submission.service';
import { EthicsFormFieldsComponent } from '../../../shared/forms/ethics-form-fields.component';
import { I18nService } from '../../../core/i18n/i18n.service';
import { I18N_IMPORTS } from '../../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-new-project-wizard-page',
  imports: [ReactiveFormsModule, EthicsFormFieldsComponent, ...I18N_IMPORTS],
  templateUrl: './new-project-wizard.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NewProjectWizardPage {
  private readonly submissions = inject(EthicsSubmissionService);
  private readonly classification = inject(EthicsClassificationService);
  private readonly router = inject(Router);
  readonly i18n = inject(I18nService);
  readonly definition = NEW_PROJECT_FORM;
  readonly step = signal(1);
  readonly saving = signal(false);
  readonly message = signal<string | null>(null);
  readonly steps = this.definition.sections.map((section) => section.title);
  readonly form = buildFormGroup(this.definition);
  readonly committeeLabels = COMMITTEE_LABELS;
  readonly formTick = signal(0);
  readonly showTutor = computed(() => {
    this.formTick();
    return requiresTutor(String(this.form.controls['irPosition'].value ?? ''));
  });
  readonly suggested = computed(() => {
    this.formTick();
    const selected = this.form.controls['studyObjects'].value;
    return this.classification.suggest(Array.isArray(selected) ? selected : []);
  });

  constructor() {
    this.form.valueChanges.subscribe(() => this.formTick.update((value) => value + 1));
  }

  questions(sectionId: string) {
    return questionsOfSection(this.definition, sectionId);
  }

  next(): void {
    this.step.update((value) => Math.min(this.steps.length, value + 1));
  }

  back(): void {
    this.step.update((value) => Math.max(1, value - 1));
  }

  saveDraft(): void {
    this.saving.set(true);
    const submission = this.submissions.saveDraft('NEW_PROJECT', this.form.getRawValue());
    this.saving.set(false);
    this.message.set(this.i18n.translate('submission.new.draftSaved'));
    void this.router.navigate(['/proyectos', submission.projectId]);
  }

  submit(): void {
    if (!String(this.form.controls['title'].value ?? '').trim() || !this.form.controls['truth'].value || !this.form.controls['singapore'].value) {
      this.message.set(this.i18n.translate('submission.new.completeToSubmit'));
      return;
    }
    this.saving.set(true);
    const submission = this.submissions.submit('NEW_PROJECT', this.form.getRawValue());
    this.saving.set(false);
    void this.router.navigate(['/proyectos', submission.projectId]);
  }
}
