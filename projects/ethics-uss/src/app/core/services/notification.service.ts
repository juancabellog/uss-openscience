import { Injectable, inject } from '@angular/core';
import { I18nService } from '../i18n/i18n.service';
import { NotificationItem } from '../models/ethics.models';
import { EthicsStore } from './ethics-store.service';
import { nowIso, uid } from './ids';

export interface NotificationDraft {
  type?: string;
  params?: Record<string, string | number>;
  title?: string;
  body?: string;
  link?: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly i18n = inject(I18nService);

  constructor(private readonly store: EthicsStore) {}

  forUser(userId: string): NotificationItem[] {
    return this.store
      .snapshot()
      .notifications.filter((item) => item.userId === userId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  unreadCount(userId: string): number {
    return this.forUser(userId).filter((item) => !item.read).length;
  }

  display(item: NotificationItem): { title: string; body: string } {
    return this.i18n.notificationText(item);
  }

  notify(userId: string, titleOrDraft: string | NotificationDraft, body?: string, link?: string): void {
    const draft: NotificationDraft = typeof titleOrDraft === 'string'
      ? { title: titleOrDraft, body, link }
      : titleOrDraft;
    const item: NotificationItem = {
      id: uid('ntf'),
      userId,
      title: draft.title ?? '',
      body: draft.body ?? '',
      createdAt: nowIso(),
      read: false,
      link: draft.link,
      type: draft.type,
      params: draft.params
    };
    this.store.update((state) => ({ ...state, notifications: [item, ...state.notifications] }));
  }

  markRead(id: string): void {
    this.store.update((state) => ({
      ...state,
      notifications: state.notifications.map((item) => (item.id === id ? { ...item, read: true } : item))
    }));
  }
}
