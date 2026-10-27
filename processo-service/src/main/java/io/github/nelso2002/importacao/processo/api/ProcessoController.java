package io.github.nelso2002.importacao.processo.api;

import io.github.nelso2002.importacao.processo.application.AbrirProcesso;
import io.github.nelso2002.importacao.processo.application.AdicionarItem;
import io.github.nelso2002.importacao.processo.domain.Cnpj;
import io.github.nelso2002.importacao.processo.domain.Dinheiro;
import io.github.nelso2002.importacao.processo.domain.ItemProcesso;
import io.github.nelso2002.importacao.processo.domain.Ncm;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Borda HTTP: converte JSON em value objects, chama o caso de uso e devolve o resultado.
 * Bean Validation cuida da presença dos campos; o formato (CNPJ, NCM, moeda) é validado pelos value objects.
 */
@RestController
@RequestMapping("/processos")
class ProcessoController {

    record AbrirProcessoRequest(@NotBlank String importador, @NotBlank String referencia) {}

    record ProcessoCriadoResponse(UUID id) {}

    record AdicionarItemRequest(@NotBlank String ncm,
                                @NotNull BigDecimal quantidade,
                                @NotNull BigDecimal valor,
                                @NotBlank String moeda) {}

    record ItemResponse(int numero, String ncm, BigDecimal quantidade, BigDecimal valor, String moeda) {

        static ItemResponse de(ItemProcesso item) {
            return new ItemResponse(
                    item.numero(),
                    item.ncm().codigo(),
                    item.quantidade(),
                    item.valorTotal().valor(),
                    item.valorTotal().moeda().getCurrencyCode());
        }
    }

    private final AbrirProcesso abrirProcesso;
    private final AdicionarItem adicionarItem;

    ProcessoController(AbrirProcesso abrirProcesso, AdicionarItem adicionarItem) {
        this.abrirProcesso = abrirProcesso;
        this.adicionarItem = adicionarItem;
    }

    @PostMapping
    ResponseEntity<ProcessoCriadoResponse> abrir(@Valid @RequestBody AbrirProcessoRequest request,
                                                 UriComponentsBuilder uri) {
        var comando = new AbrirProcesso.Comando(new Cnpj(request.importador()), request.referencia());
        var id = abrirProcesso.executar(comando);
        var location = uri.path("/processos/{id}").buildAndExpand(id.valor()).toUri();
        return ResponseEntity.created(location).body(new ProcessoCriadoResponse(id.valor()));
    }

    @PostMapping("/{id}/itens")
    ResponseEntity<ItemResponse> adicionarItem(@PathVariable String id,
                                               @Valid @RequestBody AdicionarItemRequest request,
                                               UriComponentsBuilder uri) {
        var processoId = ProcessoId.de(id);
        var comando = new AdicionarItem.Comando(
                processoId,
                new Ncm(request.ncm()),
                request.quantidade(),
                new Dinheiro(request.valor().toPlainString(), request.moeda()));
        var item = adicionarItem.executar(comando);
        var location = uri.path("/processos/{id}/itens/{numero}")
                .buildAndExpand(processoId.valor(), item.numero()).toUri();
        return ResponseEntity.created(location).body(ItemResponse.de(item));
    }
}
