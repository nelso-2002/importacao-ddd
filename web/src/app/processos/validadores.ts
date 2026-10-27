import { ValidatorFn } from '@angular/forms';

// O domínio só aceita a forma canônica (sem máscara). A máscara é apresentação:
// a tela aceita "9030.84.10" e "11.222.333/0001-81", valida e envia só os caracteres que contam.

export function normalizarNcm(valor: string): string {
  return valor.replace(/[.\s]/g, '');
}

export function normalizarCnpj(valor: string): string {
  return valor.replace(/[./\-\s]/g, '').toUpperCase();
}

// Vazio passa: quem cobra o preenchimento é o Validators.required.
export const ncmValido: ValidatorFn = (controle) =>
  !controle.value || /^\d{8}$/.test(normalizarNcm(controle.value)) ? null : { ncm: true };

// Só o formato (12 posições alfanuméricas + 2 dígitos); o dígito verificador é conferido pelo back-end.
export const cnpjValido: ValidatorFn = (controle) =>
  !controle.value || /^[0-9A-Z]{12}\d{2}$/.test(normalizarCnpj(controle.value))
    ? null
    : { cnpj: true };
