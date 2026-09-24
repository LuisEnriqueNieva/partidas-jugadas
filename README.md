# partidas-jugadas

Microservicio 2 (MS2) del proyecto **Ludoteca** — CS2032 Cloud Computing (UTEC).

Administra las partidas jugadas en la ludoteca: mesa, juego, fecha, resultado y los jugadores que participaron.

## Stack

- Java 17 + [Javalin](https://javalin.io/)
- PostgreSQL
- Docker

## Modelo de datos

**`partidas`**
| Columna    | Tipo                    |
|------------|-------------------------|
| id         | integer (PK)            |
| mesa       | integer                 |
| juego_id   | integer                 |
| fecha      | date                    |
| resultado  | varchar(255)            |

**`partida_jugadores`**
| Columna         | Tipo                                   |
|-----------------|-----------------------------------------|
| id              | integer (PK)                            |
| partida_id      | integer (FK → partidas.id)              |
| nombre_jugador  | varchar(255)                            |

## Variables de entorno

| Variable       | Default                          | Descripción                                  |
|----------------|-----------------------------------|-----------------------------------------------|
| `DB_HOST`      | `localhost`                       | Host de Postgres                              |
| `DB_PORT`      | `5433`                            | Puerto de Postgres                            |
| `DB_NAME`      | `partidas_db`                     | Nombre de la base                             |
| `DB_USER`      | `partidas_user`                   | Usuario de la base                            |
| `DB_PASSWORD`  | `partidas_pass`                   | Password de la base                           |
| `CATALOGO_URL` | `http://localhost:8001`           | URL de MS1 (catálogo), usada para validar `juego_id` al crear una partida |

El servicio corre en el puerto **8002**.

## Endpoints

| Método | Ruta                        | Descripción                                                        |
|--------|-----------------------------|----------------------------------------------------------------------|
| GET    | `/health`                   | Healthcheck                                                          |
| GET    | `/partidas`                 | Lista todas las partidas                                             |
| GET    | `/partidas?jugador={nombre}`| Lista las partidas jugadas por un jugador específico                 |
| GET    | `/partidas/{id}`            | Detalle de una partida, incluye sus jugadores                        |
| POST   | `/partidas`                 | Crea una partida. Valida contra MS1 que `juego_id` exista antes de insertar |
| PUT    | `/partidas/{id}`            | Actualiza mesa, juego, fecha y resultado de una partida              |
| DELETE | `/partidas/{id}`            | Elimina una partida y sus jugadores asociados                        |

Body de `POST /partidas`:
```json
{
  "mesa": 4,
  "juego_id": 1,
  "fecha": "2026-09-12",
  "resultado": "Ganó Ana",
  "jugadores": ["Ana", "Carlos"]
}
```

## Documentación interactiva

Swagger disponible en `/swagger.html` (spec en `/openapi.yaml`), servido como archivos estáticos desde `src/main/resources/public`.

## Correr local

```bash
docker build -t partidas-jugadas .
docker run -p 8002:8002 \
  -e DB_HOST=... -e DB_PORT=... -e DB_NAME=... -e DB_USER=... -e DB_PASSWORD=... \
  -e CATALOGO_URL=... \
  partidas-jugadas
```
