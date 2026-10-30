package com.nefisyemek.payment.domain;

/**
 * PENDING: tahsilat henüz denenmedi.
 * CAPTURED: para çekildi (Milestone 2 saga mutlu yolu).
 * REFUNDED: compensating transaction (iade).
 * FAILED: tahsilat reddedildi.
 */
public enum PaymentStatus {
    PENDING,
    CAPTURED,
    REFUNDED,
    FAILED
}
