# Nefis Yemek

Kurgusal bir yemek sipariş platformu. Amaç production'a çıkmak değil; mikroservis pattern'lerini (Saga, CQRS, Outbox, Circuit Breaker, API Gateway, Distributed Tracing) gerçek kodla denemek.

Milestone'lar **tek branch'te** birikir. README her zaman o ana kadar yapılanın tam halidir; görmek için branch değiştirmen gerekmez.

Şu an **Milestone 2**: Kafka + orchestration-based Saga. Dış dünya hâlâ yalnızca **:8080** üzerinden konuşur.

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

## Milestone 2'de ne var / ne yok

| Var | Yok (sonraki milestone) |
| --- | --- |
| 6 iş servisi + api-gateway, 6 Postgres, Flyway, CRUD | Transactional Outbox (M2.5) |
| Spring Cloud Gateway (`:8080`) | JWT, Resilience4j, Zipkin (M4) |
| Kafka KRaft (tek broker), 6 topic, Kafka UI `:8090` | Idempotent consumer tablosu (M3) |
| `OrderSagaOrchestrator` + compensating iade | notification Kafka consumer |

## Mimari (M2)

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
                          Kafka :9092 (KRaft)
                               │
                          Kafka UI :8090
```

## Neden Saga? Neden 2PC değil?

"Sipariş Ver" dört servisi ilgilendirir (order, payment, restaurant, delivery). Hepsi kendi Postgres'ine sahip — tek bir `BEGIN/COMMIT` yok. İki fazlı commit (2PC) mikroserviste pratikte kilit ve operasyonel kabus.

**Orchestration-based saga:** `order-service` içindeki `OrderSagaOrchestrator` adımları sıraya dizer. Bir adım başarısız olursa **geri alma (compensate)** yazar: restoran reddederse ödeme iade edilir, sipariş `CANCELLED` olur.

Choreography (her servis bir sonrakini tetikler) M2'de yok; tek yönetmen okumayı kolaylaştırır.

**Dual-write uyarısı:** orchestrator DB'ye yazar *ve* Kafka'ya send eder. İkisi atomik değil. Process send'den önce çökerse komut kaybolur — bunu **Milestone 2.5 Outbox** çözer.

## Kafka topic listesi

| Topic | Kim üretir | Kim tüketir | Mesaj |
| --- | --- | --- | --- |
| `payment-commands` | order-service | payment-service | `ChargePaymentCommand`, `RefundPaymentCommand` |
| `payment-events` | payment-service | order-service | `PaymentCharged` / `Failed` / `Refunded` |
| `restaurant-commands` | order-service | restaurant-service | `ReserveRestaurantCommand` |
| `restaurant-events` | restaurant-service | order-service | `RestaurantAccepted` / `Rejected` |
| `delivery-commands` | order-service | delivery-service | `AssignCourierCommand` |
| `delivery-events` | delivery-service | order-service | `CourierAssigned` / `Failed` |

Şema `nefis-contracts` JAR'ında. Bu **domain entity paylaşmak değil** — yalnızca mesajın JSON şekli. Her servis hâlâ kendi DB'sine bakar.

Komut = "yap". Event = "oldu". Orchestrator event dinler, bir sonraki komutu yollar.

## Mutlu senaryo nasıl tetiklenir?

1. `docker compose up -d` → 6 Postgres **ve Kafka**
2. IntelliJ: **Nefis Yemek: tum servisler**
3. Postman: Customer oluştur → Restaurant oluştur → **8. Saga → Sipariş Ver (mutlu yol)**
4. Body'de `forceRestaurantAccept: true` var; %20 rastgele red devre dışı. 1–3 sn sonra `GET /api/orders/{{orderId}}` → `CONFIRMED`
5. `GET /api/payments` → `CAPTURED`. `GET /api/deliveries` → kurye adı (`Kurye-Ada` / `Can` / `Ece`)

`POST /api/orders` `status` göndermezsen saga başlar. `status: "PENDING"` gönderirsen eski CRUD yolu (Kafka yok) — izole deneme için.

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

Broker'ın kendisi bir web arayüzü sunmaz. Topic, mesaj ve consumer group görmek için compose'a **Kafbat Kafka UI** eklendi (`ghcr.io/kafbat/kafka-ui` — eski adı Provectus Kafka UI).

Bu **Prometheus / Grafana değil**. O Milestone 4 (metrik, JVM, latency). Kafka UI = “şu an kuyrukta ne var?” tarayıcısı.

```bash
docker compose up -d
# tarayıcı: http://localhost:8090
```

Saga attıktan sonra UI'da:

| Menü | Ne göreceksin |
| --- | --- |
| **Topics** | `payment-commands`, `payment-events`, `restaurant-*`, `delivery-*` |
| Topic → **Messages** | JSON komut/event (key = `orderId`) |
| **Consumers** | `order-service`, `payment-service`, `restaurant-service`, `delivery-service` |

Port **8090**: gateway zaten **8080**. Kafka UI API gateway'e bağlı değil; altyapı aracı.

CLI hâlâ durur:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Masaüstü alternatif (zorunlu değil): Offset Explorer. Confluent Control Center bu laboratuvar için fazla ağır.

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
| Flyway / `Schema-validation: missing table` | Order URL `localhost:5436/orderdb`. V2 migration `cancel_reason` ekler |
| SDK is not defined | Project Structure → SDK 21, Maven Reload |

## API'yi denemek (Postman)

Koleksiyon: **`http/Nefis-Yemek.postman_collection.json`**

1. **Import → File** → bu JSON
2. Tekrar importte **Replace** (koleksiyon id sabit)
3. Hepsi `{{gateway}}` = `http://localhost:8080`
4. CRUD klasörleri (1–7) + **8. Saga — Sipariş Ver**

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
.../event          Kafka listener / OrderSagaOrchestrator
.../exception      404 / 400 ProblemDetail
```

Flyway: `src/main/resources/db/migration/V1__*.sql` (+ order `V2__order_saga_columns.sql`).

Hibernate `ddl-auto: validate` — şemayı JPA üretmez, Flyway üretir.

## Sonraki adım

Milestone 2.5: Transactional Outbox (order-service outbox tablosu + polling publisher).
