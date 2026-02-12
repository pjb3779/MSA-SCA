package buaa.msasca.sca.app.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "buaa.msasca.sca"
})
@EnableScheduling
@EntityScan(basePackages = {
    "buaa.msasca.sca.infra.persistence.jpa.entity"
})
@EnableJpaRepositories(basePackages = {
    "buaa.msasca.sca.infra.persistence.jpa.repository"
})
public class SCAWorkerApplication {
  public static void main(String[] args) {
    SpringApplication.run(SCAWorkerApplication.class, args);
  }
}
