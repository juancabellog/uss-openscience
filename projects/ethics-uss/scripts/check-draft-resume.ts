import { readFileSync } from 'node:fs';
import { questionVisible } from '../src/app/core/forms/form-rules.ts';
import { prefillValue } from '../src/app/core/forms/institutional-prefill.ts';
import { draftEditDecision, draftIdentity, firstPendingStep } from '../src/app/features/submissions/new-project/draft-resume.ts';

function check(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

check(draftIdentity(undefined, () => ({ submissionId: 'sub-1', projectId: 'prj-1' })).created, '1. a new draft receives ids');
const again = draftIdentity({ id: 'sub-1', projectId: 'prj-1' }, () => ({ submissionId: 'sub-2', projectId: 'prj-2' }));
check(!again.created && again.submissionId === 'sub-1' && again.projectId === 'prj-1', '6. saving again keeps the same draft');

check(questionVisible('formative-ir', 'Estudiante de pregrado'), '7. a saved student position keeps the sponsor questions visible');
check(!questionVisible('formative-ir', 'Docente'), '7. changing the position hides the sponsor questions again');

check(prefillValue({ value: 'María', dirty: false, current: 'Impacto de X' }) === null, 'a saved answer wins over the profile');
check(prefillValue({ value: 'María', dirty: false, current: '' }) === 'María', 'the profile fills only a field the draft left empty');

check(firstPendingStep([false, false, false, false, false, false, false]) === 1, '8. an empty draft opens the first step');
check(firstPendingStep([true, true, false, false, false, false, false]) === 3, '8. resume opens the first incomplete step');
check(firstPendingStep([true, true, true, true, true, true, true]) === 7, '8. a complete unsent draft opens the last step');
check(firstPendingStep([true, false, true, true, true, true, true]) === 2, 'an earlier gap is not skipped');

check(draftEditDecision({ found: true, status: 'DRAFT', ownerId: 'u-1', actorId: 'u-1' }) === 'ok', '4. the owner can resume');
check(draftEditDecision({ found: true, status: 'SUBMITTED', ownerId: 'u-1', actorId: 'u-1' }) === 'closed', '10. a submitted request is not a draft');
check(draftEditDecision({ found: true, status: 'DRAFT', ownerId: 'u-1', actorId: 'u-2' }) === 'forbidden', '11. another person cannot edit the draft');
check(draftEditDecision({ found: false, actorId: 'u-1' }) === 'missing', '3. a missing draft is not created from the edit route');

const wizard = readFileSync(new URL('../src/app/features/submissions/new-project/new-project-wizard.page.ts', import.meta.url), 'utf8');
const service = readFileSync(new URL('../src/app/core/services/ethics-submission.service.ts', import.meta.url), 'utf8');
const card = readFileSync(new URL('../src/app/features/projects/project-card.component.ts', import.meta.url), 'utf8');
const routes = readFileSync(new URL('../src/app/app.routes.ts', import.meta.url), 'utf8');
check(wizard.includes('this.submissionId()') && wizard.includes("['/proyectos', submission.projectId, 'borrador']"), 'the wizard reuses the draft id');
check(wizard.includes('hasAnswer') && wizard.includes('answersToPayload') && wizard.includes('resolveAnswers') && wizard.includes('firstPendingStep'), 'hydration keeps saved answers and chooses the pending step');
check(service.includes('draftIdentity') && service.includes('draftEditDecision') && service.includes('submitted ? capturePersonSnapshot'), 'update, ownership and the submit-only snapshot stay in the service');
check(card.includes('projects.continueDraft') && card.includes("'borrador'"), '12. a draft card continues the same request');
check(routes.includes("path: 'proyectos/:id/borrador'") && routes.includes('NewProjectWizardPage'), 'the draft route reuses the wizard');
check(!service.includes('Borrador sin título') && !service.includes('Solicitud sin título'), 'the fallback title is not stored');

console.log('check-draft-resume OK');
