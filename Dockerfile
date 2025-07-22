FROM openjdk:21-jdk-slim as builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN apt-get update && apt-get install -y maven && \
    mvn clean package -DskipTests && \
    mv target/*.jar app.jar

FROM openjdk:21-jdk-slim

WORKDIR /app

ENV JAVA_OPTS="-server \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:+DisableExplicitGC \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+UseJVMCICompiler \
    -XX:MaxGCPauseMillis=10 \
    -XX:G1HeapRegionSize=4m \
    -Xmx120m \
    -Xms120m \
    -XX:MaxDirectMemorySize=20m \
    -XX:+TieredCompilation \
    -XX:TieredStopAtLevel=1 \
    -Xlog:disable \
    -XX:-PrintGC \
    -XX:-PrintGCDetails"

COPY --from=builder /app/app.jar .

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
