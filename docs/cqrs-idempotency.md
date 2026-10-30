# Idempotent consumer + CQRS — aynı mektup iki kez gelirse?

Lab: [`kafka-saga-lab.md`](kafka-saga-lab.md) Senaryo F.  
Outbox neden çift gönderebilir: [`kafka-outbox.md`](kafka-outbox.md) §6.

İki ayrı ders, aynı milestone. Karıştırmamak için: biri **payment-service defteri**, biri **order-service film şeridi**.

---

## 1) Idempotent ne demek? (payment)

Tek cümle: **aynı komutu ikinci kez yersem, ikinci kez para çekmem.**

Outbox “kaybolmasın” der. En az bir kez teslim eder. Kafka ACK oldu, `published_at` yazılmadan JVM öldü → poller **aynı JSON’u** (aynı `commandId`) bir daha koyar.

Eski payment handler her mesajda yeni iş yapardı. M3’te önce deftere bakar:

```
Bu commandId’yi daha önce işledim mi?
  evet → log: PAYMENT idempotent skip. Kafka event yok. Çık.
  hayır → processed_events’e yaz + tahsilat + PaymentChargedEvent
```

Tablo `processed_events` (brief adı). Satırın anahtarı `event_id` = komutun `commandId`’si.

```56:62:payment-service/src/main/java/com/nefisyemek/payment/event/PaymentCommandHandler.java
        if (!inbox.claim(command.commandId(), "ChargePaymentCommand", command.orderId())) {
            log.info("PAYMENT idempotent skip charge commandId={} order={}",
                    command.commandId(), command.orderId());
            return;
        }
        Payment payment = payments.captureForSaga(command.orderId(), command.amount());
```

`claim` aynı transaction’da `INSERT`. İkinci kopya PK’ye çarpar, `false` döner.

Postman: `GET /api/payments/processed-events` — son 50 komut id’si.

Replay (`POST .../saga/replay`) **yeni** `commandId` üretir (kayıp mektubu yeniden yazarsın). processed_events onu yeni iş sayar; aynı siparişe ikinci `payments` satırı açılmaz (`captureForSaga` orderId’ye bakar). Çift **aynı kâğıt** (outbox) ile çift **yeni kâğıt** (replay) farklı şeyler.

---

## 2) CQRS ne demek? (order)

Tek cümle: **yazı defteri ile okuma defteri ayrı.**

| | Yazma (`orders`) | Okuma (`order_history_view`) |
| --- | --- | --- |
| Soru | Sipariş **şimdi** ne? | Bu siparişe **neler oldu?** |
| Satır | 1 | çok |
| Kim yazar? | orchestrator status değiştirirken | aynı anda `OrderHistoryRecorder` |

GET `/api/orders/{id}` → tek satır, `CONFIRMED`.  
GET `/api/orders/{id}/history` → `ORDER_CREATED` → `SAGA_STARTED` → `PAYMENT_CHARGED` → `RESTAURANT_ACCEPTED` → `COURIER_ASSIGNED`.

Bu “iki mikroservis” değil. Aynı order-service, iki tablo. Öğrenme modeli: komut tarafı status, sorgu tarafı film.

Aynı `eventId` ikinci kez gelirse history satırı **eklenmez** (okuma modeli de idempotent).

---

## 3) Postman’da 30 saniye

1. Klasör 8 → Sipariş Ver (mutlu yol). CONFIRMED olana kadar Get order.
2. **Get order history** → beş (veya altı) adım, eskiden yeniye.
3. **List processed events** → `ChargePaymentCommand`, `eventId` = commandId, `orderId` senin sipariş.

Restoran red yolunda history’de `RESTAURANT_REJECTED` + sonra `PAYMENT_REFUNDED`; processed-events’te charge **ve** refund satırı.

---

## 4) Bilerek eksik

Restaurant / delivery `processed_events` yok — brief yalnız payment.  
History, Kafka’dan ayrı bir consumer group değil; orchestrator yazıyor (GET order ile aynı anda dolsun diye).  
Payment hâlâ dual-write: DB + `kafka.send`.
