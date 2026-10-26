import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { AppLanguage } from '../../../core/i18n/i18n.models';
import { I18nService } from '../../../core/i18n/i18n.service';
import {
  PureCorrectionResult,
  ResearchOutputDoiVersions,
  ResearchOutputElectronicVersionItem
} from '../../../core/models/openscience.models';
import { OsBadgeTone } from '../status-badge/status-badge.component';
import { ResearchOutputApiService } from '../../../core/services/api/research-output-api.service';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { apiErrorCode } from '../../../core/utils/api-error';
import { CopyButtonComponent } from '../copy-button/copy-button.component';
import { IconComponent } from '../icon/icon.component';
import { StatusBadgeComponent } from '../status-badge/status-badge.component';
import { PureCorrectionDialogComponent } from '../pure-correction-dialog/pure-correction-dialog.component';
import { I18N_HOST, I18N_IMPORTS } from '../../i18n/i18n.imports';
import { DetailPopoverCoordinator } from '../../popover/detail-popover-coordinator';
import { positionFixedPopover } from '../../popover/position-fixed-popover';

interface DoiVersionRow extends ResearchOutputElectronicVersionItem {
  accessLabel: string | null;
  versionLabel: string | null;
  accessTone: OsBadgeTone;
  showDuplicate: boolean;
}

@Component({
  selector: 'os-doi-versions-cell',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    CopyButtonComponent,
    IconComponent,
    StatusBadgeComponent,
    PureCorrectionDialogComponent
  ],
  templateUrl: './doi-versions-cell.component.html',
  styleUrl: './doi-versions-cell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DoiVersionsCellComponent {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly outputsApi = inject(ResearchOutputApiService);
  private readonly i18n = inject(I18nService);
  private readonly coordinator = inject(DetailPopoverCoordinator);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  readonly researchOutputId = input.required<number>();
  readonly title = input<string | null>(null);
  readonly count = input(0);
  readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  readonly corrected = output<void>();

  readonly loading = signal(false);
  readonly error = signal(false);
  readonly detail = signal<ResearchOutputDoiVersions | null>(null);
  readonly panelStyle = signal<Record<string, string>>({});
  readonly pending = signal<DoiVersionRow | null>(null);
  readonly removing = signal(false);
  private trigger: HTMLElement | null = null;

  readonly popoverId = computed(() => `doi-${this.researchOutputId()}`);
  readonly open = computed(() => this.coordinator.activeId() === this.popoverId());
  readonly interactive = computed(() => this.count() > 1);
  readonly panelId = computed(() => `doi-versions-${this.researchOutputId()}`);
  readonly canCorrect = computed(() => this.auth.canCorrectPure());

  constructor() {
    const onScroll = (event: Event) => this.onCapturedScroll(event);
    document.addEventListener('scroll', onScroll, true);
    this.destroyRef.onDestroy(() => document.removeEventListener('scroll', onScroll, true));
  }

  readonly language = this.i18n.currentLanguage;

  readonly items = computed(() => {
    const language = this.language();
    const rows = this.detail()?.items ?? [];
    const frequencies = new Map<string, number>();
    for (const row of rows) {
      const key = normalizeDoi(row.doi);
      if (key) {
        frequencies.set(key, (frequencies.get(key) ?? 0) + 1);
      }
    }
    const allSameDoi = isSingleSharedDoi(rows);
    return rows.map((row) => ({
      ...row,
      accessLabel: localizedStored(row.accessTypeEs, row.accessTypeEn, language),
      versionLabel: localizedStored(row.versionTypeEs, row.versionTypeEn, language),
      accessTone: accessTone(row.accessTypeEs, row.accessTypeEn),
      showDuplicate: !allSameDoi && isRepeated(row.doi, frequencies)
    }));
  });

  readonly allSameDoi = computed(() => isSingleSharedDoi(this.detail()?.items ?? []));

  readonly versionCount = computed(() => this.detail()?.count ?? this.count());

  readonly distinctCount = computed(() => this.detail()?.distinctCount ?? 0);

  readonly titleKey = computed(() =>
    this.versionCount() === 1 ? 'outputs.doiPopover.oneVersion' : 'outputs.doiPopover.manyVersions'
  );

  readonly subtitleKey = computed(() => {
    if (this.versionCount() <= 1) {
      return null;
    }
    if (this.distinctCount() === 1) {
      return 'outputs.doiPopover.sameDoiAllVersions';
    }
    if (this.distinctCount() > 1) {
      return 'outputs.doiPopover.distinctDois';
    }
    return null;
  });

  readonly subtitleCount = computed(() =>
    this.subtitleKey() === 'outputs.doiPopover.sameDoiAllVersions'
      ? this.versionCount()
      : this.distinctCount()
  );

  itemTrack(item: DoiVersionRow, index: number): string {
    return `${item.pureId ?? 'none'}-${item.id ?? index}`;
  }

  toggle(event: Event): void {
    event.stopPropagation();
    if (!this.interactive() || this.pending()) {
      return;
    }
    if (this.open()) {
      this.close();
      return;
    }
    this.coordinator.activeId.set(this.popoverId());
    this.positionPanel();
    this.load();
    queueMicrotask(() => this.panel()?.nativeElement.focus());
  }

  close(): void {
    if (this.pending()) {
      return;
    }
    if (this.open()) {
      this.coordinator.activeId.set(null);
    }
  }

  askRemove(event: Event, item: DoiVersionRow): void {
    event.stopPropagation();
    if (!this.canCorrect() || item.pureId == null || this.removing()) {
      return;
    }
    this.trigger = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    this.pending.set(item);
  }

  closeConfirm(): void {
    if (this.removing()) {
      return;
    }
    this.pending.set(null);
    this.restoreTrigger();
  }

  confirmRemove(): void {
    const item = this.pending();
    const childPureId = item?.pureId;
    if (item == null || childPureId == null || this.removing()) {
      return;
    }
    this.removing.set(true);
    this.outputsApi
      .removeDoiVersion(this.researchOutputId(), childPureId, item.doi)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.removing.set(false);
          this.pending.set(null);
          if (result.status === 'REMOTE_SUCCESS_LOCAL_SYNC_FAILED') {
            this.notifyLocalFailed(childPureId);
            return;
          }
          this.finishLocalRefresh(result);
        },
        error: (error: unknown) => {
          this.removing.set(false);
          this.notifyError(error);
        }
      });
  }

  retryLocal(versionPureId: number): void {
    if (this.removing()) {
      return;
    }
    this.removing.set(true);
    this.outputsApi
      .reconcileLocalDoiVersion(this.researchOutputId(), versionPureId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.removing.set(false);
          if (result.status === 'REMOTE_SUCCESS_LOCAL_SYNC_FAILED') {
            this.notifyLocalFailed(versionPureId);
            return;
          }
          this.finishLocalRefresh(result, true);
        },
        error: (error: unknown) => {
          this.removing.set(false);
          this.notifyError(error);
        }
      });
  }

  doiHref(doi: string): string {
    const normalized = normalizeDoi(doi);
    return `https://doi.org/${normalized ?? doi.trim()}`;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (this.pending()) {
      return;
    }
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.pending()) {
      this.closeConfirm();
      return;
    }
    this.close();
  }

  @HostListener('window:resize')
  onResize(): void {
    if (this.pending()) {
      return;
    }
    this.close();
  }

  private onCapturedScroll(event: Event): void {
    if (!this.open() || this.pending()) {
      return;
    }
    const target = event.target;
    if (target instanceof Node && this.panel()?.nativeElement.contains(target)) {
      return;
    }
    this.close();
  }

  private load(): void {
    if (this.detail()) {
      return;
    }
    this.loading.set(true);
    this.error.set(false);
    this.outputsApi
      .getDoiVersions(this.researchOutputId())
      .pipe(catchError(() => of(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.loading.set(false);
        if (result == null) {
          this.error.set(true);
          return;
        }
        this.detail.set(result);
      });
  }

  private positionPanel(): void {
    this.panelStyle.set(positionFixedPopover(this.host.nativeElement.getBoundingClientRect(), {
      widthRem: 22,
      heightRem: 18
    }));
  }

  private closeAfterCorrection(): void {
    this.trigger = null;
    if (this.open()) {
      this.coordinator.activeId.set(null);
    }
  }

  private restoreTrigger(): void {
    const trigger = this.trigger;
    this.trigger = null;
    queueMicrotask(() => trigger?.focus());
  }

  private finishLocalRefresh(result: PureCorrectionResult, localRetry = false): void {
    this.detail.set(null);
    this.closeAfterCorrection();
    this.notifyResult(result, localRetry);
    this.corrected.emit();
  }

  private notifyLocalFailed(versionPureId: number): void {
    this.notifications.show(
      'warning',
      this.i18n.translate('pureCorrection.localPendingTitle'),
      this.i18n.translate('pureCorrection.localPendingBody'),
      null,
      {
        label: this.i18n.translate('pureCorrection.retryLocal'),
        run: () => this.retryLocal(versionPureId)
      }
    );
  }

  private notifyResult(result: PureCorrectionResult, localRetry = false): void {
    if (localRetry || result.status === 'LOCAL_RECONCILED') {
      this.notifications.show(
        'success',
        this.i18n.translate('pureCorrection.localRetrySuccessTitle'),
        this.i18n.translate('pureCorrection.localRetrySuccessBody')
      );
      return;
    }
    if (result.status === 'ALREADY_REMOVED') {
      this.notifications.show(
        'warning',
        this.i18n.translate('pureCorrection.alreadyTitle'),
        this.i18n.translate('pureCorrection.alreadyBody')
      );
      return;
    }
    this.notifications.show(
      'success',
      this.i18n.translate('pureCorrection.doiSuccessTitle'),
      this.i18n.translate('pureCorrection.doiSuccessBody')
    );
  }

  private notifyError(error: unknown): void {
    const code = apiErrorCode(error) ?? (error instanceof HttpErrorResponse ? String(error.status) : null);
    if (code === 'SYNC_IN_PROGRESS') {
      this.notifications.show(
        'warning',
        this.i18n.translate('pureCorrection.syncInProgressTitle'),
        this.i18n.translate('pureCorrection.syncInProgressOutputs')
      );
      return;
    }
    if (code === 'PURE_RECORD_CHANGED') {
      this.notifications.show(
        'error',
        this.i18n.translate('pureCorrection.changedTitle'),
        this.i18n.translate('pureCorrection.changedBody')
      );
      return;
    }
    if (code === 'PURE_VERIFICATION_FAILED') {
      this.notifications.show(
        'error',
        this.i18n.translate('pureCorrection.verifyFailedTitle'),
        this.i18n.translate('pureCorrection.verifyFailedBody')
      );
      return;
    }
    if (code === 'CORRECTION_FORBIDDEN') {
      this.notifications.show(
        'error',
        this.i18n.translate('pureCorrection.pureFailedTitle'),
        this.i18n.translate('pureCorrection.forbidden')
      );
      return;
    }
    this.notifications.show(
      'error',
      this.i18n.translate('pureCorrection.pureFailedTitle'),
      this.i18n.translate('pureCorrection.pureFailedBody')
    );
  }
}

function normalizeDoi(doi: string | null | undefined): string | null {
  if (!doi || doi.trim() === '') {
    return null;
  }
  let value = doi.trim().toLowerCase();
  const prefixes = ['https://dx.doi.org/', 'http://dx.doi.org/', 'https://doi.org/', 'http://doi.org/', 'doi:'];
  for (const prefix of prefixes) {
    if (value.startsWith(prefix)) {
      value = value.slice(prefix.length).trim();
      break;
    }
  }
  return value === '' ? null : value;
}

function isRepeated(doi: string | null, frequencies: Map<string, number>): boolean {
  const key = normalizeDoi(doi);
  return key != null && (frequencies.get(key) ?? 0) > 1;
}

function isSingleSharedDoi(rows: ResearchOutputDoiVersions['items']): boolean {
  if (rows.length < 2) {
    return false;
  }
  const keys = rows.map((row) => normalizeDoi(row.doi));
  if (keys.some((key) => key == null)) {
    return false;
  }
  return new Set(keys).size === 1;
}

function localizedStored(es: string | null, en: string | null, language: AppLanguage): string | null {
  const primary = language === 'en' ? en : es;
  const fallback = language === 'en' ? es : en;
  return firstNonBlank(primary, fallback);
}

function firstNonBlank(primary: string | null, fallback: string | null): string | null {
  if (primary != null && primary.trim() !== '') {
    return primary.trim();
  }
  if (fallback != null && fallback.trim() !== '') {
    return fallback.trim();
  }
  return null;
}

function accessTone(es: string | null, en: string | null): OsBadgeTone {
  const blob = `${es ?? ''} ${en ?? ''}`.toLowerCase();
  if (/(^|[^a-z])(open|abierto)([^a-z]|$)/.test(blob)) {
    return 'success';
  }
  if (/(^|[^a-z])(closed|cerrado)([^a-z]|$)/.test(blob)) {
    return 'neutral';
  }
  if (/(^|[^a-z])(unknown|unspecified|sin especificar)([^a-z]|$)/.test(blob)) {
    return 'muted';
  }
  return 'muted';
}
