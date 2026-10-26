import { Routes } from '@angular/router';
import { ResearchOutputDetailComponent } from './research-output-detail.component';
import { ResearchOutputListComponent } from './research-output-list.component';

export const RESEARCH_OUTPUT_ROUTES: Routes = [
  { path: '', component: ResearchOutputListComponent, data: { contentWidth: 'wide' } },
  { path: ':id', component: ResearchOutputDetailComponent }
];
