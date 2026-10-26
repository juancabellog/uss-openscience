import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { COMMITTEE_LABELS } from '../../mocks/catalogs';
import { ClassificationCode } from '../../core/models/ethics.models';
import { CommitteeCaseService } from '../../core/services/committee-case.service';
import { EthicsSubmissionService } from '../../core/services/ethics-submission.service';
import { EthicsStore } from '../../core/services/ethics-store.service';
import { SubmissionReadonlyComponent } from '../../shared/submission/submission-readonly.component';
import { I18nService } from '../../core/i18n/i18n.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

const CODES: ClassificationCode[] = ['CEC', 'CECUA', 'BIOSEGURIDAD', 'NO_REQUIERE_COMITE'];
const COMMITTEE_CODES: ClassificationCode[] = ['CEC', 'CECUA', 'BIOSEGURIDAD'];

@Component({
  selector: 'eth-submission-review-page',
  imports: [SubmissionReadonlyComponent, ...I18N_IMPORTS],
  templateUrl: './submission-review.page.html',
  styleUrl: './submission-review.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SubmissionReviewPage {
  readonly id = input.required<string>();
  private readonly submissions = inject(EthicsSubmissionService);
  private readonly cases = inject(CommitteeCaseService);
  private readonly store = inject(EthicsStore);
  private readonly router = inject(Router);
  readonly i18n = inject(I18nService);
  private lastId: string | null = null;
  readonly codes = CODES;
  readonly committeeCodes = COMMITTEE_CODES;
  readonly labels = COMMITTEE_LABELS;
  readonly reason = signal('');
  readonly error = signal<string | null>(null);
  readonly askConfirm = signal(false);
  readonly validated = signal<ClassificationCode[]>([]);
  readonly submission = computed(() => this.submissions.get(this.id()));
  readonly project = computed(() => this.store.snapshot().projects.find((item) => item.id === this.submission()?.projectId));
  readonly researcher = computed(() => this.store.snapshot().users.find((item) => item.id === this.project()?.researcherId)?.displayName);
  readonly changed = computed(() => !sameCodes(this.submission()?.classification.automatic ?? [], this.validated()));
  readonly canConfirm = computed(() => this.validated().length > 0 && (!this.changed() || !!this.reason().trim()));

  constructor() {
    effect(() => {
      const current = this.submission();
      if (!current || current.id === this.lastId) {
        return;
      }
      this.lastId = current.id;
      this.validated.set([...current.classification.automatic]);
      this.reason.set('');
      this.error.set(null);
      this.askConfirm.set(false);
    });
  }

  toggle(code: ClassificationCode): void {
    const current = this.validated();
    if (code === 'NO_REQUIERE_COMITE') {
      this.validated.set(current.includes(code) ? [] : ['NO_REQUIERE_COMITE']);
      return;
    }
    const next = current.filter((item) => item !== 'NO_REQUIERE_COMITE');
    this.validated.set(next.includes(code) ? next.filter((item) => item !== code) : [...next, code]);
  }

  ask(): void {
    if (!this.canConfirm()) {
      this.error.set(this.i18n.translate(this.changed() ? 'classification.needJustification' : 'classification.selectOne'));
      return;
    }
    this.error.set(null);
    this.askConfirm.set(true);
  }

  confirm(): void {
    if (!this.canConfirm()) {
      return;
    }
    const selected = this.validated();
    const submission = this.submissions.validateClassification(this.id(), selected, this.reason().trim() || undefined);
    if (!selected.includes('NO_REQUIERE_COMITE')) {
      this.cases.derive(submission.id, selected);
    }
    this.askConfirm.set(false);
    void this.router.navigate(['/proyectos', submission.projectId]);
  }
}

function sameCodes(left: ClassificationCode[], right: ClassificationCode[]): boolean {
  if (left.length !== right.length) {
    return false;
  }
  const a = [...left].sort();
  const b = [...right].sort();
  return a.every((code, index) => code === b[index]);
}
