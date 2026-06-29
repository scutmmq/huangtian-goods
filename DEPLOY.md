# Online Mall Backend Deployment

## Build release package

```bash
mvn clean package -DskipTests
```

The release package is generated at:

```text
target/huangtian-goods-release.tar.gz
```

> The package includes a ready-to-use `.env` (with the cloud deployment credentials), so the
> target server does **not** need any extra setup before running. `.env` is git-ignored locally
> so the real secrets never get committed.

If you build on a fresh clone and your working tree has no `.env`, the assembly step will fail
with a "missing file" error. In that case copy the template and edit it:

```bash
cp env.example .env
vi .env
mvn clean package -DskipTests
```

## Run with a specific Spring profile

```bash
java -jar target/huangtian-goods.jar --spring.profiles.active=dev
java -jar target/huangtian-goods.jar --spring.profiles.active=prd
```

When running the JAR directly (not via Docker), source the env file first so the placeholders
in `application-*.yaml` resolve:

```bash
set -a; . ./.env; set +a
java -jar app.jar --spring.profiles.active=prd
```

## Deploy on a cloud server

```bash
tar -xzf huangtian-goods-release.tar.gz
cd huangtian-goods
./run.sh prd
```

`run.sh` builds a Docker image from the extracted package, removes the old container with the
same name, and starts a new backend container. It automatically picks up the bundled `.env`
and passes it to the container via `--env-file`.

By default, production config uses `host.docker.internal` so the container can connect to MySQL
and Redis on the cloud server host. Set `ADD_HOST_GATEWAY=false` if your Docker version does
not support `host-gateway`, or override `MYSQL_URL` and `REDIS_HOST` in `.env` when using
remote services.

Useful environment variables:

```bash
APP_NAME=online-mall-backend
IMAGE_NAME=online-mall-backend:latest
HOST_PORT=8080
SPRING_PROFILES_ACTIVE=prd
ADD_HOST_GATEWAY=true
```

## Rotating the bundled `.env`

The `.env` shipped in the release is intended for the production cloud deployment. To change
it:

1. Edit `.env` at the project root (still git-ignored).
2. Re-run `mvn clean package -DskipTests`.
3. Upload the new `target/huangtian-goods-release.tar.gz` to the server.

Do **not** commit `.env` to git — keep real credentials out of the repository.
