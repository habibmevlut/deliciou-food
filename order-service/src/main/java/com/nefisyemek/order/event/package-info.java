/**
 * OrderSagaOrchestrator burada yaşar. Komut/event sınıfları nefis-contracts'ta;
 * bu paket orkestrasyonu (adım sırası + compensate) ve order-service outbox'ını tutar.
 * Kafka send dual-write değil: OutboxEnqueuer aynı TX'te satır atar, OutboxPublisher poller yayınlar.
 */
package com.nefisyemek.order.event;
