# ===============================
# ETAPA 1: CONSTRUCCIÓN (BUILD)
# ===============================
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copiamos el POM
COPY pom.xml .

# Creamos la estructura de directorios
RUN mkdir -p src/main/resources

# 🔥 TRUCO MAGICO: Generamos el application.properties AQUÍ, limpio y en UTF-8 puro.
# Usamos printf para evitar problemas de saltos de línea raros de Windows.
RUN printf "spring.application.name=backend-sp\n\
server.port=\${PORT:8080}\n\
logging.level.root=INFO\n\
spring.data.mongodb.uri=\${MONGO_URI}\n\
spring.data.mongodb.database=plant-monitor\n\
spring.data.mongodb.auto-index-creation=true\n\
influxdb.url=\${INFLUX_URL}\n\
influxdb.token=\${INFLUX_TOKEN}\n\
influxdb.org=\${INFLUX_ORG}\n\
influxdb.bucket=\${INFLUX_BUCKET}\n\
mqtt.broker.url=\${MQTT_URL}\n\
mqtt.username=\${MQTT_USER}\n\
mqtt.password=\${MQTT_PASSWORD}\n\
mqtt.client.id=\${MQTT_CLIENT_ID}\n\
mqtt.topic=planta/+/lecturas\n\
spring.mail.host=smtp.gmail.com\n\
spring.mail.port=587\n\
spring.mail.username=\${MAIL_USER}\n\
spring.mail.password=\${MAIL_PASSWORD}\n\
spring.mail.properties.mail.smtp.auth=true\n\
spring.mail.properties.mail.smtp.starttls.enable=true\n\
device.thresholds.humiditySoil.min=35\n\
device.thresholds.temperature.max=38.0" > src/main/resources/application.properties

# Copiamos el código fuente (Java)
COPY src/main/java ./src/main/java

# Empaquetamos
RUN mvn clean package -DskipTests

# ===============================
# ETAPA 2: EJECUCIÓN (RUN)
# ===============================
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]