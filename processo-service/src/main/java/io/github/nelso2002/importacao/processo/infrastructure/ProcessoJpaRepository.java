package io.github.nelso2002.importacao.processo.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// Spring Data gera a implementação; fica restrito à infraestrutura.
interface ProcessoJpaRepository extends JpaRepository<ProcessoEntity, UUID> {
}
