import { catchError, map, Observable, of, startWith } from 'rxjs';
import { PagedResult } from '../models/openscience.models';

export interface PageViewState<T> {
  status: 'loading' | 'success' | 'error';
  data: PagedResult<T>;
}

export function emptyPage<T>(pageSize = 8): PagedResult<T> {
  return { items: [], total: 0, page: 1, pageSize };
}

export function toPageViewState<T>(
  source: Observable<PagedResult<T>>,
  pageSize = 8
): Observable<PageViewState<T>> {
  const loading: PageViewState<T> = { status: 'loading', data: emptyPage(pageSize) };
  return source.pipe(
    map((data): PageViewState<T> => ({ status: 'success', data })),
    catchError((): Observable<PageViewState<T>> =>
      of({ status: 'error', data: emptyPage<T>(pageSize) })
    ),
    startWith(loading)
  );
}
