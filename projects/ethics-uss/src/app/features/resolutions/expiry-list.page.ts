import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { ProjectService } from '../../core/services/project.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

@Component({
  selector: 'eth-expiry-list-page',
  imports: [RouterLink, ...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'expirations.title' | translate }}</h1>
      <div class="grid-2">
        @for (row of rows(); track row.id) {
          <article class="card">
            <h2>{{ row.title }}</h2>
            <p>{{ row.code }} · {{ 'expirations.issuedExpires' | translate: { issued: i18n.formatDate(row.issued), expires: i18n.formatDate(row.expires) } }}</p>
            <p>{{ 'expirations.days' | translate: { semaphore: i18n.catalog('semaphore', row.semaphore), days: row.days } }}</p>
            @if (row.semaphore === 'VENCIDO') {
              <p class="alert danger">{{ 'expirations.critical' | translate }}</p>
            }
            <a [routerLink]="['/expedientes', row.id]">{{ 'expirations.viewCase' | translate }}</a>
          </article>
        }
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExpiryListPage {
  private readonly cases = inject(CommitteeCaseService);
  private readonly projects = inject(ProjectService);
  private readonly store = inject(EthicsStore);
  readonly i18n = inject(I18nService);
  readonly rows = computed(() =>
    this.cases
      .list()
      .filter((item) => item.approvalExpiresAt)
      .map((item) => {
        const info = this.projects.expiryInfo(item)!;
        return {
          id: item.id,
          code: item.code,
          title: this.store.snapshot().projects.find((project) => project.id === item.projectId)?.title ?? '',
          issued: item.approvalIssuedAt,
          expires: item.approvalExpiresAt,
          semaphore: info.semaphore,
          days: info.daysLeft
        };
      })
  );
}
