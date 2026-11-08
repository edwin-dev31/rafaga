package com.rafaga.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.rafaga.backend.tenant.infrastructure.persistence.TenantAwareRoutingDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackendApplicationTests {
	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoads() {
	}

	@Test
	void applicationUsesTenantRoutingAndRestrictedDatabaseRole() throws Exception {
		assertThat(dataSource).isInstanceOf(TenantAwareRoutingDataSource.class);
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user")) {
			assertThat(result.next()).isTrue();
			assertThat(result.getBoolean("rolsuper")).isFalse();
			assertThat(result.getBoolean("rolbypassrls")).isFalse();
		}
	}

}
