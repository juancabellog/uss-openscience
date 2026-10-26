import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { I18N_HOST, I18N_IMPORTS } from '../../i18n/i18n.imports';

export interface DetailSectionNavItem {
  id: string;
  labelKey: string;
}

export const DETAIL_SECTION_NAV_STRATEGY = {
  routerLink: '.',
  queryParamsHandling: 'preserve',
  replaceUrl: true
} as const;

@Component({
  selector: 'os-detail-section-nav',
  hostDirectives: [...I18N_HOST],
  imports: [...I18N_IMPORTS, RouterLink],
  templateUrl: './detail-section-nav.component.html',
  styleUrl: './detail-section-nav.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DetailSectionNavComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly document = inject(DOCUMENT);

  readonly items = input.required<readonly DetailSectionNavItem[]>();
  readonly ariaLabel = input('');
  readonly activeFragment = toSignal(this.route.fragment, { initialValue: null });

  constructor() {
    effect(() => {
      const id = this.activeFragment();
      if (!id) {
        return;
      }
      requestAnimationFrame(() => {
        this.document.getElementById(id)?.scrollIntoView();
      });
    });
  }
}
