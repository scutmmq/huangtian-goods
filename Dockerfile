FROM openjdk:17

WORKDIR /app

COPY app.jar /app/app.jar
COPY config/ /app/config/

ENV TZ=Asia/Shanghai
ENV SPRING_PROFILES_ACTIVE=prd
ENV LOG_PATH=/app/log
ENV JAVA_OPTS=""

RUN mkdir -p /app/log

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE} --spring.config.additional-location=optional:file:/app/config/ --logging.config=file:/app/config/logback.xml"]
