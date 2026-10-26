import { AppModuleCode } from '../../core/models/openscience.models';

export const APP_NAV_ITEMS = [
  { path: '/dashboard', labelKey: 'navigation.dashboard', icon: 'dashboard', module: 'DASHBOARD' as AppModuleCode },
  { path: '/people', labelKey: 'navigation.people', icon: 'people', module: 'PEOPLE' as AppModuleCode },
  { path: '/research-output', labelKey: 'navigation.researchOutput', icon: 'publications', module: 'RESEARCH_OUTPUTS' as AppModuleCode },
  { path: '/journals', labelKey: 'navigation.journals', icon: 'journals', module: 'JOURNALS' as AppModuleCode },
  { path: '/projects', labelKey: 'navigation.projects', icon: 'projects', module: 'PROJECTS' as AppModuleCode },
  { path: '/organizations', labelKey: 'navigation.organizations', icon: 'organizations', module: 'ORGANIZATIONS' as AppModuleCode },
  { path: '/directory', labelKey: 'navigation.directory', icon: 'directory', module: 'DIRECTORY' as AppModuleCode },
  { path: '/data-quality', labelKey: 'navigation.dataQuality', icon: 'quality', module: 'DATA_QUALITY' as AppModuleCode },
  { path: '/indicators', labelKey: 'navigation.indicators', icon: 'indicators', module: 'INDICATORS' as AppModuleCode },
  { path: '/integrations', labelKey: 'navigation.integrations', icon: 'integrations', module: 'INTEGRATIONS' as AppModuleCode },
  { path: '/audit', labelKey: 'navigation.audit', icon: 'audit', module: 'AUDIT' as AppModuleCode },
  { path: '/admin', labelKey: 'navigation.administration', icon: 'admin', module: 'ADMINISTRATION' as AppModuleCode }
] as const;

export function visibleNavItems(canConsult: (module: AppModuleCode) => boolean) {
  return APP_NAV_ITEMS.filter((item) => canConsult(item.module));
}
