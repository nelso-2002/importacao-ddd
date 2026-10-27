import { HttpClient, HttpParams } from '@angular/common/http';
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
}

export const ROTULOS_STATUS: Record<Status, string> = {
  ABERTO: 'Aberto',
  REGISTRADO: 'Registrado',
  DESEMBARACADO: 'Desembaraçado',
  CANCELADO: 'Cancelado',
};
