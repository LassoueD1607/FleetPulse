import { Component, computed, inject } from '@angular/core';
import { FleetStore } from '../core/fleet-store';
import { Status } from '../core/models';

interface Card {
  label: string;
  value: number;
  hint: string;
  tone: Status | 'INFO';
  filter: Status | 'ALL' | null;
}

@Component({
  selector: 'app-kpi-cards',
  templateUrl: './kpi-cards.html',
  styleUrl: './kpi-cards.css',
})
export class KpiCards {
  protected readonly store = inject(FleetStore);

  protected readonly cards = computed<Card[]>(() => {
    const c = this.store.counts();
    return [
      { label: 'Fleet', value: c.total, hint: 'vehicles tracked', tone: 'INFO', filter: 'ALL' },
      { label: 'Moving', value: c.moving, hint: 'on the road now', tone: 'OK', filter: null },
      { label: 'Speeding', value: c.SPEEDING, hint: 'above 110 km/h', tone: 'SPEEDING', filter: 'SPEEDING' },
      { label: 'Outside Tunisia', value: c.OUTSIDE_TUNISIA, hint: 'left the allowed area', tone: 'OUTSIDE_TUNISIA', filter: 'OUTSIDE_TUNISIA' },
      { label: 'Offline', value: c.OFFLINE, hint: 'no signal for 30 s', tone: 'OFFLINE', filter: 'OFFLINE' },
    ];
  });

  protected choose(card: Card): void {
    if (card.filter === null) {
      return;
    }
    // Clicking the active card again clears the filter.
    const next = this.store.statusFilter() === card.filter ? 'ALL' : card.filter;
    this.store.setStatusFilter(next);
  }
}
