import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { COMMITTEE_SHORT_LABELS } from '../../mocks/catalogs';
import { I18nService } from '../i18n/i18n.service';
import { I18N_IMPORTS } from '../../shared/i18n/i18n.imports';
import { EthicsWorkspace } from './workspace';
import { WorkspaceService } from './workspace.service';

@Component({
  selector: 'eth-workspace-switch',
  imports: [...I18N_IMPORTS],
  templateUrl: './workspace-switch.component.html',
  styleUrl: './workspace-switch.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkspaceSwitchComponent {
  private readonly host = inject(ElementRef<HTMLElement>);
  readonly workspaces = inject(WorkspaceService);
  readonly i18n = inject(I18nService);
  readonly open = signal(false);
  readonly active = this.workspaces.active;
  readonly label = computed(() => this.caption(this.active()));

  caption(item: EthicsWorkspace | null): string {
    if (!item) {
      return '';
    }
    if (!item.scopeId) {
      return this.i18n.translate(item.labelKey);
    }
    const committee = this.i18n.translate(COMMITTEE_SHORT_LABELS[item.scopeId] ?? item.scopeId);
    return this.i18n.translate(item.labelKey, { committee });
  }

  toggle(): void {
    this.open.update((value) => !value);
    if (this.open()) {
      queueMicrotask(() => this.focusSelected());
    }
  }

  choose(id: string): void {
    this.workspaces.select(id);
    this.close(true);
  }

  onTriggerKey(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp' || event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.open.set(true);
      queueMicrotask(() => this.focusSelected());
    } else if (event.key === 'Escape') {
      this.close(true);
    }
  }

  onListKey(event: KeyboardEvent): void {
    const items = this.workspaces.options();
    const buttons = this.optionButtons();
    const current = Math.max(0, buttons.findIndex((button) => button === document.activeElement));
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.focusOption(Math.min(items.length - 1, current + 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.focusOption(Math.max(0, current - 1));
    } else if (event.key === 'Home') {
      event.preventDefault();
      this.focusOption(0);
    } else if (event.key === 'End') {
      event.preventDefault();
      this.focusOption(items.length - 1);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.close(true);
    }
  }

  @HostListener('document:click', ['$event'])
  closeOnOutside(event: MouseEvent): void {
    if (!this.open()) {
      return;
    }
    const target = event.target;
    if (target instanceof Node && this.host.nativeElement.contains(target)) {
      return;
    }
    this.close(false);
  }

  private close(focusTrigger: boolean): void {
    this.open.set(false);
    if (focusTrigger) {
      queueMicrotask(() => this.host.nativeElement.querySelector('button')?.focus());
    }
  }

  private focusSelected(): void {
    const activeId = this.active()?.id;
    const index = this.workspaces.options().findIndex((item) => item.id === activeId);
    this.focusOption(index < 0 ? 0 : index);
  }

  private focusOption(index: number): void {
    this.optionButtons()[index]?.focus();
  }

  private optionButtons(): HTMLButtonElement[] {
    return [...this.host.nativeElement.querySelectorAll('[role="option"]')] as HTMLButtonElement[];
  }
}
