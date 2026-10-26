import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

let nextHintId = 0;

@Component({
  selector: 'os-info-hint',
  imports: [IconComponent],
  templateUrl: './info-hint.component.html',
  styleUrl: './info-hint.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InfoHintComponent {
  readonly text = input.required<string>();
  readonly ariaLabel = input.required<string>();
  readonly showIcon = input(true);
  readonly embedded = input(false);
  readonly actionLabel = input<string | null>(null);
  readonly action = output<void>();
  readonly open = signal(false);
  readonly panelId = `os-info-hint-${++nextHintId}`;
  private pinned = false;

  show(): void {
    this.open.set(true);
  }

  hide(): void {
    if (!this.pinned) {
      this.open.set(false);
    }
  }

  toggle(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.pinned = !this.pinned;
    this.open.set(this.pinned);
  }

  onBlur(): void {
    this.pinned = false;
    this.open.set(false);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.pinned = false;
      this.open.set(false);
      (event.currentTarget as HTMLElement | null)?.blur();
    }
  }

  runAction(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.pinned = false;
    this.open.set(false);
    this.action.emit();
  }
}
