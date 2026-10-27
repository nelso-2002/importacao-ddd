package io.github.nelso2002.importacao.consulta;

import org.springframework.boot.SpringApplication;

public class TestConsultaServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ConsultaServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
