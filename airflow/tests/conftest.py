"""pytest 공통 설정. dags/ 를 import 경로에 올려 순수 함수 단위 테스트가 Airflow 컨텍스트 없이 돌게 한다."""
import sys
from pathlib import Path

DAGS_DIR = Path(__file__).resolve().parent.parent / "dags"
if str(DAGS_DIR) not in sys.path:
    sys.path.insert(0, str(DAGS_DIR))
