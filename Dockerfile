# Build stage
FROM golang:1.24-alpine AS builder

# Install build dependencies for CGO & SQLite3
RUN apk add --no-cache gcc musl-dev

WORKDIR /app

# Cache Go modules
COPY go.mod go.sum ./
RUN go mod download

# Copy application source
COPY . .

# Compile binary with CGO enabled
RUN CGO_ENABLED=1 GOOS=linux go build -ldflags="-s -w" -o /app/personal-records ./cmd/personal-records

# Runtime stage
FROM alpine:3.21

# Install runtime certificates, timezone database and sqlite utilities
RUN apk add --no-cache ca-certificates tzdata sqlite

WORKDIR /app

# Copy binary from builder
COPY --from=builder /app/personal-records /app/personal-records

# Create persistent data directory for SQLite database
RUN mkdir -p /data

# Default runtime configuration
ENV HOST=0.0.0.0
ENV PORT=8787
ENV DB_PATH=/data/personal_records.sqlite3

EXPOSE 8787

VOLUME ["/data"]

CMD ["/app/personal-records"]
