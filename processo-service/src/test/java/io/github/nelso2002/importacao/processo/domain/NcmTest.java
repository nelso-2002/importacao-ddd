package io.github.nelso2002.importacao.processo.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NcmTest {

    @Test
    void aceitaCodigoComOitoDigitos() {
        var ncm = new Ncm("90308410");

        assertThat(ncm.codigo()).isEqualTo("90308410");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "999999",      // curto
            "999999999",   // longo
            "9030841A",    // letra, com 8 caracteres
            "9030.841",    // ponto, com 8 caracteres
            "        "     // 8 espaços
    })
    void rejeitaFormatoInvalido(String entrada) {
        assertThatThrownBy(() -> new Ncm(entrada))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("código da ncm inválido:");
    }
}
