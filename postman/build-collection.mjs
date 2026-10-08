import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const W = '{{workerBaseUrl}}', H = '{{hrBaseUrl}}';
const specDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'docs', 'openapi');
const specs = Object.fromEntries(['worker-service.yaml', 'hr-service.yaml'].map(f =>
  [f, YAML.parse(fs.readFileSync(path.join(specDir, f), 'utf8'))]));

function resolveRef(ref, file) {
  const [target, pointer] = ref.split('#');
  const doc = target ? path.basename(target) : file;
  let node = specs[doc];
  for (const part of pointer.split('/').slice(1)) node = node[part.replace(/~1/g, '/').replace(/~0/g, '~')];
  return { node, file: doc };
}

const KEPT_FORMATS = new Set(['date-time', 'uri', 'uri-reference']);

function toJsonSchema(node, file) {
  if (node.$ref) {
    const r = resolveRef(node.$ref, file);
    return toJsonSchema(r.node, r.file);
  }
  const out = {};
  for (const [k, v] of Object.entries(node)) {
    if (['example', 'description', 'readOnly', 'nullable', 'xml'].includes(k)) continue;
    if (k === 'format') { if (KEPT_FORMATS.has(v)) out.format = v; continue; }
    if (k === 'exclusiveMinimum' || k === 'exclusiveMaximum') continue;
    if (k === 'properties') out.properties = Object.fromEntries(Object.entries(v).map(([n, p]) => [n, toJsonSchema(p, file)]));
    else if (k === 'items') out.items = toJsonSchema(v, file);
    else out[k] = v;
  }
  if (node.exclusiveMinimum === true) { out.exclusiveMinimum = out.minimum; delete out.minimum; }
  if (node.exclusiveMaximum === true) { out.exclusiveMaximum = out.maximum; delete out.maximum; }
  if (node.nullable) return { anyOf: [out, { type: 'null' }] };
  return out;
}

function responsesFor(method, url) {
  const file = url.startsWith(H) ? 'hr-service.yaml' : 'worker-service.yaml';
  const actual = url.replace(/^\{\{\w+\}\}/, '').split('?')[0].split('/');
  const candidates = Object.entries(specs[file].paths)
    .map(([template, item]) => ({ template, item, parts: template.split('/') }))
    .filter(c => c.parts.length === actual.length && c.item[method.toLowerCase()]
      && c.parts.every((p, i) => p.startsWith('{') || p === actual[i]))
    .sort((a, b) => b.parts.filter(p => !p.startsWith('{')).length - a.parts.filter(p => !p.startsWith('{')).length);
  if (!candidates.length) throw new Error(`No spec operation for ${method} ${url}`);
  const op = candidates[0].item[method.toLowerCase()];
  const result = {};
  for (const [status, resp] of Object.entries(op.responses)) {
    const r = resp.$ref ? resolveRef(resp.$ref, file) : { node: resp, file };
    const schema = r.node.content?.['application/json']?.schema;
    result[status] = schema ? toJsonSchema(schema, r.file) : null;
  }
  return { operation: `${method} ${candidates[0].template}`, responses: result };
}

function schemaTest(method, url) {
  const { operation, responses } = responsesFor(method, url);
  return `
      const specResponses = ${JSON.stringify(responses)};
      pm.test("status " + pm.response.code + " documented in spec", () => pm.expect(Object.keys(specResponses)).to.include(String(pm.response.code)));
      const specSchema = specResponses[String(pm.response.code)];
      if (specSchema) pm.test("body matches OpenAPI schema", () => pm.response.to.have.jsonSchema(specSchema));
      else if (pm.response.code in specResponses) pm.test("no body", () => pm.expect(pm.response.text()).to.eql(""));`;
}

function lines(s) { return s.trim().split('\n').map(l => l.replace(/^ {6}/, '')); }

function req(name, method, url, { pre, test, headers = [], body, noRedirect, followMethod, specAs } = {}) {
  const item = {
    name,
    request: { method, header: headers, url },
    event: [],
  };
  if (body) item.request.body = { mode: 'raw', raw: body, options: { raw: { language: 'json' } } };
  if (pre) item.event.push({ listen: 'prerequest', script: { type: 'text/javascript', exec: lines(pre) } });
  item.event.push({ listen: 'test', script: { type: 'text/javascript', exec: lines((test || '') + schemaTest(...(specAs || [method, url]))) } });
  if (noRedirect) item.protocolProfileBehavior = { followRedirects: false };
  if (followMethod) item.protocolProfileBehavior = { followRedirects: true, followOriginalHttpMethod: true };
  return item;
}
const status = (code, extra = '') => `
      pm.test("status ${code}", () => pm.response.to.have.status(${code}));
      ${extra}`;
const error = (code, extra = '') => status(code, `
      const e = pm.response.json();
      pm.test("error body by spec", () => {
        pm.expect(e).to.include.keys("timestamp", "status", "error", "message", "path");
        pm.expect(e.status).to.eql(${code});
      });
      ${code === 422 ? 'pm.test("has violations", () => pm.expect(e.violations).to.be.an("array").that.is.not.empty);' : ''}
      ${extra}`);
const encode = (variable, obj) => `
      pm.variables.set("${variable}", encodeURIComponent(JSON.stringify(${obj})));`;

const workerA = `{
        name: "Ivan Petrov", coordinates: { x: 15, y: 714 }, salary: 120000.5, endDate: null,
        position: "MANAGER", status: "HIRED",
        person: { passportID: "4010123456", eyeColor: "BROWN", hairColor: "BLACK", nationality: "GERMANY",
                  location: { x: 10.5, y: 20.25, z: 3, name: "Main office" } } }`;
const workerB = `{
        name: "Anna Ivanova", coordinates: { x: -3, y: 10 }, salary: 50000, endDate: "2030-01-01T09:00:00+03:00",
        position: "BAKER", status: "REGULAR",
        person: { passportID: null, eyeColor: "BLACK", hairColor: "RED", nationality: null, location: null } }`;

const crud = [
  req('Создать работника A', 'POST', `${W}/api/workers?worker={{workerJson}}`, {
    pre: encode('workerJson', workerA),
    test: status(201, `
      const w = pm.response.json();
      pm.collectionVariables.set("workerA", w.id);
      pm.test("Location header points to the worker", () => pm.expect(pm.response.headers.get("Location")).to.match(new RegExp("/api/workers/" + w.id + "$")));
      pm.test("id and creationDate generated", () => { pm.expect(w.id).to.be.above(0); pm.expect(w.creationDate).to.be.a("string"); });
      pm.test("fields saved", () => { pm.expect(w.name).to.eql("Ivan Petrov"); pm.expect(w.person.location.name).to.eql("Main office"); });`),
  }),
  req('Создать работника B', 'POST', `${W}/api/workers?worker={{workerJson}}`, {
    pre: encode('workerJson', workerB),
    test: status(201, `
      const w = pm.response.json();
      pm.collectionVariables.set("workerB", w.id);
      pm.test("nullable fields are null", () => { pm.expect(w.person.location).to.eql(null); pm.expect(w.person.nationality).to.eql(null); });`),
  }),
  req('Получить работника по id', 'GET', `${W}/api/workers/{{workerA}}`, {
    test: status(200, `pm.test("same worker", () => pm.expect(pm.response.json().id).to.eql(Number(pm.collectionVariables.get("workerA"))));`),
  }),
  req('Список: страница по умолчанию', 'GET', `${W}/api/workers`, {
    test: status(200, `
      const p = pm.response.json();
      pm.test("page structure", () => pm.expect(p).to.have.all.keys("items", "page", "size", "totalItems", "totalPages"));
      pm.test("defaults page=1 size=20", () => { pm.expect(p.page).to.eql(1); pm.expect(p.size).to.eql(20); });`),
  }),
  req('Список: сортировка salary:desc', 'GET', `${W}/api/workers?sort=salary:desc&sort=name:asc&size=1000`, {
    test: status(200, `
      const s = pm.response.json().items.map(w => w.salary);
      pm.test("sorted by salary desc", () => pm.expect(s).to.eql([...s].sort((a, b) => b - a)));`),
  }),
  req('Список: фильтры (AND) + пагинация', 'GET', `${W}/api/workers?filter=salary:gte:100000&filter=person.eyeColor:eq:BROWN&filter=name:contains:Ivan&page=1&size=5`, {
    test: status(200, `
      const p = pm.response.json();
      pm.test("all items match filters", () => p.items.forEach(w => { pm.expect(w.salary).to.be.at.least(100000); pm.expect(w.person.eyeColor).to.eql("BROWN"); pm.expect(w.name).to.include("Ivan"); }));
      pm.test("size respected", () => pm.expect(p.items.length).to.be.at.most(5));
      pm.test("worker A found", () => pm.expect(p.items.map(w => w.id)).to.include(Number(pm.collectionVariables.get("workerA"))));`),
  }),
  req('Список: фильтр по null (location отсутствует)', 'GET', `${W}/api/workers?filter=person.location.x:eq:null&size=1000`, {
    test: status(200, `pm.test("only workers without location", () => pm.response.json().items.forEach(w => pm.expect(w.person.location).to.eql(null)));`),
  }),
  req('Список: фильтр enum по порядку (position:gt:LABORER)', 'GET', `${W}/api/workers?filter=position:gt:LABORER&size=1000`, {
    test: status(200, `pm.test("only BAKER / MANAGER_OF_CLEANING", () => pm.response.json().items.forEach(w => pm.expect(["BAKER", "MANAGER_OF_CLEANING"]).to.include(w.position)));`),
  }),
  req('Список: пустая страница за пределами', 'GET', `${W}/api/workers?page=100000&size=1000`, {
    test: status(200, `pm.test("no items", () => pm.expect(pm.response.json().items).to.eql([]));`),
  }),
  req('Полностью обновить (PUT) с expectedStatus', 'PUT', `${W}/api/workers/{{workerA}}?worker={{workerJson}}&expectedStatus=HIRED`, {
    pre: encode('workerJson', workerA.replace('"Ivan Petrov"', '"Ivan Petrovich"').replace('salary: 120000.5', 'salary: 130000')),
    test: status(200, `
      const w = pm.response.json();
      pm.test("fields replaced", () => { pm.expect(w.name).to.eql("Ivan Petrovich"); pm.expect(w.salary).to.eql(130000); });`),
  }),
  req('PUT с неверным expectedStatus → 409', 'PUT', `${W}/api/workers/{{workerA}}?worker={{workerJson}}&expectedStatus=FIRED`, {
    pre: encode('workerJson', workerA),
    test: error(409),
  }),
  req('Изменить статус (PATCH)', 'PATCH', `${W}/api/workers/{{workerA}}?status=PROBATION&expectedStatus=HIRED`, {
    test: status(200, `pm.test("status changed, name kept", () => { pm.expect(pm.response.json().status).to.eql("PROBATION"); pm.expect(pm.response.json().name).to.eql("Ivan Petrovich"); });`),
  }),
  req('PATCH с неверным expectedStatus → 409', 'PATCH', `${W}/api/workers/{{workerA}}?status=REGULAR&expectedStatus=HIRED`, { test: error(409) }),
];

const organizations = [
  req('Организация до назначения = null', 'GET', `${W}/api/workers/{{workerA}}/organization`, {
    test: status(200, `pm.test("organizationId null", () => pm.expect(pm.response.json().organizationId).to.eql(null));`),
  }),
  req('Первое назначение в организацию 10', 'PUT', `${W}/api/workers/{{workerA}}/organization?assignment={{assignment}}`, {
    pre: encode('assignment', '{ expectedOrganizationId: null, organizationId: 10 }'),
    test: status(200, `pm.test("organizationId 10", () => pm.expect(pm.response.json().organizationId).to.eql(10));`),
  }),
  req('Повторное назначение с устаревшим expected → 409', 'PUT', `${W}/api/workers/{{workerA}}/organization?assignment={{assignment}}`, {
    pre: encode('assignment', '{ expectedOrganizationId: null, organizationId: 20 }'),
    test: error(409),
  }),
  req('Назначение в несуществующую организацию → 410', 'PUT', `${W}/api/workers/{{workerB}}/organization?assignment={{assignment}}`, {
    pre: encode('assignment', '{ expectedOrganizationId: null, organizationId: 999 }'),
    test: error(410),
  }),
  req('assignment без expectedOrganizationId → 422', 'PUT', `${W}/api/workers/{{workerB}}/organization?assignment={{assignment}}`, {
    pre: encode('assignment', '{ organizationId: 10 }'),
    test: error(422),
  }),
];

const queries = [
  req('Сумма зарплат', 'GET', `${W}/api/workers/salary/sum`, {
    test: status(200, `pm.test("sum is a number", () => pm.expect(pm.response.json().sum).to.be.a("number").and.at.least(180000));`),
  }),
  req('Количество с должностью выше LABORER', 'GET', `${W}/api/workers/position/greater-than/LABORER`, {
    test: status(200, `pm.test("count >= 1 (worker B is BAKER)", () => pm.expect(pm.response.json().count).to.be.at.least(1));`),
  }),
  req('Поиск по подстроке имени (регистрозависимый)', 'GET', `${W}/api/workers/name/contains/Petrov`, {
    test: status(200, `pm.test("found worker A", () => pm.expect(pm.response.json().map(w => w.id)).to.include(Number(pm.collectionVariables.get("workerA"))));`),
  }),
  req('Поиск: другой регистр не находит', 'GET', `${W}/api/workers/name/contains/petrov`, {
    test: status(200, `pm.test("case-sensitive", () => pm.response.json().forEach(w => pm.expect(w.name).to.include("petrov")));`),
  }),
];

const hr = [
  req('HR: перевести A из 10 в 20', 'POST', `${H}/hr/move/{{workerA}}/10/20`, {
    test: status(200, `
      const r = pm.response.json();
      pm.test("MoveResult", () => { pm.expect(r.organizationFrom).to.eql(10); pm.expect(r.organizationTo).to.eql(20); pm.expect(r.worker.id).to.eql(Number(pm.collectionVariables.get("workerA"))); });
      pm.test("worker fields kept", () => pm.expect(r.worker.salary).to.eql(130000));`),
  }),
  req('HR: повтор перевода (работник уже не в 10) → 409', 'POST', `${H}/hr/move/{{workerA}}/10/20`, { test: error(409) }),
  req('HR: id-from = id-to → 409', 'POST', `${H}/hr/move/{{workerA}}/20/20`, { test: error(409) }),
  req('HR: несуществующая организация → 410', 'POST', `${H}/hr/move/{{workerA}}/20/999`, { test: error(410) }),
  req('HR: несуществующий работник → 410', 'POST', `${H}/hr/move/999999999/10/20`, { test: error(410) }),
  req('HR: id не число → 400', 'POST', `${H}/hr/move/abc/10/20`, { test: error(400) }),
  req('HR: id = 0 → 422', 'POST', `${H}/hr/move/0/10/20`, { test: error(422) }),
  req('HR: уволить → 307 на Worker', 'PATCH', `${H}/hr/fire/{{workerB}}`, {
    noRedirect: true,
    test: status(307, `pm.test("Location → PATCH /api/workers/{id}?status=FIRED", () => pm.expect(pm.response.headers.get("Location")).to.match(new RegExp("/api/workers/" + pm.collectionVariables.get("workerB") + "\\\\?status=FIRED$")));`),
  }),
  req('HR: уволить с переходом по редиректу → FIRED', 'PATCH', `${H}/hr/fire/{{workerB}}`, {
    followMethod: true,
    specAs: ['PATCH', `${W}/api/workers/{{workerB}}`],
    test: status(200, `pm.test("worker is FIRED", () => pm.expect(pm.response.json().status).to.eql("FIRED"));`),
  }),
  req('HR: уволить, id = 0 → 422', 'PATCH', `${H}/hr/fire/0`, { noRedirect: true, test: error(422) }),
];

const errors = [
  req('id не число → 400', 'GET', `${W}/api/workers/abc`, { test: error(400) }),
  req('id = 0 → 422', 'GET', `${W}/api/workers/0`, { test: error(422) }),
  req('Несуществующий работник → 410', 'GET', `${W}/api/workers/999999999`, { test: error(410) }),
  req('POST без параметра worker → 400', 'POST', `${W}/api/workers`, { test: error(400) }),
  req('POST с битым JSON → 400', 'POST', `${W}/api/workers?worker=%7B%22name%22`, { test: error(400) }),
  req('POST: неверный тип поля (salary строкой) → 400', 'POST', `${W}/api/workers?worker={{workerJson}}`, {
    pre: encode('workerJson', workerA.replace('salary: 120000.5', 'salary: "120000.5"')),
    test: error(400),
  }),
  req('POST: нарушение ограничений → 422 со списком полей', 'POST', `${W}/api/workers?worker={{workerJson}}`, {
    pre: encode('workerJson', workerA.replace('salary: 120000.5', 'salary: -1').replace('y: 714', 'y: 715').replace('"Ivan Petrov"', '""')),
    test: error(422, `pm.test("violations for name, salary, coordinates.y", () => pm.expect(e.violations.map(v => v.field)).to.have.members(["name", "salary", "coordinates.y"]));`),
  }),
  req('POST: неизвестное поле → 422', 'POST', `${W}/api/workers?worker={{workerJson}}`, {
    pre: encode('workerJson', workerA.replace('name:', 'age: 30, name:')),
    test: error(422, `pm.test("field age", () => pm.expect(e.violations[0].field).to.eql("age"));`),
  }),
  req('POST: неизвестное значение enum → 422', 'POST', `${W}/api/workers?worker={{workerJson}}`, {
    pre: encode('workerJson', workerA.replace('"MANAGER"', '"CEO"')),
    test: error(422),
  }),
  req('POST с телом запроса → 415', 'POST', `${W}/api/workers?worker={{workerJson}}`, {
    pre: encode('workerJson', workerA),
    headers: [{ key: 'Content-Type', value: 'application/json' }],
    body: '{"name": "body is not allowed"}',
    test: error(415),
  }),
  req('Список: size=0 → 422', 'GET', `${W}/api/workers?size=0`, { test: error(422) }),
  req('Список: page=abc → 400', 'GET', `${W}/api/workers?page=abc`, { test: error(400) }),
  req('Список: неизвестное поле фильтра → 422', 'GET', `${W}/api/workers?filter=age:gt:3`, { test: error(422) }),
  req('Список: contains на числе → 422', 'GET', `${W}/api/workers?filter=salary:contains:1`, { test: error(422) }),
  req('Список: неверная сортировка → 422', 'GET', `${W}/api/workers?sort=salary:up`, { test: error(422) }),
  req('PATCH без status → 400', 'PATCH', `${W}/api/workers/{{workerA}}`, { test: error(400) }),
  req('PATCH с неизвестным status → 422', 'PATCH', `${W}/api/workers/{{workerA}}?status=RETIRED`, { test: error(422) }),
  req('position неизвестна → 422', 'GET', `${W}/api/workers/position/greater-than/CEO`, { test: error(422) }),
  req('Сумма зарплат с Accept: application/xml → 406', 'GET', `${W}/api/workers/salary/sum`, {
    headers: [{ key: 'Accept', value: 'application/xml' }],
    test: error(406),
  }),
];

const cleanup = [
  req('Удалить работника A', 'DELETE', `${W}/api/workers/{{workerA}}`, { test: status(204) }),
  req('Удалить работника B', 'DELETE', `${W}/api/workers/{{workerB}}`, { test: status(204) }),
  req('Повторное удаление → 410', 'DELETE', `${W}/api/workers/{{workerA}}`, { test: error(410) }),
];

const collection = {
  info: {
    name: 'SOA lab 2 — Worker & HR',
    description:
      'Проверка Worker API (WildFly) и HR API (Payara) по спецификациям docs/openapi. ' +
      'Запускать папки по порядку (Run collection). Сертификаты самоподписанные: добавьте ' +
      '.local/docker/tls/worker.crt и hr.crt в Settings → Certificates → CA certificates ' +
      'или отключите SSL certificate verification.',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
  },
  variable: [
    { key: 'workerBaseUrl', value: 'https://localhost:8543' },
    { key: 'hrBaseUrl', value: 'https://localhost:18081' },
    { key: 'workerA', value: '' },
    { key: 'workerB', value: '' },
  ],
  item: [
    { name: '1. Worker: CRUD, сортировка, фильтры, пагинация', item: crud },
    { name: '2. Worker: организации', item: organizations },
    { name: '3. Worker: запросы', item: queries },
    { name: '4. HR service', item: hr },
    { name: '5. Ошибки и валидация', item: errors },
    { name: '6. Очистка', item: cleanup },
  ],
};
fs.writeFileSync(process.argv[2] || 'soa-lab2.postman_collection.json', JSON.stringify(collection, null, 2));
console.log('requests:', collection.item.reduce((n, f) => n + f.item.length, 0));
