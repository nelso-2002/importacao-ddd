create table processo (
    id                  uuid        primary key,
    importador          varchar(14) not null,
    referencia          text        not null,
    status              varchar(20) not null,
    ultimo_numero_item  integer     not null,
    motivo_cancelamento text,
    versao              bigint      not null
);

-- Itens pertencem ao processo: a chave inclui o processo e o número é local a ele.
create table item_processo (
    processo_id uuid           not null references processo (id),
    numero      integer        not null,
    ncm         varchar(8)     not null,
    quantidade  numeric(19, 5) not null,
    valor       numeric(19, 4) not null,
    moeda       varchar(3)     not null,
    primary key (processo_id, numero)
);
