import { Routes } from '@angular/router';
import { demoAuthGuard, roleGuard } from './core/guards/demo-auth.guard';

export const routes: Routes = [
  {
    path: 'ingresar',
    loadComponent: () => import('./features/auth/role-select.page').then((m) => m.RoleSelectPage)
  },
  {
    path: '',
    loadComponent: () => import('./core/layout/app-shell.component').then((m) => m.AppShellComponent),
    canActivate: [demoAuthGuard],
    children: [
      { path: '', loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage) },
      { path: 'proyectos', loadComponent: () => import('./features/projects/project-list.page').then((m) => m.ProjectListPage) },
      { path: 'proyectos/nuevo', loadComponent: () => import('./features/submissions/new-choice.page').then((m) => m.NewChoicePage) },
      { path: 'proyectos/nuevo/proyecto', loadComponent: () => import('./features/submissions/new-project/new-project-wizard.page').then((m) => m.NewProjectWizardPage) },
      { path: 'proyectos/nuevo/validacion', loadComponent: () => import('./features/submissions/validation-project/validation-wizard.page').then((m) => m.ValidationWizardPage) },
      { path: 'proyectos/:id', loadComponent: () => import('./features/projects/project-detail.page').then((m) => m.ProjectDetailPage) },
      {
        path: 'solicitudes',
        canActivate: [roleGuard('ADMINISTRATOR')],
        loadComponent: () => import('./features/classification/submission-inbox.page').then((m) => m.SubmissionInboxPage)
      },
      {
        path: 'solicitudes/:id',
        canActivate: [roleGuard('ADMINISTRATOR')],
        loadComponent: () => import('./features/classification/submission-review.page').then((m) => m.SubmissionReviewPage)
      },
      { path: 'expedientes', loadComponent: () => import('./features/committee-cases/case-list.page').then((m) => m.CaseListPage) },
      { path: 'expedientes/:id', loadComponent: () => import('./features/committee-cases/case-detail.page').then((m) => m.CaseDetailPage) },
      { path: 'revisiones', loadComponent: () => import('./features/reviews/review-inbox.page').then((m) => m.ReviewInboxPage) },
      { path: 'revisiones/:id', loadComponent: () => import('./features/reviews/review-detail.page').then((m) => m.ReviewDetailPage) },
      { path: 'sesiones', loadComponent: () => import('./features/sessions/session-list.page').then((m) => m.SessionListPage) },
      { path: 'sesiones/:id', loadComponent: () => import('./features/sessions/session-detail.page').then((m) => m.SessionDetailPage) },
      { path: 'revisores', loadComponent: () => import('./features/reviewer-assignments/reviewer-catalog.page').then((m) => m.ReviewerCatalogPage) },
      { path: 'vencimientos', loadComponent: () => import('./features/resolutions/expiry-list.page').then((m) => m.ExpiryListPage) },
      { path: 'notificaciones', loadComponent: () => import('./features/notifications/notification-list.page').then((m) => m.NotificationListPage) },
      { path: 'historial', loadComponent: () => import('./features/audit/audit-list.page').then((m) => m.AuditListPage) },
      { path: 'auditoria', loadComponent: () => import('./features/audit/audit-list.page').then((m) => m.AuditListPage) },
      { path: 'comites', loadComponent: () => import('./features/administration/committees.page').then((m) => m.CommitteesPage) },
      { path: 'configuracion', loadComponent: () => import('./features/administration/settings.page').then((m) => m.SettingsPage) }
    ]
  },
  { path: '**', redirectTo: '' }
];
