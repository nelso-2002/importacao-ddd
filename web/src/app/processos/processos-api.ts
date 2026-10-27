import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, repeat, retry } from 'rxjs';
import { API } from '../ambiente';
import { Autenticacao } from '../autenticacao/autenticacao';

// Leituras vêm do consulta-service; comandos e histórico (o event store), do processo-service.

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
// usuario: quem causou o evento (null nos eventos gravados antes de existir login)
export type EventoHistorico = { versao: number; ocorridoEm: string; usuario: string | null } & (
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
  private readonly autenticacao = inject(Autenticacao);

  listar(filtro: FiltroProcessos): Observable<ProcessoResumo[]> {
    let params = new HttpParams();
    if (filtro.busca.trim()) {
      params = params.set('busca', filtro.busca.trim());
    }
    if (filtro.status) {
      params = params.set('status', filtro.status);
    }
    return this.http.get<ProcessoResumo[]>(`${API.consultas}/consultas/processos`, { params });
  }

  detalhe(id: string): Observable<ProcessoDetalhe> {
    return this.http.get<ProcessoDetalhe>(`${API.consultas}/consultas/processos/${id}`);
  }

  historico(id: string): Observable<EventoHistorico[]> {
    return this.http.get<EventoHistorico[]>(`${API.processos}/processos/${id}/historico`);
  }

  abrir(processo: NovoProcesso): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${API.processos}/processos`, processo);
  }

  adicionarItem(processoId: string, item: NovoItem): Observable<ItemResumo> {
    return this.http.post<ItemResumo>(`${API.processos}/processos/${processoId}/itens`, item);
  }

  /**
   * Stream SSE do consulta-service como Observable. Com fetch, e não EventSource: o EventSource não envia
   * cabeçalhos, e o stream exige o token no Authorization (na URL ele iria parar em logs e no histórico).
   * Nada acontece até alguém assinar; o unsubscribe aborta a conexão (teardown).
   */
  mudancas(): Observable<ProcessoResumo> {
    return new Observable<ProcessoResumo>((assinante) => {
      const conexao = new AbortController();
      (async () => {
        const resposta = await fetch(`${API.consultas}/consultas/processos/stream`, {
          headers: { Accept: 'text/event-stream', Authorization: `Bearer ${this.autenticacao.token()}` },
          signal: conexao.signal,
        });
        if (!resposta.ok || !resposta.body) {
          throw new Error(`stream: HTTP ${resposta.status}`);
        }
        // lê a resposta aos pedaços, conforme chega, e separa os eventos completos
        const leitor = resposta.body.pipeThrough(new TextDecoderStream()).getReader();
        let pendente = '';
        for (;;) {
          const { value, done } = await leitor.read();
          if (done) {
            break;
          }
          const { eventos, resto } = separarEventosSse(pendente + value);
          pendente = resto;
          eventos
            .filter((evento) => evento.tipo === 'processo')
            .forEach((evento) => assinante.next(JSON.parse(evento.dados)));
        }
        assinante.complete(); // o servidor fechou: o repeat abaixo reconecta
      })().catch((erro) => {
        if (!conexao.signal.aborted) {
          assinante.error(erro); // o retry abaixo reconecta
        }
      });
      return () => conexao.abort();
    }).pipe(
      // O EventSource reconectava sozinho; com fetch, quem reconecta somos nós. Cada nova tentativa
      // assina de novo e, por isso, pega o token mais recente (renovado pelo oidc-client-ts).
      repeat({ delay: 3000 }),
      retry({ delay: 3000 }),
    );
  }
}

/**
 * Separa os eventos completos de um trecho de text/event-stream. Um evento termina numa linha em branco;
 * o que vier depois do último evento completo volta como "resto", para juntar com o próximo pedaço.
 * Linhas de comentário (":keep-alive") e eventos sem data são ignorados.
 */
export function separarEventosSse(texto: string): { eventos: { tipo: string; dados: string }[]; resto: string } {
  const blocos = texto.replace(/\r\n/g, '\n').split('\n\n');
  const resto = blocos.pop() ?? '';
  const eventos = blocos
    .map((bloco) => {
      let tipo = 'message';
      const dados: string[] = [];
      for (const linha of bloco.split('\n')) {
        if (linha.startsWith('event:')) {
          tipo = linha.slice('event:'.length).trim();
        } else if (linha.startsWith('data:')) {
          dados.push(linha.slice('data:'.length).trimStart());
        }
      }
      return { tipo, dados: dados.join('\n') };
    })
    .filter((evento) => evento.dados !== '');
  return { eventos, resto };
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
