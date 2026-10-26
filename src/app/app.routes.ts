import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { permissionGuard } from './core/guards/permission.guard';
import { AppShellComponent } from './layout/app-shell/app-shell.component';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'auth',
    children: [
      {
        path: 'activate-account',
        loadComponent: () =>
          import('./features/auth/activate-account.component').then((m) => m.ActivateAccountComponent)
      },
      {
        path: 'forgot-password',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./features/auth/forgot-password.component').then((m) => m.ForgotPasswordComponent)
      },
      {
        path: 'reset-password',
        loadComponent: () =>
          import('./features/auth/reset-password.component').then((m) => m.ResetPasswordComponent)
      },
      {
        path: 'register',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/register.component').then((m) => m.RegisterComponent)
      }
    ]
  },
  { path: 'register', redirectTo: '/auth/register', pathMatch: 'full' },
  { path: 'forgot-password', redirectTo: '/auth/forgot-password', pathMatch: 'full' },
  { path: 'reset-password', redirectTo: '/auth/reset-password', pathMatch: 'full' },
  { path: 'activate-account', redirectTo: '/auth/activate-account', pathMatch: 'full' },
  {
    path: '',
    component: AppShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        canActivate: [permissionGuard('DASHBOARD')],
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent)
      },
      {
        path: 'people',
        canActivate: [permissionGuard('PEOPLE')],
        loadChildren: () => import('./features/people/people.routes').then((m) => m.PEOPLE_ROUTES)
      },
      { path: 'researchers', pathMatch: 'full', redirectTo: 'people' },
      { path: 'researchers/:id', redirectTo: 'people/:id' },
      {
        path: 'research-output',
        canActivate: [permissionGuard('RESEARCH_OUTPUTS')],
        loadChildren: () =>
          import('./features/research-output/research-output.routes').then(
            (m) => m.RESEARCH_OUTPUT_ROUTES
          )
      },
      {
        path: 'journals',
        canActivate: [permissionGuard('JOURNALS')],
        loadChildren: () =>
          import('./features/journals/journals.routes').then((m) => m.JOURNALS_ROUTES)
      },
      {
        path: 'projects',
        canActivate: [permissionGuard('PROJECTS')],
        loadChildren: () =>
          import('./features/projects/projects.routes').then((m) => m.PROJECTS_ROUTES)
      },
      {
        path: 'organizations',
        canActivate: [permissionGuard('ORGANIZATIONS')],
        loadChildren: () =>
          import('./features/organizations/organizations.routes').then((m) => m.ORGANIZATIONS_ROUTES)
      },
      {
        path: 'directory',
        canActivate: [permissionGuard('DIRECTORY')],
        loadChildren: () =>
          import('./features/directory/directory.routes').then((m) => m.DIRECTORY_ROUTES)
      },
      {
        path: 'data-quality',
        canActivate: [permissionGuard('DATA_QUALITY')],
        loadChildren: () =>
          import('./features/data-quality/data-quality.routes').then((m) => m.DATA_QUALITY_ROUTES)
      },
      {
        path: 'indicators',
        canActivate: [permissionGuard('INDICATORS')],
        loadChildren: () =>
          import('./features/indicators/indicators.routes').then((m) => m.INDICATORS_ROUTES)
      },
      {
        path: 'integrations',
        canActivate: [permissionGuard('INTEGRATIONS')],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/integrations/integrations.component').then((m) => m.IntegrationsComponent)
          },
          {
            path: 'orcid',
            loadComponent: () =>
              import('./features/integrations/orcid/orcid-discovery.component').then(
                (m) => m.OrcidDiscoveryComponent
              )
          },
          {
            path: 'unpaywall',
            loadComponent: () =>
              import('./features/integrations/unpaywall/unpaywall-center.component').then(
                (m) => m.UnpaywallCenterComponent
              ),
            data: { contentWidth: 'wide' }
          },
          {
            path: 'crossref',
            loadComponent: () =>
              import('./features/integrations/crossref/crossref-center.component').then(
                (m) => m.CrossrefCenterComponent
              ),
            data: { contentWidth: 'wide' }
          },
          {
            path: 'openalex',
            loadComponent: () =>
              import('./features/integrations/openalex/openalex-center.component').then(
                (m) => m.OpenAlexCenterComponent
              ),
            data: { contentWidth: 'wide' }
          },
          {
            path: 'dspace',
            loadComponent: () =>
              import('./features/integrations/dspace/dspace-center.component').then(
                (m) => m.DspaceCenterComponent
              ),
            data: { contentWidth: 'wide' }
          }
        ]
      },
      {
        path: 'audit',
        canActivate: [permissionGuard('AUDIT')],
        loadComponent: () => import('./features/audit/audit.component').then((m) => m.AuditComponent)
      },
      {
        path: 'admin',
        canActivate: [permissionGuard('ADMINISTRATION')],
        loadComponent: () => import('./features/admin/admin.component').then((m) => m.AdminComponent)
      },
      {
        path: 'admin/users',
        canActivate: [permissionGuard('ADMINISTRATION')],
        loadComponent: () =>
          import('./features/admin/users/users-list.component').then((m) => m.UsersListComponent),
        data: { contentWidth: 'wide' }
      },
      {
        path: 'admin/roles',
        canActivate: [permissionGuard('ADMINISTRATION')],
        loadComponent: () =>
          import('./features/admin/roles/roles-list.component').then((m) => m.RolesListComponent),
        data: { contentWidth: 'wide' }
      },
      {
        path: 'unauthorized',
        loadComponent: () =>
          import('./features/auth/unauthorized.component').then((m) => m.UnauthorizedComponent)
      },
      {
        path: 'not-found',
        loadComponent: () =>
          import('./features/not-found/not-found.component').then((m) => m.NotFoundComponent)
      },
      { path: '**', redirectTo: 'not-found' }
    ]
  }
];
