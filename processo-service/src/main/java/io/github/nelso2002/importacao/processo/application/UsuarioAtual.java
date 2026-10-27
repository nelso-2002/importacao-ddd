package io.github.nelso2002.importacao.processo.application;

/**
 * Quem está executando o caso de uso, ligado pelo UsuarioDaRequisicaoFilter durante cada requisição.
 *
 * ScopedValue (Java 25) em vez de ThreadLocal: o valor é imutável, só existe dentro do bloco em que foi ligado
 * (some sozinho no fim, sem remove() para esquecer) e é barato com virtual threads, que são milhares.
 */
public final class UsuarioAtual {

    public static final ScopedValue<String> USUARIO = ScopedValue.newInstance();

    private UsuarioAtual() {
    }

    /** Fora de uma requisição (job agendado, teste chamando o caso de uso direto) não há usuário. */
    public static String nome() {
        return USUARIO.orElse("sistema");
    }
}
