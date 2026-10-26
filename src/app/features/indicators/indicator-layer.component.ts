import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { IndicatorService } from '../../core/services/platform-services';
import { DemoBannerComponent } from '../../shared/components/demo-banner/demo-banner.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { I18N_HOST } from '../../shared/i18n/i18n.imports';
import { TranslatePipe } from '../../shared/pipes/translate.pipe';
import { indicatorValueKey } from '../../shared/utils/status.util';

@Component({
  selector: 'os-indicator-layer',
  hostDirectives: [...I18N_HOST],
  imports: [DemoBannerComponent, EmptyStateComponent, StatusBadgeComponent, TranslatePipe],
  templateUrl: './indicator-layer.component.html',
  styleUrl: './indicator-layer.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IndicatorLayerComponent {
  readonly layer = input(0, { transform: (value: string | number) => Number(value) });
  private readonly indicators = inject(IndicatorService);
  readonly all = toSignal(this.indicators.list(), { initialValue: [] });
  readonly indicatorValueKey = indicatorValueKey;

  readonly items = computed(() => this.all().filter((item) => item.layer === this.layer()));
  readonly titleKey = computed(() => {
    if (this.layer() === 1) return 'indicators.layer1Title';
    if (this.layer() === 2) return 'indicators.layer2Title';
    return 'indicators.layer3Title';
  });
}
