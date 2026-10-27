import { Routes } from '@angular/router';
import { ListaProcessos } from './processos/lista-processos';

export const routes: Routes = [
  { path: '', component: ListaProcessos, title: 'Processos de importação' },
  {
    path: 'processos/:id',
    // carregado só quando alguém abre um processo: sai do pacote inicial
    loadComponent: () => import('./processos/pagina-processo').then((m) => m.PaginaProcesso),
    title: 'Processo de importação',
  },
  { path: '**', redirectTo: '' },
];
