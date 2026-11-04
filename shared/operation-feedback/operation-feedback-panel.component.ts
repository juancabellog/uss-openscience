import { ChangeDetectionStrategy, Component, ElementRef, effect, input, viewChild } from '@angular/core';

@Component({
  selector: 'uss-operation-feedback',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <div class="uss-op-backdrop"></div>
      <div class="uss-op-panel" role="status" aria-live="polite" aria-busy="true" tabindex="-1" #panel>
        <p class="uss-op-title">
          <span class="uss-op-spinner" aria-hidden="true"></span>
          {{ title() }}
        </p>
        @if (detail()) {
          <p>{{ detail() }}</p>
        }
        @if (extra()) {
          <p>{{ extra() }}</p>
        }
      </div>
    }
  `
})
export class OperationFeedbackPanelComponent {
  readonly title = input('');
  readonly detail = input('');
  readonly extra = input('');
  readonly visible = input(false);
  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  private previous: HTMLElement | null = null;

  constructor() {
    effect(() => {
      const open = this.visible();
      const node = this.panel();
      if (!open) {
        const previous = this.previous;
        this.previous = null;
        previous?.focus();
        return;
      }
      if (!this.previous && document.activeElement instanceof HTMLElement) {
        this.previous = document.activeElement;
      }
      queueMicrotask(() => node?.nativeElement.focus());
    });
  }
}
