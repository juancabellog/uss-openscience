import { delay, Observable, of, switchMap, throwError, timer } from 'rxjs';

export const MOCK_LATENCY_MS = 280;

export function mockOf<T>(data: T): Observable<T> {
  return of(data).pipe(delay(MOCK_LATENCY_MS));
}

export function mockNotFound(message: string): Observable<never> {
  return timer(MOCK_LATENCY_MS).pipe(switchMap(() => throwError(() => new Error(message))));
}

export function paginate<T>(items: T[], page = 1, pageSize = 8): {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
} {
  const safePage = Math.max(1, page);
  const safeSize = Math.max(1, pageSize);
  const start = (safePage - 1) * safeSize;
  return {
    items: items.slice(start, start + safeSize),
    total: items.length,
    page: safePage,
    pageSize: safeSize
  };
}

export function includesText(value: string | undefined, search: string): boolean {
  return (value ?? '').toLowerCase().includes(search.trim().toLowerCase());
}
