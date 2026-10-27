-- Cópia da V3__processo_resumo.sql do processo-service, dono da tabela.
-- Nos testes o consulta-service não tem o Flyway do outro serviço; manter as duas iguais à mão
-- é o custo do banco compartilhado, que some na Etapa 4.
create table if not exists processo_resumo (
    id                  uuid        primary key,
    importador          varchar(14) not null,
    referencia          text        not null,
    status              varchar(20) not null,
    motivo_cancelamento text,
    itens               jsonb       not null default '[]',
    atualizado_em       timestamptz not null
);
