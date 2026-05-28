# Online Mall Backend Deployment

## Build release package

```bash
mvn clean package -DskipTests
```

The release package is generated at:

```text
target/huangtian-goods-release.tar.gz
```

## Run with a specific Spring profile

```bash
java -jar target/huangtian-goods.jar --spring.profiles.active=dev
java -jar target/huangtian-goods.jar --spring.profiles.active=prd
```

## Deploy on a cloud server

```bash
tar -xzf huangtian-goods-release.tar.gz
cd huangtian-goods
cp env.example .env
vi .env
./run.sh prd
```

`run.sh` builds a Docker image from the extracted package, removes the old container with the same name, and starts a new backend container.
By default, production config uses `host.docker.internal` so the container can connect to MySQL and Redis on the cloud server host. Set `ADD_HOST_GATEWAY=false` if your Docker version does not support `host-gateway`, or override `MYSQL_URL` and `REDIS_HOST` in `.env` when using remote services.

Useful environment variables:

```bash
APP_NAME=online-mall-backend
IMAGE_NAME=online-mall-backend:latest
HOST_PORT=8080
SPRING_PROFILES_ACTIVE=prd
ADD_HOST_GATEWAY=true
```
