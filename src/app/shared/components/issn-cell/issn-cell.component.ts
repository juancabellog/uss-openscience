import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  computed,
  inject,
  input,
  signal,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { JournalIssns } from '../../../core/models/openscience.models';
import { JournalApiService } from '../../../core/services/api/journal-api.service';
import { CopyButtonComponent } from '../copy-button/copy-button.component';
import { I18N_HOST, I18N_IMPORTS } from '../../i18n/i18n.imports';
import { DetailPopoverCoordinator } from '../../popover/detail-popover-coordinator';
import { positionFixedPopover } from '../../popover/position-fixed-popover';

@Component({
  selector: 'os-issn-cell',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, CopyButtonComponent],
  templateUrl: './issn-cell.component.html',
  styleUrl: './issn-cell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IssnCellComponent {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly journalsApi = inject(JournalApiService);
  private readonly coordinator = inject(DetailPopoverCoordinator);
  private readonly destroyRef = inject(DestroyRef);

  readonly journalId = input.required<number>();
  readonly title = input<string | null>(null);
  readonly count = input(0);
  readonly displayValue = input<string | null>(null);
  readonly panel = viewChild<ElementRef<HTMLElement>>('panel');

  readonly loading = signal(false);
  readonly error = signal(false);
  readonly detail = signal<JournalIssns | null>(null);
  readonly panelStyle = signal<Record<string, string>>({});

  readonly popoverId = computed(() => `issn-${this.journalId()}`);
  readonly open = computed(() => this.coordinator.activeId() === this.popoverId());
  readonly interactive = computed(() => this.count() > 1);
  readonly panelId = computed(() => `journal-issns-${this.journalId()}`);
  readonly recordCount = computed(() => this.detail()?.count ?? this.count());
  readonly titleKey = computed(() =>
    this.recordCount() === 1 ? 'journals.issnPopover.oneRecord' : 'journals.issnPopover.manyRecords'
  );

  constructor() {
    const onScroll = (event: Event) => this.onCapturedScroll(event);
    document.addEventListener('scroll', onScroll, true);
    this.destroyRef.onDestroy(() => document.removeEventListener('scroll', onScroll, true));
  }

  toggle(event: Event): void {
    event.stopPropagation();
    if (!this.interactive()) {
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
    if (this.open()) {
      this.coordinator.activeId.set(null);
    }
  }

  itemTrack(item: { pureId?: number | null; issn?: string | null }, index: number): string {
    return `${item.pureId ?? 'none'}-${item.issn ?? index}`;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.close();
  }

  @HostListener('window:resize')
  onResize(): void {
    this.close();
  }

  private onCapturedScroll(event: Event): void {
    if (!this.open()) {
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
    this.journalsApi
      .getIssns(this.journalId())
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
    this.panelStyle.set(
      positionFixedPopover(this.host.nativeElement.getBoundingClientRect(), {
        widthRem: 18,
        heightRem: 16
      })
    );
  }
}
