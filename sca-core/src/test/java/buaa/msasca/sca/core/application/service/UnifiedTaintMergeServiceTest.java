package buaa.msasca.sca.core.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import buaa.msasca.sca.core.domain.enums.RoleType;
import buaa.msasca.sca.core.domain.enums.Severity;
import buaa.msasca.sca.core.port.out.persistence.CodeqlFindingPort;
import buaa.msasca.sca.core.port.out.persistence.MscanFindingQueryPort;
import buaa.msasca.sca.core.port.out.persistence.UnifiedTaintRecordCommandPort;

@ExtendWith(MockitoExtension.class)
class UnifiedTaintMergeServiceTest {

  @Mock
  private CodeqlFindingPort codeqlFindingPort;

  @Mock
  private MscanFindingQueryPort mscanFindingQueryPort;

  @Mock
  private UnifiedTaintRecordCommandPort unifiedTaintRecordCommandPort;

  @InjectMocks
  private UnifiedTaintMergeService service;

  /**
   * 시나리오:
   * - CodeQL finding 1개와 MScan finding 2개가 존재
   * - CodeQL finding은 그중 1개 MScan finding과 매칭되고, 나머지 1개는 매칭되지 않음
   * 기대:
   * - unified 결과가 총 2개 생성됨
   * - 매칭된 unified는 CodeQL flowSteps를 SOURCE/INTERMEDIATE/SINK 역할로 변환해 steps를 생성
   * - 매칭되지 않은 unified는 CodeQL fields가 null이고, mscan의 vulId/flowIndex 등을 기반으로 SOURCE/SINK steps를 생성
   */
  @Test
  void mergeAndStore_mergesCodeqlFlowSteps_andAddsUnusedMscan() {
    Long analysisRunId = 42L;

    List<CodeqlFindingPort.FlowStepView> steps = List.of(
        new CodeqlFindingPort.FlowStepView(0, "/src/A.java", 10, "s0"),
        new CodeqlFindingPort.FlowStepView(1, "/src/B.java", 11, "s1"),
        new CodeqlFindingPort.FlowStepView(2, "/src/C.java", 12, "s2")
    );

    CodeqlFindingPort.CodeqlFindingView c1 = new CodeqlFindingPort.CodeqlFindingView(
        1L, // finding 식별자
        100L, // service 모듈 ID
        "/src", // 루트 경로
        "RCE_RULE",
        "Found RCE issue in project",
        "high",
        "/src/C.java",
        12,
        steps
    );

    MscanFindingQueryPort.MscanFindingView m1 = new MscanFindingQueryPort.MscanFindingView(
        200L,
        0,
        "srcSig-1",
        "sinkSig-1",
        "RCE",
        1000L,
        2000L,
        "/src/C.java",
        12,
        "rawFlow-1"
    );

    // CodeQL 결과에 의해 매칭되지 않음: merge 루프 이후에 추가되어야 한다.
    MscanFindingQueryPort.MscanFindingView m2 = new MscanFindingQueryPort.MscanFindingView(
        201L,
        5,
        "srcSig-2",
        "sinkSig-2",
        "XSS",
        3000L,
        4000L,
        "/other/Other.java",
        30,
        "rawFlow-2"
    );

    when(codeqlFindingPort.findByAnalysisRunId(analysisRunId)).thenReturn(List.of(c1));
    when(mscanFindingQueryPort.findByAnalysisRunId(analysisRunId)).thenReturn(List.of(m1, m2));

    ArgumentCaptor<List<UnifiedTaintRecordCommandPort.UnifiedTaintUpsert>> outCaptor =
        ArgumentCaptor.forClass(List.class);

    service.mergeAndStore(analysisRunId);

    verify(unifiedTaintRecordCommandPort).replaceByAnalysisRun(anyLong(), outCaptor.capture());
    List<UnifiedTaintRecordCommandPort.UnifiedTaintUpsert> out = outCaptor.getValue();

    assertEquals(2, out.size());

    // 1) CodeQL 결과 + 매칭된 MScan 결과에 대한 통합 레코드
    var u0 = out.get(0);
    assertEquals(1L, u0.codeqlFindingId());
    assertEquals(200L, u0.mscanFindingId());
    assertEquals("RCE_RULE", u0.vulnerabilityType());
    assertEquals("Found RCE issue in project", u0.title());
    assertEquals(Severity.HIGH, u0.severity());
    assertEquals("/src/A.java", u0.sourceFilePath());
    assertEquals(10, u0.sourceLine());
    assertEquals("/src/C.java", u0.sinkFilePath());
    assertEquals(12, u0.sinkLine());

    assertEquals(3, u0.steps().size());
    assertEquals(RoleType.SOURCE, u0.steps().get(0).role());
    assertEquals(RoleType.INTERMEDIATE, u0.steps().get(1).role());
    assertEquals(RoleType.SINK, u0.steps().get(2).role());
    assertEquals("s0", u0.steps().get(0).description());
    assertEquals("s2", u0.steps().get(2).description());

    // 2) 사용되지 않은(매칭되지 않은) MScan 결과에 대한 통합 레코드
    var u1 = out.get(1);
    assertNull(u1.codeqlFindingId());
    assertEquals(201L, u1.mscanFindingId());
    assertEquals("XSS", u1.vulnerabilityType());
    assertEquals("MScan flow #5", u1.title());
    assertEquals(Severity.MEDIUM, u1.severity());
    assertNull(u1.sourceFilePath());
    assertNull(u1.sourceLine());
    assertEquals("/other/Other.java", u1.sinkFilePath());
    assertEquals(30, u1.sinkLine());

    assertEquals(2, u1.steps().size());
    assertEquals(RoleType.SOURCE, u1.steps().get(0).role());
    assertEquals(RoleType.SINK, u1.steps().get(1).role());
  }

  /**
   * 시나리오:
   * - CodeQL finding이 존재하지만 ruleId/message/flowSteps가 비어 있거나 부족한 상황
   * - MScan finding 1개가 존재하며, 매칭 조건(라인 유사 등)이 만족됨
   * 기대:
   * - vulnerabilityType/title/severity는 CodeQL보다 MScan 정보를 더 적극적으로 fallback하여 채워짐
   * - CodeQL flowSteps가 비어 있으므로 unified steps는 MScan 기반 SOURCE/SINK 2단계로 생성됨
   */
  @Test
  void mergeAndStore_whenCodeqlFieldsMissing_fallsBackToMscanAndGeneratesSourceSinkSteps() {
    Long analysisRunId = 77L;

    List<CodeqlFindingPort.FlowStepView> emptySteps = List.of();
    CodeqlFindingPort.CodeqlFindingView c1 = new CodeqlFindingPort.CodeqlFindingView(
        11L,
        101L,
        "/src",
        "   ", // ruleId가 비어 있음 => mscan의 vulId로 대체
        "   ", // message가 비어 있음 => title 기본값 사용
        null, // level이 없음 => mscan.vulId 기반으로 Severity 결정
        "/a/b.java",
        100,
        emptySteps
    );

    MscanFindingQueryPort.MscanFindingView m1 = new MscanFindingQueryPort.MscanFindingView(
        211L,
        7,
        "sourceSig",
        "sinkSig",
        "CMD",
        501L,
        502L,
        "/a/b.java",
        102, // delta=5 범위 내 line 유사(매칭 점수 임계값 만족)
        null
    );

    when(codeqlFindingPort.findByAnalysisRunId(analysisRunId)).thenReturn(List.of(c1));
    when(mscanFindingQueryPort.findByAnalysisRunId(analysisRunId)).thenReturn(List.of(m1));

    ArgumentCaptor<List<UnifiedTaintRecordCommandPort.UnifiedTaintUpsert>> outCaptor =
        ArgumentCaptor.forClass(List.class);

    service.mergeAndStore(analysisRunId);

    verify(unifiedTaintRecordCommandPort).replaceByAnalysisRun(anyLong(), outCaptor.capture());
    List<UnifiedTaintRecordCommandPort.UnifiedTaintUpsert> out = outCaptor.getValue();

    assertEquals(1, out.size());
    var u = out.get(0);

    assertEquals(11L, u.codeqlFindingId());
    assertEquals(211L, u.mscanFindingId());
    assertEquals("CMD", u.vulnerabilityType());
    assertEquals("MScan flow #7", u.title());
    assertTrue(u.description().contains("mscanVulId=CMD"));
    assertEquals(Severity.HIGH, u.severity());

    // flowSteps가 비어 있으면 mscan 기반 SOURCE/SINK 2단계로 생성된다.
    assertEquals(2, u.steps().size());
    assertEquals(RoleType.SOURCE, u.steps().get(0).role());
    assertEquals(RoleType.SINK, u.steps().get(1).role());
    assertEquals("/a/b.java", u.sinkFilePath());
    // sinkLine은 mscan의 sinkLine이 아니라 CodeQL의 기본 라인을 우선 사용한다.
    assertEquals(100, u.sinkLine());
  }
}

