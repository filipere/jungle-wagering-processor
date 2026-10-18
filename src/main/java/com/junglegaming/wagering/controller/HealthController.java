package com.junglegaming.wagering.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoints de saúde da aplicação. Não precisam de autenticação.
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    // Liveness: se respondeu, a aplicação está viva
    @GetMapping("/live")
    public ResponseEntity<Void> liveness() {
        return ResponseEntity.ok().build();
    }

    // Readiness: confere se a aplicação consegue falar com o banco
    @GetMapping("/ready")
    public ResponseEntity<Void> readiness() {
        try {
            // Consulta simples só para testar a conexão
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Banco fora do ar: 503 para avisar que ainda não está pronta
            return ResponseEntity.status(503).build();
        }
    }
}