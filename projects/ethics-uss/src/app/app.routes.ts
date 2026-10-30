import { Routes } from '@angular/router';
import { capabilityGuard, demoAuthGuard } from './core/guards/demo-auth.guard';

export const routes: Routes = [
  {
    path: 'ingresar',
    loadComponent: () => import('./features/auth/role-select.page').then((m) => m.RoleSelectPage)
  },
  {
    path: 'ingresar/recuperar',
    loadComponent: () => import('./features/auth/forgot-password.page').then((m) => m.ForgotPasswordPage)
  },
  {
    path: '',
    loadComponent: () => import('./core/layout/app-shell.component').then((m) => m.AppShellComponent),
    canActivate: [demoAuthGuard],
    children: [
      { path: '', loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage) },
      { path: 'proyectos', loadComponent: () => import('./features/projects/project-list.page').then((m) => m.ProjectListPage) },
      { path: 'proyectos/nuevo', canActivate: [capabilityGuard('CREATE_REQUEST')], loadComponent: () => import('./features/submissions/new-choice.page').then((m) => m.NewChoicePage) },
      { path: 'proyectos/nuevo/proyecto', canActivate: [capabilityGuard('CREATE_REQUEST')], loadComponent: () => import('./features/submissions/new-project/new-project-wizard.page').then((m) => m.NewProjectWizardPage) },
      { path: 'proyectos/nuevo/validacion', canActivate: [capabilityGuard('CREATE_REQUEST')], loadComponent: () => import('./features/submissions/validation-project/validation-wizard.page').then((m) => m.ValidationWizardPage) },
      { path: 'proyectos/:id', loadComponent: () => import('./features/projects/project-detail.page').then((m) => m.ProjectDetailPage) },
      {
        path: 'solicitudes',
        canActivate: [capabilityGuard('VIEW_PENDING_CLASSIFICATION')],
        loadComponent: () => import('./features/classification/submission-inbox.page').then((m) => m.SubmissionInboxPage)
      },
      {
        path: 'solicitudes/:id',
        canActivate: [capabilityGuard('VIEW_PENDING_CLASSIFICATION')],
        loadComponent: () => import('./features/classification/submission-review.page').then((m) => m.SubmissionReviewPage)
      },
      // Deuda técnica: el listado principal visible es /proyectos. /expedientes se conserva para el detalle de CommitteeCase y enlaces existentes.
      { path: 'expedientes', loadComponent: () => import('./features/committee-cases/case-list.page').then((m) => m.CaseListPage) },
      { path: 'expedientes/:id', loadComponent: () => import('./features/committee-cases/case-detail.page').then((m) => m.CaseDetailPage) },
      { path: 'revisiones', loadComponent: () => import('./features/reviews/review-inbox.page').then((m) => m.ReviewInboxPage) },
      { path: 'revisiones/:id', loadComponent: () => import('./features/reviews/review-detail.page').then((m) => m.ReviewDetailPage) },
      { path: 'sesiones', canActivate: [capabilityGuard('VIEW_COMMITTEE_SESSIONS')], loadComponent: () => import('./features/sessions/session-list.page').then((m) => m.SessionListPage) },
      { path: 'sesiones/:id', canActivate: [capabilityGuard('VIEW_COMMITTEE_SESSIONS')], loadComponent: () => import('./features/sessions/session-detail.page').then((m) => m.SessionDetailPage) },
      { path: 'revisores', canActivate: [capabilityGuard('VIEW_REVIEWER_POOL')], loadComponent: () => import('./features/reviewer-assignments/reviewer-catalog.page').then((m) => m.ReviewerCatalogPage) },
      { path: 'vencimientos', canActivate: [capabilityGuard('VIEW_EXPIRATIONS')], loadComponent: () => import('./features/resolutions/expiry-list.page').then((m) => m.ExpiryListPage) },
      { path: 'notificaciones', loadComponent: () => import('./features/notifications/notification-list.page').then((m) => m.NotificationListPage) },
      { path: 'historial', loadComponent: () => import('./features/audit/audit-list.page').then((m) => m.AuditListPage) },
      { path: 'auditoria', canActivate: [capabilityGuard('VIEW_AUDIT')], loadComponent: () => import('./features/audit/audit-list.page').then((m) => m.AuditListPage) },
      { path: 'comites', canActivate: [capabilityGuard('VIEW_CONFIGURATION')], loadComponent: () => import('./features/administration/committees.page').then((m) => m.CommitteesPage) },
      { path: 'usuarios', canActivate: [capabilityGuard('MANAGE_USERS')], loadComponent: () => import('./features/administration/users.page').then((m) => m.UsersPage) },
      { path: 'configuracion', canActivate: [capabilityGuard('VIEW_CONFIGURATION')], loadComponent: () => import('./features/administration/settings.page').then((m) => m.SettingsPage) }
    ]
  },
  { path: '**', redirectTo: '' }
];
