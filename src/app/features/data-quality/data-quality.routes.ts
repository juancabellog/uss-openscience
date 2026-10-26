import { Routes } from '@angular/router';
import { DataQualityListComponent } from './data-quality-list.component';

export const DATA_QUALITY_ROUTES: Routes = [
  { path: '', component: DataQualityListComponent, data: { contentWidth: 'wide' } }
];
