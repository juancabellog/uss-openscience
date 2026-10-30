import { readFileSync } from 'node:fs';
import { questionVisible, requiresSupervisedContext, requiresTutor } from '../src/app/core/forms/form-rules.ts';
import { asText, normalizeTextFields } from '../src/app/core/forms/text-value.ts';
import { prefillValue } from '../src/app/core/forms/institutional-prefill.ts';
import { canReassignReviewer } from '../src/app/core/workflow/reviewer-assignment.policy.ts';
import { isAllowedInstitutionalEmail } from '../src/app/core/identity/institutional-email.ts';

function check(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

const form = readFileSync(new URL('../src/app/core/forms/new-project-form.definition.ts', import.meta.url), 'utf8');
check(form.includes("'FONDNECYT Postdoctoral'"), 'P2 keeps the Excel spelling FONDNECYT');
check(!form.includes('FONDECYT Postdoctoral'), 'P2 does not silently correct the spelling');
check(form.split("'Economía, Negocios y Gobierno'").length - 1 === 2, 'faculty name appears in P8 and P15, not as a duplicated option');
for (const id of ['NP-011', 'NP-012', 'NP-013', 'NP-014', 'NP-015']) {
  const slice = form.slice(form.indexOf(`id: '${id}'`), form.indexOf(`id: '${id}'`) + 500);
  check(slice.includes("displayWhen: 'formative-ir'"), `${id} follows the narrower tutor condition`);
}
check(form.includes("id: 'NP-016'") && form.slice(form.indexOf("id: 'NP-016'"), form.indexOf("id: 'NP-017'")).includes("displayWhen: 'supervised-ir'"), 'P16 uses the wider condition');
check(form.slice(form.indexOf("id: 'NP-017'"), form.indexOf("id: 'NP-018'")).includes("displayWhen: 'supervised-ir'"), 'P17 uses the wider condition');
check(form.includes("linkHref: '/declaracion-singapur.pdf'"), 'Singapore statement uses a site-root PDF');
check(form.includes("linkLabelKey: 'forms.singaporePdf'"), 'Singapore link has an i18n label');
check((form.match(/Bases de datos de dominio público/g) ?? []).length === 2, 'P24 keeps both public-database rows');

check(requiresTutor('Estudiante de magister'), 'magister shows the tutor block');
check(!requiresTutor('Asistente de Investigación'), 'assistant does not show P11-P15');
check(requiresSupervisedContext('Asistente de Investigación'), 'assistant shows P16-P17');
check(requiresSupervisedContext('Personal Técnico'), 'technical staff shows P16-P17');
check(!questionVisible('supervised-ir', 'Docente'), 'a teacher does not show P16-P17');
check(questionVisible('formative-ir', 'Postdoctorante'), 'a postdoc shows P11-P15');

const rules = readFileSync(new URL('../src/app/mocks/classification-rules.mock.ts', import.meta.url), 'utf8');
check(rules.includes('option.classification'), 'P24 still classifies through the existing option metadata');

check(prefillValue({ value: 'Juan', dirty: false, current: '' }) === 'Juan', 'empty field is prefilled');
check(prefillValue({ value: 'Ana', dirty: true, current: '' }) === null, 'a dirty field is not overwritten');
check(prefillValue({ value: 'Ana', dirty: false, current: 'Juan' }) === null, 'a filled field is not overwritten');
check(prefillValue({ value: 'Rectoría', dirty: false, current: '', allowed: ['Medicina'] }) === null, 'unknown catalog values are not invented');
check(prefillValue({ value: '   ', dirty: false, current: '' }) === null, 'blank profile values stay empty');

const ordinary = { hasOrdinaryCapability: true, hasAdvancedCapability: false, reviewSubmitted: false };
const advanced = { hasOrdinaryCapability: false, hasAdvancedCapability: true, reviewSubmitted: false };
for (const caseStatus of ['PENDING_ACCEPTANCE', 'IN_REVIEW', 'AWAITING_RESEARCHER', 'PENDING_RESOLUTION', 'PENDING_PRESIDENT_VALIDATION'] as const) {
  const completed = { status: 'COMPLETED' as const, caseStatus, reviewSubmitted: true };
  check(
    canReassignReviewer({ ...completed, hasOrdinaryCapability: false, hasAdvancedCapability: true }),
    `completed assignment can be reassigned during ${caseStatus} with the advanced capability`
  );
  check(
    !canReassignReviewer({ ...completed, hasOrdinaryCapability: true, hasAdvancedCapability: false }),
    `ordinary capability does not reassign a completed assignment during ${caseStatus}`
  );
}
check(canReassignReviewer({ ...ordinary, status: 'PENDING', caseStatus: 'PENDING_ASSIGNMENT' }), 'pending assignment can be reassigned');
check(canReassignReviewer({ ...ordinary, status: 'ACCEPTED', caseStatus: 'IN_REVIEW' }), 'accepted assignment can be reassigned');
check(!canReassignReviewer({ ...advanced, status: 'ACCEPTED', caseStatus: 'IN_REVIEW' }), 'the advanced capability does not cover an ordinary review');
check(!canReassignReviewer({ ...ordinary, status: 'ACCEPTED', caseStatus: 'PENDING_RESOLUTION' }), 'ordinary capability stops while the resolution is prepared');
check(canReassignReviewer({ ...advanced, status: 'ACCEPTED', caseStatus: 'PENDING_RESOLUTION' }), 'the advanced capability covers resolution preparation');
check(!canReassignReviewer({ ...advanced, status: 'COMPLETED', caseStatus: 'APPROVED', reviewSubmitted: true }), 'a closed case is not reassigned');
check(!canReassignReviewer({ ...ordinary, hasOrdinaryCapability: false, status: 'PENDING', caseStatus: 'IN_REVIEW' }), 'reassignment still requires the capability');

check(isAllowedInstitutionalEmail('persona@uss.cl'), 'uss.cl can self-register');
check(isAllowedInstitutionalEmail('persona@correo.uss.cl'), 'correo.uss.cl can self-register');
check(isAllowedInstitutionalEmail('persona@docente.uss.cl'), 'docente.uss.cl can self-register');
check(!isAllowedInstitutionalEmail('persona@gmail.com'), 'gmail cannot self-register');
check(!isAllowedInstitutionalEmail('persona@universidad.cl'), 'another university cannot self-register');
check(!isAllowedInstitutionalEmail('persona@fakeuss.cl'), 'a lookalike domain cannot self-register');
check(!isAllowedInstitutionalEmail('persona@uss.cl.example.org'), 'an extra @ cannot self-register');

check(asText(null) === '', 'A. a null title is an empty string');
check(asText(undefined) === '', 'B. an undefined title is an empty string');
check(asText('') === '', 'C. an empty title stays empty');
check(asText('Proyecto X') === 'Proyecto X', 'D. a real title is kept');
check(String(null) === 'null' && asText(null) !== 'null', 'null is not turned into the word null');

const questions = [...form.matchAll(/id: '([^']+)'[\s\S]*?fieldKey: '([^']+)'[\s\S]*?type: '([^']+)'/g)].map((match) => ({
  id: match[1],
  fieldKey: match[2],
  type: match[3]
}));
const title = questions.find((question) => question.fieldKey === 'title');
check(title?.id === 'NP-001' && title.type === 'text', 'the proposal title is the first text field');

const stored = normalizeTextFields(questions, { title: null, irName: undefined, program: '' });
check(stored['title'] === '' && stored['irName'] === '' && stored['program'] === '', 'E. empty texts are stored as empty strings');
check(stored['title'] !== 'null' && !Object.values(stored).includes('null') && !Object.values(stored).includes('undefined'), 'E. the word null is not stored');
check(normalizeTextFields(questions, { title: 'Proyecto X' })['title'] === 'Proyecto X', 'D. a written title is stored unchanged');

const reloaded = normalizeTextFields(questions, { title: null, irEmail: undefined });
check(reloaded['title'] === '' && reloaded['irEmail'] === '', 'F. a draft reloaded with null stays empty');

for (const question of questions) {
  if (question.type === 'text' || question.type === 'email' || question.type === 'date' || question.type === 'select') {
    const value = normalizeTextFields([question], { [question.fieldKey]: null })[question.fieldKey];
    check(value === '' && value !== 'null' && value !== 'undefined', `G. ${question.fieldKey} does not render null`);
  }
}

const fields = readFileSync(new URL('../src/app/shared/forms/ethics-form-fields.component.html', import.meta.url), 'utf8');
check(fields.includes('[attr.placeholder]'), 'the placeholder binding removes a missing value');
check(!fields.includes('[placeholder]='), 'the placeholder property is not assigned null');
check(form.includes('required: true') && readFileSync(new URL('../src/app/core/forms/form-answers.ts', import.meta.url), 'utf8').includes('Validators.required'), 'the title stays required');

console.log('check-form-iteration OK');
