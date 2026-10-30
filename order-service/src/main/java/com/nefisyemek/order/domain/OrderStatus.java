package com.nefisyemek.order.domain;

/**
 * Saga adımları sipariş satırında durur — her servisin DB'sine bakmadan ilerlemeyi görürüz.
 * PENDING: kaydedildi, Kafka komutu henüz çıkmadı (veya CRUD ile elle oluşturuldu).
 * AWAITING_*: ilgili event bekleniyor.
 * CONFIRMED / CANCELLED: terminal.
 */
public enum OrderStatus {
    PENDING,
    AWAITING_PAYMENT,
    AWAITING_RESTAURANT,
    AWAITING_DELIVERY,
    CONFIRMED,
    CANCELLED
}
