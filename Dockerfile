FROM openjdk:17-alpine3.14

WORKDIR /app

COPY .mvn/ .mvn
COPY pom.xml mvnw ./

RUN ./mvnw dependency:go-offline

COPY src ./src

CMD ["./mvnw", "spring-boot:run"]
