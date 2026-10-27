// Endereços vistos pelo navegador. Sem proxy: cada serviço libera a origem da tela via CORS.
// São os mesmos no ng serve e no docker-compose, porque as portas são publicadas na máquina;
// em outro ambiente (outro domínio), estes valores e a lista de origens dos serviços mudam juntos.
export const API = {
  processos: 'http://localhost:8080',
  consultas: 'http://localhost:8081',
} as const;

export const KEYCLOAK = 'http://localhost:8180/realms/importacao';
