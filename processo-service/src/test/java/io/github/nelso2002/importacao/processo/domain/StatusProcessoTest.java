package io.github.nelso2002.importacao.processo.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusProcessoTest {

    @ParameterizedTest
    @CsvSource({
            "ABERTO,     REGISTRADO",     // registrarDeclaracao()
            "ABERTO,     CANCELADO",      // cancelar(motivo)
            "REGISTRADO, DESEMBARACADO",  // desembaracar()
            "REGISTRADO, CANCELADO"       // cancelar(motivo)
    })
    void permiteTransicao(StatusProcesso origem, StatusProcesso destino) {
        assertThat(origem.transicionarPara(destino)).isEqualTo(destino);
    }

    // As 12 combinações restantes das 16 possíveis (4 origens x 4 destinos).
    @ParameterizedTest
    @CsvSource({
            "ABERTO,        ABERTO",
            "ABERTO,        DESEMBARACADO",  // pula o registro da declaração
            "REGISTRADO,    ABERTO",         // não volta a aceitar itens
            "REGISTRADO,    REGISTRADO",
            "DESEMBARACADO, ABERTO",         // DESEMBARACADO é final
            "DESEMBARACADO, REGISTRADO",
            "DESEMBARACADO, DESEMBARACADO",
            "DESEMBARACADO, CANCELADO",      // regra do roteiro: não cancela depois de desembaraçado
            "CANCELADO,     ABERTO",         // CANCELADO é final
            "CANCELADO,     REGISTRADO",
            "CANCELADO,     DESEMBARACADO",
            "CANCELADO,     CANCELADO"       // cancelar duas vezes é erro, não operação idempotente
    })
    void rejeitaTransicao(StatusProcesso origem, StatusProcesso destino) {
        assertThatThrownBy(() -> origem.transicionarPara(destino))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Transição inválida: " + origem + " -> " + destino);
    }
}
