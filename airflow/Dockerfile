# 운영(ECS Fargate)용 Airflow 이미지 (#390). 공유 볼륨이 없으므로 DAG 를 이미지에 굽는다 — DAG 수정 = PR = 이미지 빌드 = 롤링 배포.
# 로컬 학습 환경(docker-compose.yaml)은 이 이미지 대신 dags/ 를 볼륨 마운트한다.
FROM apache/airflow:3.3.1
COPY --chown=airflow:root dags/ /opt/airflow/dags/
# Deadline AsyncCallback 은 triggerer 에서 dotted path 로 import 된다 — dags/ 를 모든 컴포넌트의 import 경로에 올린다.
ENV PYTHONPATH=/opt/airflow/dags
