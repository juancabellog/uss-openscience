import { Injectable, signal } from '@angular/core';

export type NotificationKind = 'success' | 'warning' | 'error';

export interface NotificationAction {
  label: string;
  run: () => void;
}

export interface AppNotification {
  id: string;
  kind: NotificationKind;
  title: string;
  body: string;
  detail?: string | null;
  action?: NotificationAction | null;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 0;
  readonly notifications = signal<AppNotification[]>([]);

  show(
    kind: NotificationKind,
    title: string,
    body: string,
    detail?: string | null,
    action?: NotificationAction | null
  ): string {
    const id = `toast-${++this.nextId}`;
    this.notifications.update((items) => [...items, { id, kind, title, body, detail, action }]);
    window.setTimeout(() => this.dismiss(id), action ? 14000 : 9000);
    return id;
  }

  dismiss(id: string): void {
    this.notifications.update((items) => items.filter((item) => item.id !== id));
  }
}
