-- Lado de leitura (CQRS): uma linha pronta por processo, mantida pelo projetor a cada evento.
-- Pode ser apagada e refeita a partir do event_store a qualquer momento; a verdade está nos eventos.
create table processo_resumo (
    id                  uuid        primary key,
    importador          varchar(14) not null,
    referencia          text        not null,
    status              varchar(20) not null,
    motivo_cancelamento text,
    itens               jsonb       not null default '[]',
    atualizado_em       timestamptz not null
);
