package buaa.msasca.sca.infra.persistence.jpa.repository.codeQl;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFlowStepEntity;

public interface CodeqlFlowStepJpaRepository extends JpaRepository<CodeqlFlowStepEntity, Long> {

    /** flow에 속한 step 목록 */
    List<CodeqlFlowStepEntity> findByFlow_IdOrderByStepIndexAsc(Long flowId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CodeqlFlowStepEntity s where s.flow.id in ?1")
    void deleteByFlowIds(List<Long> flowIds);
}