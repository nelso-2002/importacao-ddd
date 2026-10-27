package io.github.nelso2002.importacao.consulta;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.Collection;
import java.util.List;

/**
 * OAuth2 Resource Server na versão reativa (WebFlux). Este serviço só lê:
 * os dois papéis podem tudo; sem token, 401.
 */
@Configuration
class SegurancaConfig {

    @Bean
    SecurityWebFilterChain seguranca(ServerHttpSecurity http) {
        return http
                .authorizeExchange(regras -> regras
                        .pathMatchers("/actuator/health/**").permitAll()
                        .anyExchange().hasAnyRole("OPERADOR", "CONSULTA"))
                .oauth2ResourceServer(servidor -> servidor.jwt(jwt -> jwt.jwtAuthenticationConverter(
                        new ReactiveJwtAuthenticationConverterAdapter(papeisDoKeycloak()))))
                // sem cookie de sessão, não há CSRF a explorar: o token vai no header Authorization
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }

    // Mesma conversão do processo-service, de propósito duplicada: os serviços não compartilham código.
    static JwtAuthenticationConverter papeisDoKeycloak() {
        var conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(SegurancaConfig::papeis);
        // nome do usuário em vez do id interno (sub) do Keycloak
        conversor.setPrincipalClaimName("preferred_username");
        return conversor;
    }

    /** Separado para os testes usarem a conversão de verdade com um token no formato do Keycloak. */
    static Collection<GrantedAuthority> papeis(Jwt jwt) {
        var realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> papeis)) {
            return List.of();
        }
        return papeis.stream()
                .map(papel -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + papel))
                .toList();
    }
}
