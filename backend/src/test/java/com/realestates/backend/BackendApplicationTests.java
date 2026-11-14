package com.realestates.backend;

import com.realestates.backend.database.TestDatabaseSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
