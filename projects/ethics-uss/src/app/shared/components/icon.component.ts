import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type EthIconName = 'view' | 'download' | 'more' | 'upload' | 'file' | 'external' | 'chevron' | 'eye' | 'eye-off';

@Component({
  selector: 'eth-icon',
  template: `
    <svg viewBox="0 0 24 24" focusable="false" aria-hidden="true">
      @switch (name()) {
        @case ('view') {
          <path d="M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" />
          <circle class="ring" cx="12" cy="12" r="2.75" />
        }
        @case ('download') {
          <path d="M12 4.5v10" />
          <path d="m8 11 4 4 4-4" />
          <path d="M5 19.5h14" />
        }
        @case ('upload') {
          <path d="M12 15.5V5.5" />
          <path d="m8 9.5 4-4 4 4" />
          <path d="M5 19.5h14" />
        }
        @case ('more') {
          <circle class="dot" cx="5" cy="12" r="1.25" />
          <circle class="dot" cx="12" cy="12" r="1.25" />
          <circle class="dot" cx="19" cy="12" r="1.25" />
        }
        @case ('file') {
          <path d="M14 3.5H7.5A1.5 1.5 0 0 0 6 5v14a1.5 1.5 0 0 0 1.5 1.5h9A1.5 1.5 0 0 0 18 19V7.5L14 3.5z" />
          <path d="M14 3.5V7.5H18" />
        }
        @case ('external') {
          <path d="M15 5h4v4" />
          <path d="M19 5 10 14" />
          <path d="M13 5H6.5A1.5 1.5 0 0 0 5 6.5v11A1.5 1.5 0 0 0 6.5 19h11a1.5 1.5 0 0 0 1.5-1.5V11" />
        }
        @case ('chevron') {
          <path class="dot" d="M7.4 9.4 12 14l4.6-4.6 1.4 1.4L12 16.8 6 10.8z" />
        }
        @case ('eye') {
          <path d="M2.5 12S6 6.5 12 6.5 21.5 12 21.5 12 18 17.5 12 17.5 2.5 12 2.5 12z" />
          <circle class="ring" cx="12" cy="12" r="2.6" />
        }
        @case ('eye-off') {
          <path d="M3 4.5 19.5 21" />
          <path d="M9.2 6.7A10 10 0 0 1 12 6.5c6 0 9.5 5.5 9.5 5.5a16 16 0 0 1-3.4 3.7" />
          <path d="M6.2 8C4.2 9.4 2.5 12 2.5 12S6 17.5 12 17.5c1.1 0 2.2-.2 3.2-.6" />
          <path d="M9.8 10.2a2.6 2.6 0 0 0 3.6 3.6" />
        }
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      width: 1.05rem;
      height: 1.05rem;
      color: currentColor;
      flex: 0 0 auto;
    }
    svg {
      width: 100%;
      height: 100%;
      stroke: currentColor;
      stroke-width: 1.75;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
    path,
    .ring {
      fill: none;
    }
    .dot {
      fill: currentColor;
      stroke: none;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IconComponent {
  readonly name = input.required<EthIconName>();
}
