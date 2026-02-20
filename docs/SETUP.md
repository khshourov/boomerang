# Boomerang Setup Guide

This document provides instructions for setting up and configuring the Boomerang server.

## 1. Environment Configuration

The server requires several environment variables to be set for security and persistence.

### Encryption Master Key
Boomerang uses AES-256-GCM to encrypt client credentials at rest. You must provide a 256-bit (32-byte) master key encoded in Base64 via the `BOOMERANG_MASTER_KEY` environment variable.

#### Generating a Master Key
You can generate a valid key using OpenSSL:

```bash
openssl rand -base64 32
```

Alternatively, using Python:

```bash
python3 -c "import secrets, base64; print(base64.b64encode(secrets.token_bytes(32)).decode())"
```

Set the variable in your environment:

```bash
export BOOMERANG_MASTER_KEY="your-generated-base64-key"
```

### Storage Paths
By default, Boomerang stores data in the `data/` directory. You can override these paths using system properties or environment variables:

- `rocksdb.path`: Path for long-term task storage (Default: `data/rocksdb`)
- `rocksdb.client.path`: Path for encrypted client credential storage (Default: `data/clients`)

## 2. Running the Server

Ensure you have Java 21+ installed.

```bash
./gradlew run
```

## 3. Initial Admin Access
Upon the first startup, Boomerang provisions a default administrative client based on the configuration in `boomerang-server.properties`:

- **Admin Client ID:** `admin` (Default)
- **Admin Password:** `admin123` (Default)

It is highly recommended to change these defaults or register a new admin and delete the default one for production environments.
