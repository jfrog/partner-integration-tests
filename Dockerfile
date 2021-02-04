FROM openjdk:13-alpine
COPY . /root
WORKDIR /root
RUN ./gradlew dependencies &> /dev/null
ENTRYPOINT ["./gradlew"]
