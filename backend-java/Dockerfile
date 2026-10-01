# platform-app (Java) 两阶段构建
# build context = backend-java/
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 阿里云镜像加速
RUN mkdir -p /root/.m2 && printf '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">\n<mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors>\n</settings>' > /root/.m2/settings.xml

COPY pom.xml .
COPY platform-common/pom.xml platform-common/
COPY module-iam/pom.xml module-iam/
COPY module-governance/pom.xml module-governance/
COPY module-dataset/pom.xml module-dataset/
COPY module-product/pom.xml module-product/
COPY platform-boot/pom.xml platform-boot/
RUN mvn -q -B dependency:go-offline || true

COPY platform-common/src platform-common/src
COPY module-iam/src module-iam/src
COPY module-governance/src module-governance/src
COPY module-dataset/src module-dataset/src
COPY module-product/src module-product/src
COPY platform-boot/src platform-boot/src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN (sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g; s/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list.d/ubuntu.sources 2>/dev/null; sed -i 's/deb.debian.org/mirrors.aliyun.com/g; s/security.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources 2>/dev/null) || true
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fonts-droid-fallback \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/platform-boot/target/platform-app.jar app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx640m"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
    CMD curl -sf http://localhost:8080/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
