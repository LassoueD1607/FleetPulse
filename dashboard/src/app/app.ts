import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { AlertFeed } from './alert-feed/alert-feed';
import { clock } from './core/format';
import { FleetStore } from './core/fleet-store';
import { ALERT_LABEL, Alert, Connection } from './core/models';
import { beep } from './core/sound';
import { FleetMap } from './fleet-map/fleet-map';
import { KpiCards } from './kpi-cards/kpi-cards';
import { VehicleDetail } from './vehicle-detail/vehicle-detail';
import { VehicleTable } from './vehicle-table/vehicle-table';

const CONNECTION_LABEL: Record<Connection, string> = {
  connecting: 'Connecting',
  live: 'Live',
  reconnecting: 'Reconnecting',
};

@Component({
  imports: [KpiCards, FleetMap, AlertFeed, VehicleTable, VehicleDetail],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly store = inject(FleetStore);
  protected readonly alertLabel = ALERT_LABEL;
  protected readonly soundOn = signal(true);
  protected readonly toast = signal<Alert | null>(null);
  protected readonly clock = computed(() => clock(this.store.now()));
  protected readonly connectionLabel = computed(() => CONNECTION_LABEL[this.store.connection()]);

  private toastTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    // A critical alert (a vehicle left Tunisia) interrupts: toast plus a beep the owner can mute.
    effect(() => {
      const alert = this.store.liveCritical();
      if (!alert) {
        return;
      }
      this.toast.set(alert);
      // untracked: toggling the sound button must not replay the beep for the same alert.
      if (untracked(() => this.soundOn())) {
        beep();
      }
      clearTimeout(this.toastTimer);
      this.toastTimer = setTimeout(() => this.toast.set(null), 8000);
    });
  }

  protected toggleSound(): void {
    this.soundOn.update((on) => !on);
  }

  protected openToast(alert: Alert): void {
    this.store.select(alert.vehicleId);
    this.dismissToast();
  }

  protected dismissToast(): void {
    clearTimeout(this.toastTimer);
    this.toast.set(null);
  }
}
