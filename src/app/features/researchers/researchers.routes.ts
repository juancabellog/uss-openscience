import { Routes } from '@angular/router';
import { ResearcherDetailComponent } from './researcher-detail.component';
import { ResearcherListComponent } from './researcher-list.component';

export const RESEARCHERS_ROUTES: Routes = [
  { path: '', component: ResearcherListComponent },
  { path: ':id', component: ResearcherDetailComponent }
];
