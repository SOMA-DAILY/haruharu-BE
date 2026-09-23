# Video staging lifecycle

영상 업로드 세션은 2시간 후 만료되지만, 앱이 업로드 도중 종료되면 S3 임시 객체는 남을 수 있다.
S3 Lifecycle은 경로 중간의 `*/staging/*` 같은 glob을 지원하지 않으므로 임시 객체는
`users/video-upload-staging/{userId}/videos/{uploadId}/` 공통 prefix에 저장한다. 게시 객체는 기존
`users/{userId}/videos/{uploadId}/published/` 경로를 유지한다.

`video-staging-lifecycle-rule.json`은 `users/video-upload-staging/` 객체를 3일 후 만료한다. 업로드 세션의
2시간 재시도 기한보다 충분히 길면서 중단된 업로드를 자동으로 정리하는 보존 기간이다.
버킷에 버전 관리가 켜져 있으면 noncurrent version도 정리하며, 미완료 multipart upload는
1일 후 중단한다.

적용 주체에는 대상 버킷의 `s3:GetLifecycleConfiguration`과
`s3:PutLifecycleConfiguration` 권한이 필요하다. 버킷명과 리전은 저장소에 기록하지 않고
배포 환경에서 인자로 전달한다.

앱 런타임 IAM과 버킷 정책에서 `arn:aws:s3:::<bucket>/users/video-upload-staging/*`에
`s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`를 허용해야 한다. DB 게시가 롤백되거나
복사 중 실패하면 `users/{userId}/videos/{uploadId}/published/*`도 삭제하므로 이 경로에도
`s3:DeleteObject`를 허용해야 한다. S3 `CopyObject`는 별도
IAM action이 아니므로 source staging 객체의 `s3:GetObject`와 destination published 객체의
`s3:PutObject` 권한을 모두 확인한다. 배포 전 실제 dev 버킷에서 staging PUT·HEAD·COPY·DELETE
프로브를 수행하고 운영 IAM에도 같은 resource 범위가 적용됐는지 확인한다.

```bash
scripts/s3/video-staging-lifecycle.sh upsert "$BUCKET" "$AWS_REGION"
scripts/s3/video-staging-lifecycle.sh check  "$BUCKET" "$AWS_REGION"
```

`upsert`는 먼저 현재 설정을 읽고 `expire-video-upload-staging` ID의 규칙만 교체하므로 다른
Lifecycle 규칙과 `TransitionDefaultMinimumObjectSize` 설정을 보존한다. 동일한 설정이면 PUT을
생략한다. `check`는 버킷 설정을 다시 읽어 규칙이 파일과 정확히 같은지 검사하며, 배포 증빙으로
종료 코드 0과 `PASS lifecycle rule` 출력을 보관한다. S3 Lifecycle 설정 전체를 교체하는 원시
`put-bucket-lifecycle-configuration` 명령을 직접 실행하지 않는다.

기존 Lifecycle 규칙이 있는데 AWS CLI가 `TransitionDefaultMinimumObjectSize`를 반환하지 않으면
`upsert`는 설정을 쓰기 전에 실패한다. 이 필드와
`--transition-default-minimum-object-size`를 지원하는 최신 AWS CLI로 업그레이드한 뒤 다시 실행한다.

동시에 여러 배포가 Lifecycle 설정을 수정하면 read-modify-write 경합이 생길 수 있으므로 이
스크립트는 환경별 배포 작업에서 한 번에 하나만 실행한다.
