import { Component, computed, input } from '@angular/core';
import { STATUS_LABEL, Status } from '../core/models';

@Component({
  selector: 'app-status-pill',
  template: `<span class="pill" [class]="status()">{{ label() }}</span>`,
  styles: `
    .pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 2px 10px;
      border-radius: 999px;
      background: var(--c-bg);
      color: var(--c);
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
    }
    .pill::before {
      content: '';
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--c);
    }
  `,
})
export class StatusPill {
  readonly status = input.required<Status>();
  protected readonly label = computed(() => STATUS_LABEL[this.status()]);
}
