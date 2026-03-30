package buaa.msasca.sca.core.application.pipeline;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import buaa.msasca.sca.core.domain.enums.MscanSummaryStatus;
import buaa.msasca.sca.core.port.out.persistence.MscanResultPort;
import buaa.msasca.sca.core.port.out.tool.MscanPort;

class PipelineExecutorStageTest {

  @Test
  void execute_mscanOnly_runsAgentKnowledgeThenMscanThenMerge_skipsBuildAndCodeql(
      @TempDir Path tempDir
  ) throws Exception {
    long analysisRunId = 1L;
    var ctx = PipelineExecutorTestFactory.createMscanOnly(
        tempDir,
        analysisRunId,
        "test-mscan",
        false,
        false
    );

    ctx.executor().execute(analysisRunId);

    InOrder order = inOrder(ctx.toolRunCommandPort(), ctx.mscanPort(), ctx.unifiedTaintMergeService());
    order.verify(ctx.toolRunCommandPort()).createAgentRun(
        eq(analysisRunId),
        eq("agent-knowledge"),
        eq("agent"),
        any(JsonNode.class)
    );
    order.verify(ctx.mscanPort()).runGlobalAnalysis(any(MscanPort.RunRequest.class));
    order.verify(ctx.unifiedTaintMergeService()).mergeAndStore(eq(analysisRunId));

    verify(ctx.toolRunCommandPort(), never()).createBuildRun(anyLong(), anyLong(), anyString(), any(JsonNode.class));
    verify(ctx.toolRunCommandPort(), never()).createCodeqlRun(anyLong(), anyLong(), anyString(), any(JsonNode.class));
    verify(ctx.codeqlPort(), never()).createDatabase(any());

    verify(ctx.mscanResultPort()).replaceAll(
        eq(PipelineExecutorTestFactory.MSCAN_TOOL_RUN_ID),
        anyList()
    );
    ArgumentCaptor<List<MscanResultPort.MscanFindingIngest>> ingestCaptor = ArgumentCaptor.forClass(List.class);
    verify(ctx.mscanResultPort()).replaceAll(eq(PipelineExecutorTestFactory.MSCAN_TOOL_RUN_ID), ingestCaptor.capture());
    MscanResultPort.MscanFindingIngest firstIngest = ingestCaptor.getValue().get(0);
    assertEquals(227, firstIngest.sinkLine());
    assertEquals("invokestatic", firstIngest.sinkCallKind());
    assertTrue(firstIngest.sinkCallTarget().contains("Files.write"));
    assertNotNull(firstIngest.sinkFilePath());
    assertTrue(firstIngest.sinkFilePath().endsWith(
        "/sm1/src/main/java/org/springframework/cloud/skipper/server/service/PackageService.java"
    ));
    verify(ctx.mscanRunSummaryCommandPort()).upsert(
        eq(PipelineExecutorTestFactory.MSCAN_TOOL_RUN_ID),
        eq(MscanSummaryStatus.HAS_RESULTS),
        eq(1),
        anyString(),
        anyString(),
        any(Instant.class)
    );
  }

  @Test
  void execute_normal_runsBuildThenCodeqlSkipThenAgentKnowledgeThenMscanThenMerge(
      @TempDir Path tempDir
  ) throws Exception {
    long analysisRunId = 2L;
    var ctx = PipelineExecutorTestFactory.createNormal(
        tempDir,
        analysisRunId,
        "test-mscan",
        false,
        false
    );

    ctx.executor().execute(analysisRunId);

    InOrder order = inOrder(ctx.toolRunCommandPort(), ctx.mscanPort(), ctx.unifiedTaintMergeService());
    order.verify(ctx.toolRunCommandPort()).createBuildRun(
        eq(analysisRunId),
        eq(PipelineExecutorTestFactory.MODULE_ID),
        eq("docker-build"),
        any(JsonNode.class)
    );
    order.verify(ctx.toolRunCommandPort()).createCodeqlRun(
        eq(analysisRunId),
        eq(PipelineExecutorTestFactory.MODULE_ID),
        eq("codeql"),
        any(JsonNode.class)
    );
    order.verify(ctx.toolRunCommandPort()).createAgentRun(
        eq(analysisRunId),
        eq("agent-knowledge"),
        eq("agent"),
        any(JsonNode.class)
    );
    order.verify(ctx.mscanPort()).runGlobalAnalysis(any(MscanPort.RunRequest.class));
    order.verify(ctx.unifiedTaintMergeService()).mergeAndStore(eq(analysisRunId));

    verify(ctx.codeqlPort(), never()).createDatabase(any());

    verify(ctx.mscanResultPort()).replaceAll(
        eq(PipelineExecutorTestFactory.MSCAN_TOOL_RUN_ID),
        anyList()
    );
    ArgumentCaptor<List<MscanResultPort.MscanFindingIngest>> ingestCaptor = ArgumentCaptor.forClass(List.class);
    verify(ctx.mscanResultPort()).replaceAll(eq(PipelineExecutorTestFactory.MSCAN_TOOL_RUN_ID), ingestCaptor.capture());
    MscanResultPort.MscanFindingIngest firstIngest = ingestCaptor.getValue().get(0);
    assertEquals(227, firstIngest.sinkLine());
    assertEquals("invokestatic", firstIngest.sinkCallKind());
    assertTrue(firstIngest.sinkCallTarget().contains("Files.write"));
    assertNotNull(firstIngest.sinkFilePath());
    assertTrue(firstIngest.sinkFilePath().endsWith(
        "/sm1/src/main/java/org/springframework/cloud/skipper/server/service/PackageService.java"
    ));
    verify(ctx.mscanRunSummaryCommandPort()).upsert(
        eq(PipelineExecutorTestFactory.MSCAN_TOOL_RUN_ID),
        eq(MscanSummaryStatus.HAS_RESULTS),
        eq(1),
        anyString(),
        anyString(),
        any(Instant.class)
    );
  }

  @Test
  void execute_mscanOnly_whenAgentPrefilterThrows_failOpen_andStillRunsMscanAndMerge(
      @TempDir Path tempDir
  ) throws Exception {
    long analysisRunId = 3L;
    var ctx = PipelineExecutorTestFactory.createMscanOnly(
        tempDir,
        analysisRunId,
        "test-mscan",
        true,
        false
    );

    ctx.executor().execute(analysisRunId);

    verify(ctx.mscanPort()).runGlobalAnalysis(any(MscanPort.RunRequest.class));
    verify(ctx.unifiedTaintMergeService()).mergeAndStore(eq(analysisRunId));

    verify(ctx.toolRunCommandPort(), never()).createBuildRun(anyLong(), anyLong(), anyString(), any(JsonNode.class));
    verify(ctx.toolRunCommandPort(), never()).createCodeqlRun(anyLong(), anyLong(), anyString(), any(JsonNode.class));
  }

  @Test
  void execute_mscanOnly_whenAgentKnowledgeThrows_failOpen_andStillRunsMscanAndMerge(
      @TempDir Path tempDir
  ) throws Exception {
    long analysisRunId = 4L;
    var ctx = PipelineExecutorTestFactory.createMscanOnly(
        tempDir,
        analysisRunId,
        "test-mscan",
        false,
        true
    );

    ctx.executor().execute(analysisRunId);

    verify(ctx.mscanPort()).runGlobalAnalysis(any(MscanPort.RunRequest.class));
    verify(ctx.unifiedTaintMergeService()).mergeAndStore(eq(analysisRunId));

    verify(ctx.toolRunCommandPort(), never()).createBuildRun(anyLong(), anyLong(), anyString(), any(JsonNode.class));
    verify(ctx.toolRunCommandPort(), never()).createCodeqlRun(anyLong(), anyLong(), anyString(), any(JsonNode.class));
  }

  @Test
  void execute_mscanOnly_whenGatewayYamlCacheFileExists_skipsGatewayLookupAndRunsMscanAndMerge(
      @TempDir Path tempDir
  ) throws Exception {
    long analysisRunId = 5L;
    var ctx = PipelineExecutorTestFactory.createMscanOnly(
        tempDir,
        analysisRunId,
        "test-mscan",
        false,
        false
    );

    ctx.executor().execute(analysisRunId);

    verify(ctx.mscanGatewayYamlPort(), never()).findByProjectVersionId(anyLong());
    verify(ctx.mscanGatewayYamlCommandPort(), never()).ensureMissing(anyLong(), anyString());

    verify(ctx.mscanPort()).runGlobalAnalysis(any(MscanPort.RunRequest.class));
    verify(ctx.unifiedTaintMergeService()).mergeAndStore(eq(analysisRunId));
  }
}
