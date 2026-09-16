/**
 * payment-commands tüketicisi + idempotent inbox (processed_events).
 * REST CRUD hâlâ durur; saga bu handler üzerinden tahsilat/iade yapar.
 * Aynı commandId ikinci kez gelirse tahsilat ve event tekrarlanmaz.
 */
package com.nefisyemek.payment.event;
