package buaa.msasca.sca.infra.persistence.jpa;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * sca-infra-persistence-jpa 테스트 부트스트랩용 최소 설정.
 *
 * - @DataJpaTest가 @SpringBootConfiguration을 찾지 못하면 initializationError가 발생한다.
 * - 이 클래스는 테스트 소스에서만 사용된다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "buaa.msasca.sca.infra.persistence.jpa.entity")
@EnableJpaRepositories(basePackages = "buaa.msasca.sca.infra.persistence.jpa.repository")
public class TestJpaBootConfig {}

