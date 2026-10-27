package io.github.nelso2002.importacao.processo.api;

import io.github.nelso2002.importacao.processo.application.ProcessoNaoEncontradoException;
import io.github.nelso2002.importacao.processo.consulta.ConsultaProcessos;
import io.github.nelso2002.importacao.processo.consulta.ConsultaProcessos.EventoHistorico;
import io.github.nelso2002.importacao.processo.consulta.ConsultaProcessos.ProcessoDetalhe;
import io.github.nelso2002.importacao.processo.consulta.ConsultaProcessos.ProcessoResumo;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lado de leitura da API: só lê as tabelas de consulta, nunca carrega o agregado. */
@RestController
@RequestMapping("/processos")
class ConsultaController {

    private final ConsultaProcessos consultas;

    ConsultaController(ConsultaProcessos consultas) {
        this.consultas = consultas;
    }

    @GetMapping
    List<ProcessoResumo> listar() {
        return consultas.listar();
    }

    @GetMapping("/{id}")
    ProcessoDetalhe buscar(@PathVariable String id) {
        var processoId = ProcessoId.de(id);
        return consultas.buscar(processoId.valor())
                .orElseThrow(() -> new ProcessoNaoEncontradoException(processoId));
    }

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
