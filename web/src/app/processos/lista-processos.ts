import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  auditTime,
  catchError,
  combineLatest,
  debounceTime,
  distinctUntilChanged,
  map,
  of,
  share,
  startWith,
  switchMap,
} from 'rxjs';
import { FiltroProcessos, ProcessosApi, ProcessoResumo, ROTULOS_STATUS, Status } from './processos-api';

interface Resultado {
  processos: ProcessoResumo[];
  erro: boolean;
}

@Component({
  selector: 'app-lista-processos',
  imports: [ReactiveFormsModule, RouterLink, DatePipe],
  templateUrl: './lista-processos.html',
  styleUrl: './lista-processos.scss',
})
export class ListaProcessos {
  private readonly api = inject(ProcessosApi);

  protected readonly rotulos = ROTULOS_STATUS;
  protected readonly todosStatus = Object.keys(ROTULOS_STATUS) as Status[];

  protected readonly filtro = new FormGroup({
    busca: new FormControl('', { nonNullable: true }),
    status: new FormControl<Status | ''>('', { nonNullable: true }),
  });

  // Fluxo da busca: cada tecla vira um valor; espera 300 ms sem digitar e ignora valor repetido.
  private readonly filtroAtual$ = this.filtro.valueChanges.pipe(
    debounceTime(300),
    startWith(this.filtro.getRawValue()),
    map((): FiltroProcessos => this.filtro.getRawValue()),
    distinctUntilChanged((a, b) => a.busca === b.busca && a.status === b.status),
  );

  // Uma única conexão SSE compartilhada (share) entre a atualização da lista e o destaque das linhas.
  private readonly mudancas$ = this.api.mudancas().pipe(share());

  // Refaz a busca com o filtro atual quando o filtro muda ou quando chega uma mudança ao vivo.
  // auditTime junta uma rajada de mudanças numa só busca; switchMap cancela a busca anterior.
  // Buscar de novo, em vez de encaixar a mudança na lista, garante que filtro e ordem continuem certos.
  private readonly resultado = toSignal(
    combineLatest([this.filtroAtual$, this.mudancas$.pipe(auditTime(300), startWith(null))]).pipe(
      switchMap(([filtro]) =>
        this.api.listar(filtro).pipe(
          map((processos): Resultado => ({ processos, erro: false })),
          catchError(() => of<Resultado>({ processos: [], erro: true })),
        ),
      ),
    ),
  );

  // Estado da tela derivado do resultado: sem variáveis soltas para manter em sincronia.
  protected readonly processos = computed(() => this.resultado()?.processos ?? []);
  protected readonly carregando = computed(() => this.resultado() === undefined);
  protected readonly erro = computed(() => this.resultado()?.erro ?? false);

  // Linhas que acabaram de mudar ao vivo ficam destacadas por alguns segundos.
  protected readonly destacados = signal<ReadonlySet<string>>(new Set());

  constructor() {
    // takeUntilDestroyed encerra a assinatura (e a conexão SSE) quando o componente sai da tela.
    this.mudancas$.pipe(takeUntilDestroyed()).subscribe(({ id }) => {
      this.destacados.update((ids) => new Set(ids).add(id));
      setTimeout(
        () =>
          this.destacados.update((ids) => {
            const restantes = new Set(ids);
            restantes.delete(id);
            return restantes;
          }),
        3000,
      );
    });
  }
}
