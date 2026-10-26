import { Routes } from '@angular/router';
import { ProjectDetailComponent } from './project-detail.component';
import { ProjectListComponent } from './project-list.component';

export const PROJECTS_ROUTES: Routes = [
  { path: '', component: ProjectListComponent, data: { contentWidth: 'wide' } },
  { path: ':id', component: ProjectDetailComponent }
];
