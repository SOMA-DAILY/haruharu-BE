package org.son.haruharu.batch.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.son.haruharu.batch.api.dto.JobExecutionResponse;
import org.son.haruharu.batch.api.dto.JobExecutionStartOutcome;
import org.son.haruharu.batch.api.dto.JobExecutionStartResponse;
import org.son.haruharu.batch.api.dto.StartJobExecutionRequest;
import org.son.haruharu.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 배치 트리거 API(#389). 외부 오케스트레이터(Airflow) 전용이며 유저·어드민 API 가 아니다.
 * <p>
 * 인증은 {@code X-Internal-Token} 공유 시크릿(InternalBatchSecurityConfig). <b>이 경로는 인터넷에 노출되면 안 된다</b> —
 * 토큰은 2차 방어이고, 1차는 ALB 리스너에서 {@code /internal/*} 차단 + VPC 내부 경로(Service Connect)다(인프라 이슈).
 * <p>
 * 상태 코드: 202 새 실행/재시작 · 200 이미 완료(멱등) · 409 실행 중(동시 launch 레이스 포함) · 404 미등록 잡/실행
 * · 400 잘못된 bucket · 401 토큰 불일치.
 */
@Validated
@RestController
@RequestMapping("/internal/batch")
public class InternalBatchController {

    private final BatchJobExecutionService batchJobExecutionService;

    public InternalBatchController(BatchJobExecutionService batchJobExecutionService) {
        this.batchJobExecutionService = batchJobExecutionService;
    }

    @PostMapping("/jobs/{jobName}/executions")
    public ResponseEntity<ApiResponse<JobExecutionStartResponse>> start(
            @PathVariable @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String jobName,
            @Valid @RequestBody StartJobExecutionRequest request) {
        JobExecutionStartResponse response = batchJobExecutionService.start(jobName, request.bucket());
        HttpStatus status = response.outcome() == JobExecutionStartOutcome.ALREADY_COMPLETED
                ? HttpStatus.OK
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.ok(response));
    }

    @GetMapping("/executions/{executionId}")
    public ApiResponse<JobExecutionResponse> get(@PathVariable long executionId) {
        return ApiResponse.ok(batchJobExecutionService.get(executionId));
    }
}
