# Kafka orchestration — kodda nasıl handle ediliyor?

Lab (Postman + Kafka UI): [`kafka-saga-lab.md`](kafka-saga-lab.md)

Bu dosya “ekranda ne tıklarım?” değil. **Sipariş Ver** deyince Java’nın Kafka’da ne yaptığını, sade dille.

Tek cümle: **order-service yönetmen, diğerleri oyuncu.** Yönetmen “yap” der (komut), oyuncu işi bitirince “oldu” der (event). Yönetmen event’i duyunca bir sonraki “yap”ı atar.

---

## 1) Neden tek `BEGIN/COMMIT` yok?

Sipariş dört servisi ilgilendirir: order, payment, restaurant, delivery. Her birinin **kendi Postgres’i** var. Order, payment tablosuna JOIN atamaz.

Eski dünya: tek veritabanı, tek transaction.  
Burada: dört kasa. Hepsi aynı anda “evet” demezse para çekilmiş restoran reddetmiş olabilir. Çözüm **saga**: adım adım ilerle, biri bozulursa **geri al (compensate)**.

İki tarz var:

| | Orchestration (bizde) | Choreography |
| --- | --- | --- |
| Kim sırayı bilir? | Tek sınıf: `OrderSagaOrchestrator` | Her servis bir sonrakini tetikler |
| Okumak | Kolay: film yönetmeni belli | Dağınık |

Biz orchestration seçtik: sıra tek dosyada.

---

## 2) Komut vs event

Aynı iş iki mesaj tipi:

| | Komut (`*-commands`) | Event (`*-events`) |
| --- | --- | --- |
| Anlam | “Şunu yap” | “Bu oldu” |
| Kim yazar? | Yönetmen (`order-service`) | Oyuncu (payment / restaurant / delivery) |
| Kim okur? | Oyuncu | Yönetmen |

Topic adları `nefis-contracts` içinde sabit:

```8:14:nefis-contracts/src/main/java/com/nefisyemek/contracts/Topics.java
    public static final String PAYMENT_COMMANDS = "payment-commands";
    public static final String PAYMENT_EVENTS = "payment-events";
    public static final String RESTAURANT_COMMANDS = "restaurant-commands";
    public static final String RESTAURANT_EVENTS = "restaurant-events";
    public static final String DELIVERY_COMMANDS = "delivery-commands";
    public static final String DELIVERY_EVENTS = "delivery-events";
```

Şema (JSON şekli) de aynı JAR’da. Bu **tablo paylaşmak değil** — yalnızca mesajın alanları. `orderId` her mesajda var; Kafka **key** de `orderId`. Aynı siparişin tüm kareleri Kafka UI’da aynı key.

Örnek komut:

```7:13:nefis-contracts/src/main/java/com/nefisyemek/contracts/command/ChargePaymentCommand.java
public record ChargePaymentCommand(
        String commandId,
        UUID sagaId,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        Instant createdAt
```

---

## 3) Film nasıl başlar? (HTTP → Kafka)

Dışarıdan hâlâ tek kapı: `POST /api/orders` (gateway `:8080`). Body’de `status` yoksa saga başlar. `status: PENDING` varsa eski CRUD — Kafka yok.

```44:48:order-service/src/main/java/com/nefisyemek/order/service/OrderService.java
        Order saved = orderRepository.save(order);
        // status elle verilmişse bu klasik CRUD; saga yalnızca "Sipariş Ver" (status yok) yolunda başlar.
        if (request.status() == null && saga != null) {
            saga.start(saved);
        }
```

`start` siparişi `AWAITING_PAYMENT` yapar ve komutu **outbox**’a yazar (Kafka henüz yok). HTTP cevabı bu yüzden hemen `AWAITING_PAYMENT` döner; poller ~250 ms içinde `payment-commands`’a taşır.

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

`enqueue` = aynı Postgres transaction’ında `outbox_messages` satırı. Asıl `kafka.send` `OutboxPublisher`’da. Neden: [`kafka-outbox.md`](kafka-outbox.md).

---

## 4) Oyuncu nasıl dinler? (`@KafkaListener`)

Spring, topic’e yeni mesaj gelince metodu çağırır. Payment örneği:

```35:45:payment-service/src/main/java/com/nefisyemek/payment/event/PaymentCommandHandler.java
    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = "${spring.kafka.consumer.group-id}")
    public void onCommand(@Payload Object incoming) {
        Object command = KafkaPayloads.unwrap(incoming);
        log.info("PAYMENT command type={}", command == null ? "null" : command.getClass().getName());
        switch (command) {
            case ChargePaymentCommand charge -> charge(charge);
            case RefundPaymentCommand refund -> refund(refund);
            case null -> log.warn("PAYMENT command null — deserialize başarısız (ErrorHandlingDeserializer)");
            default -> log.warn("PAYMENT ignored command type={}", command.getClass().getName());
        }
    }
```

Üç nokta:

1. **`groupId = payment-service`** — aynı grubun içindeki tüketiciler mesajı paylaşır. Bizde servis başına bir grup: her mesaj o servise bir kez gider.
2. **`@Payload` + `unwrap`** — parametre çıplak `Object` olursa Spring bazen JSON’u değil tüm `ConsumerRecord` kutusunu verir. O zaman `ChargePaymentCommand` eşleşmez, sipariş `AWAITING_PAYMENT`’te kalır.
3. **Aynı topic, iki komut** — çekme ve iade. `switch` tipine bakarak ayırır (type header).

Çekme işi: kendi DB’sine `CAPTURED` yazar, sonra **oldu** event’i atar:

```47:58:payment-service/src/main/java/com/nefisyemek/payment/event/PaymentCommandHandler.java
    private void charge(ChargePaymentCommand command) {
        Payment payment = payments.captureForSaga(command.orderId(), command.amount());
        if (payment.getStatus() == PaymentStatus.FAILED) {
            kafka.send(Topics.PAYMENT_EVENTS, command.orderId().toString(),
                    PaymentFailedEvent.of(command.orderId(), payment.getId(), "INVALID_AMOUNT"));
            // ...
            return;
        }
        kafka.send(Topics.PAYMENT_EVENTS, command.orderId().toString(),
                PaymentChargedEvent.of(command.orderId(), payment.getId(), payment.getAmount()));
```

Payment, restaurant’a “sen de onayla” **demez**. Sadece “parayı çektim” der. Sırayı yönetmen bilir.

---

## 5) Yönetmen event’i nasıl işler?

Orchestrator `payment-events` dinler. “Charged” gelince status’u `AWAITING_RESTAURANT` yapar, restoran komutunu atar:

```131:146:order-service/src/main/java/com/nefisyemek/order/event/OrderSagaOrchestrator.java
    private void onPaymentCharged(PaymentChargedEvent event) {
        orders.findById(event.orderId()).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
                log.info("SAGA skip payment-charged order={} status={}", order.getId(), order.getStatus());
                return;
            }
            order.setStatus(OrderStatus.AWAITING_RESTAURANT);
            orders.save(order);
            enqueue(Topics.RESTAURANT_COMMANDS, order.getId(), ReserveRestaurantCommand.of(
                    order.getId(),
                    order.getRestaurantId(),
                    order.isForceRestaurantReject(),
                    order.isForceRestaurantAccept()
            ));
```

`if (status != AWAITING_PAYMENT) return` — aynı event iki kez gelirse ikinciyi yemez (kaba idempotency). Asıl `processed_events` tablosu Milestone 3.

Aynı kalıp restoran ve kuryede:

| Event | Yeni status | Sonraki komut |
| --- | --- | --- |
| `PaymentChargedEvent` | `AWAITING_RESTAURANT` | `ReserveRestaurantCommand` |
| `RestaurantAcceptedEvent` | `AWAITING_DELIVERY` | `AssignCourierCommand` |
| `CourierAssignedEvent` | `CONFIRMED` | yok — film bitti |

Restoran oyuncusu (kabul / red):

```38:49:restaurant-service/src/main/java/com/nefisyemek/restaurant/event/RestaurantCommandHandler.java
        boolean reject = policy.shouldReject(reserve.forceReject(), reserve.forceAccept());
        if (reject) {
            String reason = policy.reason(reserve.forceReject(), reserve.forceAccept());
            kafka.send(Topics.RESTAURANT_EVENTS, reserve.orderId().toString(),
                    RestaurantRejectedEvent.of(reserve.orderId(), reserve.restaurantId(), reason));
            // ...
            return;
        }
        kafka.send(Topics.RESTAURANT_EVENTS, reserve.orderId().toString(),
                RestaurantAcceptedEvent.of(reserve.orderId(), reserve.restaurantId()));
```

Kurye oyuncusu her zaman atar, “oldu” yazar:

```43:46:delivery-service/src/main/java/com/nefisyemek/delivery/event/DeliveryCommandHandler.java
        String courier = pickCourier(assign.orderId());
        Delivery delivery = deliveries.assignForSaga(assign.orderId(), courier);
        kafka.send(Topics.DELIVERY_EVENTS, assign.orderId().toString(),
                CourierAssignedEvent.of(assign.orderId(), delivery.getId(), delivery.getCourierName()));
```

---

## 6) Mutlu yol — bir bakışta

```
POST /api/orders  (status yok)
        │
        ▼
order DB: AWAITING_PAYMENT  +  outbox ChargePaymentCommand
        │  (poller ~250 ms)
        ▼
kafka → payment-commands
        │
        ▼
payment DB: CAPTURED
kafka.send → payment-events       PaymentChargedEvent   (payment hâlâ doğrudan send)
        │
        ▼
order DB: AWAITING_RESTAURANT  +  outbox ReserveRestaurantCommand
        │
        ▼
kafka → restaurant-commands
        │
        ▼
restaurant: ACCEPT
kafka.send → restaurant-events    RestaurantAcceptedEvent
        │
        ▼
order DB: AWAITING_DELIVERY  +  outbox AssignCourierCommand
        │
        ▼
kafka → delivery-commands
        │
        ▼
delivery DB: kurye satırı
kafka.send → delivery-events      CourierAssignedEvent
        │
        ▼
order DB: CONFIRMED
```

GET `/api/orders/{id}` bu tablodaki **özet**. Asıl kareler Kafka topic’lerinde.

---

## 7) Kırmızı yol — compensate (geri alma)

Restoran reddederse para çekilmişti. Geri almak = **iade komutu**.

```169:172:order-service/src/main/java/com/nefisyemek/order/event/OrderSagaOrchestrator.java
    private void onRestaurantRejected(RestaurantRejectedEvent event) {
        log.warn("SAGA COMPENSATE order={} reason={}", event.orderId(), event.reason());
        cancel(event.orderId(), event.reason() == null ? "RESTAURANT_REJECTED" : event.reason(), true);
    }
```

`cancel(..., refundPayment=true)` siparişi `CANCELLED` yapar ve `RefundPaymentCommand`’ı **outbox**’a koyar (poller `payment-commands`’a taşır). Payment handler `refund` koluna düşer, status `REFUNDED`, `PaymentRefundedEvent` yazar.

Kurye adımına hiç gelinmez — `delivery-*` bu `orderId` ile boş kalır. Bu bug değil.

Ödeme en başta fail olursa (`PaymentFailedEvent`) iade yok: çekilmemiş parayı iade etmezsin.

---

## 8) Takılırsa replay

Komut kuyruğa yazıldı, oyuncu o an okuyamadı → sipariş `AWAITING_*`’te kalır. `POST /api/orders/{id}/saga/replay` **aynı adımın komutunu tekrar yazar**. REST ile payment açmaz.

```70:75:order-service/src/main/java/com/nefisyemek/order/event/OrderSagaOrchestrator.java
    public void replayCurrentStep(Order order) {
        switch (order.getStatus()) {
            case AWAITING_PAYMENT -> {
                enqueue(Topics.PAYMENT_COMMANDS, order.getId(), ChargePaymentCommand.of(
                        order.getId(), order.getCustomerId(), order.getTotalAmount()));
```

Replay de outbox’tan geçer; poller’ı atlamaz.

---

## 9) Outbox, idempotent consumer, CQRS

order-service komutları dual-write değil: `orders` + `outbox_messages` aynı COMMIT. Ayrıntı: [`kafka-outbox.md`](kafka-outbox.md).

Aynı komut iki kez Kafka'ya düşerse payment `processed_events` ile yutulur. Siparişin film şeridi `order_history_view`. Ayrıntı: [`cqrs-idempotency.md`](cqrs-idempotency.md).

Hâlâ eksik:

- payment / restaurant / delivery hâlâ `kafka.send` (onların outbox'ı yok).
- restaurant / delivery idempotent tablo yok.
- Notification saga'ya bağlı değil; SMS hâlâ ayrı REST.

---

## 10) Dosya haritası

| Dosya | Rol |
| --- | --- |
| `OrderService` | HTTP “Sipariş Ver” → `saga.start` |
| `OrderSagaOrchestrator` | Yönetmen: outbox’a komut yazar, event dinler, status + compensate |
| `OutboxEnqueuer` / `OutboxPublisher` | Aynı TX’te satır; poller Kafka’ya taşır |
| `IdempotentConsumer` | payment: aynı commandId ikinci kez işlenmez |
| `OrderHistoryRecorder` | CQRS: `order_history_view` film şeridi |
| `PaymentCommandHandler` | `payment-commands` → DB → `payment-events` |
| `RestaurantCommandHandler` | kabul / red event |
| `DeliveryCommandHandler` | kurye atar, event |
| `nefis-contracts` | topic adları + JSON record’lar |
| `KafkaPayloads` | `ConsumerRecord` kutusundan asıl JSON’u çıkar |

Okuma sırası: bu dosya → kodda `OrderSagaOrchestrator` → lab’de Senaryo A.
