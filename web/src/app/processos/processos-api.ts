import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

// Leituras vêm do consulta-service (/api/consultas → :8081);
// o histórico vem do event store do processo-service (/api/processos → :8080). Ver proxy.conf.json.

export type Status = 'ABERTO' | 'REGISTRADO' | 'DESEMBARACADO' | 'CANCELADO';

export interface ProcessoResumo {
  id: string;
  referencia: string;
  importador: string;
  status: Status;
  quantidadeItens: number;
  atualizadoEm: string;
}

export interface ItemResumo {
  numero: number;
  ncm: string;
  quantidade: number;
  valor: number;
  moeda: string;
}

export interface ProcessoDetalhe {
  id: string;
  referencia: string;
  importador: string;
  status: Status;
  motivoCancelamento: string | null;
  itens: ItemResumo[];
  atualizadoEm: string;
}

// União discriminada pelo campo "tipo": o equivalente TypeScript da sealed interface ProcessoEvento.
export type EventoHistorico = { versao: number; ocorridoEm: string } & (
  | { tipo: 'ProcessoAberto'; dados: { importador: { numero: string }; referencia: string } }
  | {
      tipo: 'ItemAdicionado';
      dados: {
        item: {
          numero: number;
          ncm: { codigo: string };
          quantidade: number;
          valorTotal: { valor: number; moeda: string };
        };
      };
    }
  | { tipo: 'ItemRemovido'; dados: { numeroItem: number } }
  | { tipo: 'DeclaracaoRegistrada'; dados: object }
  | { tipo: 'ProcessoDesembaracado'; dados: object }
  | { tipo: 'ProcessoCancelado'; dados: { motivo: string } }
);

export interface FiltroProcessos {
  busca: string;
  status: Status | '';
}

export interface NovoProcesso {
  importador: string;
  referencia: string;
}

export interface NovoItem {
  ncm: string;
  quantidade: number;
  valor: number;
  moeda: string;
}

@Injectable({ providedIn: 'root' })
export class ProcessosApi {
  private readonly http = inject(HttpClient);

  listar(filtro: FiltroProcessos): Observable<ProcessoResumo[]> {
    let params = new HttpParams();
    if (filtro.busca.trim()) {
      params = params.set('busca', filtro.busca.trim());
    }
    if (filtro.status) {
      params = params.set('status', filtro.status);
    }
    return this.http.get<ProcessoResumo[]>('/api/consultas/processos', { params });
  }

  detalhe(id: string): Observable<ProcessoDetalhe> {
    return this.http.get<ProcessoDetalhe>(`/api/consultas/processos/${id}`);
  }

  historico(id: string): Observable<EventoHistorico[]> {
    return this.http.get<EventoHistorico[]>(`/api/processos/${id}/historico`);
  }

  abrir(processo: NovoProcesso): Observable<{ id: string }> {
    return this.http.post<{ id: string }>('/api/processos', processo);
  }

  adicionarItem(processoId: string, item: NovoItem): Observable<ItemResumo> {
    return this.http.post<ItemResumo>(`/api/processos/${processoId}/itens`, item);
  }

  /**
   * Stream SSE do consulta-service como Observable: cada evento "processo" vira um next().
   * Nada acontece até alguém assinar, e o unsubscribe fecha a conexão (teardown).
   * Erros de rede não encerram o Observable: o próprio EventSource reconecta sozinho.
   */
  mudancas(): Observable<ProcessoResumo> {
    return new Observable<ProcessoResumo>((assinante) => {
      const fonte = new EventSource('/api/consultas/processos/stream');
      fonte.addEventListener('processo', (evento) =>
        assinante.next(JSON.parse((evento as MessageEvent<string>).data)),
      );
      return () => fonte.close();
    });
  }
}

/** Mensagem legível a partir do ProblemDetail que os serviços devolvem. */
export function mensagemDeErro(erro: HttpErrorResponse): string {
  const problema = erro.error as { detail?: string; erros?: Record<string, string> } | null;
  if (problema?.erros) {
    return Object.entries(problema.erros)
      .map(([campo, mensagem]) => `${campo}: ${mensagem}`)
      .join('; ');
  }
  return problema?.detail ?? 'Não foi possível concluir a operação. Os serviços estão no ar?';
}

export const ROTULOS_STATUS: Record<Status, string> = {
  ABERTO: 'Aberto',
  REGISTRADO: 'Registrado',
  DESEMBARACADO: 'Desembaraçado',
  CANCELADO: 'Cancelado',
};
