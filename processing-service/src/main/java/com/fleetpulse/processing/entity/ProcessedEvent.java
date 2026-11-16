package com.fleetpulse.processing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

// Tracks every successfully processed event by its ID.
// The PK constraint on event_id enforces idempotency: a duplicate INSERT
// throws DataIntegrityViolationException, which the listener catches to skip reprocessing.
// Redis is a valid alternative here — faster, but adds another infrastructure dependency.
//
// Why Persistable<String>:
//   Spring Data's save() checks isNew() to decide between persist() and merge().
//   persist() → always INSERT.
//   merge()   → SELECT first, then UPDATE if found or INSERT if not (silent upsert).
//   When an entity already has its @Id set, Spring Data defaults isNew() to false → merge() path.
//   Since we always provide the eventId, save() would silently upsert and never trigger the
//   unique constraint. Implementing Persistable and returning true from isNew() forces
//   save() to always call persist() (INSERT), so the constraint fires on duplicates.
@Entity
@Table(name = "processed_events")
public class ProcessedEvent implements Persistable<String> {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    public ProcessedEvent() {}

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
    }

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        // Always true: this entity is insert-only, never updated after creation.
        return true;
    }
}
