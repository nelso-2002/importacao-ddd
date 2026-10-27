import { Routes } from '@angular/router';
import { exigeLogin } from './autenticacao/seguranca-http';
import { ListaProcessos } from './processos/lista-processos';

export const routes: Routes = [
  { path: '', component: ListaProcessos, canActivate: [exigeLogin], title: 'Processos de importação' },
  {
    // antes de 'processos/:id', senão "novo" seria lido como um id
    path: 'processos/novo',
    loadComponent: () => import('./processos/novo-processo').then((m) => m.NovoProcesso),
    canActivate: [exigeLogin],
    title: 'Novo processo',
  },
  {
    path: 'processos/:id',
    // carregado só quando alguém abre um processo: sai do pacote inicial
    loadComponent: () => import('./processos/pagina-processo').then((m) => m.PaginaProcesso),
    canActivate: [exigeLogin],
    title: 'Processo de importação',
  },
  { path: '**', redirectTo: '' },
];
