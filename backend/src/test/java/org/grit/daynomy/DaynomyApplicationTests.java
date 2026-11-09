package org.grit.daynomy;

import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class DaynomyApplicationTests extends SharedDatabaseTest {

  @Test
  void contextLoads() {}
}
