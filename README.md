# 🌱 Plant Monitor – Backend (Java / Spring Boot)

Backend del sistema **Plant Monitor**, responsable de:

* Recibir lecturas de sensores desde **ESP32 vía MQTT (HiveMQ)**
* Almacenar datos en **InfluxDB Cloud** y **MongoDB**
* Exponer una **API REST** para el frontend en **Next.js**
* Emitir datos en tiempo real (WebSocket)
* Enviar notificaciones por correo

---

## 🧠 Arquitectura General

```
ESP32 → HiveMQ (MQTT) → Spring Boot Backend
                               │
                               ├── InfluxDB (series de tiempo)
                               ├── MongoDB (datos y estado)
                               ├── WebSocket / REST API
                               └── Next.js Frontend
```

Cada ESP32 publica lecturas con un **Plant ID único**, el cual es utilizado en todo el sistema.

---

## 🛠️ Tecnologías

* Java 21
* Spring Boot 3.5.6
* Spring Integration MQTT
* InfluxDB Cloud
* MongoDB
* WebSocket
* JavaMail (Gmail SMTP)
* Docker & Dockerfile multi-stage (build + run)

---

## ⚙️ Variables de Entorno

El backend **NO debe contener credenciales hardcodeadas**. Todas las configuraciones se inyectan mediante variables de entorno.

### 📄 `.env.example`

```env
# ==================================
# SERVER
# ==================================
PORT=8080

# ==================================
# FRONTEND (CORS / URLs permitidas)
# ==================================
FRONTEND_URL=http://localhost:3000

# ==================================
# MONGODB (Railway / Atlas)
# ==================================
MONGO_URI=mongodb+srv://USER:PASSWORD@cluster.mongodb.net

# ==================================
# INFLUXDB CLOUD
# ==================================
INFLUX_URL=https://us-east-1-1.aws.cloud2.influxdata.com
INFLUX_TOKEN=TU_TOKEN_INFLUX
INFLUX_ORG=TU_ORG
INFLUX_BUCKET=planta_iot

# ==================================
# MQTT – HIVEMQ CLOUD
# ==================================
MQTT_URL=ssl://TU_CLUSTER.hivemq.cloud:8883
MQTT_USER=backend_user
MQTT_PASSWORD=PASSWORD_BACKEND
MQTT_CLIENT_ID=SpringCloud_01

# ==================================
# EMAIL (GMAIL SMTP)
# ==================================
MAIL_USER=correo@gmail.com
MAIL_PASSWORD=APP_PASSWORD_GMAIL
```

🔐 **Importante:**

* No subas tu `.env` real al repositorio
* Usa **App Password** en Gmail, no tu contraseña personal

---

## 🐳 Docker (Despliegue Recomendado)

El proyecto utiliza un **Dockerfile multi-stage**, que:

1. Compila el proyecto con Maven
2. Genera el `application.properties` dinámicamente
3. Ejecuta el `.jar` optimizado

### 📦 Build de la imagen

```bash
docker build -t plant-monitor-backend .
```

### ▶️ Ejecutar contenedor

```bash
docker run -d \
  --name plant-monitor-backend \
  -p 8080:8080 \
  --env-file .env \
  plant-monitor-backend
```

---

## 🚀 Despliegue en Producción

### Opciones compatibles:

* ✅ Railway
* ✅ Render
* ✅ VPS (Ubuntu + Docker)

Solo necesitas:

* Configurar las **variables de entorno**
* Exponer el puerto `8080`

---

## 📡 MQTT – Recepción de Lecturas

* Suscripción:

```
planta/+/lecturas
```

Ejemplo:

```
planta/Planta123/lecturas
```

El `Plant ID` se obtiene dinámicamente desde el tópico.

---

## 📬 Emails

El backend puede enviar alertas y notificaciones usando SMTP:

* Servidor: `smtp.gmail.com`
* Puerto: `587`
* TLS habilitado

---

## ✅ Buenas Prácticas

* ✅ Un **Plant ID único por ESP32**
* ✅ Un usuario/contraseña MQTT por sistema
* ✅ Variables sensibles solo en `.env`
* ✅ Mantener sincronizado ESP32 – Backend – Frontend

---

## 🧪 Desarrollo Local

```bash
mvn clean spring-boot:run
```

O usando Docker para igualar producción.

---

## 🆘 Solución de Problemas

* ❌ No llegan lecturas → revisa MQTT_USER / MQTT_PASSWORD
* ❌ Error CORS → revisa FRONTEND_URL
* ❌ No guarda datos → verifica InfluxDB / MongoDB

---

🌱 **Plant Monitor Backend**
Sistema IoT para monitoreo inteligente de plantas
