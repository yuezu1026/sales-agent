# ===== 后端 Dockerfile：多阶段构建 =====
# 阶段1：Maven 构建
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# 阶段2：运行时（JRE 精简）
FROM eclipse-temurin:21-jre
# M7.12 时区修复：容器默认 UTC 导致北京时间 0-8 点间“今日”统计不归零，强制设为中国时区
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
