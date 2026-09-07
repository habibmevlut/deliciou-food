package com.nefisyemek.order.domain;

/**
 * Milestone 0'da sipariş yaşam döngüsü henüz Saga ile bağlanmadı.
 * PENDING: oluşturuldu, henüz ödeme/restoran onayı yok.
 * CONFIRMED / CANCELLED: Milestone 2'de orchestrator dolduracak.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
