import { readFileSync } from 'node:fs';
import { questionVisible, requiresTutor } from '../src/app/core/forms/form-rules.ts';
import type { WizardStepField } from '../src/app/features/submissions/new-project/wizard-step-navigation.ts';
import {
  currentStepBlocks,
  sectionComplete,
  wizardMove,
  wizardStepStatus
} from '../src/app/features/submissions/new-project/wizard-step-navigation.ts';

function check(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

function field(value: unknown, valid = true, required = true, visible = true): WizardStepField {
  return { required, visible, value, valid };
}

const emptyStep = [field('', false)];
const filledStep = [field('Valor de prueba')];
const sevenEmpty = [false, false, false, false, false, false, false];

check(!sectionComplete(emptyStep), '1. an empty required step is not complete');
check(!sectionComplete([field('  ', true)]), 'whitespace is not an answer');
check(wizardStepStatus(1, 1, sevenEmpty) === 'current', '3. the open step is current');
check(wizardMove(1, 1, sevenEmpty) === 'stay', '4. clicking the current step does nothing');
check(wizardStepStatus(1, 7, sevenEmpty) === 'locked', '5. a future step stays locked');
check(wizardMove(1, 7, sevenEmpty) === 'blocked', '5. clicking a locked future step does not navigate');
check(currentStepBlocks(1, sevenEmpty), '5. the incomplete current step blocks progress');

const firstDone = [true, false, false, false, false, false, false];
check(wizardMove(1, 2, firstDone) === 'forward', '6. the next completed path can be opened');
check(wizardMove(1, 5, firstDone) === 'blocked', '6. a step beyond the valid path stays blocked');
check(wizardStepStatus(1, 2, firstDone) === 'available', '6. the next step is available');
check(wizardStepStatus(1, 5, firstDone) === 'locked', '6. a skipped step is locked');

const throughMethodology = [true, true, true, true, true, true, false];
check(wizardMove(6, 2, throughMethodology) === 'back', '1. a previous step opens without validating the current one');
check(wizardStepStatus(6, 2, throughMethodology) === 'available', '1. a previous step is available');
const kept = { title: 'Estudio de sueño', products: ['Tesis de Pregrado'] };
check(wizardMove(6, 2, throughMethodology) === 'back' && kept.title === 'Estudio de sueño' && kept.products.length === 1, '2. navigation does not own or clear form values');

const brokenAtTwo = [true, false, true, true, true, true, false];
check(wizardMove(2, 1, brokenAtTwo) === 'back', '8. going back does not require the current step to be valid');
check(wizardMove(2, 5, brokenAtTwo) === 'blocked', 'C. an invalid step blocks a forward jump');
check(wizardMove(2, 6, throughMethodology) === 'forward', 'D. a still-valid path reopens directly');

check(sectionComplete([field('', false, true, false)]), '12. a hidden required question does not block the step');
check(!sectionComplete([field('', false, true, true)]), '12. a visible required question blocks the step');
check(!requiresTutor('Docente') && questionVisible('formative-ir', 'Estudiante de pregrado'), '12. tutor questions follow the existing visibility rule');
check(sectionComplete([field(false, false)]) === false && sectionComplete([field(['cec-surveys'])]), 'declarations and multiselect use the same answer rule');

const page = readFileSync(new URL('../src/app/features/submissions/new-project/new-project-wizard.page.ts', import.meta.url), 'utf8');
const template = readFileSync(new URL('../src/app/features/submissions/new-project/new-project-wizard.page.html', import.meta.url), 'utf8');
const styles = readFileSync(new URL('../src/app/features/submissions/new-project/new-project-wizard.page.scss', import.meta.url), 'utf8');
const routes = readFileSync(new URL('../src/app/app.routes.ts', import.meta.url), 'utf8');
check(template.includes('type="button"') && template.includes('navigateToStep(i + 1)'), 'the step is a button');
check(template.includes('wizard-step--available') && template.includes('wizard-step--completed') && template.includes('wizard-step--locked') && template.includes('wizard-step--current'), 'each step exposes its visual state');
check(styles.includes('button.wizard-step.wizard-step--available') && styles.includes('button.wizard-step.wizard-step--completed') && styles.includes('cursor: pointer'), 'navigable pills use a pointer cursor');
check(styles.includes('button.wizard-step.wizard-step--locked') && styles.includes('button.wizard-step.wizard-step--current') && styles.includes('cursor: default'), 'current and locked pills keep the default cursor');
check(styles.includes('wizard-step--available:hover') && styles.includes('wizard-step--completed:hover') && !styles.includes('wizard-step--current:hover') && !styles.includes('wizard-step--locked:hover'), 'only navigable pills have a hover');
check(styles.includes('button.wizard-step:focus-visible') && styles.includes('outline: 2px solid var(--eth-primary)'), 'keyboard focus stays visible');
check(template.includes('aria-current') && template.includes('aria-disabled'), 'the step exposes current and locked state');
check(template.includes('submission.new.steps'), 'the step list has an accessible name');
check(template.includes('next()') && template.includes('back()') && template.includes('saveDraft()'), '7-9. continue, back and save draft remain');
check(page.includes('this.navigateToStep(this.step() + 1)') && page.includes('this.navigateToStep(this.step() - 1)'), '7-8. continue and back use the stepper');
check(page.includes('questionVisible') && page.includes('sectionComplete'), '12. step validity uses the existing visibility rules');
const navigate = page.slice(page.indexOf('navigateToStep(target: number)'), page.indexOf('private completion'));
check(!navigate.includes('saveDraft') && !navigate.includes('.reset(') && !navigate.includes('setValue'), 'navigation does not save, reset or rewrite the form');
check(!template.includes('routerLink') && (routes.match(/proyectos\/nuevo\/proyecto/g) ?? []).length === 1, 'the wizard keeps its single route');

const i18n = readFileSync(new URL('./build-i18n.mjs', import.meta.url), 'utf8');
check(i18n.includes("p('submission.new.lockedStep', 'Completa los pasos anteriores para continuar.', 'Complete the previous steps to continue.')"), 'the locked-step message is translated');
check(i18n.includes('forms.newProject.sections.B'), 'step labels stay in the existing catalog');

console.log('check-new-project-wizard OK');
