package io.github.nelso2002.importacao.processo.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;

/**
 * OAuth2 Resource Server: toda requisição traz um JWT do Keycloak no header Authorization.
 * O Spring confere assinatura, emissor, audiência e validade (application.yaml); quem pode o quê fica
 * nos controllers, com @PreAuthorize.
 */
@Configuration
@EnableMethodSecurity
public class SegurancaConfig {

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(regras -> regras
                        // o healthcheck do Docker não tem token
                        .requestMatchers("/actuator/health/**").permitAll()
                        // o resto exige um token válido; o papel exigido está em cada endpoint
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor.jwt(jwt -> jwt.jwtAuthenticationConverter(papeisDoKeycloak())))
                // sem sessão: cada requisição se autentica pelo próprio token
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF explora o cookie que o navegador manda sozinho; aqui não há cookie, o token vai num header
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /**
     * O Keycloak põe os papéis em realm_access.roles; o Spring, por padrão, procura em scope.
     * OPERADOR vira ROLE_OPERADOR, que é o que hasRole('OPERADOR') confere.
     */
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
