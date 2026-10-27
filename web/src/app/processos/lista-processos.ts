import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
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

  // Fluxo da busca: cada tecla vira um valor; espera 300 ms sem digitar, ignora valor repetido
  // e troca a requisição em andamento pela nova (switchMap cancela a anterior).
  private readonly resultado = toSignal(
    this.filtro.valueChanges.pipe(
      debounceTime(300),
      startWith(this.filtro.getRawValue()),
      map((): FiltroProcessos => this.filtro.getRawValue()),
      distinctUntilChanged((a, b) => a.busca === b.busca && a.status === b.status),
      switchMap((filtro) =>
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
}
