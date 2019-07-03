FROM openjdk:11

WORKDIR /opt

ENV LC_ALL=C
ENV RABBIT_HOST=localhost
ENV RABBIT_PORT=5672
ENV INGEST_ROOT_API=http://api.ingest.dev.data.humancellatlas.org
ENV REDIS_HOST=localhost
ENV REDIS_PORT=6379


ADD gradle ./gradle
ADD src ./src

COPY gradlew build.gradle ./

RUN ./gradlew assemble

CMD java -jar build/libs/*.jar --spring.rabbitmq.host=$RABBIT_HOST --spring.rabbitmq.port=$RABBIT_PORT --spring.profiles.active=persistence,redis-persistence --spring.redis.host=$REDIS_HOST --spring.redis.port=$REDIS_PORT
