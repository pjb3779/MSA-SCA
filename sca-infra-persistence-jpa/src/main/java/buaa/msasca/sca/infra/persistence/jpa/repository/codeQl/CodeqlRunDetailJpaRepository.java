package buaa.msasca.sca.infra.persistence.jpa.repository.codeQl;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.CodeqlRunDetailEntity;

public interface CodeqlRunDetailJpaRepository extends JpaRepository<CodeqlRunDetailEntity, Long> {}