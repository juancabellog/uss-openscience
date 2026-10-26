import { Routes } from '@angular/router';
import { JournalDetailComponent } from './journal-detail.component';
import { JournalListComponent } from './journal-list.component';

export const JOURNALS_ROUTES: Routes = [
  { path: '', component: JournalListComponent, data: { contentWidth: 'wide' } },
  { path: ':id', component: JournalDetailComponent }
];
