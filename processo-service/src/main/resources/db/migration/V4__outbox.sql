-- Padrão Outbox: cada evento gravado no event_store também entra aqui, na mesma transação.
-- Um job publica as linhas pendentes no Kafka e as marca como publicadas.
-- Assim não existe "gravou no banco mas não publicou" (nem o contrário): o banco é a única escrita da transação.
create table outbox (
    id           bigserial   primary key,          -- ordem de publicação
    aggregate_id uuid        not null,
    version      integer     not null,
    type         varchar(50) not null,
    payload      jsonb       not null,
    occurred_at  timestamptz not null default now(),
    published_at timestamptz                        -- null = ainda não publicado
);

-- índice parcial: só as pendentes, que é o que o job procura; fica pequeno mesmo com milhões de publicadas
create index outbox_pendentes on outbox (id) where published_at is null;
