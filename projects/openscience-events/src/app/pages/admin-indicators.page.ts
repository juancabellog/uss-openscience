import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { EventIndicators, EventsApiService } from '../core/events-api.service';
import { I18nService } from '../core/i18n/i18n.service';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-admin-indicators-page',
  imports: [ReactiveFormsModule, ...I18N_IMPORTS],
  templateUrl: './admin-indicators.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminIndicatorsPage {
  private readonly api = inject(EventsApiService);
  private readonly i18n = inject(I18nService);
  readonly data = signal<EventIndicators | null>(null);
  readonly errorKey = signal<string | null>(null);
  readonly filters = new FormGroup({
    from: new FormControl('', { nonNullable: true }),
    to: new FormControl('', { nonNullable: true })
  });

  constructor() {
    this.reload();
  }

  formatNumber(value: number): string {
    return this.i18n.formatNumber(value);
  }

  formatPercent(value?: number | null): string {
    return this.i18n.formatPercent(value);
  }

  reload(): void {
    const value = this.filters.getRawValue();
    this.api.adminIndicators(value.from || undefined, value.to || undefined).subscribe({
      next: (data) => {
        this.data.set(data);
        this.errorKey.set(null);
      },
      error: (err: unknown) => this.errorKey.set(this.i18n.errorKey(err, 'indicators.loadFailed'))
    });
  }
}
