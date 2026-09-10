# Transactional Outbox — sipariş neden önce Postgres’e yazar?

Lab (Postman + GET outbox): [`kafka-saga-lab.md`](kafka-saga-lab.md) Senaryo E.  
Saga sırası: [`kafka-orchestration.md`](kafka-orchestration.md).

Tek cümle: **order-service Kafka’ya “hemen” yazmaz.** Aynı `BEGIN/COMMIT` içinde `orders` + `outbox_messages` dolar. Process o anda ölürse komut kaybolmaz; satır tabloda kalır. Poller COMMIT’ten sonra Kafka’ya taşır.

---

## 1) Dual-write nedir, neden kırılır?

Milestone 2’de yönetmen şöyleydi:

```
orders.save(AWAITING_PAYMENT);
kafka.send(payment-commands, ChargePaymentCommand);
```

İkisi **atomik değil**. Postgres COMMIT oldu, `kafka.send` henüz broker’a ulaşmadan JVM öldü → sipariş `AWAITING_PAYMENT`, kuyruk boş. Replay ile toparlanırdın; production’da unutulan sipariş olur.

Outbox bunu tersine çevirir: Kafka, iş transaction’ının parçası değildir. Parça olan şey **outbox satırı**.

```
BEGIN
  UPDATE orders ...
  INSERT INTO outbox_messages (...)
COMMIT          ← burası atomik
        │
        ▼  (ayrı, sonra)
poller: Kafka send + published_at
```

---

## 2) Kim ne yazar? (sadece order-service)

Brief: **order-service outbox tablosu + polling publisher.**

Payment / restaurant / delivery hâlâ `kafka.send` ile event üretir. Onların dual-write’ı bu milestone’da duruyor; dersi tek yerde tutmak için. İleride aynı tabloyu oraya da kopyalarsın.

GET `/api/orders/outbox` bu yüzden gateway’den `order-service`’e gider (`/api/orders/**`). Payment DB’sinde outbox yok.

---

## 3) Enqueue — aynı transaction

`OrderSagaOrchestrator` `KafkaTemplate` tutmaz. Komut `OutboxEnqueuer`’a gider:

```52:63:order-service/src/main/java/com/nefisyemek/order/event/OrderSagaOrchestrator.java
    @Transactional
    public void start(Order order) {
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setCancelReason(null);
        orders.save(order);

        enqueue(Topics.PAYMENT_COMMANDS, order.getId(), ChargePaymentCommand.of(
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount()
        ));
        log.info("SAGA start order={} status=AWAITING_PAYMENT outbox -> {}", order.getId(), Topics.PAYMENT_COMMANDS);
```

`@Transactional` hem `orders.save` hem `outbox.save` için. Listener’lar (`onPaymentCharged` …) da aynı: status değişir **ve** sonraki komut outbox’a yazılır; COMMIT’ten önce Kafka yok.

JSON + tip adı satırda durur. Poller class adından record’u geri okur; Kafka `JsonSerializer` type header’ı o yüzden hâlâ doğru çıkar.

Replay de outbox’a yazar (`kafka.send` bypass yok). Aksi halde “takılı komutu düzelt” yolu eski dual-write’a dönerdi.

---

## 4) Publisher — poll + SKIP LOCKED

`@EnableScheduling` + her ~250 ms:

1. `SELECT … WHERE published_at IS NULL … FOR UPDATE SKIP LOCKED`
2. JSON → komut nesnesi
3. `kafka.send(topic, key, payload).get(5s)` — broker ACK bekle
4. `published_at = now()`

`SKIP LOCKED`: ikinci bir order-service instance aynı satırı çekmez. Lab’de tek process; kilit cümlesi yine de pattern’in parçası.

```48:57:order-service/src/main/java/com/nefisyemek/order/event/OutboxPublisher.java
    @Scheduled(fixedDelayString = "${nefis.outbox.poll-ms:250}")
    @Transactional
    public void publishBatch() {
        List<OutboxMessage> batch = outbox.lockUnpublished(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxMessage message : batch) {
            publishOne(message);
        }
```

Log sırası (mutlu yol, charge adımı):

```
SAGA start ... outbox -> payment-commands
OUTBOX enqueue topic=payment-commands ...
OUTBOX published id=... topic=payment-commands
PAYMENT CAPTURED ...
```

İlk iki satır HTTP request’inin transaction’ı. Üçüncü satır poller. Arada en fazla `poll-ms` kadar boşluk normal.

---

## 5) Postman’da nasıl görürsün?

`GET /api/orders/outbox` — son 50 satır (yeniler üstte).  
`GET /api/orders/outbox?unpublishedOnly=true` — henüz Kafka’ya gitmemişler.

Sipariş Ver’den **hemen** sonra (poller 250 ms): `publishedAt: null` görebilirsin. 1 sn sonra aynı `id` dolu. Topic = `payment-commands` / `restaurant-commands` / `delivery-commands`; `messageKey` = `orderId`.

Kafka UI hâlâ asıl kuyruk. Outbox “yola çıkmadan önceki kasa defteri”.

---

## 6) Bilerek kalan delik

Kafka ACK geldi, `published_at` COMMIT edilmeden process öldü → poller aynı satırı **bir daha** gönderir. Çift `ChargePaymentCommand` mümkün. Bunu kapatan şey outbox değil; **idempotent consumer** (Milestone 3, `processed_events`).

Payment/restaurant/delivery dual-write’ı duruyor: onlar hâlâ DB + `kafka.send`.
