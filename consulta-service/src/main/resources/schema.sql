-- Projeção de leitura do consulta-service, no banco dele.
-- Sem Flyway de propósito: a tabela é descartável. Se o formato mudar, apaga-se a tabela e o consumidor
-- a reconstrói relendo o tópico processo-eventos desde o início (novo group-id + auto-offset-reset=earliest).
create table if not exists processo_resumo (
    id                  uuid        primary key,
    importador          varchar(14) not null,
    referencia          text        not null,
    status              varchar(20) not null,
    motivo_cancelamento text,
    itens               jsonb       not null default '[]',
    atualizado_em       timestamptz not null,
    -- última versão de evento aplicada: base da idempotência (repetidos e antigos são ignorados)
    versao              integer     not null default 0
);
