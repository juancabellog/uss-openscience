import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { I18nService } from '../../../core/i18n/i18n.service';
import { I18N_HOST } from '../../i18n/i18n.imports';
import { TranslatePipe } from '../../pipes/translate.pipe';

@Component({
  selector: 'os-pagination',
  hostDirectives: [...I18N_HOST],
  imports: [TranslatePipe],
  templateUrl: './pagination.component.html',
  styleUrl: './pagination.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PaginationComponent {
  private readonly i18n = inject(I18nService);
  readonly page = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly total = input.required<number>();
  readonly pageSizeOptions = input<number[]>([]);
  readonly pageChange = output<number>();
  readonly pageSizeChange = output<number>();

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.total() / this.pageSize())));
  readonly canPrev = computed(() => this.page() > 1);
  readonly canNext = computed(() => this.page() < this.totalPages());
  readonly summaryParams = computed(() => {
    const page = this.page();
    const pageSize = this.pageSize();
    const total = this.total();
    const from = total === 0 ? 0 : (page - 1) * pageSize + 1;
    const to = Math.min(page * pageSize, total);
    const format = new Intl.NumberFormat(this.i18n.locale());
    return {
      from: format.format(from),
      to: format.format(to),
      total: format.format(total),
      page,
      totalPages: this.totalPages()
    };
  });

  prev(): void {
    if (this.canPrev()) {
      this.pageChange.emit(this.page() - 1);
    }
  }

  next(): void {
    if (this.canNext()) {
      this.pageChange.emit(this.page() + 1);
    }
  }

  changePageSize(value: string): void {
    const size = Number(value);
    if (!Number.isFinite(size) || size <= 0 || size === this.pageSize()) {
      return;
    }
    this.pageSizeChange.emit(size);
  }
}
