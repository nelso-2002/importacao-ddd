package io.github.nelso2002.importacao.processo.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessoIdTest {

    @Test
    void geraIdsDiferentesACadaChamada() {
        var primeiro = ProcessoId.novo();
        var segundo = ProcessoId.novo();

        assertThat(primeiro).isNotEqualTo(segundo);
    }

    @Test
    void recriaOMesmoIdAPartirDoTexto() {
        var original = ProcessoId.novo();

        var recriado = ProcessoId.de(original.valor().toString());

        assertThat(recriado).isEqualTo(original);
    }

    @Test
    void aceitaTextoEmMaiusculas() {
        var id = ProcessoId.de("123E4567-E89B-12D3-A456-426614174000");

        assertThat(id).isEqualTo(ProcessoId.de("123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    void rejeitaUuidNulo() {
        assertThatThrownBy(() -> new ProcessoId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ProcessoId inválido");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "abc",                                   // não é UUID
            "1-1-1-1-1",                             // forma abreviada que o UUID.fromString aceita
            "123e4567e89b12d3a456426614174000",      // sem hífens
            " 123e4567-e89b-12d3-a456-426614174000"  // espaço na frente
    })
    void rejeitaTextoQueNaoEUuidCanonico(String entrada) {
        assertThatThrownBy(() -> ProcessoId.de(entrada))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ProcessoId inválido");
    }
}
