import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { CommitteeWorkspaceComponent } from '../projects/committee-workspace.component';

@Component({
  selector: 'eth-case-detail-page',
  imports: [RouterLink, CommitteeWorkspaceComponent, ...I18N_IMPORTS],
  templateUrl: './case-detail.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CaseDetailPage {
  readonly id = input.required<string>();
  private readonly cases = inject(CommitteeCaseService);
  private readonly store = inject(EthicsStore);
  readonly projectId = computed(() => this.cases.get(this.id())?.projectId);
  readonly title = computed(() => this.store.snapshot().projects.find((item) => item.id === this.projectId())?.title ?? '');
}
