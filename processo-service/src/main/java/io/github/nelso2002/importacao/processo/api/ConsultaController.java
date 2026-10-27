package io.github.nelso2002.importacao.processo.api;

import io.github.nelso2002.importacao.processo.application.ProcessoNaoEncontradoException;
import io.github.nelso2002.importacao.processo.consulta.ConsultaProcessos;
import io.github.nelso2002.importacao.processo.consulta.ConsultaProcessos.EventoHistorico;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Histórico do processo, lido do event store. Lista e detalhe ficam em GET /consultas/processos (consulta-service). */
@RestController
@RequestMapping("/processos")
class ConsultaController {

    private final ConsultaProcessos consultas;

    ConsultaController(ConsultaProcessos consultas) {
        this.consultas = consultas;
    }

    @PreAuthorize("hasAnyRole('OPERADOR', 'CONSULTA')")
    @GetMapping("/{id}/historico")
    List<EventoHistorico> historico(@PathVariable String id) {
        var processoId = ProcessoId.de(id);
        var historico = consultas.historico(processoId.valor());
        if (historico.isEmpty()) {
            throw new ProcessoNaoEncontradoException(processoId);
        }
        return historico;
    }
}
