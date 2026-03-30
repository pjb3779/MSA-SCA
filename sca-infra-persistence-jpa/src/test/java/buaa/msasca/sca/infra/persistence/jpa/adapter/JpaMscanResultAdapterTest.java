package buaa.msasca.sca.infra.persistence.jpa.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import buaa.msasca.sca.core.port.out.persistence.MscanResultPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanFindingEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.MscanRunDetailEntity;
import buaa.msasca.sca.infra.persistence.jpa.repository.Mscan.MscanFindingJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.MscanRunDetailJpaRepository;

@ExtendWith(MockitoExtension.class)
class JpaMscanResultAdapterTest {

  @Mock
  private MscanRunDetailJpaRepository runDetailRepo;

  @Mock
  private MscanFindingJpaRepository findingRepo;

  /**
   * 시나리오:
   * - replaceAll(toolRunId, findings)이 호출되면
   *   1) 기존 mscan finding을 삭제하고
   *   2) 전달받은 findings를 다시 insert 해야 함
   * 기대:
   * - deleteByMscanRun_ToolRunId가 1번 호출됨
   * - findings 개수만큼 save가 호출됨
   * - sink 메타가 존재하는 경우 attachSinkMeta로 값이 엔티티에 반영됨
   * - sink 메타가 없는 경우에는 엔티티 값이 null로 유지됨
   */
  @Test
  void replaceAll_deletesOldAndSavesNew_withSinkMeta() {
    JpaMscanResultAdapter adapter = new JpaMscanResultAdapter(runDetailRepo, findingRepo);

    Long toolRunId = 10L;
    MscanRunDetailEntity run = mock(MscanRunDetailEntity.class);
    when(runDetailRepo.findById(toolRunId)).thenReturn(Optional.of(run));

    MscanResultPort.MscanFindingIngest f0 = new MscanResultPort.MscanFindingIngest(
        0,
        "sourceSig0",
        "sinkSig0",
        "VUL0",
        "raw0",
        "A.java",
        123,
        3,
        "invoke",
        "target"
    );

    MscanResultPort.MscanFindingIngest f1 = new MscanResultPort.MscanFindingIngest(
        1,
        "sourceSig1",
        "sinkSig1",
        "VUL1",
        "raw1",
        null,
        null,
        null,
        null,
        null
    );

    ArgumentCaptor<MscanFindingEntity> savedCaptor = ArgumentCaptor.forClass(MscanFindingEntity.class);

    adapter.replaceAll(toolRunId, List.of(f0, f1));

    verify(findingRepo, times(1)).deleteByMscanRun_ToolRunId(toolRunId);
    verify(findingRepo, times(2)).save(savedCaptor.capture());

    List<MscanFindingEntity> saved = savedCaptor.getAllValues();
    assertEquals(2, saved.size());

    assertEquals("A.java", saved.get(0).getSinkFilePath());
    assertEquals(123, saved.get(0).getSinkLine());
    assertEquals(3, saved.get(0).getSinkBasicBlock());
    assertEquals("invoke", saved.get(0).getSinkCallKind());
    assertEquals("target", saved.get(0).getSinkCallTarget());

    assertNull(saved.get(1).getSinkFilePath());
    assertNull(saved.get(1).getSinkLine());
    assertNull(saved.get(1).getSinkBasicBlock());
    assertNull(saved.get(1).getSinkCallKind());
    assertNull(saved.get(1).getSinkCallTarget());
  }
}

