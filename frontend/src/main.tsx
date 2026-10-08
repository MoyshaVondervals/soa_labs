import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { parse, stringify, LosslessNumber } from "lossless-json";
import "./style.css";

type Worker = Record<string, any>;
type Field = {
  path: string;
  label: string;
  type: "text" | "number" | "date" | "enum";
  options?: string[];
  nullable?: boolean;
};
const positions = ["MANAGER", "LABORER", "BAKER", "MANAGER_OF_CLEANING"];
const statuses = [
  "FIRED",
  "HIRED",
  "RECOMMENDED_FOR_PROMOTION",
  "REGULAR",
  "PROBATION",
];
const titles: Record<string, string> = {
  MANAGER: "Менеджер",
  LABORER: "Рабочий",
  BAKER: "Пекарь",
  MANAGER_OF_CLEANING: "Руководитель уборки",
  FIRED: "Уволен",
  HIRED: "Принят",
  RECOMMENDED_FOR_PROMOTION: "Рекомендован к повышению",
  REGULAR: "Штатный",
  PROBATION: "Испытательный срок",
  BLACK: "Чёрный",
  WHITE: "Белый",
  BROWN: "Карий",
  RED: "Красный",
  ORANGE: "Оранжевый",
  USA: "США",
  GERMANY: "Германия",
  CHINA: "Китай",
  VATICAN: "Ватикан",
};
const pad = (n: number) => String(n).padStart(2, "0");

function isoToLocalInput(iso: string) {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return "";
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}
function localInputToIso(value: string) {
  const offset = -new Date(value).getTimezoneOffset();
  const abs = Math.abs(offset);
  const withSeconds = value.length === 16 ? value + ":00" : value;
  return `${withSeconds}${offset >= 0 ? "+" : "-"}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`;
}
const fields: Field[] = [
  { path: "name", label: "Имя", type: "text" },
  { path: "coordinates.x", label: "Координата X (int64)", type: "number" },
  { path: "coordinates.y", label: "Координата Y (≤ 714)", type: "number" },
  { path: "salary", label: "Зарплата (> 0)", type: "number" },
  {
    path: "endDate",
    label: "Дата окончания",
    type: "date",
    nullable: true,
  },
  { path: "position", label: "Должность", type: "enum", options: positions },
  { path: "status", label: "Статус", type: "enum", options: statuses },
  { path: "person.passportID", label: "Паспорт", type: "text", nullable: true },
  {
    path: "person.eyeColor",
    label: "Цвет глаз",
    type: "enum",
    options: ["BLACK", "WHITE", "BROWN"],
  },
  {
    path: "person.hairColor",
    label: "Цвет волос",
    type: "enum",
    options: ["RED", "BLACK", "ORANGE", "WHITE"],
  },
  {
    path: "person.nationality",
    label: "Гражданство",
    type: "enum",
    options: ["USA", "GERMANY", "CHINA", "VATICAN"],
    nullable: true,
  },
  { path: "person.location.x", label: "Местоположение X", type: "number" },
  { path: "person.location.y", label: "Местоположение Y", type: "number" },
  {
    path: "person.location.z",
    label: "Местоположение Z (int32)",
    type: "number",
  },
  {
    path: "person.location.name",
    label: "Название места",
    type: "text",
    nullable: true,
  },
];
const queryFields = [
  { path: "id", label: "ID", type: "number" },
  { path: "creationDate", label: "Дата создания", type: "date" },
  ...fields,
] as Field[];
const label = (value: any) => titles[String(value)] ?? String(value ?? "—");
const get = (obj: any, path: string) =>
  path.split(".").reduce((v, k) => v?.[k], obj);
function set(obj: any, path: string, value: any) {
  const parts = path.split(".");
  let cur = obj;
  parts.slice(0, -1).forEach((p) => (cur = cur[p] ??= {}));
  cur[parts.at(-1)!] = value;
}
const date = (v: any) =>
  v ? new Date(String(v)).toLocaleString("ru-RU") : "—";
let hrBase = "https://localhost:18081";
class ServiceError extends Error {
  constructor(
    public status: number,
    message: string,
    public violations: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
async function api(
  path: string,
  method = "GET",
  params: Record<string, string | string[]> = {},
  hr = false,
): Promise<any> {
  const url = new URL(path, hr ? hrBase : location.origin);
  Object.entries(params).forEach(([k, v]) =>
    (Array.isArray(v) ? v : [v]).forEach((item) =>
      url.searchParams.append(k, item),
    ),
  );
  let res: Response;
  try {
    res = await fetch(url, { method, headers: { Accept: "application/json" } });
  } catch {
    throw new ServiceError(
      0,
      "Не удалось связаться с сервисом. Проверьте доступность и доверие HTTPS-сертификатам.",
    );
  }
  if (res.status === 204) return null;
  let body: any;
  try {
    body = parse(await res.text());
  } catch {
    throw new ServiceError(
      res.status,
      "Сервис вернул ответ в неожиданном формате.",
    );
  }
  if (!res.ok)
    throw new ServiceError(
      Number(body.status) || res.status,
      body.message ?? "Ошибка сервиса",
      body.violations ?? [],
    );
  return body;
}
function App() {
  const [workers, setWorkers] = useState<Worker[]>([]),
    [page, setPage] = useState(1),
    [size, setSize] = useState(20),
    [total, setTotal] = useState(0),
    [pages, setPages] = useState(0);
  const [sorts, setSorts] = useState([{ field: "id", direction: "asc" }]),
    [filters, setFilters] = useState<
      { field: string; op: string; value: string }[]
    >([]);
  const [applied, setApplied] = useState({
      sort: ["id:asc"],
      filter: [] as string[],
    }),
    [loading, setLoading] = useState(false),
    [busy, setBusy] = useState(false),
    [revision, setRevision] = useState(0);
  const [error, setError] = useState<ServiceError | null>(null),
    [notice, setNotice] = useState(""),
    [editing, setEditing] = useState<Worker | null | undefined>(undefined),
    [detail, setDetail] = useState<Worker | null>(null);
  const [selected, setSelected] = useState(""),
    [org, setOrg] = useState<any>(undefined),
    [from, setFrom] = useState("10"),
    [to, setTo] = useState("20"),
    [newStatus, setNewStatus] = useState("REGULAR"),
    [expected, setExpected] = useState("");
  const [substring, setSubstring] = useState(""),
    [searchResult, setSearchResult] = useState<Worker[] | null>(null),
    [position, setPosition] = useState("MANAGER"),
    [count, setCount] = useState<string | null>(null),
    [sum, setSum] = useState<string | null>(null);
  const [uncertain, setUncertain] = useState(false);
  async function run(fn: () => Promise<void>) {
    if (busy) return;
    setBusy(true);
    setError(null);
    setNotice("");
    try {
      await fn();
    } catch (e) {
      setError(e instanceof ServiceError ? e : new ServiceError(0, String(e)));
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    let valid = true;
    setLoading(true);
    api("/api/workers", "GET", {
      page: String(page),
      size: String(size),
      sort: applied.sort,
      filter: applied.filter,
    })
      .then((data) => {
        if (valid) {
          setWorkers(data.items);
          setTotal(Number(data.totalItems));
          setPages(Number(data.totalPages));
        }
      })
      .catch((e) => {
        if (valid) setError(e);
      })
      .finally(() => {
        if (valid) setLoading(false);
      });
    return () => {
      valid = false;
    };
  }, [page, size, applied, revision]);
  const refresh = () => setRevision((v) => v + 1);
  const choose = (w: Worker) => {
    setSelected(String(w.id));
    setOrg(undefined);
    setExpected(String(w.status));
    setUncertain(false);
  };
  async function inspect() {
    if (!selected) throw new ServiceError(0, "Укажите ID работника.");
    const result = await api(
      `/api/workers/${encodeURIComponent(selected)}/organization`,
    );
    setOrg(
      result.organizationId === null ? null : String(result.organizationId),
    );
    setDetail(result.worker);
    setExpected(String(result.worker.status));
  }
  return (
    <>
      <header>
        <nav>
          <a href="#workers">Работники</a>
          <a href="#hr">Кадровые операции</a>
          <a href="#queries">Статистика</a>
          <a href="/docs/swagger-ui.html" target="_blank" rel="noreferrer">
            API ↗
          </a>
        </nav>
      </header>
      <main>
        <section className="intro">
          <div>
            <span className="eyebrow">УПРАВЛЕНИЕ КОЛЛЕКЦИЕЙ</span>
            <h1>Люди и организации</h1>
            <p>Работники, назначения и кадровые изменения в одном месте.</p>
          </div>
          <button
            className="primary"
            onClick={() => {
              setError(null);
              setEditing(null);
            }}
          >
            ＋ Добавить работника
          </button>
        </section>
        {error && (
          <div className="alert error" role="alert">
            <strong>
              {error.status
                ? `Ошибка ${error.status}`
                : "Ошибка соединения или ввода"}
            </strong>
            <span>{error.message}</span>
            {error.violations.length > 0 && (
              <ul>
                {error.violations.map((v, i) => (
                  <li key={i}>
                    {fields.find((f) => f.path === v.field)?.label ?? v.field}:{" "}
                    {v.message}
                  </li>
                ))}
              </ul>
            )}
            <button onClick={() => setError(null)} aria-label="Закрыть ошибку">
              ×
            </button>
          </div>
        )}
        {notice && (
          <div className="alert success" role="status">
            {notice}
            <button
              onClick={() => setNotice("")}
              aria-label="Закрыть уведомление"
            >
              ×
            </button>
          </div>
        )}
        <section className="card" id="workers">
          <div className="sectionhead">
            <div>
              <h2>
                Работники <span className="badge">{total}</span>
              </h2>
              <p>Все поля доступны для фильтрации и сортировки.</p>
            </div>
            <button onClick={refresh} disabled={loading}>
              Обновить
            </button>
          </div>
          <details className="querybuilder">
            <summary>
              Фильтры и сортировка{" "}
              <span>
                {applied.filter.length} фильтров · {applied.sort.length}{" "}
                сортировок
              </span>
            </summary>
            <div className="querycols">
              <div>
                <h3>Фильтры · все условия AND</h3>
                {filters.map((f, i) => (
                  <div className="queryrow" key={i}>
                    <select
                      aria-label={`Поле фильтра ${i + 1}`}
                      value={f.field}
                      onChange={(e) =>
                        setFilters(
                          filters.map((v, j) =>
                            j === i ? { ...v, field: e.target.value } : v,
                          ),
                        )
                      }
                    >
                      {queryFields.map((v) => (
                        <option key={v.path} value={v.path}>
                          {v.label}
                        </option>
                      ))}
                    </select>
                    <select
                      aria-label={`Оператор ${i + 1}`}
                      value={f.op}
                      onChange={(e) =>
                        setFilters(
                          filters.map((v, j) =>
                            j === i ? { ...v, op: e.target.value } : v,
                          ),
                        )
                      }
                    >
                      {["eq", "ne", "gt", "gte", "lt", "lte", "contains"].map(
                        (op) => (
                          <option key={op}>{op}</option>
                        ),
                      )}
                    </select>
                    <input
                      aria-label={`Значение фильтра ${i + 1}`}
                      placeholder="Значение или null"
                      value={f.value}
                      onChange={(e) =>
                        setFilters(
                          filters.map((v, j) =>
                            j === i ? { ...v, value: e.target.value } : v,
                          ),
                        )
                      }
                    />
                    <button
                      aria-label="Удалить фильтр"
                      onClick={() =>
                        setFilters(filters.filter((_, j) => j !== i))
                      }
                    >
                      ×
                    </button>
                  </div>
                ))}
                <button
                  onClick={() =>
                    setFilters([
                      ...filters,
                      { field: "salary", op: "gte", value: "50000" },
                    ])
                  }
                >
                  ＋ Фильтр
                </button>
              </div>
              <div>
                <h3>Сортировка · приоритет сверху вниз</h3>
                {sorts.map((s, i) => (
                  <div className="queryrow" key={i}>
                    <select
                      aria-label={`Поле сортировки ${i + 1}`}
                      value={s.field}
                      onChange={(e) =>
                        setSorts(
                          sorts.map((v, j) =>
                            j === i ? { ...v, field: e.target.value } : v,
                          ),
                        )
                      }
                    >
                      {queryFields.map((v) => (
                        <option key={v.path} value={v.path}>
                          {v.label}
                        </option>
                      ))}
                    </select>
                    <select
                      aria-label={`Направление ${i + 1}`}
                      value={s.direction}
                      onChange={(e) =>
                        setSorts(
                          sorts.map((v, j) =>
                            j === i ? { ...v, direction: e.target.value } : v,
                          ),
                        )
                      }
                    >
                      <option value="asc">По возрастанию</option>
                      <option value="desc">По убыванию</option>
                    </select>
                    <button
                      aria-label="Удалить сортировку"
                      onClick={() => setSorts(sorts.filter((_, j) => j !== i))}
                    >
                      ×
                    </button>
                  </div>
                ))}
                <button
                  onClick={() =>
                    setSorts([...sorts, { field: "name", direction: "asc" }])
                  }
                >
                  ＋ Сортировка
                </button>
              </div>
            </div>
            <div className="actions">
              <button
                className="primary"
                onClick={() => {
                  setError(null);
                  setPage(1);
                  setApplied({
                    sort: sorts.map((s) => `${s.field}:${s.direction}`),
                    filter: filters.map((f) => `${f.field}:${f.op}:${f.value}`),
                  });
                }}
              >
                Применить
              </button>
              <button
                onClick={() => {
                  setFilters([]);
                  setSorts([{ field: "id", direction: "asc" }]);
                  setPage(1);
                  setApplied({ sort: ["id:asc"], filter: [] });
                }}
              >
                Сбросить
              </button>
            </div>
            <p className="hint">
              Для перечислений используйте значения API, например HIRED или
              MANAGER. contains работает со строками; eq/ne с null — с
              nullable-полями.
            </p>
          </details>
          <div className="tablewrap" aria-busy={loading}>
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Работник</th>
                  <th>Должность</th>
                  <th>Зарплата</th>
                  <th>Статус</th>
                  <th>Действия</th>
                </tr>
              </thead>
              <tbody>
                {workers.map((w) => (
                  <tr
                    key={String(w.id)}
                    className={selected === String(w.id) ? "selected" : ""}
                  >
                    <td className="mono">{String(w.id)}</td>
                    <td>
                      <button
                        className="textbutton"
                        onClick={() =>
                          run(async () => {
                            const data = await api(`/api/workers/${w.id}`);
                            setDetail(data);
                            choose(data);
                          })
                        }
                      >
                        {String(w.name)}
                      </button>
                      <small>{date(w.creationDate)}</small>
                    </td>
                    <td>{label(w.position)}</td>
                    <td className="salary">
                      {Number(w.salary).toLocaleString("ru-RU", {
                        maximumFractionDigits: 2,
                      })}
                    </td>
                    <td>
                      <span
                        className={`status ${String(w.status).toLowerCase()}`}
                      >
                        {label(w.status)}
                      </span>
                    </td>
                    <td>
                      <div className="rowactions">
                        <button
                          disabled={busy}
                          onClick={() =>
                            run(async () => {
                              const data = await api(`/api/workers/${w.id}`);
                              setEditing(data);
                            })
                          }
                        >
                          Изменить
                        </button>
                        <button disabled={busy} onClick={() => choose(w)}>
                          Выбрать
                        </button>
                        <button
                          className="danger"
                          disabled={busy}
                          onClick={() => {
                            if (
                              confirm(
                                `Удалить работника «${w.name}» (ID ${w.id})?`,
                              )
                            )
                              run(async () => {
                                await api(`/api/workers/${w.id}`, "DELETE");
                                if (selected === String(w.id)) {
                                  setSelected("");
                                  setOrg(undefined);
                                }
                                setNotice("Работник удалён.");
                                refresh();
                              });
                          }}
                        >
                          Удалить
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!workers.length && (
              <div className="empty">
                {loading
                  ? "Загрузка работников…"
                  : "Работники не найдены. Добавьте первого или измените фильтры."}
              </div>
            )}
          </div>
          <div className="pagination">
            <label>
              На странице{" "}
              <select
                value={size}
                onChange={(e) => {
                  setSize(Number(e.target.value));
                  setPage(1);
                }}
              >
                {[10, 20, 50, 100, 1000].map((v) => (
                  <option key={v}>{v}</option>
                ))}
              </select>
            </label>
            <span>
              Страница {page} из {pages || 1} · Всего {total}
            </span>
            <div>
              <button
                disabled={page <= 1 || loading}
                onClick={() => setPage(page - 1)}
              >
                ← Назад
              </button>
              <button
                disabled={page >= pages || loading}
                onClick={() => setPage(page + 1)}
              >
                Далее →
              </button>
            </div>
          </div>
        </section>
        <section className="card" id="hr">
          <div className="sectionhead">
            <div>
              <h2>Кадровые операции</h2>
              <p>Выберите работника в таблице или укажите его ID.</p>
            </div>
          </div>
          <div className="hrtarget">
            <label>
              ID работника
              <input
                value={selected}
                onChange={(e) => {
                  setSelected(e.target.value);
                  setOrg(undefined);
                  setExpected("");
                  setUncertain(false);
                }}
                placeholder="Например, 42"
              />
            </label>
            <button disabled={busy || !selected} onClick={() => run(inspect)}>
              Проверить работника и организацию
            </button>
            {org !== undefined && (
              <span className="badge">
                {org === null ? "Без организации" : `Организация ${org}`}
              </span>
            )}
          </div>
          <div className="operationgrid">
            <article>
              <span className="eyebrow">WORKER SERVICE</span>
              <h3>Изменить статус</h3>
              <label>
                Новый статус
                <select
                  value={newStatus}
                  onChange={(e) => setNewStatus(e.target.value)}
                >
                  {statuses.map((v) => (
                    <option key={v} value={v}>
                      {label(v)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Ожидаемый текущий статус
                <select
                  value={expected}
                  onChange={(e) => setExpected(e.target.value)}
                >
                  <option value="">Без проверки</option>
                  {statuses.map((v) => (
                    <option key={v} value={v}>
                      {label(v)}
                    </option>
                  ))}
                </select>
              </label>
              <button
                disabled={busy || !selected}
                onClick={() =>
                  run(async () => {
                    const w = await api(
                      `/api/workers/${encodeURIComponent(selected)}`,
                      "PATCH",
                      {
                        status: newStatus,
                        ...(expected ? { expectedStatus: expected } : {}),
                      },
                    );
                    setExpected(String(w.status));
                    setNotice("Статус изменён.");
                    refresh();
                  })
                }
              >
                Изменить статус
              </button>
            </article>
            <article>
              <span className="eyebrow">WORKER SERVICE</span>
              <h3>Назначить организацию</h3>
              <p>
                Сначала проверьте текущую организацию. Доступны организации 10, 20 и
                30.
              </p>
              <label>
                Целевая организация
                <input value={to} onChange={(e) => setTo(e.target.value)} />
              </label>
              <button
                disabled={busy || !selected || org === undefined}
                onClick={() =>
                  run(async () => {
                    const assignment = stringify({
                      expectedOrganizationId:
                        org === null ? null : new LosslessNumber(org),
                      organizationId: new LosslessNumber(to),
                    })!;
                    const r = await api(
                      `/api/workers/${encodeURIComponent(selected)}/organization`,
                      "PUT",
                      { assignment },
                    );
                    setOrg(String(r.organizationId));
                    setNotice("Организация назначена.");
                  })
                }
              >
                Назначить / переместить
              </button>
            </article>
            <article>
              <span className="eyebrow">HR SERVICE</span>
              <h3>Перенести работника</h3>
              <div className="twocol">
                <label>
                  Из организации
                  <input
                    value={from}
                    onChange={(e) => setFrom(e.target.value)}
                  />
                </label>
                <label>
                  В организацию
                  <input value={to} onChange={(e) => setTo(e.target.value)} />
                </label>
              </div>
              <button
                disabled={busy || !selected || uncertain}
                onClick={() =>
                  run(async () => {
                    try {
                      await api(
                        `/hr/move/${encodeURIComponent(selected)}/${encodeURIComponent(from)}/${encodeURIComponent(to)}`,
                        "POST",
                        {},
                        true,
                      );
                      setOrg(to);
                      setNotice("Работник перенесён через HR Service.");
                    } catch (e) {
                      if (
                        e instanceof ServiceError &&
                        (e.status === 0 || e.status >= 500)
                      ) {
                        setUncertain(true);
                        setOrg(undefined);
                      }
                      throw e;
                    }
                  })
                }
              >
                Перенести через HR
              </button>
              {uncertain && (
                <p className="warning">
                  Результат операции неизвестен. Проверьте организацию перед
                  повтором.
                </p>
              )}
              <button
                disabled={busy || !selected}
                onClick={() =>
                  run(async () => {
                    await inspect();
                    setUncertain(false);
                    setNotice("Текущая организация проверена.");
                  })
                }
              >
                Проверить результат
              </button>
            </article>
            <article>
              <span className="eyebrow">HR SERVICE</span>
              <h3>Уволить работника</h3>
              <p>
                HR направит запрос в Worker Service. Остальные данные работника
                сохранятся.
              </p>
              <button
                className="danger"
                disabled={busy || !selected}
                onClick={() => {
                  if (confirm(`Уволить работника с ID ${selected}?`))
                    run(async () => {
                      await api(
                        `/hr/fire/${encodeURIComponent(selected)}`,
                        "PATCH",
                        {},
                        true,
                      );
                      setExpected("FIRED");
                      setNotice("Работник уволен.");
                      refresh();
                    });
                }}
              >
                Уволить через HR
              </button>
            </article>
          </div>
        </section>
        <section className="card" id="queries">
          <div className="sectionhead">
            <div>
              <h2>Поиск и статистика</h2>
              <p>Дополнительные операции Worker API.</p>
            </div>
          </div>
          <div className="statsgrid">
            <article>
              <h3>Сумма зарплат</h3>
              <div className="statvalue">
                {sum === null
                  ? "—"
                  : Number(sum).toLocaleString("ru-RU", {
                      maximumFractionDigits: 2,
                    })}
              </div>
              <button
                disabled={busy}
                onClick={() =>
                  run(async () =>
                    setSum(String((await api("/api/workers/salary/sum")).sum)),
                  )
                }
              >
                Рассчитать
              </button>
            </article>
            <article>
              <h3>Должность выше заданной</h3>
              <select
                aria-label="Должность для сравнения"
                value={position}
                onChange={(e) => setPosition(e.target.value)}
              >
                {positions.map((v) => (
                  <option key={v} value={v}>
                    {label(v)}
                  </option>
                ))}
              </select>
              <p className="hint">
                MANAGER &lt; LABORER &lt; BAKER &lt; MANAGER_OF_CLEANING
              </p>
              <button
                disabled={busy}
                onClick={() =>
                  run(async () =>
                    setCount(
                      String(
                        (
                          await api(
                            `/api/workers/position/greater-than/${position}`,
                          )
                        ).count,
                      ),
                    ),
                  )
                }
              >
                Посчитать
              </button>
              {count !== null && <p className="result">Работников: {count}</p>}
            </article>
            <article>
              <h3>Поиск по имени</h3>
              <input
                aria-label="Подстрока имени"
                value={substring}
                onChange={(e) => setSubstring(e.target.value)}
                placeholder="Часть имени, с учётом регистра"
              />
              <button
                disabled={busy || !substring}
                onClick={() =>
                  run(async () =>
                    setSearchResult(
                      await api(
                        `/api/workers/name/contains/${encodeURIComponent(substring)}`,
                      ),
                    ),
                  )
                }
              >
                Найти
              </button>
              {searchResult && (
                <div className="searchresults">
                  {searchResult.length
                    ? searchResult.map((w) => (
                        <button
                          key={String(w.id)}
                          className="textbutton"
                          onClick={() => {
                            setDetail(w);
                            choose(w);
                          }}
                        >
                          {String(w.name)} · #{String(w.id)}
                        </button>
                      ))
                    : "Совпадений нет"}
                </div>
              )}
            </article>
          </div>
        </section>
        <footer>
          <a href={hrBase} target="_blank" rel="noreferrer">
            Принять сертификат HR в браузере (страница ответит 404 — это нормально) ↗
          </a>
        </footer>
      </main>
      {detail && (
        <div className="overlay" onClick={() => setDetail(null)}>
          <section
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-label="Карточка работника"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="sectionhead">
              <h2>{String(detail.name)}</h2>
              <button
                onClick={() => setDetail(null)}
                aria-label="Закрыть карточку"
              >
                ×
              </button>
            </div>
            <dl className="detailgrid">
              <dt>ID</dt>
              <dd>{String(detail.id)}</dd>
              <dt>Создан</dt>
              <dd>{date(detail.creationDate)}</dd>
              {fields.map((f) => (
                <React.Fragment key={f.path}>
                  <dt>{f.label}</dt>
                  <dd>
                    {f.type === "enum"
                      ? label(get(detail, f.path))
                      : String(get(detail, f.path) ?? "—")}
                  </dd>
                </React.Fragment>
              ))}
            </dl>
            <button
              onClick={() => {
                setEditing(detail);
                setDetail(null);
              }}
            >
              Редактировать
            </button>
          </section>
        </div>
      )}
      {editing !== undefined && (
        <WorkerForm
          worker={editing}
          busy={busy}
          violations={error?.violations ?? []}
          serviceError={error}
          onClose={() => {
            setEditing(undefined);
            setError(null);
          }}
          onSave={(worker, check) =>
            run(async () => {
              const params = {
                worker: stringify(worker)!,
                ...(check ? { expectedStatus: check } : {}),
              };
              if (editing)
                await api(`/api/workers/${editing.id}`, "PUT", params);
              else await api("/api/workers", "POST", params);
              setEditing(undefined);
              setNotice(editing ? "Работник обновлён." : "Работник создан.");
              refresh();
            })
          }
        />
      )}
    </>
  );
}
function WorkerForm({
  worker,
  busy,
  violations,
  serviceError,
  onClose,
  onSave,
}: {
  worker: Worker | null;
  busy: boolean;
  serviceError: ServiceError | null;
  violations: { field: string; message: string }[];
  onClose: () => void;
  onSave: (worker: Worker, expected: string) => void;
}) {
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      fields.map((f) => [
        f.path,
        worker
          ? f.type === "date"
            ? isoToLocalInput(String(get(worker, f.path) ?? ""))
            : String(get(worker, f.path) ?? "")
          : f.options && !f.nullable
            ? f.options[0]
            : "",
      ]),
    ),
  );
  const [nullStrings, setNullStrings] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(
      fields
        .filter((f) => f.nullable && f.type === "text")
        .map((f) => [f.path, !worker || get(worker, f.path) == null]),
    ),
  );
  const [locationEnabled, setLocationEnabled] = useState(
      !!worker?.person?.location,
    ),
    [expected, setExpected] = useState(worker ? String(worker.status) : ""),
    [localError, setLocalError] = useState("");
  function submit(e: React.FormEvent) {
    e.preventDefault();
    setLocalError("");
    try {
      const obj: Worker = {};
      for (const f of fields) {
        if (f.path.startsWith("person.location.") && !locationEnabled) continue;
        const raw = values[f.path];
        set(
          obj,
          f.path,
          (
            f.nullable && f.type === "text"
              ? nullStrings[f.path]
              : raw === "" && f.nullable
          )
            ? null
            : f.type === "number"
              ? new LosslessNumber(raw)
              : f.type === "date"
                ? localInputToIso(raw)
                : raw,
        );
      }
      if (!locationEnabled) obj.person.location = null;
      onSave(obj, expected);
    } catch {
      setLocalError(
        "Проверьте числовые поля: используйте число без пробелов, десятичный разделитель — точка.",
      );
    }
  }
  return (
    <div className="overlay">
      <section
        className="modal wide"
        role="dialog"
        aria-modal="true"
        aria-label={worker ? "Редактировать работника" : "Добавить работника"}
      >
        <div className="sectionhead">
          <div>
            <span className="eyebrow">
              {worker ? `РАБОТНИК #${worker.id}` : "НОВАЯ ЗАПИСЬ"}
            </span>
            <h2>{worker ? "Редактировать работника" : "Добавить работника"}</h2>
          </div>
          <button disabled={busy} onClick={onClose} aria-label="Закрыть форму">
            ×
          </button>
        </div>
        <form onSubmit={submit}>
          <div className="formgrid">
            {fields
              .filter((f) => !f.path.startsWith("person.location."))
              .map((f) => (
                <label key={f.path}>
                  {f.label}
                  {!f.nullable && " *"}
                  {f.type === "enum" ? (
                    <select
                      aria-label={f.label + (!f.nullable ? " *" : "")}
                      value={values[f.path]}
                      onChange={(e) =>
                        setValues({ ...values, [f.path]: e.target.value })
                      }
                    >
                      {f.nullable && (
                        <option value="">Не указано (null)</option>
                      )}
                      {f.options!.map((v) => (
                        <option key={v} value={v}>
                          {label(v)}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input
                      aria-label={f.label + (!f.nullable ? " *" : "")}
                      type={f.type === "date" ? "datetime-local" : "text"}
                      step={f.type === "date" ? 1 : undefined}
                      disabled={!!nullStrings[f.path]}
                      required={!f.nullable}
                      value={values[f.path]}
                      inputMode={f.type === "number" ? "decimal" : undefined}
                      placeholder={
                        f.nullable
                            ? "Не указано (null)"
                            : ""
                      }
                      onChange={(e) =>
                        setValues({ ...values, [f.path]: e.target.value })
                      }
                    />
                  )}{" "}
                  {f.type === "date" && (
                    <span className="nullchoice">
                      Пусто — не указана (null).{" "}
                      {values[f.path] && (
                        <button
                          type="button"
                          className="linkbutton"
                          onClick={() => setValues({ ...values, [f.path]: "" })}
                        >
                          Очистить
                        </button>
                      )}
                    </span>
                  )}
                  {f.nullable && f.type === "text" && (
                    <span className="nullchoice">
                      <input
                        type="checkbox"
                        checked={!!nullStrings[f.path]}
                        onChange={(e) =>
                          setNullStrings({
                            ...nullStrings,
                            [f.path]: e.target.checked,
                          })
                        }
                      />{" "}
                      Не указано (null)
                    </span>
                  )}
                  {violations
                    .filter((v) => v.field === f.path)
                    .map((v, i) => (
                      <span className="fielderror" key={i}>
                        {v.message}
                      </span>
                    ))}
                </label>
              ))}
          </div>
          <label className="checkbox">
            <input
              type="checkbox"
              checked={locationEnabled}
              onChange={(e) => setLocationEnabled(e.target.checked)}
            />{" "}
            Указать местоположение человека
          </label>
          {locationEnabled && (
            <div className="formgrid">
              {fields
                .filter((f) => f.path.startsWith("person.location."))
                .map((f) => (
                  <label key={f.path}>
                    {f.label}
                    {!f.nullable && " *"}
                    <input
                      aria-label={f.label + (!f.nullable ? " *" : "")}
                      type={f.type === "date" ? "datetime-local" : "text"}
                      step={f.type === "date" ? 1 : undefined}
                      disabled={!!nullStrings[f.path]}
                      required={!f.nullable}
                      value={values[f.path]}
                      onChange={(e) =>
                        setValues({ ...values, [f.path]: e.target.value })
                      }
                    />
                    {violations
                      .filter((v) => v.field === f.path)
                      .map((v, i) => (
                        <span className="fielderror" key={i}>
                          {v.message}
                        </span>
                      ))}
                    <span className="nullchoice">
                      {f.nullable && (
                        <>
                          <input
                            type="checkbox"
                            checked={!!nullStrings[f.path]}
                            onChange={(e) =>
                              setNullStrings({
                                ...nullStrings,
                                [f.path]: e.target.checked,
                              })
                            }
                          />{" "}
                          Не указано (null)
                        </>
                      )}
                    </span>
                  </label>
                ))}
            </div>
          )}
          {worker && (
            <label>
              Ожидаемый текущий статус
              <select
                value={expected}
                onChange={(e) => setExpected(e.target.value)}
              >
                <option value="">Без проверки</option>
                {statuses.map((v) => (
                  <option key={v} value={v}>
                    {label(v)}
                  </option>
                ))}
              </select>
            </label>
          )}
          {serviceError && (
            <p className="fielderror" role="alert">
              {serviceError.status ? `Ошибка ${serviceError.status}: ` : ""}
              {serviceError.message}
            </p>
          )}
          {localError && (
            <p className="fielderror" role="alert">
              {localError}
            </p>
          )}
          {violations.length > 0 && (
            <p className="fielderror" role="alert">
              Исправьте отмеченные поля. Подробности ошибки показаны в
              уведомлении на странице.
            </p>
          )}
          <div className="actions">
            <button className="primary" type="submit" disabled={busy}>
              {busy ? "Сохранение…" : "Сохранить"}
            </button>
            <button type="button" disabled={busy} onClick={onClose}>
              Отмена
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
fetch("/config.json")
  .then((r) => {
    if (!r.ok) throw Error("config");
    return r.json();
  })
  .then((c) => {
    hrBase =
      c.hrBaseUrl ||
      (c.hrPort ? `https://${location.hostname}:${c.hrPort}` : hrBase);
  })
  .catch(() => {})
  .finally(() => createRoot(document.getElementById("root")!).render(<App />));
