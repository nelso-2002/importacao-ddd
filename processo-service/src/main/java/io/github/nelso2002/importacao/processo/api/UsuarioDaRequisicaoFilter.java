package io.github.nelso2002.importacao.processo.api;

import io.github.nelso2002.importacao.processo.application.UsuarioAtual;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Liga o nome do usuário autenticado ao UsuarioAtual durante o resto da requisição.
 * Roda depois da cadeia do Spring Security (ordem padrão de um Filter), quando o token já foi validado.
 */
@Component
class UsuarioDaRequisicaoFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null) {
            chain.doFilter(request, response);
            return;
        }
        // tudo o que a requisição fizer dentro de call() enxerga o usuário; ao sair, a ligação desaparece
        try {
            ScopedValue.where(UsuarioAtual.USUARIO, autenticacao.getName()).call(() -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
