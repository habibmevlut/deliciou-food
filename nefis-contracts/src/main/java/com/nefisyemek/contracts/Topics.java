package com.nefisyemek.contracts;

/**
 * Kafka topic adları. Öğrenme için kısa ve iş yeteneğine göre bölünmüş:
 * komut (yap) ve event (oldu) ayrı topic'lerde — orchestration-based saga bunu okumayı kolaylaştırır.
 */
public final class Topics {

    public static final String PAYMENT_COMMANDS = "payment-commands";
    public static final String PAYMENT_EVENTS = "payment-events";
    public static final String RESTAURANT_COMMANDS = "restaurant-commands";
    public static final String RESTAURANT_EVENTS = "restaurant-events";
    public static final String DELIVERY_COMMANDS = "delivery-commands";
    public static final String DELIVERY_EVENTS = "delivery-events";

    private Topics() {
    }
}
