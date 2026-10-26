package io.github.nelso2002.importacao.processo;

import org.springframework.boot.SpringApplication;

public class TestProcessoServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ProcessoServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
