import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localePt from '@angular/common/locales/pt';
import {
  ApplicationConfig,
  inject,
  LOCALE_ID,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { Autenticacao } from './autenticacao/autenticacao';
import { anexarToken } from './autenticacao/seguranca-http';

// datas, números e moedas no formato brasileiro (US$ 3.200,00) em todos os pipes
registerLocaleData(localePt);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    // antes da primeira tela: conclui o retorno do Keycloak ou recupera a sessão
    provideAppInitializer(() => inject(Autenticacao).iniciar()),
    provideHttpClient(withInterceptors([anexarToken])),
    // parâmetros da rota (como :id) chegam aos componentes como input()
    provideRouter(routes, withComponentInputBinding()),
  ],
};
