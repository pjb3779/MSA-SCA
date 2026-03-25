package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import buaa.msasca.sca.core.port.out.persistence.MscanResultPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanFindingEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.MscanRunDetailEntity;
import buaa.msasca.sca.infra.persistence.jpa.repository.Mscan.MscanFindingJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.MscanRunDetailJpaRepository;

public class JpaMscanResultAdapter implements MscanResultPort {

    private final MscanRunDetailJpaRepository runDetailRepo;
    private final MscanFindingJpaRepository findingRepo;

    public JpaMscanResultAdapter(
        MscanRunDetailJpaRepository runDetailRepo,
        MscanFindingJpaRepository findingRepo
    ) {
        this.runDetailRepo = runDetailRepo;
        this.findingRepo = findingRepo;
    }

    @Override
    @Transactional
    public void replaceAll(Long toolRunId, List<MscanFindingIngest> findings) {
        MscanRunDetailEntity run = runDetailRepo.findById(toolRunId)
            .orElseThrow(() -> new IllegalArgumentException("mscan_run_detail not found: " + toolRunId));

        // 1) 기존 결과 삭제
        findingRepo.deleteByMscanRun_ToolRunId(toolRunId);

        // 2) 새 결과 insert
        for (MscanFindingIngest f : findings) {
            MscanFindingEntity e = MscanFindingEntity.create(
                run,
                f.flowIndex(),
                f.sourceSignature(),
                f.sinkSignature(),
                f.vulId(),
                f.rawFlowText()
            );
            // sink 메타가 있으면 채움(그래프 드릴다운에서 유용)
            if (f.sinkFilePath() != null
                || f.sinkLine() != null
                || f.sinkBasicBlock() != null
                || f.sinkCallKind() != null
                || f.sinkCallTarget() != null) {
                e.attachSinkMeta(
                    f.sinkFilePath(),
                    f.sinkLine(),
                    f.sinkBasicBlock(),
                    f.sinkCallKind(),
                    f.sinkCallTarget()
                );
            }
            findingRepo.save(e);
        }
    }
}

