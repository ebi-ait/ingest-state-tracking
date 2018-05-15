FROM anapsix/alpine-java:8_jdk

WORKDIR /opt

ENV LC_ALL=C
ENV RABBIT_HOST=localhost
ENV RABBIT_PORT=5672
ENV INGEST_ROOT_API=http://api.ingest.dev.data.humancellatlas.org

ADD gradle ./gradle
ADD src ./src

COPY gradlew build.gradle ./

RUN ./gradlew assemble

CMD java -jar build/libs/*.jar --spring.rabbitmq.host=$RABBIT_HOST --spring.rabbitmq.port=$RABBIT_PORT --spring.profiles.active=redis-persistence
