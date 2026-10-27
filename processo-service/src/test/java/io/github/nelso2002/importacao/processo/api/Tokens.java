package io.github.nelso2002.importacao.processo.api;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Tokens falsos no formato do Keycloak para os testes de API.
 * O jwt() do spring-security-test pula a verificação de assinatura, mas os papéis passam pela
 * conversão de verdade (SegurancaConfig.papeis), então um erro nela quebra os testes.
 */
final class Tokens {

    private Tokens() {
    }

    static RequestPostProcessor usuario(String nome, String... papeis) {
        return jwt()
                // o jwt() usa o sub como nome da autenticação; os tokens reais usam preferred_username (conversor)
                .jwt(token -> token
                        .subject(nome)
                        .claim("preferred_username", nome)
                        .claim("realm_access", Map.of("roles", List.of(papeis))))
                .authorities(SegurancaConfig::papeis);
    }

    static RequestPostProcessor operador() {
        return usuario("operador", "OPERADOR", "CONSULTA");
    }

    static RequestPostProcessor consulta() {
        return usuario("consulta", "CONSULTA");
    }
}
