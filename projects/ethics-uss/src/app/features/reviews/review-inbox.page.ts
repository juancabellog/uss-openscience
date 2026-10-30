import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DemoAuthService } from '../../core/auth/demo-auth.service';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { ReviewerService } from '../../core/services/reviewer.service';
import { EmptyStateComponent } from '../../shared/components/empty-state.component';
import { StatusChipComponent } from '../../shared/components/status-chip.component';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { ReviewInboxFilter, matchesReviewFilter, reviewInboxFilter } from './review-inbox.filter';

@Component({
  selector: 'eth-review-inbox-page',
  imports: [RouterLink, StatusChipComponent, EmptyStateComponent, ...I18N_IMPORTS],
  template: `
    <section>
      <h1>{{ 'reviews.title' | translate }}</h1>
      <div class="review-filter">
        <span class="label-caption" id="review-status-label">{{ 'reviews.filterLabel' | translate }}</span>
        <div class="status-chips" role="group" aria-labelledby="review-status-label">
          @for (option of options; track option.value) {
            <button type="button" [attr.aria-pressed]="filter() === option.value" (click)="setFilter(option.value)">
              {{ option.labelKey | translate }}
            </button>
          }
        </div>
      </div>
      @if (!rows().length) {
        <eth-empty-state [title]="emptyTitle()" message="reviews.emptyDetail" />
      } @else {
        <div class="grid-2">
          @for (row of rows(); track row.assignment.id) {
            <article class="card">
              <h2>{{ row.title }}</h2>
              <p>{{ row.code }} · {{ row.session ? ('reviews.session' | translate: { session: row.session }) : ('reviews.sessionPending' | translate) }}</p>
              <eth-status-chip [value]="row.assignment.status" />
              <a [routerLink]="['/revisiones', row.assignment.id]">{{ 'reviews.openAssignment' | translate }}</a>
            </article>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .review-filter {
      display: grid;
      gap: 0.35rem;
      margin: 0 0 1rem;
    }
    .status-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
    .status-chips button {
      height: 2.15rem;
      padding: 0 0.75rem;
      border: 1px solid var(--eth-border);
      border-radius: 999px;
      background: #f7f6f3;
      color: var(--eth-ink);
      font-weight: 600;
    }
    .status-chips button[aria-pressed='true'] {
      border-width: 2px;
      border-color: var(--eth-primary);
      background: #fff;
      color: var(--eth-primary);
      font-weight: 800;
    }
    .status-chips button:focus-visible {
      outline: 2px solid var(--eth-primary);
      outline-offset: 2px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReviewInboxPage {
  private readonly auth = inject(DemoAuthService);
  private readonly assignments = inject(ReviewerService);
  private readonly cases = inject(CommitteeCaseService);
  private readonly store = inject(EthicsStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly query = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  readonly options: readonly { value: ReviewInboxFilter; labelKey: string }[] = [
    { value: '', labelKey: 'reviews.filterAll' },
    { value: 'PENDING', labelKey: 'dashboard.pending' },
    { value: 'ACCEPTED', labelKey: 'dashboard.inReview' },
    { value: 'COMPLETED', labelKey: 'reviews.completed' }
  ];
  readonly filter = signal<ReviewInboxFilter>(reviewInboxFilter(this.route.snapshot.queryParamMap.get('status')));
  private readonly source = computed(() =>
    this.assignments.assignmentsFor(this.auth.currentUser()?.id ?? '').map((assignment) => {
      const item = this.cases.get(assignment.caseId);
      const project = this.store.snapshot().projects.find((row) => row.id === item?.projectId);
      return {
        assignment,
        title: project?.title ?? assignment.caseId,
        code: item?.code ?? '',
        session: assignment.sessionId ?? ''
      };
    })
  );
  readonly rows = computed(() => this.source().filter((row) => matchesReviewFilter(row.assignment.status, this.filter())));

  constructor() {
    effect(() => {
      const next = reviewInboxFilter(this.query().get('status'));
      if (next !== this.filter()) {
        this.filter.set(next);
      }
    });
  }

  setFilter(value: ReviewInboxFilter): void {
    this.filter.set(value);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { status: value || null },
      replaceUrl: true
    });
  }

  emptyTitle(): string {
    if (this.filter() === 'PENDING') {
      return 'reviews.emptyPending';
    }
    if (this.filter() === 'ACCEPTED') {
      return 'reviews.emptyInReview';
    }
    if (this.filter() === 'COMPLETED') {
      return 'reviews.emptyCompleted';
    }
    return 'reviews.emptyAll';
  }
}
