import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { ListaProcessos } from './lista-processos';
import { FiltroProcessos, ProcessoResumo, ProcessosApi } from './processos-api';

const processo = (referencia: string, id = referencia): ProcessoResumo => ({
  id,
  referencia,
  importador: '11222333000181',
  status: 'ABERTO',
  quantidadeItens: 1,
  atualizadoEm: '2026-10-27T10:00:00Z',
});

// O componente de verdade, com um ProcessosApi falso: sem HTTP e sem EventSource
// (que nem existe no ambiente de teste). O teste controla o que a "API" devolve e quando.
// Timers falsos controlam o debounce; por isso a renderização é pedida com detectChanges(),
// e não com whenStable(), que esperaria o agendador do Angular (um setTimeout que nunca dispara).
describe('ListaProcessos', () => {
  let listar: ReturnType<typeof vi.fn<(filtro: FiltroProcessos) => Observable<ProcessoResumo[]>>>;
  let mudancas: Subject<ProcessoResumo>;

  function criar() {
    TestBed.configureTestingModule({
      imports: [ListaProcessos],
      providers: [
        provideRouter([]),
        { provide: ProcessosApi, useValue: { listar, mudancas: () => mudancas } },
      ],
    });
    const fixture = TestBed.createComponent(ListaProcessos);
    const tela = fixture.nativeElement as HTMLElement;
    return { fixture, tela };
  }

  beforeEach(() => {
    vi.useFakeTimers();
    listar = vi.fn(() => of([processo('IMP-2026/0001'), processo('IMP-2026/0002')]));
    mudancas = new Subject<ProcessoResumo>();
  });

  afterEach(() => vi.useRealTimers());

  it('mostra uma linha por processo, com o status em português', () => {
    const { fixture, tela } = criar();
    fixture.detectChanges();

    const linhas = tela.querySelectorAll('tbody tr');
    expect(linhas.length).toBe(2);
    expect(linhas[0].textContent).toContain('IMP-2026/0001');
    expect(linhas[0].textContent).toContain('Aberto');
    expect(listar).toHaveBeenCalledWith({ busca: '', status: '' });
  });

  it('avisa quando não há processos', () => {
    listar.mockReturnValue(of([]));
    const { fixture, tela } = criar();
    fixture.detectChanges();

    expect(tela.textContent).toContain('Nenhum processo encontrado.');
  });

  it('mostra o erro quando a API falha', () => {
    listar.mockReturnValue(throwError(() => new Error('fora do ar')));
    const { fixture, tela } = criar();
    fixture.detectChanges();

    expect(tela.textContent).toContain('Não foi possível carregar os processos');
  });

  it('espera 300 ms sem digitar antes de buscar (debounce)', () => {
    const { fixture, tela } = criar();
    fixture.detectChanges();
    const campo = tela.querySelector<HTMLInputElement>('input[type="search"]')!;

    for (const texto of ['0', '00', '000']) {
      campo.value = texto;
      campo.dispatchEvent(new Event('input'));
      vi.advanceTimersByTime(100); // digitando: nenhuma busca nova
    }
    expect(listar).toHaveBeenCalledTimes(1); // só a carga inicial

    vi.advanceTimersByTime(300);
    expect(listar).toHaveBeenCalledTimes(2);
    expect(listar).toHaveBeenLastCalledWith({ busca: '000', status: '' });
  });

  it('refaz a busca e destaca a linha quando chega uma mudança ao vivo', () => {
    const { fixture, tela } = criar();
    fixture.detectChanges();

    listar.mockReturnValue(of([processo('IMP-2026/0003', 'nova'), processo('IMP-2026/0001')]));
    mudancas.next(processo('IMP-2026/0003', 'nova'));
    vi.advanceTimersByTime(300); // auditTime junta mudanças próximas numa busca só
    fixture.detectChanges();

    expect(listar).toHaveBeenCalledTimes(2);
    const primeira = tela.querySelector('tbody tr')!;
    expect(primeira.textContent).toContain('IMP-2026/0003');
    expect(primeira.classList).toContain('destacado');
  });
});
