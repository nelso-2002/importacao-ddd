package io.github.nelso2002.importacao.processo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProcessoServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
