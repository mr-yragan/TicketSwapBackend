# TicketSwap Frontend API Guide

Полный гайд для frontend по текущему backend TicketSwap с поддержкой S3/MinIO и множественных файлов билетов.

## 1. Базовая информация

### Base URL

Локально обычно используется:

```text
http://localhost:8080
```

### Формат API

- transport: `HTTP`
- архитектура: `REST`
- основной формат: `application/json`
- для загрузки файлов: `multipart/form-data`
- авторизация: `JWT` в заголовке `Authorization`

### Заголовок авторизации

```http
Authorization: Bearer <token>
```

### CORS

По умолчанию backend разрешает frontend с origin:

```text
http://localhost:3000
```

---

## 2. Жизненный цикл объявления

После создания объявление проходит статусы:

```text
CREATED -> PENDING_VALIDATION -> PENDING_RECIPIENT -> PROCESSING -> COMPLETED
```

Также возможен статус:

```text
FAILED
```

### Что это значит для фронта

- `CREATED` - объявление только создано.
- `PENDING_VALIDATION` - идёт внутренняя валидация.
- `PENDING_RECIPIENT` - объявление доступно для покупки.
- `PROCESSING` - началась покупка.
- `COMPLETED` - покупка завершена.
- `FAILED` - объявление/операция завершилась неуспешно.

После `POST /api/tickets/sell` объявление **не сразу** попадает в публичный список. Backend переводит его в `PENDING_VALIDATION`, а затем в `PENDING_RECIPIENT` автоматически через несколько секунд.

Поэтому после создания объявления frontend не должен ожидать, что оно мгновенно появится в `GET /api/tickets`.

---

## 3. Общий формат ошибок

Backend возвращает ошибки в формате:

```json
{
  "timestamp": "2026-03-30T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/tickets/sell",
  "fieldErrors": {
    "eventDate": "eventDate must be in the future"
  }
}
```

### Частые статусы

- `400 Bad Request` - ошибка данных / бизнес-правила / валидация
- `401 Unauthorized` - нет токена или нет доступа
- `404 Not Found` - сущность или файл не найдены
- `409 Conflict` - конфликт состояния (например, email уже существует, лот удержан другим покупателем)
- `413 Payload Too Large` - файл больше лимита
- `500 Internal Server Error` - внутренняя ошибка backend

---

## 4. Основные типы данных для frontend

### 4.1 AuthRequest

```ts
interface AuthRequest {
  email: string;
  password: string;
}
```

### 4.2 AuthResponse

```ts
interface AuthResponse {
  token: string;
}
```

### 4.3 CreateTicketRequest

```ts
interface CreateTicketRequest {
  uid: string;
  eventName: string;
  eventDate: string; // ISO datetime, например 2027-12-30T20:00:00
  venue: string;     // строка вида "Ziggo Dome, Amsterdam"
  price: number;
  additionalInfo?: string | null;
  organizerName?: string | null;
  sellerComment?: string | null;
}
```

### 4.4 TicketLotResponse

Короткая карточка лота для списков.

```ts
interface TicketLotResponse {
  id: number;
  eventName: string;
  eventDate: string;
  venue: string;
  price: number;
  verified: boolean;
}
```

### 4.5 ListingDetailsResponse

Полная информация по лоту.

```ts
interface ListingDetailsResponse {
  id: number;
  eventName: string;
  eventDate: string;
  venue: string;
  price: number;
  verified: boolean;
  additionalInfo?: string | null;
  organizerName?: string | null;
  sellerComment?: string | null;
  seller?: {
    displayName: string;
    memberSince: string;
  } | null;
  hasTicketFile: boolean;
}
```

### 4.6 ListingViewResponse

Детальная страница лота.

```ts
type TicketStatus =
  | "CREATED"
  | "PENDING_VALIDATION"
  | "PENDING_RECIPIENT"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED";

interface ListingViewResponse {
  details: ListingDetailsResponse;
  status: TicketStatus;
  hold?: {
    id: number;
    holdUntil: string;
  } | null;
}
```

### 4.7 ListingHoldResponse / HoldResponse

```ts
interface ListingHoldResponse {
  id: number;
  listing: TicketLotResponse;
  holdUntil: string;
  createdAt: string;
}
```

### 4.8 TicketFileDownloadUrlResponse

```ts
interface TicketFileDownloadUrlResponse {
  url: string;
  expiresAt: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
}
```

### 4.9 TicketFileResponse

```ts
interface TicketFileResponse {
  id: number;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
}
```

### 4.10 MeProfileResponse

```ts
interface MeProfileResponse {
  id: number;
  email: string;
  login?: string | null;
  phoneNumber?: string | null;
  role: string;
  createdAt: string;
}
```

### 4.11 UpdateMeRequest

```ts
interface UpdateMeRequest {
  login?: string | null;
  phoneNumber?: string | null;
}
```

---

## 5. Валидация на frontend

## 5.1 Регистрация / логин

### Email
- обязательный
- должен быть валидным email
- максимум `255` символов

### Password
- обязательный
- от `8` до `72` символов

## 5.2 Создание объявления

### Поля
- `uid` - обязательный, max `255`
- `eventName` - обязательный, max `255`
- `eventDate` - обязательный, должен быть в будущем
- `venue` - обязательный, max `255`
- `price` - обязательный, `> 0`
- `additionalInfo` - max `2000`
- `organizerName` - max `255`
- `sellerComment` - max `2000`

### Важный формат `venue`

Backend принимает `venue` одной строкой. Если передать:

```text
"Ziggo Dome, Amsterdam"
```

backend сам разложит это на:
- venueName = `Ziggo Dome`
- venueCity = `Amsterdam`

## 5.3 Профиль

### login
- от `3` до `32` символов
- допустимы только: буквы, цифры, `_`, `.`, `-`
- regex:

```text
^[a-zA-Z0-9_.-]+$
```

### phoneNumber
- от `5` до `32` символов
- допустимы только: `+`, цифры, пробел, скобки, дефис
- regex:

```text
^[+0-9 ()-]+$
```

## 5.4 Файлы билетов

На один билет теперь можно загрузить **несколько файлов**.

Разрешены только:

- `application/pdf`
- `image/png`
- `image/jpeg`

По расширению:

- `.pdf`
- `.png`
- `.jpg`
- `.jpeg`

Максимальный размер:

```text
10 MB
```

Рекомендуемая frontend-валидация:

```ts
const allowedTypes = ["application/pdf", "image/png", "image/jpeg"];
const maxSize = 10 * 1024 * 1024;

function validateTicketFile(file: File): string | null {
  if (!allowedTypes.includes(file.type)) {
    return "Допустимы только PDF, PNG и JPG/JPEG";
  }

  if (file.size > maxSize) {
    return "Файл должен быть не больше 10 MB";
  }

  return null;
}
```

---

## 6. Полный список endpoint-ов

## 6.1 Auth

### POST `/api/auth/register`

Регистрация пользователя.

**Auth:** не нужен

**Body:**

```json
{
  "email": "seller@example.com",
  "password": "Password123"
}
```

**Response:**
- `201 Created`
- тело пустое

**Ошибки:**
- `409 Conflict` - email уже существует
- `400 Bad Request` - ошибка валидации

---

### POST `/api/auth/login`

Логин и получение JWT.

**Auth:** не нужен

**Body:**

```json
{
  "email": "seller@example.com",
  "password": "Password123"
}
```

**Response 200:**

```json
{
  "token": "<jwt>"
}
```

**Ошибки:**
- `401 Unauthorized` - неверный email/пароль
- `400 Bad Request` - ошибка валидации

---

## 6.2 Tickets: публичные запросы

### GET `/api/tickets`

Публичный список доступных для покупки лотов.

**Auth:** не нужен

**Возвращает только:**
- лоты в статусе `PENDING_RECIPIENT`
- лоты, у которых `eventDate` ещё в будущем
- лоты, которые сейчас не удерживаются активным hold

**Response 200:**

```json
[
  {
    "id": 8,
    "eventName": "Test Concert",
    "eventDate": "2027-12-30T20:00:00",
    "venue": "Ziggo Dome, Amsterdam",
    "price": 3500.0,
    "verified": true
  }
]
```

---

### GET `/api/tickets/{id}`

Детальная информация по конкретному лоту.

**Auth:** не нужен

**Response 200:**

```json
{
  "details": {
    "id": 8,
    "eventName": "Test Concert",
    "eventDate": "2027-12-30T20:00:00",
    "venue": "Ziggo Dome, Amsterdam",
    "price": 3500.0,
    "verified": true,
    "additionalInfo": "Sector A, Row 2, Seat 15",
    "organizerName": "Live Nation",
    "sellerComment": "Original PDF ticket",
    "seller": {
      "displayName": "seller@example.com",
      "memberSince": "2026-03-30T12:00:00Z"
    },
    "hasTicketFile": true
  },
  "status": "PENDING_RECIPIENT",
  "hold": null
}
```

Если активен hold, поле `hold` будет заполнено.

`details.hasTicketFile` означает, что у лота есть **хотя бы один** файл. Для получения списка файлов нужно использовать отдельный endpoint `GET /api/tickets/{id}/files`.

**Ошибки:**
- `404 Not Found` - лот не найден

---

## 6.3 Tickets: protected

### GET `/api/tickets/my`

Список лотов текущего продавца.

**Auth:** нужен

**Response 200:**

```json
[
  {
    "id": 8,
    "eventName": "Test Concert",
    "eventDate": "2027-12-30T20:00:00",
    "venue": "Ziggo Dome, Amsterdam",
    "price": 3500.0,
    "verified": true
  }
]
```

---

### POST `/api/tickets/sell` (`application/json`)

Создание объявления **без файла**.

**Auth:** нужен

**Headers:**

```http
Content-Type: application/json
Authorization: Bearer <token>
```

**Body:**

```json
{
  "uid": "TEST-UID-001",
  "eventName": "Test Concert",
  "eventDate": "2027-12-30T20:00:00",
  "venue": "Ziggo Dome, Amsterdam",
  "price": 3500.00,
  "additionalInfo": "Sector A, Row 2, Seat 15",
  "organizerName": "Live Nation",
  "sellerComment": "Original ticket"
}
```

**Response 201:**

```json
{
  "id": 9,
  "eventName": "Test Concert",
  "eventDate": "2027-12-30T20:00:00",
  "venue": "Ziggo Dome, Amsterdam",
  "price": 3500.0,
  "verified": false,
  "additionalInfo": "Sector A, Row 2, Seat 15",
  "organizerName": "Live Nation",
  "sellerComment": "Original ticket",
  "seller": {
    "displayName": "seller@example.com",
    "memberSince": "2026-03-30T12:00:00Z"
  },
  "hasTicketFile": false
}
```

---

### POST `/api/tickets/sell` (`multipart/form-data`)

Создание объявления **с файлами билета сразу**.

**Auth:** нужен

**Parts:**
- `ticket` - JSON
- `ticketFiles` - один или несколько файлов

**Пример через `fetch`:**

```ts
async function createTicketWithFiles(token: string, ticketData: CreateTicketRequest, files: File[]) {
  const formData = new FormData();

  formData.append(
    "ticket",
    new Blob([JSON.stringify(ticketData)], { type: "application/json" })
  );

  for (const file of files) {
    formData.append("ticketFiles", file);
  }

  const response = await fetch("http://localhost:8080/api/tickets/sell", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: formData
  });

  if (!response.ok) {
    throw await response.json();
  }

  return response.json();
}
```

**Response 201:**
`ListingDetailsResponse`

**Важно:**
- если передан хотя бы один корректный файл, `hasTicketFile` будет `true`
- создание лота и файлы обрабатываются одной операцией с откатом, если загрузка файлов не удалась

---

### GET `/api/tickets/{id}/files`

Получить список файлов лота.

**Auth:** нужен

**Кто может получать список:**
- продавец этого лота
- покупатель этого лота **только если статус `COMPLETED`**

**Response 200:**
массив `TicketFileResponse`

---

### POST `/api/tickets/{id}/files`

Загрузить один или несколько файлов в уже существующий лот.

**Auth:** нужен

**Доступ:** только продавец этого лота

**Parts:**
- `ticketFiles`

**Response 200:**
массив `TicketFileResponse`

**Ограничения:**
- менять файлы нельзя, если статус уже `PROCESSING` или `COMPLETED`

**Пример:**

```ts
async function uploadTicketFiles(token: string, ticketId: number, files: File[]) {
  const formData = new FormData();

  for (const file of files) {
    formData.append("ticketFiles", file);
  }

  const response = await fetch(`http://localhost:8080/api/tickets/${ticketId}/files`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: formData
  });

  if (!response.ok) {
    throw await response.json();
  }

  return response.json();
}
```

---

### GET `/api/tickets/{id}/files/{fileId}/download-url`

Получить presigned URL для скачивания конкретного файла билета.

**Auth:** нужен

**Кто может получать ссылку:**
- продавец этого лота
- покупатель этого лота **только если статус `COMPLETED`**

**Response 200:**

```json
{
  "url": "http://minio:9000/...",
  "expiresAt": "2026-03-30T12:15:00Z",
  "originalName": "ticket.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 123456
}
```

**Срок жизни ссылки:**
- по умолчанию `15` минут

---

### DELETE `/api/tickets/{id}/files/{fileId}`

Удалить конкретный файл у лота.

**Auth:** нужен

**Доступ:** только продавец этого лота

**Response:**
- `204 No Content`

**Ограничения:**
- удалять нельзя после начала покупки (`PROCESSING`, `COMPLETED`)

---

### DELETE `/api/tickets/{id}/files`

Удалить все файлы у лота.

**Auth:** нужен

**Доступ:** только продавец этого лота

**Response:**
- `204 No Content`

**Ограничения:**
- удалять нельзя после начала покупки (`PROCESSING`, `COMPLETED`)

После удаления:
- `details.hasTicketFile` станет `false`

---

### POST `/api/tickets/{id}/file`

Legacy-endpoint для загрузки **одного** файла.

**Auth:** нужен

**Доступ:** только продавец этого лота

**Parts:**
- `ticketFile`

**Response 200:**
массив `TicketFileResponse`

**Примечание:**
- endpoint оставлен для совместимости
- он **добавляет** один файл, а не заменяет все файлы лота

---

### GET `/api/tickets/{id}/file/download-url`

Legacy-endpoint для скачивания файла.

**Auth:** нужен

**Примечание:**
- работает только если у лота **ровно один файл**
- если файлов несколько, backend вернёт `400 Bad Request`

---

### DELETE `/api/tickets/{id}/file`

Legacy-endpoint удаления файлов.

**Auth:** нужен

**Примечание:**
- теперь удаляет **все** файлы у лота

---

### POST `/api/tickets/{id}/hold`

Поставить hold на лот.

**Auth:** нужен

**Response 201:**

```json
{
  "id": 15,
  "listing": {
    "id": 8,
    "eventName": "Test Concert",
    "eventDate": "2027-12-30T20:00:00",
    "venue": "Ziggo Dome, Amsterdam",
    "price": 3500.0,
    "verified": true
  },
  "holdUntil": "2026-03-30T12:05:00Z",
  "createdAt": "2026-03-30T12:00:00Z"
}
```

**Важно:**
- hold живёт `5` минут
- если активный hold уже есть у другого пользователя, вернётся `409 Conflict`
- если это свой же hold, backend может вернуть существующий hold

**Бизнес-ограничения:**
- нельзя hold/buy собственный билет
- нельзя hold билет со статусом, отличным от `PENDING_RECIPIENT`
- нельзя hold билет на прошедшее событие

---

### DELETE `/api/tickets/{id}/hold`

Снять hold.

**Auth:** нужен

**Response:**
- `204 No Content`

**Важно:**
- снять hold может только тот пользователь, который его создал
- если hold отсутствует, backend просто завершит запрос без ошибки

---

### POST `/api/tickets/{id}/buy`

Купить билет.

**Auth:** нужен

**Response 200:**
`ListingDetailsResponse`

**Поведение:**
- backend сам гарантирует наличие hold
- есть mock payment delay примерно `2` секунды
- после успешной покупки статус станет `COMPLETED`
- buyer привяжется к лоту
- hold удалится

---

## 6.4 Me / профиль и личные разделы

### GET `/api/me`

Профиль текущего пользователя.

**Auth:** нужен

**Response 200:**

```json
{
  "id": 1,
  "email": "seller@example.com",
  "login": "seller123",
  "phoneNumber": "+31 123 456 789",
  "role": "USER",
  "createdAt": "2026-03-30T12:00:00Z"
}
```

---

### PATCH `/api/me`

Обновить профиль.

**Auth:** нужен

**Body:**

```json
{
  "login": "seller123",
  "phoneNumber": "+31 123 456 789"
}
```

**Response 200:**
`MeProfileResponse`

**Ошибки:**
- `400 Bad Request` - некорректный login/phoneNumber
- `400 Bad Request` - login уже занят (`Login already in use`)

---

### GET `/api/me/listings?scope=active`

Личные объявления пользователя.

**Auth:** нужен

**Query param:**
- `scope=active` - активные
- `scope=archived` или `scope=archive` - архив
- любое другое значение - backend вернёт всё

**Логика scope для listings:**
- `active` = не `COMPLETED` и `eventDate` ещё в будущем
- `archived` = `COMPLETED` или дата события уже прошла

**Response 200:**
массив `TicketLotResponse`

---

### GET `/api/me/holds`

Активные hold текущего пользователя.

**Auth:** нужен

**Response 200:**
массив `HoldResponse`

---

### GET `/api/me/purchases?scope=active`

Покупки текущего пользователя.

**Auth:** нужен

**Возвращаются только:**
- лоты со статусом `COMPLETED`

**Query param:**
- `scope=active` - события ещё не наступили
- `scope=archived` / `archive` - события уже прошли
- любое другое значение - всё

**Response 200:**
массив `TicketLotResponse`

---

## 9. Polling и обновление UI

### Где полезен polling

- после создания объявления
- на карточке детали лота
- на странице hold / checkout

### Что обновлять

- `status`
- `hold`
- `details.hasTicketFile`

### Пример простой стратегии

- после создания лота: polling `GET /api/tickets/{id}` каждые `1-2` секунды
- остановить, когда статус стал:
  - `PENDING_RECIPIENT`
  - `FAILED`

---

## 10. Особенности работы с файлами и MinIO

### 10.1 Backend отдаёт не файл, а presigned URL

Поэтому frontend делает так:

1. вызывает `GET /api/tickets/{id}/files`
2. получает список файлов
3. выбирает нужный `fileId`
4. вызывает `GET /api/tickets/{id}/files/{fileId}/download-url`
5. получает `url`
6. открывает эту ссылку в новой вкладке или скачивает через браузер

### 10.2 Локальная разработка на Windows

Если backend в Docker, а MinIO работает по внутреннему имени `minio`, может понадобиться hosts-фикс:

```text
127.0.0.1 minio
```

Иначе браузер/PowerShell не сможет открыть presigned URL.

---
## Короткая сводка по endpoint-ам

### Public
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/tickets`
- `GET /api/tickets/{id}`

### Protected
- `GET /api/tickets/my`
- `POST /api/tickets/sell` (`application/json`)
- `POST /api/tickets/sell` (`multipart/form-data`)
- `GET /api/tickets/{id}/files`
- `POST /api/tickets/{id}/files`
- `GET /api/tickets/{id}/files/{fileId}/download-url`
- `DELETE /api/tickets/{id}/files/{fileId}`
- `DELETE /api/tickets/{id}/files`
- `POST /api/tickets/{id}/file`
- `GET /api/tickets/{id}/file/download-url`
- `DELETE /api/tickets/{id}/file`
- `POST /api/tickets/{id}/hold`
- `DELETE /api/tickets/{id}/hold`
- `POST /api/tickets/{id}/buy`
- `GET /api/me`
- `PATCH /api/me`
- `GET /api/me/listings?scope=active|archived|all`
- `GET /api/me/holds`
- `GET /api/me/purchases?scope=active|archived|all`

---
