import { Component, DestroyRef, ElementRef, afterNextRender, effect, inject, viewChild } from '@angular/core';
import * as L from 'leaflet';
import { FleetStore } from '../core/fleet-store';
import { Vehicle } from '../core/models';

interface MarkerEntry {
  marker: L.Marker;
  el: HTMLElement;
}

@Component({
  selector: 'app-fleet-map',
  templateUrl: './fleet-map.html',
  styleUrl: './fleet-map.css',
})
export class FleetMap {
  private readonly store = inject(FleetStore);
  private readonly host = viewChild.required<ElementRef<HTMLDivElement>>('map');
  // inject() only works while the component is being built, not later inside afterNextRender.
  private readonly destroyRef = inject(DestroyRef);

  private map?: L.Map;
  private trail?: L.Polyline;
  private readonly markers = new Map<string, MarkerEntry>();
  private focused: string | null = null;

  constructor() {
    afterNextRender(() => this.createMap());

    effect(() => {
      const vehicles = this.store.vehicles();
      const selectedId = this.store.selectedId();
      if (this.map) {
        this.sync(vehicles, selectedId);
      }
    });

    effect(() => {
      const trail = this.store.selectedTrail();
      this.trail?.setLatLngs(trail.map((point) => [point.lat, point.lon] as L.LatLngTuple));
    });
  }

  private createMap(): void {
    const element = this.host().nativeElement;
    // zoomSnap 0.25 lets fitBounds pick a zoom between the whole levels, so Tunisia fills the card.
    const map = L.map(element, { center: [34.2, 9.6], zoom: 6, minZoom: 5, zoomSnap: 0.25 });
    this.map = map;

    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 18,
    }).addTo(map);

    // The allowed area, the same polygon the processing service checks positions against.
    fetch('/tunisia.geojson')
      .then((response) => response.json())
      .then((geometry) => {
        const outline = L.geoJSON(geometry, {
          interactive: false,
          style: { color: '#2563eb', weight: 2, dashArray: '6 6', fillColor: '#2563eb', fillOpacity: 0.04 },
        }).addTo(map);
        map.fitBounds(outline.getBounds(), { padding: [16, 16] });
      })
      .catch(() => {
        // The map works without the outline.
      });

    this.trail = L.polyline([], { color: '#2563eb', weight: 3, opacity: 0.8 }).addTo(map);

    // The card's size is decided by CSS after the map is created.
    const observer = new ResizeObserver(() => map.invalidateSize());
    observer.observe(element);
    this.destroyRef.onDestroy(() => {
      observer.disconnect();
      map.remove();
    });

    this.sync(this.store.vehicles(), this.store.selectedId());
  }

  private sync(vehicles: Vehicle[], selectedId: string | null): void {
    for (const vehicle of vehicles) {
      const entry = this.markers.get(vehicle.vehicleId) ?? this.createMarker(vehicle);
      entry.marker.setLatLng([vehicle.lat, vehicle.lon]);
      const selected = vehicle.vehicleId === selectedId;
      entry.el.className = `fleet-marker ${vehicle.status}${selected ? ' selected' : ''}`;
      entry.marker.setZIndexOffset(selected ? 1000 : 0);
    }

    // Move the view only when the selection changes, not on every update of the selected vehicle.
    if (selectedId !== this.focused) {
      this.focused = selectedId;
      const target = vehicles.find((vehicle) => vehicle.vehicleId === selectedId);
      if (target && this.map) {
        this.map.flyTo([target.lat, target.lon], Math.max(this.map.getZoom(), 8), { duration: 0.8 });
      }
    }
  }

  private createMarker(vehicle: Vehicle): MarkerEntry {
    // Built with DOM calls, not an HTML string, so a vehicle ID can never inject markup.
    const el = document.createElement('div');
    const dot = document.createElement('span');
    dot.className = 'dot';
    const tag = document.createElement('span');
    tag.className = 'tag';
    tag.textContent = vehicle.vehicleId;
    el.append(dot, tag);

    const marker = L.marker([vehicle.lat, vehicle.lon], {
      icon: L.divIcon({ html: el, className: '', iconSize: [0, 0] }),
      title: vehicle.vehicleId,
    }).addTo(this.map!);
    marker.on('click', () => this.store.select(vehicle.vehicleId));

    const entry = { marker, el };
    this.markers.set(vehicle.vehicleId, entry);
    return entry;
  }
}
