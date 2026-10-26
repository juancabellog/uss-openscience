import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { ResearcherService } from '../../core/services/platform-services';
import { DataTableShellComponent } from '../../shared/components/data-table-shell/data-table-shell.component';
import { DetailSectionNavComponent, DetailSectionNavItem } from '../../shared/components/detail-section-nav/detail-section-nav.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { qualityKey, qualityTone } from '../../shared/utils/status.util';

export const RESEARCHER_DETAIL_SECTIONS: DetailSectionNavItem[] = [
  { id: 'resumen', labelKey: 'researchers.summary' },
  { id: 'identidad', labelKey: 'researchers.identity' },
  { id: 'publicaciones', labelKey: 'researchers.publications' },
  { id: 'calidad', labelKey: 'researchers.quality' },
  { id: 'auditoria', labelKey: 'researchers.audit' }
];

@Component({
  selector: 'os-researcher-detail',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    RouterLink,
    DataTableShellComponent,
    DetailSectionNavComponent,
    ErrorStateComponent,
    LoadingStateComponent,
    PageHeaderComponent,
    StatusBadgeComponent
  ],
  templateUrl: './researcher-detail.component.html',
  styleUrl: './researcher-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResearcherDetailComponent {
  private readonly researchers = inject(ResearcherService);
  readonly id = input.required<string>();
  readonly sectionNav = RESEARCHER_DETAIL_SECTIONS;
  readonly qualityKey = qualityKey;
  readonly qualityTone = qualityTone;

  readonly person = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        this.researchers.getById(Number(id)).pipe(
          catchError(() => of(null))
        )
      )
    )
  );

  readonly displayName = computed(() => {
    const person = this.person();
    return person ? `${person.firstName} ${person.lastName}` : '';
  });

  readonly headerSubtitle = computed(() => {
    const person = this.person();
    if (!person) {
      return '';
    }
    return [person.faculty, person.position].filter(Boolean).join(' · ');
  });
}
