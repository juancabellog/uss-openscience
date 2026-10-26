import { Routes } from '@angular/router';
import { DirectoryDetailComponent } from './directory-detail.component';
import { DirectoryListComponent } from './directory-list.component';

export const DIRECTORY_ROUTES: Routes = [
  { path: '', component: DirectoryListComponent, data: { contentWidth: 'wide' } },
  { path: ':id', component: DirectoryDetailComponent }
];
