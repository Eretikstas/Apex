# Tasks from Claude (copied from the user's private notes, 23.11)


---

# Задания для ChatGPT до среды 25.11 (от Claude)

## Жёсткие правила (нарушать нельзя)

1. Работаешь ТОЛЬКО в репозитории `Eretikstas/Apex`, ветка `aimi-replay`. Ветку `autoisf-atc3` не трогать никогда.
2. Меняешь ТОЛЬКО: `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/HistoryReplayTest.kt`,
   файлы в `lab/aimi-replay/` (кроме раздела «Results» в `STATUS.md` — он мой).
3. НЕ запускаешь `aimi-replay.yml` (прогоны). Проверка сборки `aimi-replay-compile.yml` идёт сама при push — это можно.
4. НЕ меняешь формулы существующих карточек и поведение без новых токенов: всё новое включается ТОЛЬКО своим токеном,
   без токена прогон должен давать ровно то же, что раньше.
5. Каждое задание — отдельными маленькими коммитами, после каждого дождись зелёной проверки сборки, потом следующее.
   Если сборка красная — сначала чини, новое не начинай.
6. Никаких личных данных в репозитории (имя ребёнка, адрес Nightscout, реальные цифры).
7. В конце запиши в `STATUS.md` (раздел «Task»): что сделано (коммиты), какие токены, что я должен проверить.
8. Не уверен — не делай, напиши вопрос в «Task».

## Задание 1. Два порядка выбора карточки (токены LAORDER_SAFE и LAORDER_EVID)

Сейчас `learnAllCheck` выбирает карточку в постоянном порядке. Добавь:
- `LAORDER_SAFE` — порядок как сейчас (сначала процент вниз и ослабление инсулина: isf+, cr+; потом базал; потом то, что
  даёт больше инсулина). Без токена — тоже этот порядок (поведение не меняется).
- `LAORDER_EVID` — среди карточек, которые хотят изменения, выбрать ту, у которой больше всего принятых данных по журналу
  (accepted episodes/windows), при равенстве — ту, что уменьшает инсулин.
- В строку журнала `learner ALL` добавь: какие карточки хотели изменения, какая выбрана, какие отложены.
Не включать оба токена одновременно (ошибка с понятным текстом).

## Задание 2. Контроль результата (токен LAROLLBACK)

После каждого изменения карточкой запомни: что изменено, день, значение до. Через 4 дня сравни 4 дня после с 7 днями до
ТОЛЬКО по целевой области карточки (базал — изменённые часы; CR — приёмы еды этого блока; ISF и процент — весь день;
dura — весь день). Показатели: % ниже 70 мг/дл, % ниже 54, граммы на лечение гипо в день, % выше 180.
- «Хуже» = ниже 70 выросло больше чем на 1,5 пункта ИЛИ ниже 54 больше 1 % ИЛИ граммы на гипо выросли больше чем на 30 %.
- Если хуже: вернуть значение до (это и есть «карточка отката», считается изменением), причину заморозить на 7 дней.
- Второй откат одной и той же карточки — карточка выключается до конца прогона.
- Дни с меткой illness/activity/oldCannula/sensorGap из сравнения выбрасываются; если чистых дней меньше 3 — «нет решения».
- В журнал: `rollback check card=… before=… after=… verdict=better|same|worse|insufficient`.

## Задание 3. Уверенность (только печать, токен LACONF)

Для каждой карточки в строке журнала напечатай: принятых эпизодов, разных дней, доля выброшенных, разброс (для ISF —
межквартильный размах наблюдений), и категорию low / medium / high по простому правилу (запиши правило в STATUS).
На решения НЕ влияет.

## Задание 4. Готовые строки запуска (без запуска)

В `lab/aimi-replay/BATCHES.md` запиши готовые строки `batch` (токены через запятую, задания через `|`):
- A: 8 ситуаций × seed 1–3 (base, calm, dawn1.3, meals1.3, ins+20, ins-20, start+30, growth2%): вариант APP и вариант
  APP+`LEARNALL,LAISF,LACR,LACAUSE,LAPCTBACK,LADAYS` (токены APP возьми из файла `app22_batch.json`, который тебе дал
  пользователь; ничего не придумывай);
- B: три пары совместимости по seed 1: `MEALX_1.3`+`CHGISF_0.8`, `MEALX_1.3`+`DAYNEED_1.2`, `CHGISF_0.8`+`DAYNEED_1.2`;
- C: OUTDAY_4_1.3, OUTDAY_4_0.7, GAP_15, GAP_30, GAP_60 по seed 1–3 с LACAUSE;
- D: SAFE против EVID на ситуациях B и C.
Проверь, что каждая строка меньше 65 000 символов вся вместе и заданий в одной не больше 256.

## Задание 5 (не код). Письмо автору драйвера v4

На английском, вежливо, коротко, для https://github.com/russoturisto2026-create/aaps-apex-driver (issue). Не упоминать
ребёнка и личные данные. Вопросы:
1. Could you add a license file (for example AGPL-3.0, like AAPS)? We use the driver in our own build.
2. How do empty journal cells look on a new pump? Zeros? (v4 treats a zero cell as a broken record.)
3. A pump replaced by another one with the same serial: does the kept journal copy cause problems?
4. How long does an AA battery last with the held link (HoldLink on)?
5. When the heartbeat is late, the overdue read only reads status and does not settle the journal: a bolus can stay
   unrecorded until the next real heartbeat. Is this intended?
6. We changed two things in our copy: a lost answer to a basal profile write and to a settings write is read back
   instead of failing. Would you accept this as a pull request?
Отдай текст пользователю в чат (не коммить).

---

# Задание 6 для ChatGPT: карточки для ВСЕХ весов AutoISF (от Claude)

Правила те же, что в `2026-11-23_chatgpt-zadaniya.md` (только ветка `aimi-replay`, только `HistoryReplayTest.kt` и
`lab/aimi-replay/`, без прогонов, всё новое — только своим токеном, без токена поведение не меняется, маленькие
коммиты, зелёная сборка после каждого). Делать ПОСЛЕ заданий 1–3.

## Что есть сейчас

Веса AutoISF модели — `AisfWeights` (поля acce, brake, lower, higher, pp, dura, min, max; токены WACC/WBRK/WLOW/WHIGH/
WPP/WDUR/WMIN/WMAX). Карточка есть только у dura (`learnDuraCard`, токен LEARNDURA). Нужны карточки для:
**lower, higher, acce, brake, pp**. (min и max — границы, их ученик НЕ трогает.)

## Шаг 0 (обязательно, до кода карточек)

Найди в `HistoryReplayTest.kt`, как каждый вес влияет на ISF в модели (функция расчёта autoISF, переменные `aw.acce`,
`aw.brake`, `aw.lower`, `aw.higher`, `aw.pp`). Для КАЖДОГО веса запиши в `STATUS.md` (Task) одной строкой с номером строки
кода: «вес больше → инсулина больше/меньше, когда …». НЕ угадывай направление — только из кода. Если непонятно —
вопрос в Task, карточку этого веса не делать.

## Шаг 1. Карточки «только печать» (токен LAWREPORT)

Каждая карточка смотрит последние 7 дней, только через фильтр причин (LACAUSE: выбрасывать старую канюлю, пропуски,
ложные провалы, болезнь, активность, отскоки после гипо), минимум 3 разных дня (LADAYS) и минимум 5 эпизодов.
Печатает: `learner WEIGHT card=<имя> episodes=… days=… evidence=… proposal=<+шаг|-шаг|none> reason=…`.
Ничего не меняет.

Какие эпизоды смотреть (направление предложения — по шагу 0):
- **lower** (глюкоза ниже цели): гипо, у которых перед этим цикл давал инсулин при глюкозе ниже цели → предложить
  меньше инсулина при низкой глюкозе. Если гипо таких нет, а глюкоза долго стоит ниже цели без гипо — ничего.
- **higher** (глюкоза выше цели): долгие высокие (> 180 мг/дл больше 60 мин) БЕЗ еды за 4 ч и без отскока, при гипо ≤ 3 %
  → предложить больше инсулина при высокой глюкозе. Если после таких эпизодов были гипо — предложить меньше.
- **acce** (быстрый рост): рост > 1 ммоль/л за 15 мин без записанной еды, после которого глюкоза ушла > 180 → больше;
  если после быстрого роста через 2–4 ч гипо (цикл перестарался) → меньше.
- **brake** (падение): падение > 1 ммоль/л за 15 мин при инсулине в организме, закончившееся гипо → больше тормоза;
  если тормоз и так высокий и после падений долгие высокие → меньше. Отскоки после лечения гипо НЕ считать.
- **pp** (после еды): высокие через 1–3 ч после еды с болюсом, без гипо до 6 ч → больше; гипо через 1–4 ч после еды,
  не объяснённые SMB, → меньше. Шестичасовое правило SMB для CR здесь НЕ применяется, но эпизоды с гипо от SMB
  (hypoCause = smb) не считаются доводом «меньше pp».

Шаг: 25 % от текущего значения веса, но не меньше 0,05. Границы: от 0 до 2× значения ребёнка (в коде значения
по умолчанию `AisfWeights`). Предложение «больше» запрещено, если гипо за 3 дня > 3 %.

## Шаг 2. Включение (токен LAWAPPLY) — код написать, но НЕ включать в прогоны до решения Claude

С LAWAPPLY выбранная карточка веса меняет вес. Место в порядке `learnAllCheck`: предложения «меньше инсулина»
(lower-, brake+, acce-, pp-, higher-) — сразу после «процент вниз»; «больше инсулина» (higher+, acce+, pp+, lower+,
brake-) — после базала и CR, перед dura. Одно изменение раз в LAGAP дней, как у всех. Каждое изменение веса подчиняется
контролю результата (LAROLLBACK), если он включён.

## Отчёт

В `STATUS.md` (Task): шаг 0 (направления с номерами строк), коммиты, токены, что проверить Claude. Claude в среду сведёт
результаты проверки весов (прогон 45071041692) и скажет, какие карточки включать.

---

# Задание 8 для ChatGPT: запуск прогона коммитом файла RUN.txt (от Claude)

Зачем: подключение GitHub в ChatGPT не умеет нажимать «Run workflow». Делаем так, чтобы прогон стартовал сам, когда
ты коммитишь строку запуска в файл. Все правила из `lab/aimi-replay/COORDINATION.md` действуют. Ветка только
`aimi-replay`. Делать ПОСЛЕ задания 7 (открытая сводка), иначе ты всё равно не прочитаешь результат.

## Что сделать

Новый файл `.github/workflows/aimi-replay-run.yml` (существующие workflow НЕ менять):

```yaml
name: AIMI replay from RUN.txt
on:
  push:
    branches: [aimi-replay]
    paths: ['lab/aimi-replay/RUN.txt']
permissions:
  contents: read
  actions: write
concurrency:
  group: aimi-replay-run
  cancel-in-progress: false
jobs:
  start:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Check RUN.txt and start the batch
        env:
          GH_TOKEN: ${{ github.token }}
          REPO: ${{ github.repository }}
        run: |
          set -euo pipefail
          # The batch: the first line that is not empty and does not start with '#'.
          BATCH="$(grep -v '^[[:space:]]*#' lab/aimi-replay/RUN.txt | grep -v '^[[:space:]]*$' | head -n 1 | tr -d '\r')"
          [ -n "$BATCH" ] || { echo "RUN.txt has no batch line"; exit 1; }
          [ "${#BATCH}" -lt 65000 ] || { echo "batch too long"; exit 1; }
          JOBS=$(printf '%s' "$BATCH" | awk -F'|' '{print NF}')
          [ "$JOBS" -le 120 ] || { echo "too many jobs: $JOBS (max 120)"; exit 1; }
          # Every job must be the virtual child.
          printf '%s' "$BATCH" | tr '|' '\n' | while IFS= read -r job; do
            case "$job" in *SYNTH_*) ;; *) echo "a job without SYNTH_: refused"; exit 1;; esac
          done
          # Nothing else of the lab may run now.
          BUSY=$(gh api "repos/$REPO/actions/workflows/aimi-replay.yml/runs?per_page=20" \
            --jq '[.workflow_runs[] | select(.status=="queued" or .status=="in_progress")] | length')
          [ "$BUSY" -eq 0 ] || { echo "another lab run is going: refused"; exit 1; }
          # At most 2 starts in the last 24 h.
          SINCE=$(date -u -d '24 hours ago' +%Y-%m-%dT%H:%M:%SZ)
          STARTS=$(gh api "repos/$REPO/actions/workflows/aimi-replay-run.yml/runs?per_page=20&status=success" \
            --jq "[.workflow_runs[] | select(.created_at > \"$SINCE\")] | length")
          [ "$STARTS" -lt 2 ] || { echo "2 starts in 24 h already: refused"; exit 1; }
          gh workflow run aimi-replay.yml --repo "$REPO" --ref aimi-replay \
            -f sim=true -f algo=autoisf -f learn=false -f batch="$BATCH"
          echo "started: $JOBS jobs"
```

Важно (не менять):
- строку запуска НЕ подставлять в скрипт через `${{ }}` — только читать из файла в переменную (защита от подмены команд);
- никаких сторонних actions, кроме `actions/checkout@v4`;
- права только `contents: read` и `actions: write`.

Перед этим проверь в `aimi-replay.yml`, какие у него входы (`sim`, `algo`, `learn`, `batch`) и их типы; если имена
другие — поправь строку `gh workflow run` под них, остальное не трогай.

Файл `lab/aimi-replay/RUN.txt`: первая строка — комментарий `# one batch line below; commit = start`, вторая — строка
запуска. Каждый новый запуск = новый коммит этого файла с новой строкой.

## Проверка

1. Закоммить workflow с `RUN.txt`, где только комментарий (без строки) — прогон должен отказаться «no batch line».
2. Закоммить `RUN.txt` с одной задачей: строка APP для base seed 1 из `lab/aimi-replay/app22_batch.json` — должен
   стартовать `aimi-replay.yml` с 1 задачей, и (после задания 7) на его странице видна сводка.
3. Попробуй строку без `SYNTH_` — должен отказаться. Верни нормальную строку.
4. Отчёт в `STATUS.md` (Task): коммиты, номера прогонов, что отказы сработали.

Если что-то не работает (например, отказ прав на запуск) — НЕ расширяй права и НЕ обходи проверки, напиши в Task,
Claude посмотрит.

---

## Task 7 (open summary for SYNTH_ jobs)

See STATUS.md / the user's message of 23.11: a step in aimi-replay.yml, only for jobs with SYNTH_, after the replay and before encryption: aggregate table (% <70, % <54, % >180, CV, hypo grams per day, TDD, 2nd half) plus the 'AIMI replay: learner' lines from gradle.log, into GITHUB_STEP_SUMMARY and the log. No timestamps, no glucose traces, no single meals. Encryption unchanged.

Fix for task 8: BATCH="$(grep -v -e '^[[:space:]]*#' -e '^[[:space:]]*$' lab/aimi-replay/RUN.txt | head -n 1 | tr -d '\r' || true)"
