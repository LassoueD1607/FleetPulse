import { Component, computed, inject } from '@angular/core';
import { FleetStore } from '../core/fleet-store';
import { ago } from '../core/format';
import { ALERT_LABEL, MAX_SPEED_KMH, SPEED_LIMIT_KMH } from '../core/models';
import { StatusPill } from '../shared/status-pill';

const SPARK_WIDTH = 340;
const SPARK_HEIGHT = 96;
const SPARK_POINTS = 60;

@Component({
  selector: 'app-vehicle-detail',
  imports: [StatusPill],
  templateUrl: './vehicle-detail.html',
  styleUrl: './vehicle-detail.css',
  host: { '(document:keydown.escape)': 'close()' },
})
export class VehicleDetail {
  protected readonly store = inject(FleetStore);
  protected readonly ago = ago;
  protected readonly label = ALERT_LABEL;
  protected readonly limit = SPEED_LIMIT_KMH;

  protected readonly vehicleAlerts = computed(() =>
    this.store
      .alerts()
      .filter((alert) => alert.vehicleId === this.store.selectedId())
      .slice(0, 8),
  );

  // The last readings as an inline SVG chart, with a dashed line at the speed limit.
  protected readonly spark = computed(() => {
    const points = this.store.selectedTrail().slice(-SPARK_POINTS);
    if (points.length < 2) {
      return null;
    }
    const x = (index: number) => (index / (points.length - 1)) * SPARK_WIDTH;
    const y = (speed: number) => SPARK_HEIGHT - (Math.min(speed, MAX_SPEED_KMH) / MAX_SPEED_KMH) * SPARK_HEIGHT;
    const line = points.map((point, index) => `${x(index).toFixed(1)},${y(point.speedKmh).toFixed(1)}`).join(' ');
    return {
      viewBox: `0 0 ${SPARK_WIDTH} ${SPARK_HEIGHT}`,
      width: SPARK_WIDTH,
      line,
      area: `0,${SPARK_HEIGHT} ${line} ${SPARK_WIDTH},${SPARK_HEIGHT}`,
      limitY: y(SPEED_LIMIT_KMH),
      count: points.length,
    };
  });

  close(): void {
    this.store.select(null);
  }
}
