-- Quem causou cada evento (auditoria: "quem fez o quê"). Metadado do evento, fora do payload do domínio.
-- Eventos anteriores ficam sem usuário: o histórico não é reescrito.
alter table event_store add column usuario varchar(100);
alter table outbox add column usuario varchar(100);
