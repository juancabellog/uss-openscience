import { Injectable, computed, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class SyncDetailService {
  private readonly syncId = signal<string | null>(null);

  readonly open = computed(() => this.syncId() != null);
  readonly currentSyncId = this.syncId.asReadonly();

  show(syncId: string): void {
    if (!syncId) {
      return;
    }
    this.syncId.set(syncId);
  }

  close(): void {
    this.syncId.set(null);
  }
}
