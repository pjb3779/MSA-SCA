package buaa.msasca.sca.infra.persistence.jpa.repository.codeQl;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlRunSummaryEntity;

public interface CodeqlRunSummaryJpaRepository extends JpaRepository<CodeqlRunSummaryEntity, Long> {

}