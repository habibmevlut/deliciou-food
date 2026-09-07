# Nefis Yemek

Kurgusal bir yemek sipariş platformu. Amaç production'a çıkmak değil; mikroservis pattern'lerini (Saga, CQRS, Outbox, Circuit Breaker, API Gateway, Distributed Tracing) gerçek kodla denemek.

Şu an **Milestone 0**: iskelet + Database per Service.

## Neden Database per Service?

Üç servis aynı Postgres'e bağlansaydı "mikroservis" sadece process sınırında kalırdı: `order-service` `JOIN restaurants` yazabilir, şema değişikliği üç ekibi kırardı.

Bu yüzden her servisin **kendi PostgreSQL instance'ı** var. `payment.order_id` başka DB'deki siparişe FK **değil**; sadece bir UUID. Bütünlük ileride Kafka event'leri ve (gerektiğinde) REST ile sağlanacak.

## Milestone 0'da ne var / ne yok

| Var | Yok (sonraki milestone) |
| --- | --- |
| 3 servis, 3 Postgres, Flyway, CRUD REST | Kafka, Saga, Outbox |
| Katmanlı paket yapısı (`controller` / `service` / `repository` / `domain` / `event`) | API Gateway |
| Actuator `/actuator/health` | JWT, Resilience4j, Zipkin |

## Mimari (M0)

```
                 ┌─────────────────┐   :5433   ┌──────────────┐
   :8081  REST   │  order-service  │──────────▶│   order-db   │
                 └─────────────────┘           └──────────────┘

                 ┌─────────────────┐   :5434   ┌──────────────┐
   :8082  REST   │ payment-service │──────────▶│  payment-db  │
                 └─────────────────┘           └──────────────┘

                 ┌─────────────────┐   :5435   ┌──────────────┐
   :8083  REST   │restaurant-svc   │──────────▶│restaurant-db │
                 └─────────────────┘           └──────────────┘
```

Servisler birbirini **çağırmaz**. Her biri izole bir CRUD uygulaması.

## Portlar

| Servis / DB | Host port | İç port | Kullanıcı / DB |
| --- | --- | --- | --- |
| order-service | 8081 | 8081 | — |
| payment-service | 8082 | 8082 | — |
| restaurant-service | 8083 | 8083 | — |
| order-db | 5433 | 5432 | `order` / `orderdb` |
| payment-db | 5434 | 5432 | `payment` / `paymentdb` |
| restaurant-db | 5435 | 5432 | `restaurant` / `restaurantdb` |

## Gereksinimler

- Java 21
- Maven 3.9+ (veya kökteki `./mvnw`)
- Docker + Docker Compose

## Ayağa kaldırma

```bash
docker compose up --build
```

İlk build parent POM + üç servisi derler; 2–5 dakika sürebilir. Hazır olduğunda:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

Üçü de `"status":"UP"` dönmeli.

Sadece veritabanlarını ayağa kaldırıp servisleri IDE'den çalıştırmak için:

```bash
docker compose up order-db payment-db restaurant-db
./mvnw -pl order-service spring-boot:run
# başka terminallerde payment-service ve restaurant-service
```

Yerel `application.yml` zaten host portlarına (`5433`/`5434`/`5435`) bakıyor.

## Test curl komutları

Aşağıdaki komutlar `docker compose up` sonrası her üç servisi uçtan uca doğrular.

### 1) restaurant-service — restoran oluştur, oku, güncelle, sil

```bash
# Oluştur (201 + Location)
curl -sS -X POST http://localhost:8083/api/restaurants \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Adana Sofrası",
    "address": "Caferağa Mah. Kadıköy / İstanbul",
    "cuisine": "Türk",
    "open": true
  }' | tee /tmp/restaurant.json

RESTAURANT_ID=$(python3 -c "import json; print(json.load(open('/tmp/restaurant.json'))['id'])")
echo "restaurant id: $RESTAURANT_ID"

# Listele
curl -sS http://localhost:8083/api/restaurants

# Tek kayıt
curl -sS http://localhost:8083/api/restaurants/$RESTAURANT_ID

# Güncelle (restoranı kapat)
curl -sS -X PUT http://localhost:8083/api/restaurants/$RESTAURANT_ID \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Adana Sofrası",
    "address": "Caferağa Mah. Kadıköy / İstanbul",
    "cuisine": "Türk",
    "open": false
  }'

# Sil (204)
curl -sS -o /dev/null -w "%{http_code}\n" -X DELETE http://localhost:8083/api/restaurants/$RESTAURANT_ID
```

### 2) order-service — sipariş oluştur, oku, güncelle, sil

```bash
CUSTOMER_ID=$(python3 -c "import uuid; print(uuid.uuid4())")
RESTAURANT_ID=$(python3 -c "import uuid; print(uuid.uuid4())")

curl -sS -X POST http://localhost:8081/api/orders \
  -H 'Content-Type: application/json' \
  -d "{
    \"customerId\": \"$CUSTOMER_ID\",
    \"restaurantId\": \"$RESTAURANT_ID\",
    \"totalAmount\": 149.90
  }" | tee /tmp/order.json

ORDER_ID=$(python3 -c "import json; print(json.load(open('/tmp/order.json'))['id'])")
echo "order id: $ORDER_ID"

curl -sS http://localhost:8081/api/orders
curl -sS http://localhost:8081/api/orders/$ORDER_ID

# Durumu CONFIRMED yap (Milestone 2'de bunu saga yapacak)
curl -sS -X PUT http://localhost:8081/api/orders/$ORDER_ID \
  -H 'Content-Type: application/json' \
  -d "{
    \"customerId\": \"$CUSTOMER_ID\",
    \"restaurantId\": \"$RESTAURANT_ID\",
    \"totalAmount\": 149.90,
    \"status\": \"CONFIRMED\"
  }"

curl -sS -o /dev/null -w "%{http_code}\n" -X DELETE http://localhost:8081/api/orders/$ORDER_ID
```

`status` göndermezsen varsayılan **PENDING** olur.

### 3) payment-service — ödeme oluştur, oku, güncelle, sil

```bash
ORDER_ID=$(python3 -c "import uuid; print(uuid.uuid4())")

curl -sS -X POST http://localhost:8082/api/payments \
  -H 'Content-Type: application/json' \
  -d "{
    \"orderId\": \"$ORDER_ID\",
    \"amount\": 149.90
  }" | tee /tmp/payment.json

PAYMENT_ID=$(python3 -c "import json; print(json.load(open('/tmp/payment.json'))['id'])")
echo "payment id: $PAYMENT_ID"

curl -sS http://localhost:8082/api/payments
curl -sS http://localhost:8082/api/payments/$PAYMENT_ID

curl -sS -X PUT http://localhost:8082/api/payments/$PAYMENT_ID \
  -H 'Content-Type: application/json' \
  -d "{
    \"orderId\": \"$ORDER_ID\",
    \"amount\": 149.90,
    \"status\": \"CAPTURED\"
  }"

curl -sS -o /dev/null -w "%{http_code}\n" -X DELETE http://localhost:8082/api/payments/$PAYMENT_ID
```

### Beklenen hata cevapları

```bash
# 404 — olmayan kayıt
curl -sS -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/orders/00000000-0000-0000-0000-000000000000

# 400 — validation (negatif tutar)
curl -sS -X POST http://localhost:8081/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"00000000-0000-0000-0000-000000000001","restaurantId":"00000000-0000-0000-0000-000000000002","totalAmount":-1}'
```

## Unit testler

Veritabanı gerektirmez:

```bash
./mvnw test
```

## Modül / katman yapısı

Her servis aynı paket düzenini kullanır:

```
.../controller     REST + DTO (record)
.../service        iş kuralları, transaction sınırı
.../repository     Spring Data JPA
.../domain         entity + enum
.../event          Milestone 2+ Kafka mesajları için rezerv
.../exception      404 / 400 ProblemDetail
```

Flyway migration'lar: `src/main/resources/db/migration/V1__*.sql`

Hibernate `ddl-auto: validate` — şemayı JPA üretmez, Flyway üretir. Böylece production'daki gibi migration tek kaynak olur.

## Sonraki adım

Milestone 1: `customer-service`, `delivery-service`, `notification-service`, `api-gateway`. Bundan sonra dış dünya servislere doğrudan değil, gateway üzerinden konuşacak.
