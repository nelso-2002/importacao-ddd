-- Event Sourcing: o processo deixa de ser gravado como estado e passa a ser a sequência dos seus eventos.
-- A chave (aggregate_id, version) é o controle de concorrência: duas gravações sobre a mesma versão
-- tentam inserir a mesma chave e só a primeira entra.
create table event_store (
    aggregate_id uuid        not null,
    version      integer     not null,
    type         varchar(50) not null,
    payload      jsonb       not null,
    occurred_at  timestamptz not null default now(),
    primary key (aggregate_id, version)
);

-- Estado da Etapa 1, substituído pelo event store. Em produção, antes de apagar, cada processo seria
-- convertido em eventos (ProcessoAberto + ItemAdicionado...); aqui são só dados de desenvolvimento.
drop table item_processo;
drop table processo;
