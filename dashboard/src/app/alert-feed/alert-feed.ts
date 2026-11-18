import { Component, computed, inject, signal } from '@angular/core';
import { FleetStore } from '../core/fleet-store';
import { ago } from '../core/format';
import { ALERT_LABEL } from '../core/models';

type Filter = 'ALL' | 'CRITICAL' | 'WARNING';

@Component({
  selector: 'app-alert-feed',
  templateUrl: './alert-feed.html',
  styleUrl: './alert-feed.css',
})
export class AlertFeed {
  protected readonly store = inject(FleetStore);
  protected readonly ago = ago;
  protected readonly label = ALERT_LABEL;

  protected readonly filters: { key: Filter; label: string }[] = [
    { key: 'ALL', label: 'All' },
    { key: 'CRITICAL', label: 'Critical' },
    { key: 'WARNING', label: 'Warnings' },
  ];
  protected readonly filter = signal<Filter>('ALL');

  protected readonly items = computed(() => {
    const filter = this.filter();
    return this.store.alerts().filter((alert) => filter === 'ALL' || alert.severity === filter);
  });
}
