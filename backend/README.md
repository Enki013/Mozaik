# Personal Records - Go Backend

Kişisel Kayıtlar platformunun öz-barındırılan (self-hosted) **Go + SQLite** sunucu motoru. Clean Architecture prensiplerine göre katmanlandırılmıştır ve `worker/` dizinindeki Cloudflare Worker motoruyla %100 aynı REST API sözleşmesini sunar.

## Dizin Yapısı

- `cmd/personal-records/main.go`: Sunucu giriş noktası ve bağımlılık enjeksiyonu.
- `internal/application/`: İş kuralları, birleşik aktivite zaman tüneli ve RSS/OAuth senkronizasyon servisleri.
- `internal/infrastructure/sqlite/`: SQLite şema göçleri ve veri erişim katmanı.
- `internal/integrations/fatsecret/`: FatSecret OAuth 1.0a imzalama ve API istemcisi.
- `internal/presentation/http/`: REST API uç noktaları ve gömülü web arayüzü.
- `internal/config/`: Ortam değişkenleri yapılandırması (`.env`).

## Çalıştırma

### Yerel Go ile:
```bash
cd backend
cp .env.example .env
go run ./cmd/personal-records
```

### Testleri Çalıştırma:
```bash
cd backend
go test ./...
```

### Docker Compose ile (Kök Dizinden):
```bash
docker compose up -d
```
