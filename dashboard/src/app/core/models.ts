export type Status = 'OK' | 'SPEEDING' | 'OUTSIDE_TUNISIA' | 'OFFLINE';
export type Severity = 'INFO' | 'WARNING' | 'CRITICAL';
export type AlertType = 'SPEEDING' | 'LEFT_TUNISIA' | 'RETURNED_TO_TUNISIA';
export type Connection = 'connecting' | 'live' | 'reconnecting';

export interface Vehicle {
  vehicleId: string;
  lat: number;
  lon: number;
  speedKmh: number;
  status: Status;
  lastSeen: string;
}

export interface Alert {
  alertId: string;
  vehicleId: string;
  alertType: AlertType;
  severity: Severity;
  message: string;
  speedKmh: number;
  lat: number;
  lon: number;
  timestamp: string;
}

export interface TrailPoint {
  lat: number;
  lon: number;
  speedKmh: number;
  at: number;
}

// Same value as fleet.rules.speed-limit-kmh in processing-service; only used to draw the limit line.
export const SPEED_LIMIT_KMH = 110;
export const MAX_SPEED_KMH = 140;

export const STATUS_LABEL: Record<Status, string> = {
  OK: 'Normal',
  SPEEDING: 'Speeding',
  OUTSIDE_TUNISIA: 'Outside Tunisia',
  OFFLINE: 'Offline',
};

export const ALERT_LABEL: Record<AlertType, string> = {
  SPEEDING: 'Speeding',
  LEFT_TUNISIA: 'Left Tunisia',
  RETURNED_TO_TUNISIA: 'Back in Tunisia',
};
