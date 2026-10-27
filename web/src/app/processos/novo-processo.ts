import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { mensagemDeErro, ProcessosApi } from './processos-api';
import { cnpjValido, normalizarCnpj } from './validadores';

@Component({
  selector: 'app-novo-processo',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './novo-processo.html',
})
export class NovoProcesso {
  private readonly api = inject(ProcessosApi);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    importador: ['', [Validators.required, cnpjValido]],
    referencia: ['', Validators.required],
  });

  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched(); // mostra os erros de quem ainda não foi tocado
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    const { importador, referencia } = this.form.getRawValue();
    // Requisição HTTP completa sozinha depois da resposta: assinar direto não deixa assinatura pendurada.
    this.api.abrir({ importador: normalizarCnpj(importador), referencia: referencia.trim() }).subscribe({
      next: ({ id }) => this.router.navigate(['/processos', id]),
      error: (erro: HttpErrorResponse) => {
        this.erro.set(mensagemDeErro(erro));
        this.enviando.set(false);
      },
    });
  }
}
