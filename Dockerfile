FROM openjdk:13-alpine
RUN apk add --no-cache jq python3 bash \
    && pip3 install --upgrade pip && pip3 install yq
COPY . /root
WORKDIR /root
RUN ./gradlew dependencies &> /dev/null
ENTRYPOINT ["./gradlew"]
