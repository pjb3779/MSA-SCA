package buaa.msasca.sca.infra.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.result.sanitizer.SanitizerCandidateEntity;

public interface SanitizerCandidateJpaRepository extends JpaRepository<SanitizerCandidateEntity, Long> {}
