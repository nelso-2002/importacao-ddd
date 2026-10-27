import {
  CurrencyPipe,
  DatePipe,
  DecimalPipe,
  formatCurrency,
  getCurrencySymbol,
} from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, LOCALE_ID } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, filter, forkJoin, map, merge, Observable, of, Subject, switchMap } from 'rxjs';
import { FormItem } from './form-item';
import {
  EventoHistorico,
  ProcessoDetalhe,
  ProcessosApi,
  ROTULOS_STATUS,
} from './processos-api';

interface Carregado {
  processo: ProcessoDetalhe | null;
  historico: EventoHistorico[];
  erro: string | null;
}

@Component({
  selector: 'app-pagina-processo',
  imports: [RouterLink, DatePipe, DecimalPipe, CurrencyPipe, FormItem],
  templateUrl: './pagina-processo.html',
  styleUrl: './pagina-processo.scss',
})
export class PaginaProcesso {
  private readonly api = inject(ProcessosApi);
  private readonly locale = inject(LOCALE_ID);

  // Preenchido pelo roteador a partir de /processos/:id (withComponentInputBinding).
  readonly id = input.required<string>();

  protected readonly rotulos = ROTULOS_STATUS;

  // Disparado pelo formulário de item: recarrega na hora, sem esperar o SSE.
  protected readonly recarregar = new Subject<void>();

  // Para cada id: carrega ao abrir, ao adicionar um item e quando o SSE avisa que este processo mudou
  // (inclusive mudanças feitas em outra aba ou por outra pessoa).
  // Detalhe (consulta-service) e histórico (processo-service) em paralelo; forkJoin espera os dois.
  private readonly carregado = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        merge(
          of(undefined),
          this.recarregar,
          this.api.mudancas().pipe(filter((mudanca) => mudanca.id === id)),
        ).pipe(switchMap(() => this.carregar(id))),
      ),
    ),
  );

  protected readonly processo = computed(() => this.carregado()?.processo ?? null);
  protected readonly erro = computed(() => this.carregado()?.erro ?? null);
  // mais recente primeiro, como numa linha do tempo
  protected readonly historico = computed(() => [...(this.carregado()?.historico ?? [])].reverse());

  private carregar(id: string): Observable<Carregado> {
    return forkJoin({ processo: this.api.detalhe(id), historico: this.api.historico(id) }).pipe(
      map(({ processo, historico }): Carregado => ({ processo, historico, erro: null })),
      catchError((erro: HttpErrorResponse) =>
        of<Carregado>({
          processo: null,
          historico: [],
          erro:
            erro.status === 404
              ? 'Processo não encontrado.'
              : 'Não foi possível carregar o processo.',
        }),
      ),
    );
  }

  protected descrever(evento: EventoHistorico): { titulo: string; detalhe: string } {
    // Narrowing pelo "tipo": dentro de cada case o TypeScript sabe o formato de evento.dados.
    switch (evento.tipo) {
      case 'ProcessoAberto':
        return {
          titulo: 'Processo aberto',
          detalhe: `${evento.dados.referencia} · importador ${evento.dados.importador.numero}`,
        };
      case 'ItemAdicionado': {
        const item = evento.dados.item;
        const valor = formatCurrency(
          item.valorTotal.valor,
          this.locale,
          getCurrencySymbol(item.valorTotal.moeda, 'wide', this.locale),
          item.valorTotal.moeda,
        );
        return {
          titulo: `Item ${item.numero} adicionado`,
          detalhe: `NCM ${item.ncm.codigo} · ${item.quantidade} un. · ${valor}`,
        };
      }
      case 'ItemRemovido':
        return { titulo: `Item ${evento.dados.numeroItem} removido`, detalhe: '' };
      case 'DeclaracaoRegistrada':
        return { titulo: 'Declaração registrada', detalhe: '' };
      case 'ProcessoDesembaracado':
        return { titulo: 'Mercadoria desembaraçada', detalhe: '' };
      case 'ProcessoCancelado':
        return { titulo: 'Processo cancelado', detalhe: evento.dados.motivo };
      default: {
        // Mesma ideia do switch sem default no Java: se surgir um tipo novo na união e
        // não for tratado acima, esta atribuição deixa de compilar.
        const naoTratado: never = evento;
        return naoTratado;
      }
    }
  }
}
