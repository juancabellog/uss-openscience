import { ChangeDetectionStrategy, Component, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EventDocument, EventsApiService } from '../core/events-api.service';
import { I18nService } from '../core/i18n/i18n.service';
import { EventAdminTabsComponent } from '../shared/event-admin-tabs.component';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-admin-documents-page',
  imports: [RouterLink, ReactiveFormsModule, EventAdminTabsComponent, ...I18N_IMPORTS],
  templateUrl: './admin-documents.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminDocumentsPage {
  private readonly api = inject(EventsApiService);
  private readonly i18n = inject(I18nService);
  readonly id = input<number, unknown>(0, { transform: numberAttribute });
  readonly items = signal<EventDocument[]>([]);
  readonly errorKey = signal<string | null>(null);
  readonly busy = signal(false);
  readonly file = signal<File | null>(null);
  readonly form = new FormGroup({
    kind: new FormControl('SIGNED_MINUTES', { nonNullable: true }),
    note: new FormControl('', { nonNullable: true })
  });

  constructor() {
    effect(() => {
      if (this.id() > 0) {
        this.reload();
      }
    });
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
  }

  upload(): void {
    const file = this.file();
    if (!file) {
      this.errorKey.set('documents.selectFile');
      return;
    }
    this.busy.set(true);
    this.errorKey.set(null);
    const value = this.form.getRawValue();
    this.api.adminUploadDocument(this.id(), file, value.kind, value.note).subscribe({
      next: () => {
        this.busy.set(false);
        this.file.set(null);
        this.reload();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.errorKey.set(this.i18n.errorKey(err, 'documents.uploadFailed'));
      }
    });
  }

  download(item: EventDocument): string {
    return this.api.downloadUrl(item.downloadPath);
  }

  kindKey(kind: string): string {
    if (kind === 'SIGNED_MINUTES' || kind === 'PHOTO' || kind === 'COMPLEMENTARY') {
      return `documents.kindShort.${kind}`;
    }
    return kind;
  }

  private reload(): void {
    this.api.adminDocuments(this.id()).subscribe({
      next: (items) => this.items.set(items),
      error: (err: unknown) => this.errorKey.set(this.i18n.errorKey(err, 'documents.loadFailed'))
    });
  }
}
