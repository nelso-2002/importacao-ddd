import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { mensagemDeErro, ProcessoResumo, ProcessosApi } from './processos-api';

// HttpTestingController: o HttpClient de verdade, mas nenhuma requisição sai para a rede.
// O teste diz qual requisição espera, confere método/URL/corpo e responde com flush().
describe('ProcessosApi', () => {
  let api: ProcessosApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ProcessosApi);
    http = TestBed.inject(HttpTestingController);
  });

  // falha se sobrou requisição que nenhum teste esperava
  afterEach(() => http.verify());

  it('lista no consulta-service enviando só os filtros preenchidos', () => {
    const processos: ProcessoResumo[] = [
      {
        id: 'p1',
        referencia: 'IMP-1',
        importador: '11222333000181',
        status: 'ABERTO',
        quantidadeItens: 2,
        atualizadoEm: '2026-10-27T10:00:00Z',
      },
    ];
    let recebido: ProcessoResumo[] | undefined;

    api.listar({ busca: '  0101 ', status: '' }).subscribe((resposta) => (recebido = resposta));

    const requisicao = http.expectOne((r) => r.url === '/api/consultas/processos');
    expect(requisicao.request.method).toBe('GET');
    expect(requisicao.request.params.get('busca')).toBe('0101');
    expect(requisicao.request.params.has('status')).toBe(false);
    requisicao.flush(processos);
    expect(recebido).toEqual(processos);
  });

  it('manda o status quando escolhido', () => {
    api.listar({ busca: '', status: 'CANCELADO' }).subscribe();

    const requisicao = http.expectOne((r) => r.url === '/api/consultas/processos');
    expect(requisicao.request.params.get('status')).toBe('CANCELADO');
    expect(requisicao.request.params.has('busca')).toBe(false);
    requisicao.flush([]);
  });

  it('busca o detalhe no consulta-service e o histórico no processo-service', () => {
    api.detalhe('p1').subscribe();
    api.historico('p1').subscribe();

    http.expectOne('/api/consultas/processos/p1').flush({});
    http.expectOne('/api/processos/p1/historico').flush([]);
  });

  it('abre processo com POST e devolve o id', () => {
    let id: string | undefined;

    api.abrir({ importador: '11222333000181', referencia: 'IMP-1' }).subscribe((r) => (id = r.id));

    const requisicao = http.expectOne('/api/processos');
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual({ importador: '11222333000181', referencia: 'IMP-1' });
    requisicao.flush({ id: 'novo' }, { status: 201, statusText: 'Created' });
    expect(id).toBe('novo');
  });

  it('entrega o erro do servidor para quem assinou', () => {
    let erro: HttpErrorResponse | undefined;

    api.adicionarItem('p1', { ncm: '90308410', quantidade: 1, valor: 10, moeda: 'USD' }).subscribe({
      error: (e: HttpErrorResponse) => (erro = e),
    });

    http
      .expectOne('/api/processos/p1/itens')
      .flush(
        { title: 'Regra de negócio violada', detail: 'Processo CANCELADO' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(erro?.status).toBe(409);
    expect(mensagemDeErro(erro!)).toBe('Processo CANCELADO');
  });
});

describe('mensagemDeErro', () => {
  const erro = (corpo: unknown, status = 400) => new HttpErrorResponse({ error: corpo, status });

  it('usa o detail do ProblemDetail', () => {
    expect(mensagemDeErro(erro({ detail: 'CNPJ inválido: 11222333000182' }))).toBe(
      'CNPJ inválido: 11222333000182',
    );
  });

  it('lista os campos da Bean Validation', () => {
    expect(mensagemDeErro(erro({ detail: 'ignorado', erros: { referencia: 'não deve estar em branco' } }))).toBe(
      'referencia: não deve estar em branco',
    );
  });

  it('tem uma mensagem genérica quando o servidor nem respondeu', () => {
    expect(mensagemDeErro(erro(null, 0))).toContain('Os serviços estão no ar?');
  });
});
