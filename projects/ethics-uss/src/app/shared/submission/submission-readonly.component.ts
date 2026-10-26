import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { CASE_STATUS_LABELS, COMMITTEE_LABELS } from '../../mocks/catalogs';
import { DocumentMetadata, EthicsCommittee, EthicsSubmission, Project } from '../../core/models/ethics.models';
import { completeness, formForType, questionsOfSection, resolveAnswers } from '../../core/forms';
import {
  VALIDATION_COMMITTEE_ORDER,
  isValidationCommittee,
  isValidationLetter,
  validationDocumentLabelKey
} from '../../core/forms/validation-documents';
import { DocumentService } from '../../core/services/document.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { EthicsFormFieldsComponent } from '../forms/ethics-form-fields.component';
import { I18N_IMPORTS } from '../i18n/i18n.imports';

@Component({
  selector: 'eth-submission-readonly',
  imports: [EthicsFormFieldsComponent, ...I18N_IMPORTS],
  templateUrl: './submission-readonly.component.html',
  styleUrl: './submission-readonly.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SubmissionReadonlyComponent {
  private readonly i18n = inject(I18nService);
  private readonly documents = inject(DocumentService);
  readonly committeeLabels = COMMITTEE_LABELS;
  readonly submission = input.required<EthicsSubmission>();
  readonly project = input<Project | undefined>();
  readonly researcherName = input<string | undefined>();
  readonly previewNote = signal<string | null>(null);

  readonly form = computed(() => formForType(this.submission().type));
  readonly answers = computed(() => resolveAnswers(this.submission()));
  readonly progress = computed(() => completeness(this.form(), this.answers()));
  readonly title = computed(() => {
    const value = this.answers()[this.submission().type === 'VALIDATION' ? 'VAL-008' : 'NP-001'];
    return typeof value === 'string' && value.trim() ? value : this.project()?.title ?? this.i18n.translate('common.untitledRequest');
  });
  readonly researcher = computed(() => {
    const keys = this.submission().type === 'VALIDATION' ? ['VAL-001', 'VAL-002'] : ['NP-004', 'NP-005'];
    const name = keys
      .map((key) => this.answers()[key])
      .filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
      .join(' ');
    return name || this.researcherName();
  });
  readonly typeLabel = computed(() => this.i18n.translate(this.submission().type === 'VALIDATION' ? 'readonly.validationType' : 'readonly.newProjectType'));
  readonly statusLabel = computed(() => {
    const item = this.submission();
    if (item.status === 'DRAFT') {
      return this.i18n.translate(CASE_STATUS_LABELS['DRAFT']);
    }
    if (item.status === 'SUBMITTED' && !item.classification.validated) {
      return this.i18n.translate(CASE_STATUS_LABELS['PENDING_CLASSIFICATION']);
    }
    return this.i18n.translate('readonly.classified');
  });
  readonly submittedAt = computed(() => this.i18n.formatDate(this.submission().submittedAt));
  readonly submissionDocuments = computed(() => this.documents.list(this.submission().id));
  readonly letters = computed(() => this.submissionDocuments().filter(isValidationLetter).sort((a, b) => b.version - a.version));
  readonly validationCommittees = computed(() => {
    const value = this.answers()['VAL-010'];
    const selected = Array.isArray(value)
      ? value.filter((item): item is EthicsCommittee => typeof item === 'string' && isValidationCommittee(item))
      : [];
    const documented = this.submissionDocuments()
      .map((doc) => doc.committeeType)
      .filter((item): item is EthicsCommittee => !!item);
    return VALIDATION_COMMITTEE_ORDER.filter((item) => selected.includes(item) || documented.includes(item));
  });

  sections() {
    const form = this.form();
    return form.sections
      .filter((section) => !(this.submission().type === 'VALIDATION' && section.id === 'E'))
      .map((section) => ({
        ...section,
        questions: questionsOfSection(form, section.id)
      }));
  }

  docsFor(committee: EthicsCommittee) {
    return this.submissionDocuments()
      .filter((doc) => doc.committeeType === committee)
      .sort((a, b) => a.documentType?.localeCompare(b.documentType ?? '') || b.version - a.version);
  }

  documentLabel(doc: DocumentMetadata) {
    return validationDocumentLabelKey(doc) ?? 'projects.documents';
  }

  showPreview(): void {
    this.previewNote.set(this.i18n.translate('readonly.previewUnavailable'));
  }
}
