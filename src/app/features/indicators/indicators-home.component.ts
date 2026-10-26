import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SectionCardComponent } from '../../shared/components/section-card/section-card.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { I18N_HOST } from '../../shared/i18n/i18n.imports';
import { TranslatePipe } from '../../shared/pipes/translate.pipe';

@Component({
  selector: 'os-indicators-home',
  hostDirectives: [...I18N_HOST],
  imports: [RouterLink, SectionCardComponent, StatusBadgeComponent, TranslatePipe],
  templateUrl: './indicators-home.component.html',
  styleUrl: './indicators-home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IndicatorsHomeComponent {}
