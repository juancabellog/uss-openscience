import { Routes } from '@angular/router';
import { IndicatorLayerComponent } from './indicator-layer.component';
import { IdentityCasesComponent } from './identity-cases.component';
import { IdentityIndicatorsComponent } from './identity-indicators.component';
import { IndicatorsHomeComponent } from './indicators-home.component';
import { IndicatorsComponent } from './indicators.component';
import { OpenAccessIndicatorsComponent } from './open-access-indicators.component';

export const INDICATORS_ROUTES: Routes = [
  {
    path: '',
    component: IndicatorsComponent,
    children: [
      { path: '', component: IndicatorsHomeComponent },
      { path: 'identity', component: IdentityIndicatorsComponent },
      { path: 'open-access', component: OpenAccessIndicatorsComponent },
      {
        path: 'identity/cases/:indicator',
        component: IdentityCasesComponent,
        data: { contentWidth: 'wide' }
      },
      { path: 'layer-1', component: IndicatorLayerComponent, data: { layer: 1 } },
      { path: 'layer-2', component: IndicatorLayerComponent, data: { layer: 2 } },
      { path: 'layer-3', component: IndicatorLayerComponent, data: { layer: 3 } }
    ]
  }
];
