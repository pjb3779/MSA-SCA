package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.ArrayList;
import java.util.List;

import buaa.msasca.sca.core.port.out.persistence.CodeqlFindingPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFindingEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFlowEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFlowStepEntity;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFindingJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFlowJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFlowStepJpaRepository;

/**
 * CodeQL finding 조회 어댑터.
 *
 * <p>analysis_run 기준으로 SARIF(CodeQL) 결과를 조회하여 Agent Sanitizer 파이프라인에 전달한다.
 * flow step까지 로드하여 Judge 프롬프트 맥락으로 활용한다.</p>
 */
public class JpaCodeqlFindingAdapter implements CodeqlFindingPort {

    private final CodeqlFindingJpaRepository findingRepo;
    private final CodeqlFlowJpaRepository flowRepo;
    private final CodeqlFlowStepJpaRepository stepRepo;

    public JpaCodeqlFindingAdapter(
        CodeqlFindingJpaRepository findingRepo,
        CodeqlFlowJpaRepository flowRepo,
        CodeqlFlowStepJpaRepository stepRepo
    ) {
        this.findingRepo = findingRepo;
        this.flowRepo = flowRepo;
        this.stepRepo = stepRepo;
    }

    @Override
    public List<CodeqlFindingView> findByAnalysisRunId(Long analysisRunId) {
        if (analysisRunId == null) {
            return List.of();
        }
        // MSCAN_ONLY 모드 등 CodeQL 미실행 시 빈 리스트 반환
        List<CodeqlFindingEntity> findings = findingRepo.findByCodeqlRun_ToolRun_AnalysisRun_Id(analysisRunId);
        List<CodeqlFindingView> out = new ArrayList<>();
        for (CodeqlFindingEntity f : findings) {
            List<FlowStepView> steps = loadFlowSteps(f.getId());
            var sm = f.getCodeqlRun().getServiceModule();
            String rootPath = (sm != null && sm.getRootPath() != null) ? sm.getRootPath() : "";
            out.add(new CodeqlFindingView(
                f.getId(),
                sm != null ? sm.getId() : null,
                rootPath,
                f.getRuleId(),
                f.getMessage(),
                f.getLevel(),
                f.getPrimaryFile(),
                f.getPrimaryLine(),
                steps
            ));
        }
        return out;
    }

    /** finding에 속한 flow step 목록을 flowIndex·stepIndex 순으로 로드한다. */
    private List<FlowStepView> loadFlowSteps(Long findingId) {
        List<FlowStepView> steps = new ArrayList<>();
        for (CodeqlFlowEntity flow : flowRepo.findByFinding_IdOrderByFlowIndexAsc(findingId)) {
            for (CodeqlFlowStepEntity step : stepRepo.findByFlow_IdOrderByStepIndexAsc(flow.getId())) {
                steps.add(new FlowStepView(
                    step.getStepIndex(),
                    step.getFilePath(),
                    step.getLineNumber(),
                    step.getLabel()
                ));
            }
        }
        return steps;
    }
}
