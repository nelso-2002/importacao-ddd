import { FormControl } from '@angular/forms';
import { cnpjValido, ncmValido, normalizarCnpj, normalizarNcm } from './validadores';

describe('validadores', () => {
  describe('NCM', () => {
    it.each(['90308410', '9030.84.10', ' 9030.8410 '])('aceita %s', (valor) => {
      expect(ncmValido(new FormControl(valor))).toBeNull();
    });

    it.each(['9030841', '903084101', '9030841A', '9030-84-10'])('rejeita %s', (valor) => {
      expect(ncmValido(new FormControl(valor))).toEqual({ ncm: true });
    });

    it('deixa o vazio para o required', () => {
      expect(ncmValido(new FormControl(''))).toBeNull();
    });

    it('normaliza para só dígitos', () => {
      expect(normalizarNcm('9030.84.10')).toBe('90308410');
    });
  });

  describe('CNPJ', () => {
    it.each(['11222333000181', '11.222.333/0001-81', '12.abc.345/01de-35'])('aceita %s', (valor) => {
      expect(cnpjValido(new FormControl(valor))).toBeNull();
    });

    it.each(['1122233300018', '112223330001A1', '11222333#00181'])('rejeita %s', (valor) => {
      expect(cnpjValido(new FormControl(valor))).toEqual({ cnpj: true });
    });

    it('normaliza tirando a máscara e passando para maiúsculas', () => {
      expect(normalizarCnpj('12.abc.345/01de-35')).toBe('12ABC34501DE35');
    });
  });
});
