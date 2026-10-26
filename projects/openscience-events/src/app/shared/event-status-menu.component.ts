import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  inject,
  input,
  output,
  signal,
  viewChild
} from '@angular/core';
import { EventAdmin } from '../core/events-api.service';
import { eventAdminActionFlags } from '../core/event-admin-actions';
import { I18N_IMPORTS } from './i18n/i18n.imports';

@Component({
  selector: 'ose-event-status-menu',
  imports: [...I18N_IMPORTS],
  template: `
    @if (hasActions()) {
      <div class="action-menu" [class.is-open]="open()">
        <button
          #trigger
          type="button"
          class="secondary action-menu__label"
          [attr.aria-expanded]="open()"
          aria-haspopup="menu"
          [attr.aria-controls]="menuId"
          (click)="toggle($event)"
          (keydown)="onTriggerKey($event)"
        >
          {{ 'adminEvent.moreActions' | translate }}
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
            @if (flags().canPublish) {
              <li role="none">
                <button type="button" role="menuitem" class="action-menu__item" (click)="emit('publish')">
                  {{ 'adminList.publish' | translate }}
                </button>
              </li>
            }
            @if (flags().canUnpublish) {
              <li role="none">
                <button type="button" role="menuitem" class="action-menu__item" (click)="emit('unpublish')">
                  {{ 'adminList.unpublish' | translate }}
                </button>
              </li>
            }
            @if (flags().canFinish) {
              <li role="none">
                <button type="button" role="menuitem" class="action-menu__item is-warning" (click)="emit('finish')">
                  {{ 'form.finish' | translate }}
                </button>
              </li>
            }
            @if (flags().canCancel) {
              <li role="none">
                <button type="button" role="menuitem" class="action-menu__item is-danger" (click)="emit('cancel')">
                  {{ 'form.cancelEvent' | translate }}
                </button>
              </li>
            }
          </ul>
        }
      </div>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
    }
    .action-menu {
      position: relative;
    }
    .action-menu__label {
      min-width: 8.5rem;
    }
    .action-menu__label:focus-visible {
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
    .action-menu__item {
      box-sizing: border-box;
      display: flex;
      align-items: center;
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
      cursor: pointer;
    }
    .action-menu__item:hover,
    .action-menu__item:focus-visible {
      background: #eef3f7;
      outline: none;
    }
    .action-menu__item.is-warning {
      color: #7a5408;
    }
    .action-menu__item.is-danger {
      color: var(--ose-danger);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EventStatusMenuComponent {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly document = inject(DOCUMENT);
  readonly event = input.required<EventAdmin>();
  readonly publish = output<EventAdmin>();
  readonly unpublish = output<EventAdmin>();
  readonly finish = output<EventAdmin>();
  readonly cancel = output<EventAdmin>();
  readonly open = signal(false);
  readonly menuTop = signal(0);
  readonly menuLeft = signal(0);
  readonly menuId = `ose-event-status-${Math.random().toString(36).slice(2, 8)}`;
  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');
  private readonly menu = viewChild<ElementRef<HTMLElement>>('menu');

  flags() {
    return eventAdminActionFlags(this.event().publicationStatus);
  }

  hasActions(): boolean {
    const flags = this.flags();
    return flags.canPublish || flags.canUnpublish || flags.canFinish || flags.canCancel;
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

  emit(kind: 'publish' | 'unpublish' | 'finish' | 'cancel'): void {
    this.close();
    const event = this.event();
    if (kind === 'publish') {
      this.publish.emit(event);
    } else if (kind === 'unpublish') {
      this.unpublish.emit(event);
    } else if (kind === 'finish') {
      this.finish.emit(event);
    } else {
      this.cancel.emit(event);
    }
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

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.open()) {
      return;
    }
    const target = event.target as Node | null;
    if (target && this.host.nativeElement.contains(target)) {
      return;
    }
    this.close();
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKey(event: KeyboardEvent): void {
    if (this.open() && event.key === 'Escape') {
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
    let left = trigger.right - width;
    let top = trigger.bottom + 6;
    if (left < 8) {
      left = 8;
    }
    if (left + width > this.document.documentElement.clientWidth - 8) {
      left = this.document.documentElement.clientWidth - width - 8;
    }
    if (top + 220 > window.innerHeight - 8) {
      top = Math.max(8, trigger.top - 220);
    }
    this.menuLeft.set(left);
    this.menuTop.set(top);
  }

  private menuItems(): HTMLElement[] {
    return Array.from(this.menu()?.nativeElement.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
  }
}
