export type TableSortDirection = 'asc' | 'desc';

export interface TableSort {
  field: string;
  direction: TableSortDirection;
}

export function nextTableSort(
  current: TableSort,
  field: string,
  fallback: TableSort,
  explicit: boolean
): { sort: TableSort; explicit: boolean } {
  if (!explicit || current.field !== field) {
    return { sort: { field, direction: 'asc' }, explicit: true };
  }
  if (current.direction === 'asc') {
    return { sort: { field, direction: 'desc' }, explicit: true };
  }
  return { sort: { ...fallback }, explicit: false };
}

export function ariaSortValue(
  field: string,
  activeField: string,
  direction: TableSortDirection
): 'ascending' | 'descending' | 'none' {
  if (field !== activeField) {
    return 'none';
  }
  return direction === 'asc' ? 'ascending' : 'descending';
}
