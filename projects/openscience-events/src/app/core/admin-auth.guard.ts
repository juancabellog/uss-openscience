import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { EventsApiService } from './events-api.service';

export const adminAuthGuard: CanActivateFn = () => {
  const api = inject(EventsApiService);
  const router = inject(Router);
  return api.me().pipe(
    map((user) =>
      api.hasEventsPermission(user) ? true : router.createUrlTree(['/admin/login'])
    ),
    catchError(() => of(router.createUrlTree(['/admin/login'])))
  );
};
