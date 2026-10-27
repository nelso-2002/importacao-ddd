-- A projeção de leitura passou a ser do consulta-service, no banco dele, alimentada pelo Kafka.
-- O processo-service fica só com a escrita (event_store, outbox) e o histórico, que é o próprio event_store.
drop table processo_resumo;
