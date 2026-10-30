# Nefis Yemek — uçtan uca hikâye

Bu dosya “hangi port?” değil. **Ali sipariş verdi** deyince sistemde ne oldu, sen ne öğrendin.

Tıklama lab’leri ayrı: saga [`kafka-saga-lab.md`](kafka-saga-lab.md), JWT/sigorta/sayaç [`m4-kapı-sigorta-sayaç.md`](m4-kapı-sigorta-sayaç.md), test [`test-piramidi.md`](test-piramidi.md).

---

## Sahne

Kurgusal bir yemek uygulaması. Amaç production değil; mikroservis pattern’lerini **gerçek Java** ile görmek.

Dış dünya tek kapı: `http://localhost:8080`. İçeride altı iş servisi, altı Postgres, bir Kafka.

```
Sen (Postman) ──► api-gateway :8080
                    │
        customer  restaurant  order  payment  delivery  notification
           PG        PG        PG      PG        PG          PG
                              │
                           Kafka
```

**Öğrendiğin ilk kural:** bir servis diğerinin tablosuna JOIN atamaz. Buna **Database per Service** denir. Tek `BEGIN/COMMIT` yok; “Sipariş Ver” dört kasayı ilgilendirir.

---

## 1) İskelet ve tek kapı (M0–M1)

Önce her servis kendi evini kurdu: Flyway şema üretir, Hibernate yalnızca `validate` eder. REST katmanı hep aynı: controller → service → repository → domain.

Sonra **gateway**. Neden: altı URL ezberletmemek, ileride JWT’yi tek yerde tutmak.

```23:31:api-gateway/src/main/resources/application.yml
        - id: auth
          uri: ${CUSTOMER_SERVICE_URI:http://localhost:8084}
          predicates:
            - Path=/api/auth/**
          filters:
            - name: CircuitBreaker
              args:
                name: customerService
                fallbackUri: forward:/fallback/customer
```

Kapı JSON’u yorumlamaz. Path’e bakar, isteği olduğu gibi iletir. Health biletsiz kalır: `GET :8080/actuator/health`.

**Öğrendin:** gateway bir iş servisi değil; ters vekil + tek kapı. WebFlux/Netty; içeride Tomcat yok.

---

## 2) Ali “Sipariş Ver” der — saga (M2)

Ali müşteri ve restoran oluşturdu. Postman klasör **8**. Body’de `status` yok — bu CRUD değil, film başlar.

```54:60:order-service/src/main/java/com/nefisyemek/order/service/OrderService.java
        Order saved = orderRepository.save(order);
        history.record(saved.getId(), OrderHistoryStep.ORDER_CREATED, status.name(),
                "order-created:" + saved.getId());
        // status elle verilmişse bu klasik CRUD; saga yalnızca "Sipariş Ver" (status yok) yolunda başlar.
        if (request.status() == null && saga != null) {
            saga.start(saved);
        }
```

`OrderSagaOrchestrator` **yönetmen**. Oyuncular (payment, restaurant, delivery) birbirini aramaz.

```56:68:order-service/src/main/java/com/nefisyemek/order/event/OrderSagaOrchestrator.java
    public void start(Order order) {
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setCancelReason(null);
        orders.save(order);
        history.record(order.getId(), OrderHistoryStep.SAGA_STARTED, "AWAITING_PAYMENT",
                "saga-started:" + order.getId());

        enqueue(Topics.PAYMENT_COMMANDS, order.getId(), ChargePaymentCommand.of(
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount()
        ));
```

Komut = “yap”. Event = “oldu”. Topic’ler `nefis-contracts` JAR’ında; bu tablo paylaşmak değil, mesajın şekli.

```
öde ──► restoran ayır ──► kurye ata ──► CONFIRMED
              │
              └── restoran red ──► parayı iade ──► CANCELLED
```

`forceRestaurantReject: true` compensate’i tetikler: restoran reddeder, yönetmen `RefundPaymentCommand` yazar.

**Öğrendin:** 2PC (tek büyük transaction) burada yok. Saga adım adım ilerler, bozulursa **geri alır**. Orchestration = sıra tek sınıfta. Choreography = herkes bir sonrakini tetikler; M2’de yok.

---

## 3) Aynı deftere yaz, sonra kuyruğa koy — outbox (M2.5)

İlk hali tehlikeliydi: siparişi Postgres’e yaz, hemen Kafka’ya gönder. Process ikisinin arasında ölürse para hiç çekilmez.

Çözüm: yönetmen **aynı transaction**’da `orders` + `outbox_messages` yazar. Kafka henüz yok.

```28:37:order-service/src/main/java/com/nefisyemek/order/event/OutboxEnqueuer.java
    public void enqueue(String topic, String messageKey, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox JSON yazılamadı: " + payload.getClass().getName(), e);
        }
        OutboxMessage row = new OutboxMessage(topic, messageKey, payload.getClass().getName(), json);
        outbox.save(row);
```

`OutboxPublisher` COMMIT’ten sonra satırı Kafka’ya taşır. Process send’den önce ölürse komut tabloda kalır.

Postman: `GET /api/orders/outbox`. Kafka UI (`:8090`) kuyruktaki **mektubu** gösterir.

**Öğrendin:** dual-write (DB + kuyruk ayrı ayrı) kaybettirir. Outbox = önce defter, sonra kurye.

---

## 4) Aynı mektup iki kez gelirse — inbox + film şeridi (M3)

Outbox poller aynı komutu bir daha gönderebilir (Kafka ACK oldu, `published_at` yazılmadan öldü). Payment **iki kez tahsil etmemeli**.

```25:37:payment-service/src/main/java/com/nefisyemek/payment/event/IdempotentConsumer.java
    public boolean claim(String eventId, String eventType, UUID orderId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        if (processed.existsById(eventId)) {
            return false;
        }
        try {
            processed.saveAndFlush(new ProcessedEvent(eventId, eventType, orderId));
            return true;
```

Handler önce `claim` der. `false` ise çıkış — ikinci `ChargePaymentCommand` yutulur.

```55:61:payment-service/src/main/java/com/nefisyemek/payment/event/PaymentCommandHandler.java
    private void charge(ChargePaymentCommand command) {
        if (!inbox.claim(command.commandId(), "ChargePaymentCommand", command.orderId())) {
            log.info("PAYMENT idempotent skip charge commandId={} order={}",
                    command.commandId(), command.orderId());
            return;
        }
```

Aynı anda `orders` “şu an neredeyim” (`AWAITING_PAYMENT` / `CONFIRMED`). `order_history_view` “nasıl geldim” — film şeridi. Buna **CQRS** denir: yazma modeli ≠ okuma modeli.

```27:34:order-service/src/main/java/com/nefisyemek/order/event/OrderHistoryRecorder.java
    public void record(UUID orderId, OrderHistoryStep step, String detail, String eventId) {
        String id = eventId == null || eventId.isBlank() ? UUID.randomUUID().toString() : eventId;
        if (history.existsByEventId(id)) {
            log.info("HISTORY skip duplicate eventId={} step={}", id, step);
            return;
        }
        history.save(new OrderHistoryEntry(orderId, id, step, detail));
```

Postman: `GET /api/orders/{id}/history`, `GET /api/payments/processed-events`.

**Öğrendin:** at-least-once teslimat gerçeği. Idempotent consumer = “bu `commandId`’yi yedim”. CQRS = anlık durum ayrı, hikâye ayrı.

---

## 5) Kapıda bilet, sigorta, bakmak (M4)

Eskiden `GET /api/orders` herkese açıktı. Şimdi Ali önce müşteri olur, sonra login. Şifre yok; laboratuvar e-posta ile tanır. Cevaptaki `accessToken` imzalı bilet (**JWT**).

```35:43:customer-service/src/main/java/com/nefisyemek/customer/security/JwtIssuer.java
    public String issue(UUID customerId, String email) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(customerId.toString())
                .claim("email", email)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlHours * 3600)))
                .build();
```

Bileti **yalnızca gateway** kontrol eder. order-service JWT bilmez.

```37:41:api-gateway/src/main/java/com/nefisyemek/gateway/GatewaySecurityConfig.java
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**", "/fallback/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/customers").permitAll()
                        .anyExchange().authenticated())
```

Restaurant-service düşünce kapı 30 saniye bekletmez. Peş peşe hata → **circuit breaker** 503.

```19:27:api-gateway/src/main/java/com/nefisyemek/gateway/FallbackController.java
    public Mono<ResponseEntity<Map<String, String>>> fallback(@PathVariable String service) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "title", "Service unavailable",
                        "service", service,
                        "detail", "Sigorta atmış (circuit breaker). Servis ayakta değil veya peş peşe hata verdi."
                )));
    }
```

GET’de retry var, POST’ta yok (siparişi iki kez yazmasın).

Sonra **bakmak** — saga kodu değişmez:

| Soru | Araç |
| --- | --- |
| Kuyrukta mektup var mı? | Kafka UI `:8090` |
| Servis ayakta mı, kaç istek? | Prometheus `:9090` sayar, Grafana `:3000` çizer |
| **Bu** istek hangi servislerden geçti? | Zipkin `:9411` (`traceId` log’da da var) |

**Öğrendin:** kimlik kapıda (altı Security kopyası yok). Sigorta komşu servisi korur. Üç gözlem aleti üç soru sorar; Kafka UI dördüncü (kuyruk).

---

## 6) “Bozuldu mu?” otomatik — test piramidi (M5)

Postman Ali’nin eli. `./mvnw test` her commit’in eli.

```
            /\
           /  \      az, yavaş: Testcontainers (gerçek Postgres / Kafka)
          /----\
         /      \    WireMock: “restoran JSON’u böyle durur”
        /--------\
       /   unit   \  çok, hızlı: Mockito
```

- Unit: `OrderServiceTest` saga’yı mock’lar — Docker yok.
- `GatewayRestaurantContractIT`: restaurant-service yok; WireMock sözleşme JSON’u verir, kapı JWT ile geçirir.
- `OrderOutboxIT`: gerçek Postgres’te Sipariş Ver → `outbox_messages` satırı.
- `PaymentIdempotencyIT`: aynı komut Kafka’ya iki kez → tek tahsilat.

Docker yoksa `*IT` atlanır; unit + WireMock yine koşar.

**Öğrendin:** mock ucuzdur, Flyway kolonunu görmez. Testcontainers yavaştır, SQL’i görür. WireMock altı servisi her testte açtırmaz.

---

## Ali’nin bir mutlu siparişi (özet kare)

1. `POST /api/customers` — bilet yok.
2. `POST /api/auth/login` — JWT gelir.
3. Restoran oluştur — `Authorization: Bearer`.
4. `POST /api/orders` (`forceRestaurantAccept: true`) — kapı JWT’yi görür, order-service’e iletir.
5. Aynı TX: `orders` = `AWAITING_PAYMENT`, `outbox_messages` = `ChargePaymentCommand`.
6. Poller Kafka `payment-commands`.
7. Payment `claim` + tahsilat + `PaymentChargedEvent`.
8. Yönetmen restoran komutu, restoran kabul, kurye, `CONFIRMED`.
9. History: `ORDER_CREATED` → `SAGA_STARTED` → `PAYMENT_CHARGED` → … → kurye.
10. Grafana çizgi, Zipkin’de kutu kutu `api-gateway` → `order-service` (ve saga servisleri).

Red senaryosu 4. adımda `forceRestaurantReject: true`: restoran red, iade, `CANCELLED`.

---

## Ne öğrendik — tek bakışta

| Konu | Tek cümle | Kodun evinde |
| --- | --- | --- |
| Database per Service | Her işin kendi Postgres’i | `docker-compose.yml` 6 DB |
| API Gateway | Tek kapı, iş kuralı yok | `api-gateway` route YAML |
| Orchestration saga | Yönetmen sıra bilir | `OrderSagaOrchestrator` |
| Compensate | Red → iade → iptal | `forceRestaurantReject` |
| Transactional outbox | Önce defter, sonra Kafka | `OutboxEnqueuer` |
| At-least-once + inbox | Aynı `commandId` ikinci kez yeme | `IdempotentConsumer` |
| CQRS | Şu anki status ≠ film şeridi | `orders` vs `order_history_view` |
| JWT kapıda | Bilet bir yerde | `JwtIssuer` + `GatewaySecurityConfig` |
| Circuit breaker | Komşu ölünce 503 | `FallbackController` |
| Observability | Sayı / pano / bir isteğin filmi | Prometheus, Grafana, Zipkin |
| Test piramidi | Ucuz unit, gerçek IT, sahte sözleşme | `*Test`, `*IT`, WireMock |

---

## Elle nasıl yeniden izlersin

```bash
git pull
docker compose up -d
# IntelliJ: Nefis Yemek: tum servisler
# Postman: Import → Replace
```

Create customer → **Login** → klasör **8** mutlu yol. Kafka UI `:8090`, Grafana `:3000`, Zipkin `:9411`.

Otomatik: `./mvnw test`.
