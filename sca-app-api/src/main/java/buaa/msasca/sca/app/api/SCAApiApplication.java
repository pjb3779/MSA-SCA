package buaa.msasca.sca.app.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import buaa.msasca.sca.infra.persistence.jpa.config.PersistenceWiringConfig;

@SpringBootApplication(scanBasePackages = {
    "buaa.msasca.sca"
})
@EntityScan(basePackages = {
    "buaa.msasca.sca.infra.persistence.jpa.entity"
})
@EnableJpaRepositories(basePackages = {
    "buaa.msasca.sca.infra.persistence.jpa.repository"
})
public class SCAApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(SCAApiApplication.class, args);
  }
}
