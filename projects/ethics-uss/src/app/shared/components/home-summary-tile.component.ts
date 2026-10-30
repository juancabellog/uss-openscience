import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18N_IMPORTS } from '../i18n/i18n.imports';

/**
 * Caluga de resumen del Inicio. Si hay ruta, toda la superficie es un enlace.
 * Sin ruta queda como dato, sin aspecto de control.
 */
@Component({
  selector: 'eth-home-summary-tile',
  imports: [RouterLink, ...I18N_IMPORTS],
  template: `
    @if (route(); as path) {
      <a
        class="tile"
        [class.is-zero]="count() === 0"
        [routerLink]="path"
        [queryParams]="query()"
        [attr.aria-label]="ariaKey() | translate: { count: count() }"
      >
        <span class="label">
          {{ labelKey() | translate }}
          <span class="arrow" aria-hidden="true">→</span>
        </span>
        <strong class="count">{{ count() }}</strong>
      </a>
    } @else {
      <div class="tile is-static" [class.is-zero]="count() === 0">
        <span class="label">{{ labelKey() | translate }}</span>
        <strong class="count">{{ count() }}</strong>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .tile {
      display: grid;
      align-content: start;
      gap: 0.2rem;
      min-height: 4.5rem;
      padding: 0.7rem 0.4rem 0.35rem;
      border-top: 1px solid var(--eth-border);
      color: inherit;
      text-decoration: none;
    }
    a.tile {
      cursor: pointer;
      border-radius: 2px;
    }
    a.tile:hover {
      border-top-color: var(--eth-primary);
      background: #f7f6f3;
    }
    a.tile:focus-visible {
      outline: 2px solid var(--eth-accent);
      outline-offset: 2px;
    }
    .label {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 0.5rem;
      color: var(--eth-ink);
      font-size: 0.875rem;
      font-weight: 650;
      line-height: 1.35;
    }
    .arrow {
      color: var(--eth-primary);
      font-weight: 650;
    }
    .count {
      color: var(--eth-ink);
      font-size: 1.35rem;
      font-weight: 650;
      line-height: 1.2;
    }
    .is-zero .label,
    .is-zero .count,
    .is-zero .arrow {
      color: var(--eth-muted);
      font-weight: 600;
    }
    .is-zero .count {
      font-size: 1.15rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HomeSummaryTileComponent {
  readonly labelKey = input.required<string>();
  readonly count = input.required<number>();
  readonly route = input<string | null>(null);
  readonly query = input<Record<string, string>>({});
  readonly ariaKey = input('dashboard.viewProjects');
}
