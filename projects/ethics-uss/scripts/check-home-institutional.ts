import { capabilitiesForRoles, institutionalModules, matchesCaseQueue } from '../src/app/features/dashboard/home-institutional.ts';

function check(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

const camila = capabilitiesForRoles(['ADMINISTRATOR']);
const modules = institutionalModules(camila);
check(modules.map((item) => item.id).join(',') === 'pending-classification,admissibility,resolutions,expiring', 'Camila modules');
check(modules.every((item) => camila.has(item.capability)), 'each module cites a granted capability');
check(!modules.some((item) => item.capability.startsWith('SHOW_')), 'no presentation capabilities');

const expirationsOnly = institutionalModules(new Set(['VIEW_EXPIRATIONS']));
check(expirationsOnly.length === 1 && expirationsOnly[0].id === 'expiring', 'expiring module follows VIEW_EXPIRATIONS');
check(institutionalModules(new Set()).length === 0, 'no capabilities hides every module');

const combined = institutionalModules(capabilitiesForRoles(['ADMINISTRATOR', 'REVIEWER']));
check(new Set(combined.map((item) => item.id)).size === combined.length, 'multi-role modules are not duplicated');

check(matchesCaseQueue('ADMISSIBILITY', 'admissibility'), 'admissibility queue');
check(!matchesCaseQueue('IN_REVIEW', 'admissibility'), 'in review is not admissibility');
check(matchesCaseQueue('PENDING_PRESIDENT_VALIDATION', 'resolutions'), 'chair validation counts as resolutions');
check(matchesCaseQueue('PENDING_RESOLUTION', 'resolutions'), 'pending resolution counts as resolutions');
check(!matchesCaseQueue('APPROVED', 'resolutions'), 'approved is not the resolutions queue');

console.log('home-institutional.check OK');
