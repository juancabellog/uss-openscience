import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class LayoutService {
  private readonly collapsed = signal(false);
  readonly sidebarCollapsed = this.collapsed.asReadonly();

  constructor() {
    this.collapseForMobile();
  }

  toggleSidebar(): void {
    this.collapsed.update((value) => !value);
  }

  collapse(): void {
    this.collapsed.set(true);
  }

  collapseForMobile(): void {
    if (typeof window !== 'undefined' && window.innerWidth < 960) {
      this.collapsed.set(true);
    }
  }
}
