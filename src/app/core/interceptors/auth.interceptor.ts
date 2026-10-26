import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { currentPagePath, shouldRedirectUnauthorized } from '../auth/auth-public.util';
import { I18nService } from '../i18n/i18n.service';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const notifications = inject(NotificationService);
  const i18n = inject(I18nService);
  const withCreds = req.clone({ withCredentials: true });
  return next(withCreds).pipe(
    catchError((error: unknown) => {
      const page = currentPagePath(
        router.url,
        typeof location === 'undefined' ? undefined : location.pathname
      );
      if (error instanceof HttpErrorResponse && error.status === 403) {
        notifications.show(
          'warning',
          i18n.translate('common.forbiddenTitle'),
          i18n.translate('common.forbiddenAction')
        );
        return throwError(() => error);
      }
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        shouldRedirectUnauthorized(req.url, page)
      ) {
        auth.clear();
        const returnUrl = page.startsWith('/login') ? '/dashboard' : page;
        void router.navigate(['/login'], { queryParams: { returnUrl } });
      }
      return throwError(() => error);
    })
  );
};
