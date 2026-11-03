# GBSW

차량(Android Automotive OS) 전용 앱. Car App Library 기반, MVVM 구조.

## 모듈

| 모듈 | 역할 |
|---|---|
| `:automotive` | 차량용 앱 진입점 (`CarAppActivity`). 코드는 거의 없고 매니페스트 위주 |
| `:shared` | 실제 로직. Car App Service/Session/Screen, 이후 Model·ViewModel도 여기에 둠 |
| `:mobile` | Android Studio 기본 템플릿(Compose). 차량 전용이라 현재 사용하지 않음 |

## MVVM 구조 (계획)

```
:shared
 ├─ model/       데이터 클래스, Repository
 ├─ viewmodel/   화면 상태와 로직
 ├─ view/        Car App Screen (화면)
 └─ service/     MyCarAppService, MyCarAppSession
```

- **View**: `Screen`이 화면을 그리고 ViewModel의 상태만 보여줌
- **ViewModel**: 화면에 필요한 상태와 동작을 관리
- **Model**: 데이터와 Repository
- **Service**: Car App 호스트와 연결되는 진입점

## 개발 환경

- AGP 9.5.0, Kotlin 2.2.10
- minSdk 35, targetSdk 37
- Android Studio에서 `automotive` 구성으로 Automotive 에뮬레이터에서 실행

## 협업 규칙

- `main`에 직접 푸시하지 않고 기능별 브랜치 + PR
- `local.properties`는 커밋하지 않음
