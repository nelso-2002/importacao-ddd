package io.github.nelso2002.importacao.processo.domain;

import java.util.UUID;

public record ProcessoId(UUID valor) {

    public ProcessoId {
        if (valor == null) {
            throw new IllegalArgumentException("ProcessoId inválido: null");
        }
    }

    public static ProcessoId novo() {
        return new ProcessoId(UUID.randomUUID());
    }

    public static ProcessoId de(String texto) {
        if (texto != null) {
            try {
                var uuid = UUID.fromString(texto);
                // fromString aceita formas abreviadas como "1-1-1-1-1"; só a forma canônica passa
                if (uuid.toString().equalsIgnoreCase(texto)) {
                    return new ProcessoId(uuid);
                }
            } catch (IllegalArgumentException _) {
                // cai no throw abaixo, com a nossa mensagem
            }
        }
        throw new IllegalArgumentException("ProcessoId inválido: " + texto);
    }
}
