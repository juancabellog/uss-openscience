import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { environment } from '../../../environments/environment';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-settings-page',
  imports: [...I18N_IMPORTS],
  template: `
    <section class="panel">
      <h1>{{ 'settings.title' | translate }}</h1>
      <p>{{ 'settings.futureApi' | translate: { api } }}</p>
      <p>{{ 'settings.storage' | translate }}</p>
      <button type="button" class="secondary" (click)="reset()">{{ 'settings.reset' | translate }}</button>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SettingsPage {
  private readonly store = inject(EthicsStore);
  readonly api = environment.futureApiBaseUrl;

  reset(): void {
    this.store.reset();
  }
}
