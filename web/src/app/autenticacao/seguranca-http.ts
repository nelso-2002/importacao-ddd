import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API } from '../ambiente';
import { Autenticacao } from './autenticacao';

const nossasApis = Object.values(API);

/**
 * Anexa o token às chamadas para as nossas APIs, e só para elas: o token nunca vai para outro destino
 * (um link externo ou uma biblioteca chamando outro servidor não recebe a credencial do usuário).
 * Um 401 (token expirado ou revogado) manda o usuário fazer login de novo.
 */
export const anexarToken: HttpInterceptorFn = (requisicao, proximo) => {
  const autenticacao = inject(Autenticacao);
  const token = autenticacao.token();
  if (!token || !nossasApis.some((api) => requisicao.url.startsWith(api + '/'))) {
    return proximo(requisicao);
  }
  return proximo(requisicao.clone({ setHeaders: { Authorization: `Bearer ${token}` } })).pipe(
    catchError((erro: HttpErrorResponse) => {
      if (erro.status === 401) {
        void autenticacao.entrar();
      }
      return throwError(() => erro);
    }),
  );
};

/** Toda tela exige login: sem sessão, vai para o Keycloak e volta para cá depois. */
export const exigeLogin: CanActivateFn = (_rota, estado) => {
  const autenticacao = inject(Autenticacao);
  if (autenticacao.logado()) {
    return true;
  }
  void autenticacao.entrar(estado.url); // o destino, e não a tela atual
  return false;
};
