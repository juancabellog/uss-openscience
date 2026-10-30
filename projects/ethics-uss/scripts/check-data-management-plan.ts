import { readFileSync } from 'node:fs';
import {
  DATA_MANAGEMENT_PLAN,
  currentPlan,
  genericAttach,
  isDataManagementPlan,
  planAttach,
  planVersions
} from '../src/app/core/documents/data-management-plan.ts';

function check(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

const empty = currentPlan([]);
check(empty === null, 'A. a request without a plan has no current version');

const first = planAttach([], 'documento_final_2026.pdf');
check(first.documentType === DATA_MANAGEMENT_PLAN, 'B. the plan upload sets DATA_MANAGEMENT_PLAN');
check(first.requirementKey === DATA_MANAGEMENT_PLAN, 'B. the logical key is the stable type');
check(first.fileName === 'documento_final_2026.pdf', 'E. an arbitrary file name is stored');
check(first.version === 1, 'E. the first upload is version 1');

const second = planAttach([first], 'otro_archivo.pdf');
check(second.requirementKey === first.requirementKey, 'C. a new upload keeps the same logical document');
check(second.version === 2 && first.version === 1, 'C. the new upload is the next version');
const series = [first, second];
check(currentPlan(series)?.version === 2, 'the highest version is current');
check(planVersions(series).map((item) => item.version).join(',') === '2,1', 'D. the previous version stays available');

const namedLikePlan = genericAttach('Plan de Gestion de Datos.pdf', 'other-support');
check(!isDataManagementPlan(namedLikePlan.documentType), 'F. a generic upload is not a plan');
check(currentPlan([namedLikePlan, ...series])?.fileName === 'otro_archivo.pdf', 'F. the generic file does not become the current plan');

const namedPlan = planAttach(series, 'pgd.pdf');
check(namedPlan.documentType === DATA_MANAGEMENT_PLAN && namedPlan.version === 3, 'a file named pgd.pdf is a plan only on the plan channel');

const policy = readFileSync(new URL('../../../backend/src/main/java/cl/uss/ca/ethics/documents/DataManagementPlanPolicy.java', import.meta.url), 'utf8');
const service = readFileSync(new URL('../src/app/core/services/document.service.ts', import.meta.url), 'utf8');
const component = readFileSync(new URL('../src/app/features/projects/request-documents.component.ts', import.meta.url), 'utf8');
const classifier = readFileSync(new URL('../src/app/core/documents/data-management-plan.ts', import.meta.url), 'utf8');
const committee = readFileSync(new URL('../src/app/features/projects/committee-documents.component.ts', import.meta.url), 'utf8');
const submit = readFileSync(new URL('../src/app/core/services/ethics-submission.service.ts', import.meta.url), 'utf8');
const permissions = readFileSync(new URL('../../../backend/src/main/resources/ethics/permissions-V4.yml', import.meta.url), 'utf8');
const seed = readFileSync(new URL('../src/app/mocks/seed.ts', import.meta.url), 'utf8');
const spanish = readFileSync(new URL('../src/app/core/i18n/es.json', import.meta.url), 'utf8');
const english = readFileSync(new URL('../src/app/core/i18n/en.json', import.meta.url), 'utf8');

for (const source of [policy, service, classifier, component]) {
  check(!source.includes('contains("pgd")') && !source.includes("includes('pgd'") && !source.includes('includes("pgd"'), `${source.slice(0, 40)} does not classify by file name`);
  check(!source.includes('contains("gestion")') && !source.includes("includes('gestion'"), 'file name substrings are not a rule');
}
check(component.includes('uploadDataManagementPlan') && component.includes('uploadOther'), 'the plan block and the generic block use different channels');
check(component.includes('caseDocumentsEditable'), 'upload still follows the existing document lock');
check(component.includes('VIEW_DOCUMENTS') && component.includes('UPLOAD_DOCUMENT_VERSION') && component.includes('VIEW_DOCUMENT_VERSIONS'), 'existing document capabilities');
check(!component.includes('VIEW_DATA_MANAGEMENT_PLAN') && !component.includes('UPLOAD_DATA_MANAGEMENT_PLAN'), 'no plan-specific capabilities in the UI');
check(!permissions.includes('VIEW_DATA_MANAGEMENT_PLAN') && !permissions.includes('UPLOAD_DATA_MANAGEMENT_PLAN'), 'no plan-specific capabilities in permissions');
check(!submit.includes('DATA_MANAGEMENT_PLAN'), 'SUBMIT_REQUEST was not tied to the plan');
check(!committee.slice(committee.indexOf('simulateUpload'), committee.indexOf('simulateUpload') + 220).includes('DATA_MANAGEMENT_PLAN'), 'a committee upload does not assign the plan type');

const generic = seed.slice(seed.indexOf("id: 'doc-named-like-pgd'"), seed.indexOf("id: 'doc-val-letter'"));
check(generic.includes('Plan de Gestion de Datos.pdf') && !generic.includes('DATA_MANAGEMENT_PLAN'), 'the lookalike demo file stays generic');
check((seed.match(/documentType: 'DATA_MANAGEMENT_PLAN'/g) ?? []).length === 2, 'only the two explicit demo versions are classified');
check(seed.includes("id: 'doc-pgd-v1'") && seed.includes("id: 'doc-pgd-v2'") && seed.includes('documento_final_2026.pdf'), 'prj-1 demo plan has two versions');
check(spanish.includes('Plan de Gestión de Datos') && spanish.includes('Sin documento cargado') && spanish.includes('Subir nueva versión'), 'Spanish labels');
check(english.includes('Data Management Plan') && english.includes('No document uploaded') && english.includes('Upload new version'), 'English labels');

console.log('check-data-management-plan OK');
