# 1. Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# 2. Run stage
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# build stage dan JAR faylni olish
COPY --from=build /app/target/MafiyaBot-1.0-SNAPSHOT.jar app.jar

# JAR faylni ishga tushirish
ENTRYPOINT ["java","-jar","app.jar"]
