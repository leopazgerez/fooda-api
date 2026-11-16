# Template hexagonal (Spring Boot 4)

Punto de partida para una API con login, registro, recuperación de contraseña, perfil y gestión de usuarios. Java 17, Spring Boot 4.1, PostgreSQL, JWT, arquitectura hexagonal.

## Requisitos

- Java 17
- Docker (para la base de datos y para los tests, que usan Testcontainers)

## Arranque

```bash
cp .env.example .env
docker compose up -d
set -a; source .env; set +a      # exporta las variables al shell
./mvnw spring-boot:run
```

- Swagger: http://localhost:8080/swagger-ui.html
- Tests: `./mvnw test` (Docker tiene que estar corriendo).
- El esquema lo maneja Flyway (`src/main/resources/db/migration`); Hibernate solo valida.
- `APP_MAIL_MODE` es obligatoria. Con `log` (el `.env.example` la trae así) los emails se imprimen en el log: ahí está el enlace de verificación. Nunca uses `log` en producción: los tokens quedarían en los logs.

## Variables de entorno

| Variable | Por defecto | Para qué |
|---|---|---|
| `PORT` | `8080` | Puerto HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5432/fooda` | Base de datos |
| `DB_USERNAME` / `DB_PASSWORD` | `fooda` / `fooda` | Credenciales de la base |
| `APP_JWT_SECRET` | (obligatoria) | Clave HS256, al menos 32 bytes. Sin ella la app no arranca |
| `APP_JWT_ACCESS_TTL` | `15m` | Vida del access token |
| `APP_JWT_REFRESH_TTL` | `30d` | Vida del refresh token |
| `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` / `APP_ADMIN_NAME` | vacías | Primer admin. Se crea solo si la tabla `app_user` está vacía; si faltan email o contraseña, se omite |
| `APP_CORS_ORIGINS` | `http://localhost:3000` | Orígenes permitidos, separados por coma |
| `APP_FRONTEND_URL` | `http://localhost:3000` | Base de los enlaces de los emails |
| `APP_MAIL_MODE` | obligatoria | `log` imprime los emails (solo desarrollo), `smtp` los envía en segundo plano |
| `APP_MAIL_FROM` | `no-reply@example.com` | Remitente |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | — / `587` / — / — | Servidor SMTP (solo con `smtp`) |

## Endpoints

Públicos:

| Método y ruta | Respuesta |
|---|---|
| `POST /api/auth/register` | 201 (siempre, exista o no el email). Crea el usuario (sin verificar) y manda el email de verificación; si el email ya existe no crea nada y le manda un aviso (o reenvía la verificación si la cuenta no está verificada) |
| `POST /api/auth/verify-email` `{token}` | 204 |
| `POST /api/auth/resend-verification` `{email}` | 204 (siempre) |
| `POST /api/auth/login` `{email,password}` | 200 sesión `{accessToken, accessExpiresAt, refreshToken, user}`; 403 si falta verificar el email; 401 si las credenciales no sirven |
| `POST /api/auth/refresh` `{refreshToken}` | 200 sesión nueva. El refresh token usado queda revocado (rotación) |
| `POST /api/auth/forgot-password` `{email}` | 204 (siempre, exista o no la cuenta) |
| `POST /api/auth/reset-password` `{token,newPassword}` | 204. Revoca todos los refresh tokens del usuario |

Con `Authorization: Bearer <accessToken>`:

| Método y ruta | Respuesta |
|---|---|
| `POST /api/auth/logout` `{refreshToken}` | 204. Revoca ese refresh token |
| `GET /api/me` | Tu perfil, con `role` y `permissions` (para que el frontend muestre u oculte acciones) |
| `PATCH /api/me` `{fullName}` | Perfil actualizado |
| `PUT /api/me/password` `{currentPassword,newPassword}` | **200 con una sesión nueva** (no 204). Revoca los demás refresh tokens, así que el cliente tiene que guardar la sesión devuelta |

Con permiso `USERS_READ` (los `GET`) o `USERS_WRITE` (`POST` y `PATCH`); hoy solo los tiene `ADMIN`:

| Método y ruta | Respuesta |
|---|---|
| `GET /api/users?page=0&size=20` | Página de usuarios |
| `GET /api/users/{id}` | Un usuario |
| `POST /api/users` | 201, crea un usuario (con rol) |
| `PATCH /api/users/{id}` `{fullName, role, active}` | Cambia nombre, rol o estado. Un admin no puede desactivarse ni quitarse el rol a sí mismo (409). Desactivar a alguien revoca sus sesiones |

### Catálogo

Con permiso `CATALOG_READ` (los `GET`) o `CATALOG_WRITE` (`POST`, `PATCH`, `DELETE`); `PUT /api/settings` pide `SETTINGS_WRITE`. Hoy solo los tiene `ADMIN`. Los `DELETE` son borrado lógico y dan 409 si el recurso está en uso.

| Recurso | Rutas |
|---|---|
| Proveedores | `/api/suppliers` (CRUD) |
| Insumos | `/api/supplies` (CRUD), `/api/supplies/{id}/prices` (historial de precios) |
| Platos | `/api/dishes` (CRUD) con receta `[{supplyId, quantity, unit, portions?}]` |
| Packs | `/api/packs` (CRUD), `/api/packs/{id}/versions` (5 menús por versión). `GET /api/packs/{id}` devuelve `cost` y `salePrice` |
| Sucursales | `/api/branches` (CRUD) |
| Fondos de reserva | `/api/reserve-funds` (CRUD) |
| Configuración | `GET`/`PUT /api/settings` |

Al arrancar, si `APP_ADMIN_EMAIL` está definido y no hay configuración, se siembran la configuración por defecto (pack a 45000, etc.) y la sucursal cocina "Salta", registradas a nombre del admin.

Errores: siempre JSON `{message, status, timestamp}` y, si aplica, `field` (por ejemplo 409 con `field: "email"` si un admin crea un usuario con un email que ya existe). Si dos operaciones modifican el mismo usuario a la vez, la segunda recibe 409 y tiene que reintentar.

### Cosas a tener en cuenta

- **Logout:** solo revoca el refresh token. El access token sigue valiendo hasta 15 minutos después del logout (lo que dure `APP_JWT_ACCESS_TTL`).
- **Rol y estado:** cada request autenticado lee el usuario de la base (rol y `active`), no confía en el token. Un usuario desactivado o degradado pierde el acceso en el acto, aunque su access token no haya vencido.
- **Emails:** se envían después del commit y, con `smtp`, en un hilo aparte (`applicationTaskExecutor`), así que un SMTP lento no frena la respuesta ni delata si la cuenta existe. Los fallos se registran en el log y no se devuelven al cliente; un email en cola se pierde si la app se apaga justo en ese momento.

## Roles y permisos

Los endpoints piden **permisos**, no roles. Cada rol declara los suyos en `user/domain/Role.java`:

```java
ADMIN(EnumSet.allOf(Permission.class)), // USERS_*, CATALOG_READ, CATALOG_WRITE, SETTINGS_WRITE
USER(EnumSet.noneOf(Permission.class));
```

- En cada request se lee el rol del usuario desde la base y se convierte en authorities: `ROLE_<rol>` más un authority por permiso (`USERS_READ`, `CATALOG_READ`, ...). Cambiar el rol de alguien surte efecto en el próximo request.
- En el controller: `@PreAuthorize("hasAuthority('USERS_WRITE')")`. Si falta el permiso, 403 JSON.
- **Permiso nuevo:** agregalo a `user/domain/Permission.java`, asignalo a los roles en `Role.java` y usalo en `@PreAuthorize`.
- **Rol nuevo:** agregalo a `Role.java` con sus permisos y al `CHECK` de `app_user.role` (con una migración nueva que reemplace el constraint).
- Un admin no puede quitarse a sí mismo `USERS_WRITE` (ni desactivarse), y quien ya no tiene `USERS_WRITE` no puede modificar usuarios aunque su request haya empezado antes del cambio.

## Capas

Cada módulo (`user`, `auth`) tiene la misma forma, bajo `com.fooda.api.<módulo>`:

```
domain/                          Java puro: entidades y reglas
application/port/in/             lo que la app ofrece (casos de uso)
application/port/out/            lo que la app necesita (repositorios, email, hasher)
application/service/             implementa los port/in usando los port/out
infrastructure/in/web/           controllers y DTOs
infrastructure/out/...           JPA, mail, seguridad: implementan los port/out
config/                          @Configuration que arma los services con @Bean
```

`shared` tiene lo común (errores, `Page`, `Transactions`) y `config` la seguridad y el reloj.

Reglas, comprobadas por `HexagonalArchitectureTest` (ArchUnit):

- `domain` solo depende de `java..` y de otras clases de `domain`.
- `application` no depende de `infrastructure`, `config`, Spring, Jakarta, Hibernate, Lombok ni Jackson.

Si rompés una, `./mvnw test` falla.

## Agregar un módulo

```bash
java scripts/NewModule.java Product
```

Genera el módulo `product` con la forma de `user` (dominio, ports, service, config, JPA, controller, DTOs) y la migración `V<siguiente>__product.sql`:

| Método y ruta | Respuesta |
|---|---|
| `GET /api/products?page=0&size=20` | Página, sin los borrados |
| `GET /api/products/{id}` | Uno (404 si no existe o está borrado) |
| `POST /api/products` | 201 con `Location`. `createdBy` es quien llama |
| `PATCH /api/products/{id}` | Actualiza `updatedAt` |
| `DELETE /api/products/{id}` | 204. Borrado lógico: completa `deletedAt`, la fila queda |

Cada entidad trae `id`, `createdBy` (FK a `app_user`), `createdAt`, `updatedAt`, `deletedAt` y `version` (bloqueo optimista). Los endpoints piden estar logueado, sin permisos. Nombres de varias palabras: `OrderItem` → paquete `orderitem`, tabla `order_item`, ruta `/api/order-items`. Si el módulo ya existe o el nombre es una palabra reservada, no genera nada.

A mano (buscá `TODO`): los campos de negocio (dominio, `JpaEntity`, request/response, una migración nueva), los tests y, si hace falta restringir, los permisos (paso 8 de abajo).

### Qué es cada pieza

Para entender lo que genera, o para armar un módulo distinto a mano (ejemplo: `note`). Seguí `user` archivo por archivo, en este orden. Cada paso compila y se prueba antes de pasar al siguiente.

1. **Dominio** (`note/domain/`): como `user/domain/User.java` y `UserId.java`. Un `record` que valida en el constructor y lanza `ValidationException(campo, mensaje)` de `shared.domain`. Sin Spring. Test: `UserTest`.
2. **Port out** (`note/application/port/out/NoteRepository.java`): como `UserRepository.java`. Una interfaz con lo que necesita la app, en términos del dominio. Para listar usar `shared.application.Page`.
3. **Adapter JPA + migración** (`note/infrastructure/out/persistence/`): `NoteJpaEntity`, `NoteJpaRepository` (Spring Data) y `NotePersistenceAdapter`, que implementa el port y convierte entidad <-> dominio, como `UserJpaEntity`, `UserJpaRepository`, `UserPersistenceAdapter`. Migración nueva `src/main/resources/db/migration/V2__note.sql` (nunca edites `V1__identity.sql`; Hibernate solo valida). Test con Postgres: `UserPersistenceAdapterTest`.
4. **Port in** (`note/application/port/in/`): los casos de uso como interfaces (`ProfileUseCase`, `ManageUsersUseCase`) y los objetos de entrada (`NewUser`, `UserChanges`).
5. **Service + test con fakes** (`note/application/service/NoteService.java`): implementa los port in con los port out y `Transactions` para las operaciones que escriben, como `UserService.java`. Nada de Spring ahí. Test sin Spring con repositorio en memoria: `UserServiceTest` usa `InMemoryUserRepository` y `DirectTransactions`.
6. **Config** (`note/config/NoteConfig.java`): `@Configuration` con un `@Bean` que construye el service, como `UserConfig.java`.
7. **Controller + DTOs + test** (`note/infrastructure/in/web/`): `NotesController` y los `record` de request/response, como `UsersController`, `CreateUserRequest`, `UserResponse`. Usá `Principals.userId(jwt)` para saber quién llama. Test con `@WebMvcTest` y el use case con `@MockitoBean`, importando `SecurityConfig`, `GlobalExceptionHandler` y `TimeConfig`: `UsersControllerTest`. Los errores de `shared` ya se convierten en JSON.
8. **Seguridad** (`config/SecurityConfig.java`), solo si hace falta: por defecto todo `/api/**` pide Bearer. Para abrir una ruta agregala a los `permitAll`; para restringir, agregá un permiso (`NOTES_WRITE`) a `Permission` y a los roles que correspondan, y anotá el método del controller con `@PreAuthorize("hasAuthority('NOTES_WRITE')")` (como `UsersController`). En el `@WebMvcTest`, `jwt().authorities(new SimpleGrantedAuthority("NOTES_WRITE"))`.

Al final corré `./mvnw test`: `HexagonalArchitectureTest` te avisa si una capa depende de lo que no debe.
