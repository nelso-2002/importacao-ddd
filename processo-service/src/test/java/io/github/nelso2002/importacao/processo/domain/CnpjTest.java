package io.github.nelso2002.importacao.processo.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CnpjTest {

    @Test
    void aceitaCnpjNumerico() {
        var cnpj = new Cnpj("11222333000181");

        assertThat(cnpj.numero()).isEqualTo("11222333000181");
    }

    @Test
    void aceitaCnpjAlfanumerico() {
        // exemplo oficial da Receita Federal: 12.ABC.345/01DE-35
        var cnpj = new Cnpj("12ABC34501DE35");

        assertThat(cnpj.numero()).isEqualTo("12ABC34501DE35");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "1122233300018",       // curto (13)
            "112223330001810",     // longo (15)
            "11222333/00018",      // caractere fora de [0-9A-Z], com 14 posições
            "12abc34501de35",      // minúsculas: só a forma canônica (maiúsculas) é aceita
            "11222333000A81",      // letra na posição de dígito verificador
            "11.222.333/0001-81"   // com máscara: rejeitada, mesma decisão do Ncm
    })
    void rejeitaFormatoInvalido(String entrada) {
        assertThatThrownBy(() -> new Cnpj(entrada))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CNPJ inválido");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11222333000182",      // segundo dígito errado
            "11222333000191",      // primeiro dígito errado
            "12ABC34501DE36"       // alfanumérico com dígito errado
    })
    void rejeitaDigitoVerificadorErrado(String entrada) {
        assertThatThrownBy(() -> new Cnpj(entrada))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CNPJ inválido");
    }

    @Test
    void rejeitaTodosOsDigitosIguais() {
        // é o único repetido que passa no cálculo do dígito; por isso a regra precisa existir
        assertThatThrownBy(() -> new Cnpj("00000000000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CNPJ inválido");
    }
}
