import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  afterNextRender,
  inject,
  input,
  output,
  signal,
  viewChild
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { EventAdmin } from '../core/events-api.service';
import { eventAdminActionFlags } from '../core/event-admin-actions';
import { PublicEventLinks } from '../core/public-event-links';
import { I18nService } from '../core/i18n/i18n.service';
import { I18N_IMPORTS } from './i18n/i18n.imports';

@Component({
  selector: 'ose-event-row-actions',
  imports: [RouterLink, ...I18N_IMPORTS],
  template: `
    <div class="row-actions">
      <a class="button-link row-actions__manage" [routerLink]="['/admin/eventos', event().id]">
        {{ 'adminList.manage' | translate }}
      </a>
      <div class="action-menu" [class.is-open]="open()">
        <button
          #trigger
          type="button"
          class="secondary action-menu__trigger"
          [attr.aria-expanded]="open()"
          aria-haspopup="menu"
          [attr.aria-controls]="menuId"
          [attr.aria-label]="'adminList.moreActions' | translate"
          (click)="toggle($event)"
          (keydown)="onTriggerKey($event)"
        >
          <span aria-hidden="true">⋯</span>
        </button>
        @if (open()) {
          <ul
            #menu
            role="menu"
            class="action-menu__list"
            [id]="menuId"
            [style.top.px]="menuTop()"
            [style.left.px]="menuLeft()"
            (keydown)="onMenuKey($event)"
          >
            @if (flags().canEdit) {
              <li role="none">
                <a role="menuitem" [routerLink]="['/admin/eventos', event().id, 'editar']" (click)="close()">
                  {{ 'adminList.editEvent' | translate }}
                </a>
              </li>
            }
            @if (registrationUrl(); as url) {
              <li role="none">
                <button type="button" role="menuitem" class="action-menu__item" (click)="copy(url, 'registration')">
                  @if (copiedKind() === 'registration') {
                    <span class="copy-icon" aria-hidden="true">
                      <svg viewBox="0 0 16 16" width="14" height="14" focusable="false">
                        <path fill="currentColor" d="M6.2 11.4 2.8 8l1.1-1.1 2.3 2.3 5.9-5.9L13.2 4z" />
                      </svg>
                    </span>
                    {{ 'common.copied' | translate }}
                  } @else {
                    {{ 'form.copyRegistration' | translate }}
                  }
                </button>
              </li>
            }
            @if (publicUrl(); as url) {
              <li role="none">
                <button type="button" role="menuitem" class="action-menu__item" (click)="copy(url, 'public')">
                  @if (copiedKind() === 'public') {
                    <span class="copy-icon" aria-hidden="true">
                      <svg viewBox="0 0 16 16" width="14" height="14" focusable="false">
                        <path fill="currentColor" d="M6.2 11.4 2.8 8l1.1-1.1 2.3 2.3 5.9-5.9L13.2 4z" />
                      </svg>
                    </span>
                    {{ 'common.copied' | translate }}
                  } @else {
                    {{ 'form.copyPublic' | translate }}
                  }
                </button>
              </li>
            }
            @if (flags().canPublish) {
              <li role="none">
                <button type="button" role="menuitem" class="action-menu__item" (click)="askPublish()">
                  {{ 'adminList.publish' | translate }}
                </button>
              </li>
            }
            @if (flags().canUnpublish) {
              <li role="none">
                <button type="button" role="menuitem" class="action-menu__item" (click)="askUnpublish()">
                  {{ 'adminList.unpublish' | translate }}
                </button>
              </li>
            }
          </ul>
        }
      </div>
    </div>
    <span class="sr-only" aria-live="polite">{{ live() }}</span>
  `,
  styles: `
    :host {
      display: block;
    }
    .row-actions {
      display: inline-flex;
      flex-wrap: nowrap;
      align-items: center;
      justify-content: flex-end;
      gap: 0.5rem;
    }
    .row-actions__manage {
      min-width: 6.5rem;
    }
    .action-menu {
      position: relative;
    }
    .action-menu__trigger {
      width: var(--ose-btn-height);
      min-width: var(--ose-btn-height);
      padding: 0;
      font-size: 1.25rem;
      line-height: 1;
    }
    .action-menu__trigger:focus-visible,
    .row-actions__manage:focus-visible {
      outline: 2px solid var(--uss-secondary);
      outline-offset: 2px;
    }
    .action-menu__list {
      position: fixed;
      z-index: 40;
      min-width: 15.5rem;
      margin: 0;
      padding: 0.35rem 0;
      list-style: none;
      background: var(--ose-surface);
      border: 1px solid var(--ose-border);
      border-radius: var(--ose-radius);
      box-shadow: 0 12px 28px rgba(16, 43, 66, 0.16);
    }
    .action-menu__list a,
    .action-menu__item {
      box-sizing: border-box;
      display: flex;
      align-items: center;
      gap: 0.45rem;
      width: 100%;
      min-height: 2.4rem;
      padding: 0 0.9rem;
      border: 0;
      background: transparent;
      color: var(--ose-text);
      font-family: var(--ose-font);
      font-size: 0.92rem;
      font-weight: 600;
      line-height: 1.2;
      text-align: left;
      text-decoration: none;
      cursor: pointer;
    }
    .action-menu__list a:hover,
    .action-menu__list a:focus-visible,
    .action-menu__item:hover,
    .action-menu__item:focus-visible {
      background: #eef3f7;
      text-decoration: none;
      outline: none;
    }
    .copy-icon {
      display: inline-flex;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EventRowActionsComponent implements OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly document = inject(DOCUMENT);
  private readonly i18n = inject(I18nService);
  private readonly publicLinks = inject(PublicEventLinks);
  readonly event = input.required<EventAdmin>();
  readonly publish = output<EventAdmin>();
  readonly unpublish = output<EventAdmin>();
  readonly copied = output<void>();
  readonly open = signal(false);
  readonly copiedKind = signal<'registration' | 'public' | null>(null);
  readonly live = signal('');
  readonly menuTop = signal(0);
  readonly menuLeft = signal(0);
  readonly menuId = `ose-event-actions-${Math.random().toString(36).slice(2, 8)}`;
  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');
  private readonly menu = viewChild<ElementRef<HTMLElement>>('menu');
  private copyTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    afterNextRender(() => {
      if (this.open()) {
        this.placeMenu();
      }
    });
  }

  flags() {
    return eventAdminActionFlags(this.event().publicationStatus);
  }

  registrationUrl(): string | null {
    return this.publicLinks.registrationUrl(this.publicLinks.slugOf(this.event()));
  }

  publicUrl(): string | null {
    return this.publicLinks.eventUrl(this.publicLinks.slugOf(this.event()));
  }

  toggle(event: Event): void {
    event.stopPropagation();
    if (this.open()) {
      this.close();
      return;
    }
    this.open.set(true);
    queueMicrotask(() => {
      this.placeMenu();
      this.menuItems()[0]?.focus();
    });
  }

  onTriggerKey(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (!this.open()) {
        this.toggle(event);
      } else {
        this.menuItems()[0]?.focus();
      }
    }
  }

  onMenuKey(event: KeyboardEvent): void {
    const items = this.menuItems();
    if (!items.length) {
      return;
    }
    const current = this.document.activeElement as HTMLElement | null;
    const index = current ? items.indexOf(current) : -1;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      items[(index + 1 + items.length) % items.length].focus();
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      items[(index <= 0 ? items.length : index) - 1].focus();
      return;
    }
    if (event.key === 'Home') {
      event.preventDefault();
      items[0].focus();
      return;
    }
    if (event.key === 'End') {
      event.preventDefault();
      items[items.length - 1].focus();
      return;
    }
    if (event.key === 'Tab') {
      event.preventDefault();
      this.close(true);
    }
  }

  ngOnDestroy(): void {
    this.clearCopyTimer();
  }

  close(restoreFocus = false): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    if (restoreFocus) {
      this.trigger()?.nativeElement.focus();
    }
  }

  askPublish(): void {
    this.close();
    this.publish.emit(this.event());
  }

  askUnpublish(): void {
    this.close();
    this.unpublish.emit(this.event());
  }

  async copy(url: string, kind: 'registration' | 'public'): Promise<void> {
    try {
      await navigator.clipboard.writeText(url);
      this.copiedKind.set(kind);
      this.live.set(this.i18n.translate('common.copied'));
      this.copied.emit();
      this.clearCopyTimer();
      this.copyTimer = setTimeout(() => {
        this.copiedKind.set(null);
        this.live.set('');
        this.close();
        this.copyTimer = null;
      }, 1600);
    } catch {
      this.copiedKind.set(null);
      this.live.set(this.i18n.translate('common.copyFailed'));
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.open()) {
      return;
    }
    const target = event.target as Node | null;
    if (target && this.host.nativeElement.contains(target)) {
      return;
    }
    if (target && this.menu()?.nativeElement.contains(target)) {
      return;
    }
    this.close();
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKey(event: KeyboardEvent): void {
    if (!this.open()) {
      return;
    }
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close(true);
    }
  }

  @HostListener('window:resize')
  onResize(): void {
    if (this.open()) {
      this.placeMenu();
    }
  }

  private placeMenu(): void {
    const trigger = this.trigger()?.nativeElement.getBoundingClientRect();
    if (!trigger) {
      return;
    }
    const width = 248;
    const estimated = 220;
    let left = trigger.right - width;
    let top = trigger.bottom + 6;
    if (left < 8) {
      left = 8;
    }
    if (left + width > this.document.documentElement.clientWidth - 8) {
      left = this.document.documentElement.clientWidth - width - 8;
    }
    if (top + estimated > window.innerHeight - 8) {
      top = Math.max(8, trigger.top - estimated - 6);
    }
    this.menuLeft.set(left);
    this.menuTop.set(top);
  }

  private menuItems(): HTMLElement[] {
    return Array.from(this.menu()?.nativeElement.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
  }

  private clearCopyTimer(): void {
    if (this.copyTimer) {
      clearTimeout(this.copyTimer);
      this.copyTimer = null;
    }
  }
}
