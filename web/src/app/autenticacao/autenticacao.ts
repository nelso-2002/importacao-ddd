import { computed, Injectable, signal } from '@angular/core';
import { User, UserManager, WebStorageStateStore } from 'oidc-client-ts';
import { KEYCLOAK } from '../ambiente';

const CALLBACK = '/login/callback';

/**
 * Login OIDC no Keycloak com Authorization Code + PKCE (o fluxo recomendado para SPAs).
 * A tela nunca vê a senha: o navegador vai até o Keycloak, o usuário entra lá, e volta com um "code"
 * que só vale junto com o code_verifier que a tela gerou antes de sair (é isso o PKCE).
 */
@Injectable({ providedIn: 'root' })
export class Autenticacao {
  private readonly oidc = new UserManager({
    authority: KEYCLOAK,
    client_id: 'web',
    redirect_uri: location.origin + CALLBACK,
    post_logout_redirect_uri: location.origin,
    response_type: 'code', // Authorization Code; o oidc-client-ts gera o PKCE (S256), o state e o nonce
    scope: 'openid profile',
    automaticSilentRenew: true, // troca o refresh token por um access token novo antes de expirar
    // sessionStorage: some ao fechar a aba e não é compartilhado entre abas (ver SEGURANCA.md, sobre XSS)
    userStore: new WebStorageStateStore({ store: sessionStorage }),
  });

  private readonly usuario = signal<User | null>(null);

  readonly logado = computed(() => !!this.usuario() && !this.usuario()!.expired);
  readonly nome = computed(() => this.usuario()?.profile.preferred_username ?? '');
  // só para mostrar ou esconder botões; quem decide de verdade são os serviços, que conferem o token
  readonly ehOperador = computed(() => papeis(this.usuario()?.access_token).includes('OPERADOR'));

  /** Roda antes da primeira tela: conclui o login se voltamos do Keycloak, ou recupera a sessão salva. */
  async iniciar(): Promise<void> {
    this.oidc.events.addUserLoaded((usuario) => this.usuario.set(usuario));
    this.oidc.events.addUserUnloaded(() => this.usuario.set(null));

    if (location.pathname === CALLBACK) {
      // troca o code pelo token (com o code_verifier) e volta para a tela de onde o login partiu
      const usuario = await this.oidc.signinRedirectCallback();
      this.usuario.set(usuario);
      history.replaceState(null, '', (usuario.state as string | undefined) ?? '/');
      return;
    }
    this.usuario.set(await this.oidc.getUser());
  }

  entrar(destino = location.pathname + location.search): Promise<void> {
    return this.oidc.signinRedirect({ state: destino });
  }

  sair(): Promise<void> {
    return this.oidc.signoutRedirect();
  }

  /** Access token válido, ou undefined (não logado ou expirado). */
  token(): string | undefined {
    const usuario = this.usuario();
    return usuario && !usuario.expired ? usuario.access_token : undefined;
  }
}

/** Papéis do Keycloak (realm_access.roles) lidos do payload do JWT, sem verificar assinatura: só para a interface. */
export function papeis(token: string | undefined): string[] {
  if (!token) {
    return [];
  }
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64));
    return payload.realm_access?.roles ?? [];
  } catch {
    return [];
  }
}
