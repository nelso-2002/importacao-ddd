import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Autenticacao, papeis } from './autenticacao';
import { anexarToken } from './seguranca-http';

describe('anexarToken', () => {
  let http: HttpClient;
  let servidor: HttpTestingController;
  const entrar = vi.fn();

  beforeEach(() => {
    entrar.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([anexarToken])),
        provideHttpClientTesting(),
        { provide: Autenticacao, useValue: { token: () => 'token-do-operador', entrar } },
      ],
    });
    http = TestBed.inject(HttpClient);
    servidor = TestBed.inject(HttpTestingController);
  });

  afterEach(() => servidor.verify());

  it('anexa o token às chamadas para as nossas APIs', () => {
    http.get('http://localhost:8080/processos/p1/historico').subscribe();
    http.get('http://localhost:8081/consultas/processos').subscribe();

    for (const url of ['http://localhost:8080/processos/p1/historico', 'http://localhost:8081/consultas/processos']) {
      const requisicao = servidor.expectOne(url);
      expect(requisicao.request.headers.get('Authorization')).toBe('Bearer token-do-operador');
      requisicao.flush({});
    }
  });

  it('nunca manda o token para outro destino', () => {
    http.get('https://outro-servidor.test/qualquer-coisa').subscribe();
    // mesmo host com outra porta, ou prefixo parecido: também não
    http.get('http://localhost:80809/processos').subscribe();

    expect(servidor.expectOne('https://outro-servidor.test/qualquer-coisa').request.headers.has('Authorization')).toBe(false);
    expect(servidor.expectOne('http://localhost:80809/processos').request.headers.has('Authorization')).toBe(false);
    servidor.match(() => true).forEach((r) => r.flush({}));
  });

  it('manda para o login quando o serviço responde 401', () => {
    http.get('http://localhost:8081/consultas/processos').subscribe({ error: () => undefined });

    servidor.expectOne('http://localhost:8081/consultas/processos').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(entrar).toHaveBeenCalled();
  });
});

describe('papeis', () => {
  const token = (payload: object) => `cabecalho.${btoa(JSON.stringify(payload))}.assinatura`;

  it('lê realm_access.roles do token do Keycloak', () => {
    expect(papeis(token({ realm_access: { roles: ['OPERADOR', 'CONSULTA'] } }))).toEqual(['OPERADOR', 'CONSULTA']);
  });

  it('devolve vazio sem token, sem papéis ou com token malformado', () => {
    expect(papeis(undefined)).toEqual([]);
    expect(papeis(token({ sub: 'x' }))).toEqual([]);
    expect(papeis('nao-e-um-jwt')).toEqual([]);
  });
});
