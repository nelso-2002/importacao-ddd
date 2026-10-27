import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, output, signal } from '@angular/core';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ItemResumo, mensagemDeErro, ProcessosApi } from './processos-api';
import { ncmValido, normalizarNcm } from './validadores';

/** Formulário de item, usado dentro da página do processo. */
@Component({
  selector: 'app-form-item',
  imports: [ReactiveFormsModule],
  templateUrl: './form-item.html',
})
export class FormItem {
  private readonly api = inject(ProcessosApi);

  // input(): dado que vem do pai; output(): aviso que sobe para o pai ("adicionado").
  readonly processoId = input.required<string>();
  readonly adicionado = output<ItemResumo>();

  protected readonly moedas = ['USD', 'EUR', 'BRL', 'CNY', 'GBP', 'JPY'];

  protected readonly form = inject(NonNullableFormBuilder).group({
    ncm: ['', [Validators.required, ncmValido]],
    // number | null: o campo começa vazio, não com zero
    quantidade: new FormControl<number | null>(null, [Validators.required, Validators.min(0.00001)]),
    valor: new FormControl<number | null>(null, [Validators.required, Validators.min(0)]),
    moeda: ['USD', Validators.required],
  });

  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    const { ncm, quantidade, valor, moeda } = this.form.getRawValue();
    this.api
      .adicionarItem(this.processoId(), {
        ncm: normalizarNcm(ncm),
        quantidade: quantidade!,
        valor: valor!,
        moeda,
      })
      .subscribe({
        next: (item) => {
          this.adicionado.emit(item);
          this.form.reset({ moeda }); // limpa os campos, mas mantém a moeda escolhida
          this.enviando.set(false);
        },
        error: (erro: HttpErrorResponse) => {
          this.erro.set(mensagemDeErro(erro));
          this.enviando.set(false);
        },
      });
  }
}
