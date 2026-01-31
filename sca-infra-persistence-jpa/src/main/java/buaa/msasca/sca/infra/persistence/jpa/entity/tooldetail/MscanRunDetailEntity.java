package buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail;

import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.MscanRunDetailEntity;
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
@Table(name = "mscan_run_detail")
public class MscanRunDetailEntity {

    @Id
    @Column(name = "tool_run_id")
    private Long toolRunId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "tool_run_id")
    private ToolRunEntity toolRun;

    private MscanRunDetailEntity(ToolRunEntity toolRun) { this.toolRun = toolRun; }

    public static MscanRunDetailEntity create(ToolRunEntity toolRun) {
        return new MscanRunDetailEntity(toolRun);
    }
}
