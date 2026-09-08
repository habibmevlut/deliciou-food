# Nefis Yemek

Kurgusal bir yemek sipariş platformu. Amaç production'a çıkmak değil; mikroservis pattern'lerini (Saga, CQRS, Outbox, Circuit Breaker, API Gateway, Distributed Tracing) gerçek kodla denemek.

Milestone'lar **tek branch'te** birikir. README her zaman o ana kadar yapılanın tam halidir; görmek için branch değiştirmen gerekmez.

Şu an **Milestone 1**: kalan servisler + API Gateway. Dış dünya yalnızca **:8080** üzerinden konuşur.

## Neden API Gateway?

Altı servisin altı portunu istemciye açmak, URL'leri ve (Milestone 4'te) JWT'yi her yerde kopyalamak demek. Gateway tek kapı: path'e bakıp isteği doğru servise iletir. Servisler hâlâ **Database per Service** — birbirinin Postgres'ine dokunmazlar.

## Milestone 1'de ne var / ne yok

| Var | Yok (sonraki milestone) |
| --- | --- |
| 6 iş servisi + api-gateway, 6 Postgres, Flyway, CRUD | Kafka, Saga, Outbox |
| Gateway route'ları (`/api/**` → ilgili servis) | JWT, Resilience4j, Zipkin |
| notification-service REST + console mock | Kafka consumer |

## Mimari (M1)

```
                    ┌─────────────┐
         :8080      │ api-gateway │
                    └──────┬──────┘
           ┌─────────┬─────┼─────┬─────────┬──────────┐
           ▼         ▼     ▼     ▼         ▼          ▼
        customer  restaurant order payment delivery notification
        :8084      :8083    :8081 :8082    :8085      :8086
           ▼         ▼       ▼      ▼        ▼          ▼
         PG 5437   PG 5435  PG 5436 PG 5434  PG 5438    PG 5439
```

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

Gateway üzerinden örnek:

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
  -d '{"customerId":"<CUSTOMER_ID>","restaurantId":"<RESTAURANT_ID>","totalAmount":149.90}'

curl -sS -X POST http://localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"<ORDER_ID>","amount":149.90}'

curl -sS -X POST http://localhost:8080/api/deliveries \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"<ORDER_ID>","courierName":"Ahmet"}'

curl -sS -X POST http://localhost:8080/api/notifications \
  -H 'Content-Type: application/json' \
  -d '{"recipient":"5551112233","channel":"SMS","message":"Siparis alindi"}'
```

notification-service konsola `[MOCK SMS] to=...` yazar; gerçek SMS yok.

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

`--profile apps` olmazsa yalnızca Postgres ayağa kalkar (IntelliJ için doğru). Java + gateway container'dan koşsun dersen profile gerekir.

İlk build uzun sürebilir. Dışarıdan yalnızca gateway:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/restaurants
```

Sadece veritabanlarını ayağa kaldırıp servisleri IDE'den çalıştırmak için:

```bash
docker compose up -d
# IntelliJ: Nefis Yemek: tum servisler (gateway dahil)
```

## IntelliJ IDEA ile derleme ve çalıştırma

Servisleri container içinde değil, IDE'den debug ederek çalıştırmak için Java süreçlerini IntelliJ'e bırak; Postgres'i Docker'da tut.

**IntelliJ ile çalışırken** Java container'larını açma. `docker compose up` yalnızca Postgres'leri başlatır. Tam stack için: `docker compose --profile apps up --build`.

### 1) Projeyi aç

1. **File → Open** → repo kökündeki `pom.xml` dosyasını seç → **Open as Project**.
2. Trust / Maven import uyarısını onayla.
3. **File → Project Structure → Project SDK** = **21** (Eclipse Temurin / Oracle / Microsoft, fark etmez).
   Language level da 21 olmalı; Maven `java.version` bunu zaten dayatır.
4. Sağdaki **Maven** aracında kök proje `nefis-yemek` görünmeli, altında üç modül.
5. **Settings → Build, Execution, Deployment → Build Tools → Maven**
   - **Maven home path:** *Use Maven wrapper* (`./mvnw`)
   - **JDK for importer:** 21

İlk import'tan sonra Maven aracındaki yenile (Reload All Maven Projects) butonuna bas.

### 2) Sadece veritabanlarını başlat

Terminal (IntelliJ altındaki) veya harici shell:

```bash
docker compose up -d
docker compose ps
```

`nefis-order-db` satırında `0.0.0.0:5436->5432/tcp` görünmeli. `PORTS` boşsa host portu bind olmamıştır; IntelliJ yanlış Postgres'e gider.

Üç Postgres ayağa kalkmadan servisler Flyway/`ddl-auto: validate` yüzünden **fail** olur.

### 3) Build

Run dropdown'dan **Nefis Yemek: Maven Build** (`clean verify`) veya Maven aracında kök `nefis-yemek` → **Lifecycle → verify**.

Yeşil tik: üç modül derlendi, unit testler geçti.

### 4) Servisleri çalıştır

Run dropdown'da hazır konfigürasyonlar var:

| Konfigürasyon | Ne yapar |
| --- | --- |
| **Nefis Yemek: tum servisler** | Gateway + 6 iş servisi (önerilen) |
| `order-service` / `payment-service` / `restaurant-service` | `main` metodundan Application olarak çalıştırır |
| `*-service (Maven)` | `spring-boot:run` — modül adı IntelliJ'de farklıysa bunu kullan |

**Nefis Yemek: tum servisler** → yeşil Run (veya Debug). Gateway en son ayağa kalkabilir; 8080 dinlemeden istek atma.

Konsolda `Started ApiGatewayApplication` ve diğer `*ServiceApplication` satırları görünmeli.

`main` yanındaki yeşil üçgene basmak da aynı.

**spring-boot-devtools** var: kaynak kaydedince (Build → Build Project, varsayılan Ctrl/Cmd+F9) uygulama kendini yeniden başlatır.

### 5) IntelliJ içinden dene

`http/api.http` dosyasını aç (IntelliJ HTTP Client — Ultimate'te yerleşik). Satırın üstündeki **Run** ile health ve CRUD isteklerini gönder.

Community Edition'da aynı URL'leri README'deki curl'lerle veya tarayıcıdan `/actuator/health` ile doğrula.

### Sık karşılaşılan hatalar

| Belirti | Çözüm |
| --- | --- |
| `nefis-order-db` healthy ama `PORTS` sütunu boş | Host **5433** başka bir container'da (ör. `socialflow-postgres`). Order DB **5436** kullanır — compose'u güncelleyip `docker compose up -d --force-recreate order-db` |
| `Connection refused` / `HikariPool` Postgres'e bağlanamıyor | `docker compose ps` — `order-db` 5436, `payment-db` 5434, `restaurant-db` 5435 dinliyor mu? |
| `Port 8081 was already in use` | Java container'ı da açık kalmış: `docker compose --profile apps stop` |
| Run config: *module not specified* | Maven Reload; veya `order-service (Maven)` konfigürasyonunu kullan |
| Flyway / `Schema-validation: missing table` / `password authentication failed` | `localhost:5433` hâlâ socialflow'a gidiyor olabilir. URL `localhost:5436/orderdb`, user `order` olmalı |
| SDK is not defined | Project Structure → SDK 21 ekle, sonra Maven Reload |

## API'yi denemek (Postman)

Koleksiyon: **`http/Nefis-Yemek.postman_collection.json`**

Her milestone'da bu dosya güncellenir. Sen Postman'da:

1. **Import → File** → bu JSON
2. Aynı dosyayı tekrar import edince **Replace** de (koleksiyon id sabit; güncellenir)
3. Soldaki klasörler route tablosu: hepsi `{{gateway}}` = `http://localhost:8080`
4. Sırayla: Gateway health → Customer → Restaurant → Order → Payment → Delivery → Notification

Create istekleri `customerId` / `orderId` gibi değişkenleri otomatik doldurur. **8081–8086'ya istek atma.**

IntelliJ HTTP Client istersen: `http/api.http`. Terminal: `./http/smoke.sh`.

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

Milestone 2: Kafka + orchestration-based Saga ("Sipariş Ver" akışı).
