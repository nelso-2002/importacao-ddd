package io.github.nelso2002.importacao.processo.api;

import io.github.nelso2002.importacao.processo.application.ProcessoNaoEncontradoException;
import io.github.nelso2002.importacao.processo.domain.RegraDeNegocioException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * Traduz as exceções das camadas internas em respostas RFC 9457 (application/problem+json).
 * Herdar de ResponseEntityExceptionHandler já cobre os erros do próprio Spring MVC (JSON malformado etc.).
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    // ponytail: IllegalArgumentException é genérica; se um bug interno lançar uma, vira 400 em vez de 500.
    // Trocar por uma exceção própria dos value objects se isso começar a esconder erros.
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail dadoInvalido(IllegalArgumentException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Dado inválido", ex.getMessage());
    }

    @ExceptionHandler(ProcessoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(ProcessoNaoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Processo não encontrado", ex.getMessage());
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    ProblemDetail regraDeNegocio(RegraDeNegocioException ex) {
        return problema(HttpStatus.CONFLICT, "Regra de negócio violada", ex.getMessage());
    }

    // Mesmo 409, mas o título diz ao cliente que repetir a requisição pode dar certo.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail gravacaoConcorrente(OptimisticLockingFailureException ex) {
        return problema(HttpStatus.CONFLICT, "Gravação concorrente",
                "O processo foi alterado por outra requisição; carregue de novo e repita a operação");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> erros = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(erro -> erros.put(erro.getField(), erro.getDefaultMessage()));
        var problema = problema(HttpStatus.BAD_REQUEST, "Campos inválidos", "Corrija os campos indicados em 'erros'");
        problema.setProperty("erros", erros);
        return ResponseEntity.badRequest().body(problema);
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        var problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
