import { Component, computed, inject, signal } from '@angular/core';
import { FleetStore } from '../core/fleet-store';
import { ago } from '../core/format';
import { MAX_SPEED_KMH, SPEED_LIMIT_KMH, STATUS_LABEL } from '../core/models';
import { StatusPill } from '../shared/status-pill';

@Component({
  selector: 'app-vehicle-table',
  imports: [StatusPill],
  templateUrl: './vehicle-table.html',
  styleUrl: './vehicle-table.css',
})
export class VehicleTable {
  protected readonly store = inject(FleetStore);
  protected readonly ago = ago;
  protected readonly search = signal('');
  protected readonly limitPosition = (SPEED_LIMIT_KMH / MAX_SPEED_KMH) * 100;

  // Sorted by ID and never by status: rows that jump around when a status changes make the
  // row you are about to click move away. The status filter cards bring problem vehicles forward.
  protected readonly rows = computed(() => {
    const query = this.search().trim().toLowerCase();
    const filter = this.store.statusFilter();
    return this.store
      .vehicles()
      .filter((v) => (filter === 'ALL' || v.status === filter) && v.vehicleId.toLowerCase().includes(query))
      .sort((a, b) => a.vehicleId.localeCompare(b.vehicleId));
  });

  protected readonly activeFilterLabel = computed(() => {
    const filter = this.store.statusFilter();
    return filter === 'ALL' ? null : STATUS_LABEL[filter];
  });

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected barWidth(speedKmh: number): number {
    return Math.min(100, (speedKmh / MAX_SPEED_KMH) * 100);
  }
}
