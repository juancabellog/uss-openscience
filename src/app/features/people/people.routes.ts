import { Routes } from '@angular/router';
import { PeopleDetailComponent } from './people-detail.component';
import { PeopleListComponent } from './people-list.component';

export const PEOPLE_ROUTES: Routes = [
  { path: '', component: PeopleListComponent, data: { contentWidth: 'wide' } },
  { path: ':id', component: PeopleDetailComponent }
];
