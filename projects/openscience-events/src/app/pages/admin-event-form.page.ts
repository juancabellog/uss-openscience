import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  EVENT_CAMPUS_OTHER,
  EVENT_DEFAULT_BLANK_ROWS,
  EVENT_DEFAULT_TIMEZONE,
  EVENT_ISSUE_FIELDS,
  EVENT_UNIT_OTHER,
  EventFieldIssue,
  collectEventIssues,
  trimToNull
} from '../core/event-form.rules';
import {
  EVENT_MODALITIES,
  EVENT_TYPES,
  EventAdmin,
  EventOrganizingUnit,
  EventWrite,
  EventsApiService
} from '../core/events-api.service';
import { I18nService } from '../core/i18n/i18n.service';
import { I18N_IMPORTS } from '../shared/i18n/i18n.imports';

@Component({
  selector: 'ose-admin-event-form-page',
  imports: [ReactiveFormsModule, RouterLink, ...I18N_IMPORTS],
  templateUrl: './admin-event-form.page.html',
  styleUrl: './page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminEventFormPage {
  private readonly api = inject(EventsApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  readonly id = input<number | undefined, unknown>(undefined, { transform: numberAttribute });
  readonly types = EVENT_TYPES;
  readonly modalities = EVENT_MODALITIES;
  readonly otherCampus = EVENT_CAMPUS_OTHER;
  readonly lang = this.i18n.currentLanguage;
  readonly campuses = signal<string[]>([]);
  readonly units = signal<EventOrganizingUnit[]>([]);
  readonly busy = signal(false);
  readonly submitted = signal(false);
  readonly errorKey = signal<string | null>(null);
  readonly successKey = signal<string | null>(null);
  readonly saved = signal<EventAdmin | null>(null);
  readonly confirmPublish = signal(false);
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly formTick = signal(0);
  readonly form = new FormGroup({
    title: new FormControl('', { nonNullable: true }),
    titleEn: new FormControl('', { nonNullable: true }),
    description: new FormControl('', { nonNullable: true }),
    descriptionEn: new FormControl('', { nonNullable: true }),
    primaryLanguage: new FormControl('es', { nonNullable: true }),
    eventType: new FormControl('', { nonNullable: true }),
    startsAt: new FormControl('', { nonNullable: true }),
    endsAt: new FormControl('', { nonNullable: true }),
    timezone: new FormControl(EVENT_DEFAULT_TIMEZONE, { nonNullable: true }),
    modality: new FormControl('', { nonNullable: true }),
    campusSelect: new FormControl('', { nonNullable: true }),
    locationName: new FormControl('', { nonNullable: true }),
    virtualAccess: new FormControl('', { nonNullable: true }),
    organizingUnitCode: new FormControl('', { nonNullable: true }),
    organizingUnitOther: new FormControl('', { nonNullable: true }),
    registrationOpensAt: new FormControl('', { nonNullable: true }),
    registrationClosesAt: new FormControl('', { nonNullable: true }),
    allowRegistrationDuringEvent: new FormControl(false, { nonNullable: true }),
    capacity: new FormControl('', { nonNullable: true }),
    blankSignatureRows: new FormControl(String(EVENT_DEFAULT_BLANK_ROWS), { nonNullable: true }),
    publicSlug: new FormControl('', { nonNullable: true })
  });

  constructor() {
    this.api.adminCampuses().subscribe({
      next: (names) => this.campuses.set(names.filter((name) => !!name && name.trim().length > 0)),
      error: () => this.campuses.set([])
    });
    this.api.adminOrganizingUnits().subscribe({
      next: (items) => this.units.set(items.filter((unit) => unit.active !== false)),
      error: () => this.units.set([])
    });
    this.form.controls.organizingUnitCode.valueChanges.subscribe((code) => {
      if (code !== EVENT_UNIT_OTHER && this.form.controls.organizingUnitOther.value) {
        this.form.controls.organizingUnitOther.setValue('', { emitEvent: false });
      }
    });
    this.form.valueChanges.subscribe(() => {
      this.formTick.update((value) => value + 1);
      if (this.submitted()) {
        this.refreshFieldErrors(this.pendingPublish);
      }
    });
    effect(() => {
      this.i18n.currentLanguage();
      if (this.submitted()) {
        this.refreshFieldErrors(this.pendingPublish);
      }
    });
    effect(() => {
      const id = this.id();
      if (Number.isFinite(id) && (id as number) > 0) {
        this.api.adminGet(id as number).subscribe({
          next: (event) => this.patch(event),
          error: (err: unknown) => this.errorKey.set(this.i18n.errorKey(err, 'form.saveFailed'))
        });
      }
    });
  }

  private pendingPublish = false;

  get editing(): boolean {
    return Number.isFinite(this.id()) && (this.id() as number) > 0;
  }

  get status(): string {
    return this.saved()?.publicationStatus ?? 'DRAFT';
  }

  get statusLocked(): boolean {
    return this.status === 'FINISHED' || this.status === 'CANCELLED';
  }

  get canSaveDraft(): boolean {
    return !this.editing && !this.statusLocked;
  }

  get canPublishNew(): boolean {
    return !this.editing && !this.statusLocked;
  }

  get canSaveChanges(): boolean {
    return this.editing && !this.statusLocked;
  }

  showPlaceField(_tick = this.formTick()): boolean {
    const modality = this.form.controls.modality.value;
    return modality === 'PRESENTIAL' || modality === 'HYBRID' || modality === '';
  }

  showVirtualField(_tick = this.formTick()): boolean {
    const modality = this.form.controls.modality.value;
    return modality === 'VIRTUAL' || modality === 'HYBRID';
  }

  showCustomPlace(_tick = this.formTick()): boolean {
    return this.form.controls.campusSelect.value === EVENT_CAMPUS_OTHER || this.form.controls.campusSelect.value === '';
  }

  showOtherUnit(_tick = this.formTick()): boolean {
    return this.form.controls.organizingUnitCode.value === EVENT_UNIT_OTHER;
  }

  fieldError(field: string): string | null {
    const control = this.form.get(field) ?? (field === 'organizingUnit' ? this.form.get('organizingUnitOther') : null);
    if (!this.submitted() && !control?.touched && !control?.dirty) {
      return null;
    }
    return this.fieldErrors()[field] ?? null;
  }

  invalid(field: string): boolean {
    return !!this.fieldError(field);
  }

  markTouched(field: string): void {
    this.form.get(field)?.markAsTouched();
    this.refreshFieldErrors(this.pendingPublish);
  }

  saveDraft(): void {
    this.persist('DRAFT', 'form.savedDraft');
  }

  saveChanges(): void {
    const status = this.status === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT';
    this.persist(status, 'form.savedChanges');
  }

  askPublish(): void {
    this.pendingPublish = true;
    this.submitted.set(true);
    this.form.markAllAsTouched();
    const issues = this.currentIssues(true);
    this.fieldErrors.set(this.toMap(issues));
    this.errorKey.set(null);
    this.successKey.set(null);
    if (issues.length) {
      this.errorKey.set('form.publishIncomplete');
      return;
    }
    this.confirmPublish.set(true);
  }

  confirmPublishNow(): void {
    this.confirmPublish.set(false);
    this.persist('PUBLISHED', 'form.published');
  }

  cancel(): void {
    if (this.editing) {
      void this.router.navigate(['/admin/eventos', this.id()]);
      return;
    }
    void this.router.navigate(['/admin/eventos']);
  }

  dismissConfirm(): void {
    this.confirmPublish.set(false);
  }

  private persist(status: 'DRAFT' | 'PUBLISHED', successMessage: string): void {
    if (this.busy()) {
      return;
    }
    this.pendingPublish = status === 'PUBLISHED';
    this.submitted.set(true);
    this.form.markAllAsTouched();
    const issues = this.currentIssues(status === 'PUBLISHED');
    this.fieldErrors.set(this.toMap(issues));
    this.errorKey.set(null);
    this.successKey.set(null);
    if (issues.length) {
      this.errorKey.set(status === 'PUBLISHED' ? 'form.publishIncomplete' : 'form.draftInvalid');
      return;
    }
    this.busy.set(true);
    const body = this.body(status);
    const request = this.editing
      ? this.api.adminUpdate(this.id() as number, body)
      : this.api.adminCreate(body);
    request.subscribe({
      next: (event) => {
        this.busy.set(false);
        this.patch(event);
        this.successKey.set(successMessage);
        this.submitted.set(false);
        this.fieldErrors.set({});
        if (!this.editing) {
          void this.router.navigate(['/admin/eventos', event.id]);
        }
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.applyServerError(err);
      }
    });
  }

  private patch(event: EventAdmin): void {
    this.saved.set(event);
    const campusName = event.campusName ?? '';
    if (campusName && !this.campuses().includes(campusName)) {
      this.campuses.set([...this.campuses(), campusName]);
    }
    this.form.patchValue({
      title: event.title,
      titleEn: event.titleEn ?? '',
      description: event.description ?? '',
      descriptionEn: event.descriptionEn ?? '',
      primaryLanguage: event.primaryLanguage === 'en' ? 'en' : 'es',
      eventType: event.eventType ?? '',
      startsAt: this.toInput(event.startsAt),
      endsAt: this.toInput(event.endsAt),
      timezone: event.timezone || EVENT_DEFAULT_TIMEZONE,
      modality: event.modality ?? '',
      campusSelect: campusName ? campusName : event.locationName ? EVENT_CAMPUS_OTHER : '',
      locationName: event.locationName ?? '',
      virtualAccess: event.virtualAccess ?? '',
      organizingUnitCode: event.organizingUnitCode ?? '',
      organizingUnitOther: event.organizingUnitCode === EVENT_UNIT_OTHER ? event.organizingUnit ?? '' : '',
      registrationOpensAt: this.toInput(event.registrationOpensAt),
      registrationClosesAt: this.toInput(event.registrationClosesAt),
      allowRegistrationDuringEvent: !!event.allowRegistrationDuringEvent,
      capacity: event.capacity == null ? '' : String(event.capacity),
      blankSignatureRows: String(event.blankSignatureRows ?? EVENT_DEFAULT_BLANK_ROWS),
      publicSlug: event.publicSlug ?? event.slug ?? ''
    });
  }

  campusOptions(extra?: string): string[] {
    const values = [...this.campuses()];
    if (extra && !values.includes(extra)) {
      values.push(extra);
    }
    return values;
  }

  private body(status: 'DRAFT' | 'PUBLISHED'): EventWrite {
    const value = this.form.getRawValue();
    const campusSelect = value.campusSelect;
    const campusName = campusSelect && campusSelect !== EVENT_CAMPUS_OTHER ? campusSelect : null;
    return {
      title: value.title,
      titleEn: trimToNull(value.titleEn),
      description: trimToNull(value.description),
      descriptionEn: trimToNull(value.descriptionEn),
      primaryLanguage: value.primaryLanguage || 'es',
      eventType: trimToNull(value.eventType),
      startsAt: this.fromInput(value.startsAt),
      endsAt: this.fromInput(value.endsAt),
      timezone: value.timezone || EVENT_DEFAULT_TIMEZONE,
      modality: trimToNull(value.modality),
      locationName: trimToNull(value.locationName),
      campusName,
      virtualAccess: trimToNull(value.virtualAccess),
      organizingUnitCode: trimToNull(value.organizingUnitCode),
      organizingUnit: trimToNull(value.organizingUnitCode) === EVENT_UNIT_OTHER ? trimToNull(value.organizingUnitOther) : null,
      publicationStatus: status,
      registrationOpensAt: this.fromInput(value.registrationOpensAt),
      registrationClosesAt: this.fromInput(value.registrationClosesAt),
      allowRegistrationDuringEvent: value.allowRegistrationDuringEvent,
      capacity: trimToNull(value.capacity) ? Number(value.capacity) : null,
      blankSignatureRows: trimToNull(value.blankSignatureRows)
        ? Number(value.blankSignatureRows)
        : EVENT_DEFAULT_BLANK_ROWS,
      publicSlug: trimToNull(value.publicSlug)
    };
  }

  private currentIssues(publish: boolean): EventFieldIssue[] {
    return collectEventIssues(this.body(publish ? 'PUBLISHED' : 'DRAFT'), publish);
  }

  private refreshFieldErrors(publish: boolean): void {
    this.fieldErrors.set(this.toMap(this.currentIssues(publish)));
  }

  private toMap(issues: EventFieldIssue[]): Record<string, string> {
    const map: Record<string, string> = {};
    for (const issue of issues) {
      if (!map[issue.field]) {
        map[issue.field] = this.i18n.has(`validation.${issue.code}`)
          ? this.i18n.translate(`validation.${issue.code}`)
          : issue.message;
      }
    }
    return map;
  }

  private applyServerError(err: unknown): void {
    const payload = err instanceof HttpErrorResponse ? err.error : null;
    const code = payload?.code as string | undefined;
    const key = this.i18n.errorKey(err, 'form.saveFailed');
    this.errorKey.set(key);
    if (code && EVENT_ISSUE_FIELDS[code]) {
      const message = this.i18n.has(`validation.${code}`)
        ? this.i18n.translate(`validation.${code}`)
        : this.i18n.translate(key);
      this.fieldErrors.set({ ...this.fieldErrors(), [EVENT_ISSUE_FIELDS[code]]: message });
    }
  }

  private toInput(value?: string | null): string {
    return value ? value.slice(0, 16) : '';
  }

  private fromInput(value: string): string | null {
    return value ? `${value}:00` : null;
  }

}

