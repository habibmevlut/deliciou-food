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
                 ┌─────────────────┐   :5436   ┌──────────────┐
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
| order-db | 5436 | 5432 | `order` / `orderdb` |
| payment-db | 5434 | 5432 | `payment` / `paymentdb` |
| restaurant-db | 5435 | 5432 | `restaurant` / `restaurantdb` |

## Gereksinimler

- Java 21
- Maven 3.9+ (veya kökteki `./mvnw`)
- Docker + Docker Compose

## Ayağa kaldırma

```bash
docker compose --profile apps up --build
```

`--profile apps` olmazsa yalnızca üç Postgres ayağa kalkar (IntelliJ için doğru). Java servisleri de container'dan koşsun dersen profile gerekir.

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

Yerel `application.yml` zaten host portlarına (`5436`/`5434`/`5435`) bakıyor.

## IntelliJ IDEA ile derleme ve çalıştırma

Servisleri container içinde değil, IDE'den debug ederek çalıştırmak için Java süreçlerini IntelliJ'e bırak; Postgres'i Docker'da tut.

**IntelliJ ile çalışırken** Java container'larını açma. `docker compose up` yalnızca Postgres'leri başlatır (8081–8083 bind edilmez). Tam stack için: `docker compose --profile apps up --build`.

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
docker compose up -d --force-recreate order-db payment-db restaurant-db
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
| **Nefis Yemek: 3 servis** | Üç uygulamayı birden başlatır (önerilen) |
| `order-service` / `payment-service` / `restaurant-service` | `main` metodundan Application olarak çalıştırır |
| `*-service (Maven)` | `spring-boot:run` — modül adı IntelliJ'de farklıysa bunu kullan |

**Nefis Yemek: 3 servis** → yeşil Run (veya Debug).

Konsolda `Started OrderServiceApplication`, `Started PaymentServiceApplication`, `Started RestaurantServiceApplication` görünmeli.

`main` yanındaki yeşil üçgene basmak da aynı: `OrderServiceApplication` → Run.

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

## API'yi denemek (Postman gerekmez)

Servisler IntelliJ'den ayaktayken iki yol var:

**1) IntelliJ HTTP Client** — `http/api.http` dosyasını aç, isteğin üstündeki yeşil **Run**.

**2) Terminal** — proje kökünden:

```bash
chmod +x http/smoke.sh
./http/smoke.sh
```

Health, üç servisin CRUD'u, 404 ve 400'ü sırayla çağırır; `OK` yazmalı.

Postman/Insomnia istersen: `http/Nefis-Yemek.postman_collection.json` → Import.

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
