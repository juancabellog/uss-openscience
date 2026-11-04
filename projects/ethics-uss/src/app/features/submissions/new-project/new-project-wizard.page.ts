import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { DemoAuthService } from '../../../core/auth/demo-auth.service';
import { NEW_PROJECT_FORM, asText, answersToPayload, buildFormGroup, hasAnswer, questionOf, questionVisible, questionsOfSection, requiresSupervisedContext, requiresTutor, resolveAnswers, FormAnswerValue, FormQuestion } from '../../../core/forms';
import { InstitutionalProfile, prefillValue } from '../../../core/forms/institutional-prefill';
import { EthicsSubmissionService } from '../../../core/services/ethics-submission.service';
import { ProjectService } from '../../../core/services/project.service';
import { EthicsFormFieldsComponent } from '../../../shared/forms/ethics-form-fields.component';
import { I18nService } from '../../../core/i18n/i18n.service';
import { I18N_IMPORTS } from '../../../shared/i18n/i18n.imports';
import { draftEditDecision, firstPendingStep } from './draft-resume';
import { currentStepBlocks, sectionComplete, WizardStepField, wizardMove, wizardStepStatus } from './wizard-step-navigation';

@Component({
  selector: 'eth-new-project-wizard-page',
  imports: [ReactiveFormsModule, EthicsFormFieldsComponent, ...I18N_IMPORTS],
  templateUrl: './new-project-wizard.page.html',
  styleUrl: './new-project-wizard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NewProjectWizardPage {
  private readonly submissions = inject(EthicsSubmissionService);
  private readonly projects = inject(ProjectService);
  private readonly auth = inject(DemoAuthService);
  private readonly router = inject(Router);
  readonly i18n = inject(I18nService);
  /** Vacío en una solicitud nueva. En `/proyectos/:id/borrador` es el proyecto ya guardado. */
  readonly id = input<string>();
  readonly definition = NEW_PROJECT_FORM;
  readonly step = signal(1);
  readonly saving = signal(false);
  readonly savingAction = signal<'draft' | 'submit' | null>(null);
  readonly message = signal<string | null>(null);
  private readonly submissionId = signal<string | null>(null);
  private readonly openedDraft = signal<string | null>(null);
  readonly steps = this.definition.sections.map((section) => section.title);
  readonly form = buildFormGroup(this.definition);
  readonly formTick = signal(0);
  /** Identidad que PURE administra. Sede, facultad y programa no son columnas de pure_person: igual quedan en este bloque y, si vienen vacíos, no se completan aquí. */
  readonly pureIdentityFields = ['irName', 'irLastName', 'irEmail', 'campus', 'faculty', 'program', 'irPosition'];
  readonly identityLocked = computed(() => this.auth.currentUser()?.purePersonId != null);
  readonly showTutor = computed(() => {
    this.formTick();
    return requiresTutor(asText(this.form.controls['irPosition'].value));
  });
  readonly showSponsorRole = computed(() => {
    this.formTick();
    const position = asText(this.form.controls['irPosition'].value);
    return requiresSupervisedContext(position) && !requiresTutor(position);
  });
  readonly stepStates = computed(() => {
    this.formTick();
    const complete = this.completion();
    const current = this.step();
    return this.definition.sections.map((_, index) => wizardStepStatus(current, index + 1, complete));
  });

  constructor() {
    this.form.valueChanges.subscribe(() => this.formTick.update((value) => value + 1));
    effect(() => {
      const projectId = this.id();
      if (!projectId || this.openedDraft() === projectId) {
        return;
      }
      this.openedDraft.set(projectId);
      this.openDraft(projectId);
    });
    this.prefillAuthenticatedUser();
    void this.prefillFromBackend();
  }

  private openDraft(projectId: string): void {
    const submission = this.submissions.byProject(projectId);
    const project = this.projects.get(projectId)?.project;
    const decision = draftEditDecision({
      found: !!submission,
      status: submission?.status,
      ownerId: project?.researcherId,
      actorId: this.auth.currentUser()?.id
    });
    if (decision !== 'ok' || !submission) {
      void this.router.navigate(decision === 'closed' ? ['/proyectos', projectId] : ['/proyectos']);
      return;
    }
    this.submissionId.set(submission.id);
    const saved = answersToPayload(this.definition, resolveAnswers(submission));
    const patch: Record<string, FormAnswerValue> = {};
    for (const [key, value] of Object.entries(saved)) {
      if (hasAnswer(value as FormAnswerValue)) {
        patch[key] = value as FormAnswerValue;
      }
    }
    this.form.patchValue(patch);
    this.step.set(firstPendingStep(this.completion()));
    if (typeof history !== 'undefined' && history.state?.draftSaved === true) {
      this.message.set(this.i18n.translate('submission.new.draftSaved'));
    }
  }

  private prefillAuthenticatedUser(): void {
    const user = this.auth.currentUser();
    if (!user) {
      return;
    }
    const [givenName, ...rest] = user.displayName.trim().split(/\s+/);
    this.applyProfile({
      givenName,
      familyName: rest.join(' '),
      email: user.email
    });
  }

  private async prefillFromBackend(): Promise<void> {
    try {
      const response = await fetch(`${environment.futureApiBaseUrl}/me/profile`);
      if (!response.ok) {
        return;
      }
      const profile = await response.json() as InstitutionalProfile;
      this.applyProfile(profile);
    } catch {
      // Sin perfil institucional la solicitud sigue en ingreso manual.
    }
  }

  private applyProfile(profile: InstitutionalProfile): void {
    const fields: Array<{ key: string; value: string | null | undefined; allowed?: string[] }> = [
      { key: 'irName', value: profile.givenName },
      { key: 'irLastName', value: profile.familyName },
      { key: 'irEmail', value: profile.email },
      { key: 'campus', value: profile.campus, allowed: this.optionIds('NP-007') },
      { key: 'faculty', value: profile.faculty, allowed: this.optionIds('NP-008') },
      { key: 'program', value: profile.program },
      { key: 'irPosition', value: profile.position, allowed: this.optionIds('NP-010') }
    ];
    for (const field of fields) {
      const control = this.form.controls[field.key];
      const next = prefillValue({
        value: field.value,
        dirty: control.dirty,
        current: asText(control.value),
        allowed: field.allowed
      });
      if (next) {
        control.setValue(next);
      }
    }
  }

  private optionIds(questionId: string): string[] {
    return (questionOf(this.definition, questionId).options ?? []).map((option) => option.id);
  }

  text(field: string): string {
    return asText(this.form.controls[field]?.value);
  }

  questions(sectionId: string) {
    return questionsOfSection(this.definition, sectionId);
  }

  next(): void {
    this.navigateToStep(this.step() + 1);
  }

  back(): void {
    this.navigateToStep(this.step() - 1);
  }

  navigateToStep(target: number): void {
    const complete = this.completion();
    const move = wizardMove(this.step(), target, complete);
    if (move === 'stay') {
      return;
    }
    if (move === 'blocked') {
      if (currentStepBlocks(this.step(), complete)) {
        this.revealCurrentStep();
        this.message.set(this.i18n.translate('submission.new.completeStep'));
      } else {
        this.message.set(this.i18n.translate('submission.new.lockedStep'));
      }
      return;
    }
    this.step.set(target);
    this.clearNavigationMessage();
  }

  private completion(): boolean[] {
    const position = asText(this.form.controls['irPosition'].value);
    return this.definition.sections.map((section) => sectionComplete(this.questions(section.id).map((question) => this.stepField(question, position))));
  }

  private stepField(question: FormQuestion, position: string): WizardStepField {
    const control = this.form.controls[question.fieldKey];
    return {
      required: question.required,
      visible: !question.displayWhen || questionVisible(question.displayWhen, position),
      value: control.value as FormAnswerValue,
      valid: control.valid
    };
  }

  private revealCurrentStep(): void {
    const section = this.definition.sections[this.step() - 1];
    const position = asText(this.form.controls['irPosition'].value);
    for (const question of this.questions(section.id)) {
      if (!this.countsForStep(question, position)) {
        continue;
      }
      this.form.controls[question.fieldKey]?.markAsTouched();
    }
    this.formTick.update((value) => value + 1);
  }

  private countsForStep(question: FormQuestion, position: string): boolean {
    return question.required && (!question.displayWhen || questionVisible(question.displayWhen, position));
  }

  private clearNavigationMessage(): void {
    const current = this.message();
    if (current === this.i18n.translate('submission.new.completeStep') || current === this.i18n.translate('submission.new.lockedStep')) {
      this.message.set(null);
    }
  }

  saveDraft(): void {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.savingAction.set('draft');
    try {
      const submission = this.submissions.saveDraft('NEW_PROJECT', this.form.getRawValue(), this.submissionId() ?? undefined);
      this.submissionId.set(submission.id);
      this.message.set(this.i18n.translate('submission.new.draftSaved'));
      if (this.id() !== submission.projectId) {
        void this.router.navigate(['/proyectos', submission.projectId, 'borrador'], { replaceUrl: true, state: { draftSaved: true } });
      }
    } catch {
      this.message.set(this.i18n.translate('submission.new.draftClosed'));
    } finally {
      this.saving.set(false);
      this.savingAction.set(null);
    }
  }

  submit(): void {
    if (this.saving()) {
      return;
    }
    if (!asText(this.form.controls['title'].value).trim() || !this.form.controls['truth'].value || !this.form.controls['singapore'].value) {
      this.message.set(this.i18n.translate('submission.new.completeToSubmit'));
      return;
    }
    this.saving.set(true);
    this.savingAction.set('submit');
    try {
      const submission = this.submissions.submit('NEW_PROJECT', this.form.getRawValue(), this.submissionId() ?? undefined);
      void this.router.navigate(['/proyectos', submission.projectId]);
    } catch {
      this.message.set(this.i18n.translate('submission.new.draftClosed'));
      this.saving.set(false);
      this.savingAction.set(null);
    }
  }
}
