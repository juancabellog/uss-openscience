import { ChangeDetectionStrategy, Component, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EventArtifact, EventsApiService } from '../core/events-api.service';
import { I18nService } from '../core/i18n/i18n.service';
import { EventAdminTabsComponent } from '../shared/event-admin-tabs.component';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-admin-minutes-page',
  imports: [RouterLink, ReactiveFormsModule, EventAdminTabsComponent, ...I18N_IMPORTS],
  templateUrl: './admin-minutes.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminMinutesPage {
  private readonly api = inject(EventsApiService);
  private readonly i18n = inject(I18nService);
  readonly id = input<number, unknown>(0, { transform: numberAttribute });
  readonly items = signal<EventArtifact[]>([]);
  readonly errorKey = signal<string | null>(null);
  readonly busy = signal(false);
  readonly blankRows = new FormControl('10', { nonNullable: true });
  readonly language = new FormControl('es', { nonNullable: true });

  constructor() {
    effect(() => {
      if (this.id() > 0) {
        this.reload();
      }
    });
  }

  generate(): void {
    this.busy.set(true);
    this.errorKey.set(null);
    this.api.adminGenerateMinutes(this.id(), Number(this.blankRows.value) || 10, this.language.value).subscribe({
      next: () => {
        this.busy.set(false);
        this.reload();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.errorKey.set(this.i18n.errorKey(err, 'minutes.generateFailed'));
      }
    });
  }

  download(item: EventArtifact): string {
    return this.api.downloadUrl(item.downloadPath);
  }

  private reload(): void {
    this.api.adminMinutes(this.id()).subscribe({
      next: (items) => this.items.set(items),
      error: (err: unknown) => this.errorKey.set(this.i18n.errorKey(err, 'minutes.loadFailed'))
    });
  }
}
