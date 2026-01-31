package buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail;

import buaa.msasca.sca.infra.persistence.jpa.entity.run.ToolRunEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "agent_run_detail")
public class AgentRunDetailEntity {

    @Id
    @Column(name = "tool_run_id")
    private Long toolRunId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "tool_run_id")
    private ToolRunEntity toolRun;

    @Column(name = "model_name", length = 128)
    private String modelName;

    private AgentRunDetailEntity(ToolRunEntity toolRun, String modelName) {
        this.toolRun = toolRun;
        this.modelName = modelName;
    }

    public static AgentRunDetailEntity create(ToolRunEntity toolRun, String modelName) {
        return new AgentRunDetailEntity(toolRun, modelName);
    }

    public void changeModel(String modelName) { this.modelName = modelName; }
}
