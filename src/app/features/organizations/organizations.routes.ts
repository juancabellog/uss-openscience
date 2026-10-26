import { Routes } from '@angular/router';
import { OrganizationDetailComponent } from './organization-detail.component';
import { OrganizationListComponent } from './organization-list.component';

export const ORGANIZATIONS_ROUTES: Routes = [
  { path: '', component: OrganizationListComponent, data: { contentWidth: 'wide' } },
  { path: ':id', component: OrganizationDetailComponent }
];
