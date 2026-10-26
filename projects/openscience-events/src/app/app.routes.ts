import { Routes } from '@angular/router';
import { adminAuthGuard } from './core/admin-auth.guard';
import { AdminShellComponent } from './layout/admin-shell.component';
import { PublicShellComponent } from './layout/public-shell.component';
import { AdminAttendancePage } from './pages/admin-attendance.page';
import { AdminDocumentsPage } from './pages/admin-documents.page';
import { AdminEventDetailPage } from './pages/admin-event-detail.page';
import { AdminEventFormPage } from './pages/admin-event-form.page';
import { AdminEventListPage } from './pages/admin-event-list.page';
import { AdminHomePage } from './pages/admin-home.page';
import { AdminIndicatorsPage } from './pages/admin-indicators.page';
import { AdminLoginPage } from './pages/admin-login.page';
import { AdminMinutesPage } from './pages/admin-minutes.page';
import { AdminRegistrantsPage } from './pages/admin-registrants.page';
import { EventDetailPage } from './pages/event-detail.page';
import { EventListPage } from './pages/event-list.page';
import { HomePage } from './pages/home.page';
import { RegisterPage } from './pages/register.page';

export const routes: Routes = [
  {
    path: '',
    component: PublicShellComponent,
    children: [
      { path: '', component: HomePage },
      { path: 'eventos', component: EventListPage },
      { path: 'eventos/:slug', component: EventDetailPage },
      { path: 'eventos/:slug/inscripcion', component: RegisterPage }
    ]
  },
  {
    path: 'admin',
    component: AdminShellComponent,
    children: [
      { path: 'login', component: AdminLoginPage },
      { path: '', component: AdminHomePage, canActivate: [adminAuthGuard] },
      { path: 'eventos', component: AdminEventListPage, canActivate: [adminAuthGuard] },
      { path: 'eventos/nuevo', component: AdminEventFormPage, canActivate: [adminAuthGuard] },
      { path: 'eventos/:id/editar', component: AdminEventFormPage, canActivate: [adminAuthGuard] },
      { path: 'eventos/:id', component: AdminEventDetailPage, canActivate: [adminAuthGuard] },
      { path: 'eventos/:id/inscritos', component: AdminRegistrantsPage, canActivate: [adminAuthGuard] },
      { path: 'eventos/:id/acta', component: AdminMinutesPage, canActivate: [adminAuthGuard] },
      { path: 'eventos/:id/evidencias', component: AdminDocumentsPage, canActivate: [adminAuthGuard] },
      { path: 'eventos/:id/asistencia', component: AdminAttendancePage, canActivate: [adminAuthGuard] },
      { path: 'indicadores', component: AdminIndicatorsPage, canActivate: [adminAuthGuard] }
    ]
  },
  { path: '**', redirectTo: '' }
];
