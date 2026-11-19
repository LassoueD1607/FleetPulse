import { Injectable, computed, signal } from '@angular/core';
import { Alert, Connection, Status, TrailPoint, Vehicle } from './models';

const TRAIL_LENGTH = 120;
const ALERT_LIMIT = 100;
const FLUSH_MS = 500;
const RECONNECT_MS = 3000;

async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${url} answered ${response.status}`);
  }
  return response.json() as Promise<T>;
}

// The only place that talks to live-service (through /api). Pages read signals, never fetch.
@Injectable({ providedIn: 'root' })
export class FleetStore {
  private readonly latest = new Map<string, Vehicle>();
  private readonly trails = new Map<string, TrailPoint[]>();
  private readonly trailVersion = signal(0);
  private pending: Vehicle[] = [];

  readonly vehicles = signal<Vehicle[]>([]);
  readonly alerts = signal<Alert[]>([]);
  readonly connection = signal<Connection>('connecting');
  readonly selectedId = signal<string | null>(null);
  readonly statusFilter = signal<Status | 'ALL'>('ALL');
  readonly now = signal(Date.now());
  // Only critical alerts that arrive live: the ones loaded at startup already happened.
  readonly liveCritical = signal<Alert | null>(null);

  readonly counts = computed(() => {
    const counts = { total: 0, moving: 0, OK: 0, SPEEDING: 0, OUTSIDE_TUNISIA: 0, OFFLINE: 0 };
    for (const vehicle of this.vehicles()) {
      counts.total++;
      counts[vehicle.status]++;
      if (vehicle.status !== 'OFFLINE' && vehicle.speedKmh > 5) {
        counts.moving++;
      }
    }
    return counts;
  });

  readonly selected = computed(() => this.vehicles().find((v) => v.vehicleId === this.selectedId()) ?? null);

  readonly selectedTrail = computed(() => {
    this.trailVersion();
    const id = this.selectedId();
    return id ? [...(this.trails.get(id) ?? [])] : [];
  });

  constructor() {
    void this.loadSnapshot();
    this.openStream();
    setInterval(() => this.flush(), FLUSH_MS);
    setInterval(() => this.now.set(Date.now()), 1000);
  }

  select(id: string | null): void {
    this.selectedId.set(id);
  }

  setStatusFilter(filter: Status | 'ALL'): void {
    this.statusFilter.set(filter);
  }

  // First load, and again after a reconnect to fill whatever was missed while disconnected.
  private async loadSnapshot(): Promise<void> {
    try {
      const [vehicles, alerts] = await Promise.all([
        fetchJson<Vehicle[]>('/api/vehicles'),
        fetchJson<Alert[]>('/api/alerts'),
      ]);
      vehicles.forEach((vehicle) => this.apply(vehicle));
      this.publish();
      this.alerts.set(alerts);
    } catch {
      // The stream fills the dashboard in anyway.
    }
  }

  private openStream(): void {
    const source = new EventSource('/api/stream');

    source.onopen = () => {
      const recovering = this.connection() === 'reconnecting';
      this.connection.set('live');
      if (recovering) {
        void this.loadSnapshot();
      }
    };
    source.onerror = () => {
      this.connection.set('reconnecting');
      // The browser retries by itself unless the server answered with an error status.
      if (source.readyState === EventSource.CLOSED) {
        source.close();
        setTimeout(() => this.openStream(), RECONNECT_MS);
      }
    };
    // About ten updates a second: buffered and applied in batches so the page renders twice a second.
    source.addEventListener('vehicle', (event) => this.pending.push(JSON.parse((event as MessageEvent).data)));
    source.addEventListener('alert', (event) => this.onAlert(JSON.parse((event as MessageEvent).data)));
  }

  private onAlert(alert: Alert): void {
    this.alerts.update((alerts) => [alert, ...alerts].slice(0, ALERT_LIMIT));
    if (alert.severity === 'CRITICAL') {
      this.liveCritical.set(alert);
    }
  }

  private flush(): void {
    if (this.pending.length === 0) {
      return;
    }
    this.pending.forEach((vehicle) => this.apply(vehicle));
    this.pending = [];
    this.publish();
    this.trailVersion.update((version) => version + 1);
  }

  private apply(vehicle: Vehicle): void {
    const previous = this.latest.get(vehicle.vehicleId);
    // The snapshot can arrive after newer stream updates; never let it overwrite them.
    if (previous && Date.parse(vehicle.lastSeen) < Date.parse(previous.lastSeen)) {
      return;
    }
    this.latest.set(vehicle.vehicleId, vehicle);

    // An OFFLINE event repeats the last reading, so it adds nothing to the trail.
    if (vehicle.status === 'OFFLINE') {
      return;
    }
    const trail = this.trails.get(vehicle.vehicleId) ?? [];
    trail.push({ lat: vehicle.lat, lon: vehicle.lon, speedKmh: vehicle.speedKmh, at: Date.parse(vehicle.lastSeen) });
    if (trail.length > TRAIL_LENGTH) {
      trail.shift();
    }
    this.trails.set(vehicle.vehicleId, trail);
  }

  private publish(): void {
    this.vehicles.set([...this.latest.values()]);
  }
}
