package io.github.nelso2002.importacao.processo.application;

import io.github.nelso2002.importacao.processo.domain.Cnpj;
import io.github.nelso2002.importacao.processo.domain.StatusProcesso;
import io.github.nelso2002.importacao.processo.infrastructure.ProcessoRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Sem Spring: o caso de uso é uma classe comum, montada à mão com o adaptador em memória.
class AbrirProcessoTest {

    private static final Cnpj IMPORTADOR = new Cnpj("11222333000181");

    private final ProcessoRepositoryEmMemoria repositorio = new ProcessoRepositoryEmMemoria();
    private final AbrirProcesso abrirProcesso = new AbrirProcesso(repositorio);

    @Test
    void abreESalvaOProcesso() {
        var id = abrirProcesso.executar(new AbrirProcesso.Comando(IMPORTADOR, "IMP-2026/0001"));

        var salvo = repositorio.buscarPorId(id).orElseThrow();
        assertThat(salvo.status()).isEqualTo(StatusProcesso.ABERTO);
        assertThat(salvo.importador()).isEqualTo(IMPORTADOR);
        assertThat(salvo.referencia()).isEqualTo("IMP-2026/0001");
    }

    @Test
    void regraDoDominioChegaAteQuemChamou() {
        assertThatThrownBy(() -> abrirProcesso.executar(new AbrirProcesso.Comando(IMPORTADOR, " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referência");
    }
}
