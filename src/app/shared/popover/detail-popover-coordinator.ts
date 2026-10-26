import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class DetailPopoverCoordinator {
  readonly activeId = signal<string | null>(null);
}
