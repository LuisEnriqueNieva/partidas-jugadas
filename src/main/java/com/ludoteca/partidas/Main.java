package com.ludoteca.partidas;

import io.javalin.Javalin;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Main {

    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "5433");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "partidas_db");
    private static final String DB_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "partidas_user");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "partidas_pass");
    private static final String CATALOGO_URL = System.getenv().getOrDefault("CATALOGO_URL", "http://localhost:8001");

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
        config.staticFiles.add(staticFiles -> {
            staticFiles.directory = "/public";
            staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
        });
        config.bundledPlugins.enableCors(cors -> {
            cors.addRule(it -> {
                it.anyHost();
                });
            });
        }).start(8002);

        app.get("/", ctx -> ctx.result("OK"));

        app.get("/partidas", ctx -> {
            List<Map<String, Object>> partidas = new ArrayList<>();
            String sql = "SELECT * FROM partidas";
            System.out.println("DEBUG-BYTES=" + java.util.Arrays.toString(DB_USER.getBytes()));
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    Map<String, Object> partida = new HashMap<>();
                    partida.put("id", rs.getInt("id"));
                    partida.put("mesa", rs.getInt("mesa"));
                    partida.put("juego_id", rs.getInt("juego_id"));
                    partida.put("fecha", rs.getDate("fecha").toString());
                    partida.put("resultado", rs.getString("resultado"));
                    partidas.add(partida);
                }

                ctx.json(partidas);

            } catch (Exception e) {
                ctx.status(500).result("Error conectando a la base de datos: " + e.getMessage());
            }
        });

        app.post("/partidas", ctx -> {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);

            int mesa = (int) body.get("mesa");
            int juegoId = (int) body.get("juego_id");
            String fecha = (String) body.get("fecha");
            String resultado = (String) body.get("resultado");
            List<String> jugadores = (List<String>) body.get("jugadores");
            
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(CATALOGO_URL + "/juegos/" + juegoId))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    ctx.status(400).json(Map.of("error", "El juego con id " + juegoId + " no existe en el catálogo."));
                    return;
                }
            } catch (Exception e) {
                ctx.status(503).json(Map.of("error", "No se pudo contactar al Microservicio 1 (catálogo): " + e.getMessage()));
                return;
            }
            

            String sqlInsertPartida = "INSERT INTO partidas (mesa, juego_id, fecha, resultado) VALUES (?, ?, ?, ?) RETURNING id";

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                int partidaId;
                try (PreparedStatement stmt = conn.prepareStatement(sqlInsertPartida)) {
                    stmt.setInt(1, mesa);
                    stmt.setInt(2, juegoId);
                    stmt.setDate(3, java.sql.Date.valueOf(fecha));
                    stmt.setString(4, resultado);
                    ResultSet rs = stmt.executeQuery();
                    rs.next();
                    partidaId = rs.getInt("id");
                }

                String sqlInsertJugador = "INSERT INTO partida_jugadores (partida_id, nombre_jugador) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sqlInsertJugador)) {
                    for (String jugador : jugadores) {
                        stmt.setInt(1, partidaId);
                        stmt.setString(2, jugador);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }

                ctx.status(201).json(Map.of("id", partidaId, "mensaje", "Partida creada correctamente"));

            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", "Error guardando la partida: " + e.getMessage()));
            }
        });

        app.get("/partidas/{id}", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            String sqlPartida = "SELECT * FROM partidas WHERE id = ?";
            String sqlJugadores = "SELECT nombre_jugador FROM partida_jugadores WHERE partida_id = ?";

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                Map<String, Object> partida = null;

                try (PreparedStatement stmt = conn.prepareStatement(sqlPartida)) {
                    stmt.setInt(1, id);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        partida = new HashMap<>();
                        partida.put("id", rs.getInt("id"));
                        partida.put("mesa", rs.getInt("mesa"));
                        partida.put("juego_id", rs.getInt("juego_id"));
                        partida.put("fecha", rs.getDate("fecha").toString());
                        partida.put("resultado", rs.getString("resultado"));
                    }
                }

                if (partida == null) {
                    ctx.status(404).json(Map.of("error", "Partida con id " + id + " no encontrada."));
                    return;
                }

                List<String> jugadores = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(sqlJugadores)) {
                    stmt.setInt(1, id);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        jugadores.add(rs.getString("nombre_jugador"));
                    }
                }
                partida.put("jugadores", jugadores);

                ctx.json(partida);

            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", "Error obteniendo la partida: " + e.getMessage()));
            }
        });

        app.put("/partidas/{id}", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);

            int mesa = (int) body.get("mesa");
            int juegoId = (int) body.get("juego_id");
            String fecha = (String) body.get("fecha");
            String resultado = (String) body.get("resultado");

            String sql = "UPDATE partidas SET mesa = ?, juego_id = ?, fecha = ?, resultado = ? WHERE id = ?";

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, mesa);
                stmt.setInt(2, juegoId);
                stmt.setDate(3, java.sql.Date.valueOf(fecha));
                stmt.setString(4, resultado);
                stmt.setInt(5, id);

                int filas = stmt.executeUpdate();

                if (filas == 0) {
                    ctx.status(404).json(Map.of("error", "Partida con id " + id + " no encontrada."));
                } else {
                    ctx.json(Map.of("mensaje", "Partida actualizada correctamente"));
                }

            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", "Error actualizando la partida: " + e.getMessage()));
            }
        });

        app.delete("/partidas/{id}", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM partida_jugadores WHERE partida_id = ?")) {
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM partidas WHERE id = ?")) {
                    stmt.setInt(1, id);
                    int filas = stmt.executeUpdate();

                    if (filas == 0) {
                        ctx.status(404).json(Map.of("error", "Partida con id " + id + " no encontrada."));
                    } else {
                        ctx.json(Map.of("mensaje", "Partida eliminada correctamente"));
                    }
                }

            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", "Error eliminando la partida: " + e.getMessage()));
            }
        });
    }
}