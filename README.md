# Nefis Yemek

Kurgusal bir yemek sipariş platformu. Amaç production'a çıkmak değil; mikroservis pattern'lerini (Saga, CQRS, Outbox, Circuit Breaker, API Gateway, Distributed Tracing) gerçek kodla denemek.

Milestone'lar **tek branch'te** birikir. README her zaman o ana kadar yapılanın tam halidir; görmek için branch değiştirmen gerekmez.

Şu an **Milestone 3**: Saga + Outbox + **idempotent consumer** (payment) + **CQRS read model** (order history). Dış dünya hâlâ yalnızca **:8080** üzerinden konuşur.

## Gateway nedir, burada hangisi kullanıldı?

Gateway bir **ters vekil (reverse proxy)** + **tek kapı**. İstemci altı porta dağılmaz; `http://localhost:8080/api/...` der, gateway path'e bakıp isteği doğru servise iletir (method, header, body aynı kalır). JSON'u yorumlamaz, sipariş iş kuralı çalıştırmaz, Postgres'e dokunmaz.

Neden var:

- İstemciye 6 URL ezberletmemek
- Milestone 4'te JWT'yi **tek yerde** doğrulamak (her serviste Security filter kopyalamamak)
- İçerideki port/host değişince yalnızca gateway config'i değişir

Bu repoda kullanılan kütüphane: **`spring-cloud-starter-gateway`** — yani **Spring Cloud Gateway (WebFlux / Netty)**.

Kullanılmayanlar (bilerek):

| Alternatif | Neden değil |
| --- | --- |
| Netflix Zuul | Eski nesil; Spring Cloud onu Gateway ile değiştirdi |
| `spring-cloud-starter-gateway-server-webmvc` | Servlet/Tomcat tabanlı yeni MVC gateway. Biz reaktif olanı seçtik |
| nginx / Envoy | Harika prod proxy'ler; bu laboratuvarda route + ileride JWT Java kodunda yaşasın diye Spring'i seçtik |
| `spring-boot-starter-web` gateway içinde | Tomcat + Netty çakışır. Gateway **yalnızca WebFlux** |

Nasıl çalışıyor: `application.yml` içinde `Path=/api/orders/**` → `localhost:8081` (IDE) veya `http://order-service:8081` (Docker). **Prefix strip yok** — backend zaten `/api/orders` dinliyor. Health: `GET :8080/actuator/health` gateway'in kendisi; `include: gateway` ile route listesi de actuator'da.

## Milestone 3'te ne var / ne yok

| Var | Yok (sonraki milestone) |
| --- | --- |
| 6 iş servisi + api-gateway, 6 Postgres, Flyway, CRUD | JWT, Resilience4j, Zipkin (M4) |
| Spring Cloud Gateway (`:8080`) | restaurant/delivery `processed_events` |
| Kafka KRaft, 6 topic, Kafka UI `:8090` | payment/restaurant/delivery outbox |
| `OrderSagaOrchestrator` + compensating iade | notification Kafka consumer |
| order-service `outbox_messages` + polling publisher | Testcontainers (M5) |
| payment-service `processed_events` (idempotent consumer) | |
| order-service `order_history_view` (CQRS okuma modeli) | |

## Mimari (M3)

```
                    ┌─────────────┐
         :8080      │ api-gateway │     Spring Cloud Gateway (WebFlux)
                    └──────┬──────┘
           ┌─────────┬─────┼─────┬─────────┬──────────┐
           ▼         ▼     ▼     ▼         ▼          ▼
        customer  restaurant order payment delivery notification
        :8084      :8083    :8081 :8082    :8085      :8086
           ▼         ▼       ▼      ▼        ▼          ▼
         PG 5437   PG 5435  PG 5436 PG 5434  PG 5438    PG 5439
                             │
                    OrderSagaOrchestrator
                             │
                    outbox_messages (aynı TX)
                    order_history_view (film şeridi)
                             │ poller ~250 ms
                          Kafka :9092 (KRaft)
                               │
                          Kafka UI :8090
                          payment processed_events
```

## Neden Saga? Neden 2PC değil?

"Sipariş Ver" dört servisi ilgilendirir (order, payment, restaurant, delivery). Hepsi kendi Postgres'ine sahip — tek bir `BEGIN/COMMIT` yok. İki fazlı commit (2PC) mikroserviste pratikte kilit ve operasyonel kabus.

**Orchestration-based saga:** `order-service` içindeki `OrderSagaOrchestrator` adımları sıraya dizer. Bir adım başarısız olursa **geri alma (compensate)** yazar: restoran reddederse ödeme iade edilir, sipariş `CANCELLED` olur.

Choreography (her servis bir sonrakini tetikler) M2'de yok; tek yönetmen okumayı kolaylaştırır.

**Dual-write (M2.5 çözümü, yalnızca order-service):** orchestrator `orders` ile **aynı** Postgres transaction’ında `outbox_messages` satırı atar. `OutboxPublisher` COMMIT’ten sonra Kafka’ya taşır. Process send’den önce ölürse komut tabloda kalır. Payment/restaurant/delivery hâlâ doğrudan `kafka.send`. Ayrıntı: [`docs/kafka-outbox.md`](docs/kafka-outbox.md).

Kafka ACK olup `published_at` yazılmadan ölünürse aynı komut bir daha gider. **Milestone 3** bunu payment tarafında kapatır: `processed_events` aynı `commandId`'yi ikinci kez yemez. Ayrıntı: [`docs/cqrs-idempotency.md`](docs/cqrs-idempotency.md).

**CQRS (order):** `GET /api/orders/{id}` yazma modeli (şu anki status). `GET /api/orders/{id}/history` okuma modeli (neler oldu). İki tablo, aynı servis.

## Kafka topic listesi

| Topic | Kim üretir | Kim tüketir | Mesaj |
| --- | --- | --- | --- |
| `payment-commands` | order-service (outbox poller) | payment-service | `ChargePaymentCommand`, `RefundPaymentCommand` |
| `payment-events` | payment-service | order-service | `PaymentCharged` / `Failed` / `Refunded` |
| `restaurant-commands` | order-service (outbox poller) | restaurant-service | `ReserveRestaurantCommand` |
| `restaurant-events` | restaurant-service | order-service | `RestaurantAccepted` / `Rejected` |
| `delivery-commands` | order-service (outbox poller) | delivery-service | `AssignCourierCommand` |
| `delivery-events` | delivery-service | order-service | `CourierAssigned` / `Failed` |

Şema `nefis-contracts` JAR'ında. Bu **domain entity paylaşmak değil** — yalnızca mesajın JSON şekli. Her servis hâlâ kendi DB'sine bakar.

Komut = "yap". Event = "oldu". Orchestrator event dinler, bir sonraki komutu yollar.

## Mutlu senaryo nasıl tetiklenir?

Adım adım (üç senaryo + Kafka UI + outbox + history): [`docs/kafka-saga-lab.md`](docs/kafka-saga-lab.md)  
Kodda nasıl handle ediliyor: [`docs/kafka-orchestration.md`](docs/kafka-orchestration.md)  
Neden outbox: [`docs/kafka-outbox.md`](docs/kafka-outbox.md)  
Neden processed_events / history: [`docs/cqrs-idempotency.md`](docs/cqrs-idempotency.md)

Kısaca: `docker compose up -d` → IntelliJ tum servisler → Postman klasör **8** mutlu yol (`forceRestaurantAccept: true`) → 1–3 sn sonra GET order = `CONFIRMED`. `status: "PENDING"` = saga yok (CRUD).

## Red / compensate hangi loglarda?

Postman: **Sipariş Ver (restoran red)**. Body: `forceRestaurantReject: true`.

| Konsol (IntelliJ) | Satır |
| --- | --- |
| restaurant-service | `RESTAURANT REJECT order=... reason=FORCE_REJECT (compensate tetiklenecek)` |
| order-service | `SAGA COMPENSATE order=... reason=FORCE_REJECT` |
| order-service | `SAGA cancelled order=... -> RefundPaymentCommand` |
| payment-service | `PAYMENT REFUNDED order=... reason=FORCE_REJECT` |

`GET` sipariş: `status=CANCELLED`, `cancelReason=FORCE_REJECT`. Ödeme: `REFUNDED`.

Bayrak yoksa restoran **%20** `RANDOM_REJECT` eder (`nefis.restaurant.reject-probability`, env `RESTAURANT_REJECT_PROBABILITY`).

## Kafka'yı izlemek (Kafka UI)

Broker'ın kendisi bir web arayüzü sunmaz. Topic, mesaj ve consumer group görmek için compose'a **Kafbat Kafka UI** eklendi.

**Adım adım senaryolar (Postman + Kafka UI):** [`docs/kafka-saga-lab.md`](docs/kafka-saga-lab.md)  
**Kodda orchestration (komut / event / compensate):** [`docs/kafka-orchestration.md`](docs/kafka-orchestration.md)  
**Outbox (önce Postgres, sonra Kafka):** [`docs/kafka-outbox.md`](docs/kafka-outbox.md)  
**Idempotent consumer + CQRS history:** [`docs/cqrs-idempotency.md`](docs/cqrs-idempotency.md)

Kısaca: [http://localhost:8090](http://localhost:8090) — cluster `nefis-yemek`. Port **8090** (gateway **8080**). Bu Prometheus/Grafana değil (M4).

```bash
docker compose up -d
```

| Menü | Ne göreceksin |
| --- | --- |
| **Topics** | `payment-commands`, `payment-events`, `restaurant-*`, `delivery-*` |
| Topic → **Messages** (Seek: Earliest) | JSON, key = `orderId` |
| **Consumers** | `order-service`, `payment-service`, `restaurant-service`, `delivery-service` |

## Gateway route tablosu

| Path | Hedef (IDE) | Docker URI |
| --- | --- | --- |
| `/api/customers/**` | localhost:8084 | http://customer-service:8084 |
| `/api/restaurants/**` | localhost:8083 | http://restaurant-service:8083 |
| `/api/orders/**` | localhost:8081 | http://order-service:8081 |
| `/api/payments/**` | localhost:8082 | http://payment-service:8082 |
| `/api/deliveries/**` | localhost:8085 | http://delivery-service:8085 |
| `/api/notifications/**` | localhost:8086 | http://notification-service:8086 |
| `/actuator/health` | gateway'in kendisi | — |

Prefix strip yok: backend zaten aynı path'i dinliyor.

Gateway üzerinden örnek (CRUD; saga için Postman klasör 8):

```bash
curl http://localhost:8080/actuator/health

curl -sS -X POST http://localhost:8080/api/customers \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Ali Veli","email":"ali@example.com","phone":"5551112233"}'

curl -sS -X POST http://localhost:8080/api/restaurants \
  -H 'Content-Type: application/json' \
  -d '{"name":"Adana Sofrası","address":"Kadıköy","cuisine":"Türk","open":true}'

curl -sS -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"<CUSTOMER_ID>","restaurantId":"<RESTAURANT_ID>","totalAmount":149.90,"forceRestaurantAccept":true}'

curl -sS http://localhost:8080/api/orders/outbox
curl -sS http://localhost:8080/api/orders/<ORDER_ID>/history
curl -sS http://localhost:8080/api/payments/processed-events
```

notification-service konsola `[MOCK SMS] to=...` yazar; gerçek SMS yok. M2'de saga SMS atmaz.

## Portlar

| Servis / DB | Host port | Kullanıcı / DB |
| --- | --- | --- |
| api-gateway | 8080 | — |
| order-service | 8081 | — |
| payment-service | 8082 | — |
| restaurant-service | 8083 | — |
| customer-service | 8084 | — |
| delivery-service | 8085 | — |
| notification-service | 8086 | — |
| kafka | 9092 | KRaft, tek broker |
| kafka-ui | 8090 | http://localhost:8090 |
| order-db | 5436 | `order` / `orderdb` |
| payment-db | 5434 | `payment` / `paymentdb` |
| restaurant-db | 5435 | `restaurant` / `restaurantdb` |
| customer-db | 5437 | `customer` / `customerdb` |
| delivery-db | 5438 | `delivery` / `deliverydb` |
| notification-db | 5439 | `notification` / `notificationdb` |

## Gereksinimler

- Java 21
- Maven 3.9+ (veya kökteki `./mvnw`)
- Docker + Docker Compose

## Ayağa kaldırma

```bash
docker compose --profile apps up --build
```

`--profile apps` olmazsa Postgres, Kafka **ve Kafka UI** (`:8090`) ayağa kalkar (IntelliJ için doğru). Java + gateway container'dan koşsun dersen profile gerekir.

İlk build uzun sürebilir. Dışarıdan yalnızca gateway:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/restaurants
```

Sadece altyapıyı ayağa kaldırıp servisleri IDE'den çalıştırmak için:

```bash
docker compose up -d
docker compose ps   # nefis-kafka :9092, nefis-kafka-ui :8090, 6 Postgres
# IntelliJ: Nefis Yemek: tum servisler (gateway dahil)
```

Topic / mesaj: tarayıcıda [http://localhost:8090](http://localhost:8090) (Kafka UI). CLI:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

## IntelliJ IDEA ile derleme ve çalıştırma

Servisleri container içinde değil, IDE'den debug ederek çalıştırmak için Java süreçlerini IntelliJ'e bırak; Postgres **ve Kafka**'yı Docker'da tut.

**IntelliJ ile çalışırken** Java container'larını açma. `docker compose up` Postgres + Kafka + Kafka UI başlatır. Tam stack için: `docker compose --profile apps up --build`.

### 1) Projeyi aç

1. **File → Open** → repo kökündeki `pom.xml` dosyasını seç → **Open as Project**.
2. Trust / Maven import uyarısını onayla.
3. **File → Project Structure → Project SDK** = **21**.
4. Sağdaki **Maven** aracında kök proje `nefis-yemek` görünmeli; yeni modül: `nefis-contracts`.
5. **Settings → Build, Execution, Deployment → Build Tools → Maven**
   - **Maven home path:** *Use Maven wrapper* (`./mvnw`)
   - **JDK for importer:** 21

İlk import'tan sonra Maven aracındaki yenile (Reload All Maven Projects) butonuna bas.

### 2) Sadece altyapıyı başlat

```bash
docker compose up -d
docker compose ps
```

`nefis-order-db` satırında `0.0.0.0:5436->5432/tcp`, `nefis-kafka` satırında `9092`, `nefis-kafka-ui` satırında `8090` görünmeli. Tarayıcı: http://localhost:8090

### 3) Build

Run dropdown'dan **Nefis Yemek: Maven Build** (`clean verify`) veya Maven aracında kök `nefis-yemek` → **Lifecycle → verify**.

### 4) Servisleri çalıştır

| Konfigürasyon | Ne yapar |
| --- | --- |
| **Nefis Yemek: tum servisler** | Gateway + 6 iş servisi (önerilen) |
| `order-service` / `payment-service` / … | `main` metodundan Application |
| `*-service (Maven)` | `spring-boot:run` |

**Nefis Yemek: tum servisler** → yeşil Run (veya Debug). Kafka yoksa order/payment/restaurant/delivery log'unda broker hatası görürsün.

Konsolda `Started ApiGatewayApplication` ve `*ServiceApplication` satırları görünmeli. Saga için `SAGA start` / `PAYMENT CAPTURED` / `RESTAURANT ACCEPT` satırlarını izle.

**spring-boot-devtools** var: kaynak kaydedince (Build → Build Project) uygulama kendini yeniden başlatır.

### 5) IntelliJ içinden dene

`http/api.http` (Ultimate HTTP Client) veya Postman.

### Sık karşılaşılan hatalar

| Belirti | Çözüm |
| --- | --- |
| `nefis-order-db` healthy ama `PORTS` sütunu boş | Host **5433** başka bir container'da. Order DB **5436** |
| `Connection refused` / `HikariPool` | `docker compose ps` — Postgres portları + `nefis-kafka :9092` |
| `Connection to localhost:9092 refused` | Kafka yok: `docker compose up -d` |
| `localhost:8090` açılmıyor | `docker compose ps` — `nefis-kafka-ui` UP mi? İlk pull uzun sürebilir. Port 8090 doluysa değiştir |
| `Port 8081 was already in use` | `docker compose --profile apps stop` |
| Run config: *module not specified* | Maven Reload |
| Flyway / `Schema-validation: missing table` | Order URL `localhost:5436/orderdb`. V2 `cancel_reason`, V3 `outbox_messages`, V4 `order_history_view`. Payment V2 `processed_events`. `git pull` + ilgili servisi restart |
| SDK is not defined | Project Structure → SDK 21, Maven Reload |
| restaurant-service `No default constructor` / `RestaurantDecisionPolicy` | İki kurucu varken Spring `@Autowired` ister. `git pull` sonrası Maven Reload ve restaurant-service restart |
| payment-service `trusted packages` / `SerializationException` döngüsü | JsonDeserializer `com.nefisyemek.contracts` alt paketleri (`command`, `event`) kapsamaz. Kod `com.nefisyemek.contracts.*` kullanır. `git pull`, Maven Reload, order/payment/restaurant/delivery'yi restart. Kafka'yı silme — offset 0'daki mesaj geçerlidir. |

## API'yi denemek (Postman)

Koleksiyon: **`http/Nefis-Yemek.postman_collection.json`**

1. **Import → File** → bu JSON
2. Tekrar importte **Replace** (koleksiyon id sabit)
3. Hepsi `{{gateway}}` = `http://localhost:8080`
4. CRUD klasörleri (1–7) + **8. Saga** + **9. Outbox** + **10. Idempotency / History**

**8081–8086'ya istek atma.**

IntelliJ HTTP Client: `http/api.http`. Terminal: `./http/smoke.sh`.

## Unit testler

Veritabanı / Kafka gerektirmez:

```bash
./mvnw test
```

## Modül / katman yapısı

```
nefis-contracts    Kafka komut/event record + JSON serileştirme
.../controller     REST + DTO (record)
.../service        iş kuralları, transaction sınırı
.../repository     Spring Data JPA
.../domain         entity + enum
.../event          Kafka listener / saga / outbox / history recorder / idempotent consumer
.../exception      404 / 400 ProblemDetail
```

Flyway: `src/main/resources/db/migration/V1__*.sql` (+ order `V2` saga, `V3` outbox, `V4` history; payment `V2` processed_events).

Hibernate `ddl-auto: validate` — şemayı JPA üretmez, Flyway üretir.

## Sonraki adım

Milestone 4: Resilience, Security, Observability (Resilience4j, JWT, Actuator, Prometheus, Grafana, Zipkin).
