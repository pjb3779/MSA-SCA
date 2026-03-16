package buaa.msasca.sca.infra.persistence.jpa.repository.Mscan;

import org.springframework.data.jpa.repository.JpaRepository;

import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanRunSummaryEntity;

public interface MscanRunSummaryJpaRepository extends JpaRepository<MscanRunSummaryEntity, Long> {}