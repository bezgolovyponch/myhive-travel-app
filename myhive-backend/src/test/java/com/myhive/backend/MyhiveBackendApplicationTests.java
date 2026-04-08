package com.myhive.backend;

import com.myhive.backend.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestSecurityConfig.class)
class MyhiveBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
