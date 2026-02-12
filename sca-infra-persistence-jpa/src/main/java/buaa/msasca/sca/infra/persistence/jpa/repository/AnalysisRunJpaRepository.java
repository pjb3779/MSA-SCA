package buaa.msasca.sca.infra.persistence.jpa.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.AnalysisRunEntity;

import java.time.Instant;
import java.util.List;

public interface AnalysisRunJpaRepository extends JpaRepository<AnalysisRunEntity, Long> {
  List<AnalysisRunEntity> findByStatusOrderByCreatedAtAsc(RunStatus status, Pageable pageable);

  /**
   * PENDING -> RUNNING 클레임 (원자적 전이)
   * 성공하면 1, 실패(이미 다른 워커가 클레임했거나 PENDING 아님)면 0
   *
   * @param id run id
   * @param now started_at
   * @return 업데이트 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update AnalysisRunEntity r
      set r.status = buaa.msasca.sca.core.domain.enums.RunStatus.RUNNING,
          r.startedAt = :now
      where r.id = :id
        and r.status = buaa.msasca.sca.core.domain.enums.RunStatus.PENDING
      """)
  int tryMarkRunning(@Param("id") Long id, @Param("now") Instant now);

  /**
   * RUNNING -> DONE 전이
   *
   * @param id run id
   * @param now finished_at
   * @return 업데이트 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update AnalysisRunEntity r
      set r.status = buaa.msasca.sca.core.domain.enums.RunStatus.DONE,
          r.finishedAt = :now
      where r.id = :id
        and r.status = buaa.msasca.sca.core.domain.enums.RunStatus.RUNNING
      """)
  int markDone(@Param("id") Long id, @Param("now") Instant now);

  /**
   * RUNNING -> FAILED 전이 (필요하면 PENDING도 허용 가능)
   *
   * @param id run id
   * @param now finished_at
   * @return 업데이트 건수
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update AnalysisRunEntity r
      set r.status = buaa.msasca.sca.core.domain.enums.RunStatus.FAILED,
          r.finishedAt = :now
      where r.id = :id
        and r.status in (
          buaa.msasca.sca.core.domain.enums.RunStatus.PENDING,
          buaa.msasca.sca.core.domain.enums.RunStatus.RUNNING
        )
      """)
  int markFailed(@Param("id") Long id, @Param("now") Instant now);

  /**
   * 해당 project_version에 PENDING/RUNNING이 존재하는지 확인.
   *
   * @param pvId project_version id
   * @return 존재하면 true
   */
  @Query("""
      select (count(r) > 0)
        from AnalysisRunEntity r
       where r.projectVersion.id = :pvId
         and r.status in (
           buaa.msasca.sca.core.domain.enums.RunStatus.PENDING,
           buaa.msasca.sca.core.domain.enums.RunStatus.RUNNING
         )
      """)
  boolean existsActiveRun(@Param("pvId") Long projectVersionId);
}