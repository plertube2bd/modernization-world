# 인계 문서: Modernization World 1.0.0 작업 이어서 하기

이 문서를 다른 AI(ChatGPT 등)에게 그대로 붙여 넣고, 아래 "같이 줄 파일"을 첨부하세요.

## 현재 상태 (2026-11-08, 아래 본문보다 우선)

- **보스 장비**를 넣었습니다(`src/main/java/com/bossmode/gear/`, 믹스인 `com/bossmode/mixin/BossGearDamageMixin`, 리소스 생성기 `tools/boss_mode/gen_boss_gear.py`). 네 보스가 재료를 떨어뜨리고, 재료로 도구·무기·갑옷 10종씩. 네 보스 재료를 하나씩 합치면 최종 합금, 그걸로 최종 장비. 수치는 CHANGELOG 11장. 그림은 바닐라 다이아몬드·네더라이트 장비의 모양과 명암에 등급별 색을 입힌 것으로, 생성기가 Loom 캐시의 클라이언트 jar에서 바닐라 그림을 읽습니다(jar가 없으면 그림은 그대로 두고 JSON만 씁니다).
- **보스 체력**: 페이즈마다 1,024였던 것을 1페이즈 100에서 마지막 페이즈 300(드래곤)/400(와이번)/500(거수)/600(지배자)까지 고르게 늘렸습니다(`entity/PhaseHealth.java`, 각 Config의 `FIRST_PHASE_HEALTH`·`LAST_PHASE_HEALTH`). 회복도 페이즈 체력에 비례합니다. CHANGELOG 12장.
- 서버 게임 테스트 239개 통과(보스 장비 12개, 보스 체력 4개 포함). 클라이언트에서 다섯 세트를 입힌 모습(`RT_SHOTS=gear`)과 네더라이트 창·보스 창의 1인칭·3인칭 동작(`RT_SHOTS=spear`)을 찍었습니다.

### 2026-11-07 상태

- 아래 "완료 (커밋 전)"의 작업은 이제 모두 커밋돼 GitHub main에 올라가 있습니다.
- **음반 5곡은 끝났습니다**(음반 7장). 음원 ogg는 git에서 빠져 있어 GitHub에서 받아 빌드하면 소리가 없습니다.
- **영국군 레이드**는 다른 AI가 만든 v14(`com.example.modernizationworld.redcoat`)를 그대로 넣었습니다. 이 PC에서는 빌드·테스트하지 않았습니다.
- 코드를 읽어서 찾은 v14의 문제:
  1. 모자가 장비(갑옷) 에셋으로 등록돼 있는데 `RedcoatRenderer`에 `HumanoidArmorLayer`가 없습니다. 그래서 병사에게 모자가 안 그려질 가능성이 높고, 그려져도 머리를 감싼 평평한 상자일 뿐 높은 모자 모양이 아닙니다.
  2. 군복 스킨이 단순합니다. 그림의 흰 바지, 흰 교차 띠, 검은 장화가 없습니다.
  3. `ItemInHandLayer`를 한 번 더 추가해서 무기가 두 번 그려집니다.
  4. 하사가 미늘창 대신 삼지창을 듭니다.
  5. 척탄병 수류탄이 대상 위치에서 즉시 터지는 폭발이라, 던지는 물체가 없고 주변 병사도 다칩니다.
  6. 장교·용기병이 매 틱 무기를 새로 쥡니다(장비 패킷 낭비).
  7. 사격할 때 시야(벽)를 확인하지 않고 조준 자세도 없습니다.
  8. 웨이브 판정이 저장된 병사 UUID가 아니라 반경 64칸 안의 병사 수입니다.
  9. 테스트가 얕습니다. 실제 사격·피해·레이드 진행·용기병 말 이동을 시험하지 않고, 한 테스트는 테스트 월드에 진짜 레이드를 시작합니다.
- 그대로 남은 일: BOSS 클라이언트 테스트의 사망 단계 실패, 모터 테스트 실행, 문서(CHANGELOG·README) 갱신, 전체 테스트.

---

## 프로젝트

- Minecraft **26.3** Fabric 모드 (Fabric Loader 0.19.5, Fabric API 0.165.0+26.3, Loom 1.19.3, **Java 25**, Gradle 9.8.1).
- 26.x는 **난독화가 없는 Mojang 이름**을 씁니다(Yarn 아님). `Identifier`(ResourceLocation 아님), `hurtServer(ServerLevel, DamageSource, float)`, 렌더 상태(RenderState) 방식 렌더러 등 최신 API입니다.
- 모드 ID `modernization_world`. `analog_alu`, `redstone_transportation`, `boss_mode` 모드 ID를 `provides`로 대신 제공합니다(기존 월드 호환). 블록·아이템 ID는 각 네임스페이스 그대로입니다.
- 소스 위치:
  - `src/main/java/com/example/analogalu/` — ALU(회로, 석유, 총기 `gun/`)
  - `src/main/java/com/example/redstonetransportation/` — 운송·공장·구조물·자동차
  - `src/main/java/com/example/modernizationworld/` — 합친 모드 자체 기능(음반)
  - `src/main/java/com/bossmode/` — BOSS Mode(보스, GeckoLib 사용)
  - `src/gametest/` — 서버 게임 테스트와 클라이언트 게임 테스트
- 소스 세트는 하나입니다(클라이언트 전용 클래스도 `src/main`에 있고, 클라이언트 엔트리포인트에서만 씁니다).
- GitHub: https://github.com/plertube2bd/analogAlu (main = `28e85cd`까지 올라가 있음). **아래 "진행 중" 작업은 아직 커밋되지 않았습니다.**

## 빌드와 테스트 (Windows)

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-25.0.3"
cmd //c "C:\Gradle\gradle-9.8.1\bin\gradle.bat --offline -q runGameTest"        # 서버 게임 테스트
cmd //c "C:\Gradle\gradle-9.8.1\bin\gradle.bat --offline -q runClientGameTest"  # 클라이언트 게임 테스트(스크린샷)
cmd //c "C:\Gradle\gradle-9.8.1\bin\gradle.bat --offline -q build -x runGameTest -x runClientGameTest"  # jar
```

- `RT_SHOTS=guns,car,power,electric,music,none` 환경 변수를 주면 운송 쪽 클라이언트 테스트가 그 장면만 찍습니다.
- 리소스는 `tools/*.py`(표준 라이브러리만)로 생성합니다: `gen_gun_assets.py`(총), `generate_assets.py`(운송 블록), `gen_music_discs.py`(음반). 생성기는 다른 모드가 같이 쓰는 태그 파일을 **덮어쓰지 말고 합쳐야** 합니다.
- GeckoLib 5.5.7은 `libs/maven/com/geckolib/geckolib-fabric-26.3/5.5.7/`(로컬 메이븐, git 제외)에서 가져오고 `include`로 jar 안에 넣습니다.
- 마지막으로 확인된 상태: 서버 게임 테스트 **216개 통과**(모터 테스트 2개는 아직 안 돌림).

## 이번 요청 (사용자 원문 요약)

1. 유튜브 5곡을 음반으로 추가 (MP3는 사용자가 마지막에 줌)
2. 비행선·배 모터를 추가해 속도를 올리기
3. 총: **좌클릭 발사, 우클릭 조준**
4. **영국군 레이드** 추가: 총을 든 병사, **명중률 50%**, 그림(1750 척탄병·1780 장교·1807 하사·1835 머스킷 병사) 같은 군복, 종류 더 추가
5. BOSS_Mode 모드(`C:\BOSS_Mode`)를 합치기

사용자가 정한 것:
- 레이드 시작: **흉조 병(ominous bottle) + 빨간 염료 → "영국군 흉조 병"**, 마시면 효과가 걸리고 마을에 들어가면 레이드 시작(바닐라 약탈자 레이드 방식)
- 영국군 총 피해: **플레이어 총과 같게**(머스킷 30, 리볼버 25), 명중률 50%
- 추가 병사: **소총병, 고수, 용기병(말)**
- 음반은 **맨 마지막**

## 완료 (커밋 전)

### 1. BOSS_Mode 합치기
- `C:\BOSS_Mode`의 main·client 소스, 리소스(`assets/boss_mode`, `data/boss_mode`), 테스트, `tools/boss_mode`, `docs/boss/README.md`를 복사했습니다. `data/minecraft` 태그 4개는 합쳤습니다.
- `fabric.mod.json`에 BossMode 엔트리포인트, 클라이언트 믹스인(`boss_mode.client.mixins.json`), `provides: boss_mode`, `depends: geckolib >=5.5.7`를 넣었습니다.
- 서버 테스트는 통과합니다(모드 46개 로드).
- **남은 문제:** BOSS 클라이언트 테스트(`com.bossmode.test.ShardDragonClientGameTest`)가 "death animation running"에서 실패합니다.
  - `/kill` 직후 드래곤이 **PHASE_TRANSITION, 체력 1.0** 상태입니다. `ShardDragonEntity.setHealth`가 마지막 페이즈 전의 치명타를 체력 1로 막는 처리이고, 이 처리는 `allowDeath`(= 피해가 `bypasses_invulnerability`)가 false일 때만 일어납니다.
  - 원래 프로젝트(Fabric API 0.161)에서는 통과했고, 그 뒤로 원래 소스는 바뀌지 않았습니다.
  - 데이터 태그 덮어쓰기는 없음을 확인했습니다. 페이즈 번호를 로그로 찍는 진단 코드를 테스트에 넣는 중에 중단됐습니다(`ShardDragonClientGameTest.java` 260~280행 근처).

### 2. 총 조작 (좌클릭 발사 / 우클릭 조준)
- `GunActionPayload`에 `Action { FIRE, COCK_OR_RELOAD }`를 넣었습니다.
- `GunItem.use()`는 조준(`startUsingItem`, `ItemUseAnimation.SPYGLASS`)이고, `GunItem.fire(ServerPlayer, ItemStack)`가 발사입니다.
- `GunAttackMixin`(Minecraft.startAttack/continueAttack)이 좌클릭을 발사로 바꾸고 블록 캐기를 막습니다. 조준 중에는 바닐라가 공격 클릭을 버리므로 `GunClient`의 START_CLIENT_TICK에서 먼저 가져갑니다.
- `GunAimFovMixin`: 조준 중 FOV 확대(머스킷 0.55, 리볼버 0.75, 샷건 0.85). 샷건 조준 시 퍼짐 ×0.6.
- `models/item/*_aim.json` + 아이템 정의의 `minecraft:using_item` 조건으로 조준 중 총이 화면 가운데로 옵니다(이 위치는 아직 화면으로 확인 안 함).
- `GunFiring.fire(ServerLevel, LivingEntity, GunType, Vec3 look, double spread, double range, Predicate<Entity> hittable)`로 일반화했습니다. **영국군 병사가 이걸 쓰면 됩니다.**
- 테스트: `GunGameTests`에 조준 테스트 2개 추가(통과). 클라이언트 테스트에서 R → 우클릭 유지(조준) → 좌클릭 발사로 허스크 피해 확인(통과).

### 3. 배·비행선 모터
- 블록 `redstone_transportation:ship_motor`, `airship_motor`를 추가했습니다(ModContent, 크리에이티브 탭, 툴팁, 언어, 생성기 `write_vessel_motors`, 조합법).
  - 선박 모터: `IEI / CPC / _I_`(E 디젤 엔진, P 피스톤, C 구리 주괴)
  - 비행선 모터: `IEI / SPS / _I_`(S 막대)
- `MovingStructureEntity.motors()`/`motorBoost()`: 맞는 종류 모터 1개마다 최고 속도 +25%, 최대 4개(2배). 비행선은 상승 속도도 같이 오릅니다. 모터 1개마다 디젤 +25%(소수는 `fuelOwed`에 누적).
- 테스트 `shipMotorsMakeAShipFaster`, `airshipMotorsCountOnAnAirship`를 `StructureGameTests` 끝에 넣었지만 **아직 실행 안 함**.

## 남은 일

1. BOSS 클라이언트 테스트의 사망 단계 원인 찾기
2. 모터 테스트 실행, 조준 화면(총이 가운데 오는지) 확인
3. **영국군 레이드** (네임스페이스 `modernization_world`, 패키지 `com.example.modernizationworld.redcoat` 제안)
   - 병사 7종 (모두 `Monster` 기반, 철 골렘과 서로 적대):
     - 머스킷 병사(1835): 벨톱 샤코+깃털, 빨간 상의, 흰 교차 띠, 흰 바지 · 머스킷(30, 48칸, 장전 60틱)
     - 척탄병(1750): 높은 미트라 모자 · 머스킷 + 가끔 수류탄(블록 안 부숨)
     - 하사(1807): 원통형 샤코, 검은 장화 · 미늘창 근접
     - 장교(1780): 검은 삼각모, 파란 깃 · 검(근접) + 권총(리볼버 수치 25, 32칸)
     - 소총병: 녹색 군복 · 소총(머스킷 피해, 사거리 64)
     - 고수: 무기 없음, 북을 쳐 주변 병사에게 신속·힘 버프, 플레이어를 피함
     - 용기병: 바닐라 말(안장·길들임)을 타고 등장 · 검 + 권총
   - 명중률 50%: 맞히는 경우 목표 몸 중앙으로, 빗나가는 경우 목표를 확실히 벗어나는 각도로 `GunFiring.fire`. 같은 편은 맞지 않게 predicate로 제외.
   - 겉모습: 사람형(HumanoidMobRenderer) + 64×64 스킨(파이썬으로 생성). 모자는 **머리 칸 3D 아이템**(CustomHeadLayer가 그려 줌, 플레이어도 착용 가능). 총을 들 때 팔 자세는 CROSSBOW_HOLD.
   - 레이드: `ominous_bottle + red_dye → 영국군 흉조 병`(마시면 `영국군 흉조` 효과, 100분). 효과 중 마을(`ServerLevel.isVillage`)에 들어가면 시작.
     - 웨이브: 쉬움 3 / 보통 5 / 어려움 7. 마을 중심 24~32칸 밖에서 나타나 중심으로 행진.
     - 빨간 보스바, SavedData로 저장(병사 UUID).
     - 승리: 마을의 영웅 효과. 패배: 주민이 모두 사라지면.
   - 스폰 알, 언어(ko_kr/en_us), 서버 게임 테스트(명중률이 약 50%인지, 피해 30, 레이드 시작·웨이브), 클라이언트 스크린샷(그림처럼 줄 세우기)
4. 음반 5곡(사용자가 MP3 제공): ffmpeg로 모노 ogg 변환
   ```bash
   ffmpeg -i in.mp3 -vn -map_metadata -1 -ac 1 -ar 44100 -c:a libvorbis -q:a 5 name.ogg
   ```
   - 이 PC의 ffmpeg: `C:\Users\user\AppData\Local\Medal\recorder-2645.3375.1\ffmpeg7.exe`
   - `tools/gen_music_discs.py`의 `DISCS`에 추가하고, ogg는 `assets/modernization_world/sounds/records/`에 둡니다(git 제외).
   - 곡: Die For You(VALORANT), Mice On Venus(EMBRR), I Really Want to Stay at Your House, はいよろこんで, The Amazing Digital Circus
5. 문서(CHANGELOG_1.0.0_KO.md, README.md, docs/) 갱신, 전체 테스트, jar 빌드, 커밋. GitHub 푸시는 사용자 확인 후에만.

## 지켜 온 규칙
- 답변은 한국어로.
- 결과 jar는 `C:\Users\user\Downloads\modernization-world-1.0.0.jar`에 복사.
- 테스트를 돌려 확인한 것만 "확인했다"고 보고.
- GitHub 푸시는 사용자가 "올려줘"라고 한 뒤에만.
