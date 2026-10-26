import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { ProjectDetail, ProjectOrganization, ProjectParticipant } from '../../core/models/openscience.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { ProjectApiService } from '../../core/services/api/project-api.service';
import { DetailSectionNavComponent, DetailSectionNavItem } from '../../shared/components/detail-section-nav/detail-section-nav.component';
import { ErrorStateComponent } from '../../shared/components/error-state/error-state.component';
import { IdentifierRowComponent } from '../../shared/components/identifier-row/identifier-row.component';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { I18N_HOST, I18N_IMPORTS } from '../../shared/i18n/i18n.imports';

export const PROJECT_DETAIL_SECTIONS: DetailSectionNavItem[] = [
  { id: 'identidad', labelKey: 'projects.identity' },
  { id: 'periodo', labelKey: 'projects.period' },
  { id: 'clasificacion', labelKey: 'projects.classification' },
  { id: 'participantes', labelKey: 'projects.participants' },
  { id: 'organizaciones', labelKey: 'projects.organizations' },
  { id: 'auditoria', labelKey: 'projects.pureAudit' }
];

type DetailVm =
  | { status: 'loading'; project: null }
  | { status: 'error'; project: null }
  | { status: 'notfound'; project: null }
  | { status: 'success'; project: ProjectDetail };

@Component({
  selector: 'os-project-detail',
  hostDirectives: [...I18N_HOST],
  imports: [
    ...I18N_IMPORTS,
    RouterLink,
    DetailSectionNavComponent,
    ErrorStateComponent,
    IdentifierRowComponent,
    LoadingStateComponent,
    PageHeaderComponent
  ],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProjectDetailComponent {
  private readonly projectsApi = inject(ProjectApiService);
  private readonly i18n = inject(I18nService);
  readonly id = input.required<string>();
  readonly sectionNav = PROJECT_DETAIL_SECTIONS;

  readonly vm = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) => {
        if (!id) {
          return of<DetailVm>({ status: 'notfound', project: null });
        }
        return this.projectsApi.getById(id).pipe(
          map((project): DetailVm => ({ status: 'success', project })),
          catchError((error: unknown) =>
            of<DetailVm>(
              error instanceof HttpErrorResponse && error.status === 404
                ? { status: 'notfound', project: null }
                : { status: 'error', project: null }
            )
          ),
          startWith<DetailVm>({ status: 'loading', project: null })
        );
      })
    ),
    { initialValue: { status: 'loading', project: null } satisfies DetailVm }
  );

  readonly language = this.i18n.currentLanguage;

  readonly displayTitle = computed(() => {
    const project = this.vm().project;
    if (!project) {
      return '';
    }
    return project.title || project.titleEs || project.titleEn || project.acronym || this.i18n.translate('projects.untitled');
  });

  typeLabel(project: ProjectDetail): string {
    return localized(project.typeEs, project.typeEn, project.type, this.language());
  }

  workflowLabel(project: ProjectDetail): string {
    return localized(project.workflowEs, project.workflowEn, project.workflowStep, this.language());
  }

  visibilityLabel(project: ProjectDetail): string {
    return localized(project.visibilityEs, project.visibilityEn, project.visibilityKey, this.language());
  }

  roleLabel(participant: ProjectParticipant): string {
    return localized(participant.roleTermEs, participant.roleTermEn, participant.roleUri, this.language());
  }

  identifierType(item: ProjectDetail['identifiers'][number]): string {
    return localized(item.typeEs, item.typeEn, item.typeUri || item.typeDiscriminator, this.language());
  }

  natureLabel(item: ProjectDetail['natureTypes'][number]): string {
    return localized(item.termEs, item.termEn, item.uri, this.language());
  }

  descriptionType(item: ProjectDetail['descriptions'][number]): string {
    return localized(item.typeEs, item.typeEn, item.typeUri, this.language());
  }

  organizationName(organization: ProjectOrganization): string {
    const preferred = localized(organization.nameEs, organization.nameEn, organization.organizationSystemName, this.language());
    if (preferred) {
      return preferred;
    }
    return this.i18n.translate('projects.organizationUnavailable');
  }

  managingOrganizationName(project: ProjectDetail): string {
    const preferred = localized(
      project.managingOrganizationNameEs,
      project.managingOrganizationNameEn,
      project.managingOrganizationName,
      this.language()
    );
    if (preferred) {
      return preferred;
    }
    if (project.managingOrganizationUuid) {
      return this.i18n.translate('projects.organizationUnavailable');
    }
    return this.i18n.translate('common.dash');
  }

  participantName(participant: ProjectParticipant): string {
    return participant.displayName
      || `${participant.firstName ?? ''} ${participant.lastName ?? ''}`.trim()
      || this.i18n.translate('common.dash');
  }
}

function localized(
  es: string | null | undefined,
  en: string | null | undefined,
  fallback: string | null | undefined,
  language: string
): string {
  const preferred = language === 'en' ? en || es : es || en;
  return preferred || fallback || '';
}
