import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { I18N_HOST } from '../../shared/i18n/i18n.imports';
import { TranslatePipe } from '../../shared/pipes/translate.pipe';

@Component({
  selector: 'os-unauthorized',
  hostDirectives: [...I18N_HOST],
  imports: [RouterLink, PageHeaderComponent, TranslatePipe],
  template: `
    @if (hasAccess) {
      <os-page-header [title]="'auth.unauthorizedTitle' | translate" [subtitle]="'auth.unauthorizedSubtitle' | translate" />
      <p><a [routerLink]="home">{{ 'auth.backHome' | translate }}</a></p>
    } @else {
      <os-page-header [title]="'auth.enabledTitle' | translate" [subtitle]="'auth.enabledSubtitle' | translate" />
      <p>
        <button type="button" class="page-action" (click)="signOut()">{{ 'header.signOut' | translate }}</button>
      </p>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UnauthorizedComponent {
  private readonly auth = inject(AuthService);
  readonly hasAccess = this.auth.hasFunctionalAccess();
  readonly home = this.auth.firstAllowedPath();

  signOut(): void {
    void this.auth.signOut();
  }
}
