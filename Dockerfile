FROM openjdk:11

COPY ./target/universal/x-sqsd-1.0.tgz /usr/local/app/x-sqsd-1.0.tgz

WORKDIR /usr/local/app

RUN tar xf x-sqsd-1.0.tgz
RUN rm x-sqsd-1.0.tgz

WORKDIR /usr/local/app/x-sqsd-1.0.tgz

ENTRYPOINT ["./bin/x-sqsd"]
