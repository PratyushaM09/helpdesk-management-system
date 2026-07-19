# ADR-0007: Event-Driven Notification Abstraction (Observer Pattern)

**Status:** Accepted
**Date:** 2026-07-19

## Context

FR-NOTIF-3 explicitly requires the notification system to be built behind an abstraction that allows adding email delivery later *without changing notification-triggering logic*. Assumption A7 confirms in-app-only delivery for this phase. SRS §15 anticipates Kafka/RabbitMQ for asynchronous event processing including notification dispatch.

## Decision

Ticket/comment services never call a `NotificationService` directly inline with business logic. Instead, they publish a domain event (e.g., `TicketAssignedEvent`, `TicketStatusChangedEvent`, `CommentAddedEvent`) via Spring's `ApplicationEventPublisher` at the end of the transaction (`@TransactionalEventListener(phase = AFTER_COMMIT)` on the consumer side, so a notification is never sent for a change that then rolls back).

One or more `@EventListener` observers consume each event:

- `InAppNotificationListener` (this phase): writes a `Notification` row and marks it unread — satisfies FR-NOTIF-1/2.
- `EmailNotificationListener` (future phase, SRS §15): subscribes to the same event types, sends email — added with zero change to the seven trigger points in the Ticket/Comment services, satisfying FR-NOTIF-3 exactly.

This is the **Observer pattern**, implemented via Spring's in-process event bus for now. Because publisher and listener are already decoupled through an event object (not a direct method call), swapping the in-process event bus for a Kafka/RabbitMQ topic later (SRS §15) is a matter of changing the listener's transport binding, not the business logic that raises the event — see [02-Architecture.md](../02-Architecture.md#event-package).

## Consequences

- **Positive:** Directly satisfies FR-NOTIF-3's explicit non-functional constraint; zero coupling between "a ticket was assigned" (business fact) and "how many/which channels get notified about it" (delivery concern).
- **Positive:** New notification channels (SMS, push, Slack webhook) are new listeners, not modifications to existing services — Open/Closed Principle in practice.
- **Negative:** In-process events are lost if the application crashes between commit and listener execution; acceptable for in-app notifications at this phase (a missed in-app notification is low-severity and the underlying ticket data is unaffected), and explicitly the reason a durable broker (Kafka/RabbitMQ) is the named upgrade path once delivery guarantees matter more (e.g., email, SRS §15).
- **Alternatives considered:**
  - *Direct synchronous call from `TicketService` to `NotificationService`* — rejected: violates FR-NOTIF-3's explicit abstraction requirement and tightly couples ticket business logic to notification delivery mechanics.
  - *Adopting Kafka now* — rejected as premature infrastructure for C2's scale; the Observer abstraction is deliberately chosen so this is a later infrastructure swap, not a redesign.
