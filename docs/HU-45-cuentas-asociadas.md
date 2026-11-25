# HU-45 — Cuentas asociadas Pro

Un usuario Pro asocia otras cuentas a la suya y las gestiona sin cerrar sesión, desde
**Perfil → Cuentas asociadas**.

## Cómo funciona

1. **Asociar.** Invita a una cuenta existente por su correo, o crea una nueva.
2. **Confirmar.** El titular acepta o rechaza desde su propia cuenta (no necesita ser Pro). La
   invitación vence a los 7 días. Una cuenta creada desde aquí acepta con su propia sesión temporal.
3. **Gestionar.** "Gestionar" cambia la cuenta activa: Perfil, Panel, Mapa y Crear evento trabajan
   con ella, y un aviso fijo permite volver. La selección sobrevive al cierre de la app.
4. **Desasociar.** Cualquiera de las dos partes, con confirmación. Si era la activa, se vuelve a la
   principal. Si el titular revoca, quien la gestionaba sale al momento.

### Permisos

| | Solo gestionar (`manage`) | Actuar en su nombre (`act`) | Nunca se delega |
|---|---|---|---|
| Ver su panel, editar nombre y foto | ✔ | ✔ | |
| Quitar eventos y ampliar cupos | ✔ | ✔ | |
| Crear eventos, reservar y cancelar | | ✔ | |
| Privacidad, plan, correo, amistades, vínculos | | | ✔ |

Un evento público creado en nombre de otra cuenta exige que **esa** cuenta sea Pro. El rol se fija en
la invitación: para cambiarlo hay que volver a invitar, así el titular siempre consiente.

## Modelo de datos

| Colección | Documento | Campos |
|---|---|---|
| `accountLinks` | `{primaryUid}_{linkedUid}` | `linkId, primaryUid, linkedUid, linkedEmail, role, label, status (pending/active), createdAt, updatedAt, respondedAt` |
| `accountLinkIndex` | `{primaryUid}` | `linkedUids[], updatedAt` |
| `accountSessions` | `{uid}` | `activeUid, updatedAt` |
| `accountAudit` | automático | `actorUid, primaryUid, linkedUid, action, role, createdAt` |
| `config` | `accountLinks` | `maxLinkedAccounts` (opcional) |

Firestore no necesita migración: las colecciones nacen con la primera escritura y `users` no cambia.
Tampoco hacen falta índices compuestos.

**Límite:** 10 por defecto; las invitaciones pendientes (también las vencidas sin cancelar) ocupan
cupo. Para cambiarlo, crear en la consola `config/accountLinks` con `maxLinkedAccounts` (entero;
`-1` = ilimitado). Aplica a la app y a las reglas sin publicar versión.

**Auditoría** (`accountAudit.action`): `invited, accepted, declined, cancelled, unlinked, switched`.
Se escribe en la misma transacción que la acción, es inmutable y las reglas rechazan registros que no
correspondan a lo que ocurre en esa transacción.

## Seguridad

No hay servidor ni Cloud Functions (plan Spark): el backend son `firebase/firestore.rules` y las
transacciones. Plan, límite, duplicados, confirmación, vencimiento y permisos por rol se validan en
las reglas; el código Kotlin repite las validaciones solo para dar mensajes claros.

Cambiar de cuenta **no** cambia la sesión de Firebase: no se guardan credenciales ajenas. Si el
principal deja de ser Pro, sus permisos delegados dejan de valer en el servidor de inmediato.

### Antes de desplegar las reglas

Las reglas publicadas solo estaban en la consola, así que `firestore.rules` incluye una base para
`users`, `events` y `friendships` **deducida del código**. Desplegar reemplaza todo:

1. Copiar las reglas actuales de la consola y compararlas con este archivo.
2. Fusionar lo que falte.
3. Publicar el resultado desde la consola de Firebase (Firestore → Reglas).

Sin las reglas de HU-45 desplegadas, la pantalla muestra "No tienes permiso para hacer esto".

La base también cierra dos huecos que anulaban el gating: cambiarse `accountType` a `pro` y
registrarse con el correo de otra persona en el perfil.

## Cómo probarlo

Con las reglas publicadas y dos cuentas, una Pro y una normal:

1. Entrar con la Pro a Perfil → Cuentas asociadas e invitar el correo de la normal.
2. Entrar con la normal a la misma pantalla: ve el aviso de función Pro y puede aceptar.
3. Volver a la Pro y tocar "Gestionar": aparece el aviso fijo y cambian Perfil, Panel y Mapa.
4. Cerrar y abrir la app: sigue activa la misma cuenta.
5. Desasociar: pide confirmación y vuelve a la cuenta principal.
6. Casos de error: invitarse a sí mismo, repetir una invitación, un correo que no existe y superar el
   límite (bajarlo en `config/accountLinks`).

## Decisiones y supuestos

- **Límite vs. "ilimitadas".** `contextoProyecto.txt` dice que Pro tiene cuentas ilimitadas y la HU
  pide un límite. Queda en 10 y configurable; con `-1` se cumple el contexto.
- **Confirmación en la app, no por correo.** Un correo con token exige servidor y secretos. Iniciar
  sesión en la cuenta invitada prueba lo mismo. Todavía no hay notificación: el invitado la ve al
  entrar a la pantalla.
- **Upgrade informativo.** No existe un flujo de pago o cambio de plan. `ProUpgradeCard` es
  reutilizable y tiene el sitio para el botón.
- **Social se bloquea** y el panel y el mapa no muestran datos de terceros al gestionar otra cuenta:
  los amigos de esa cuenta no dieron permiso.
- **`accountLinkIndex`** existe porque las reglas no pueden contar documentos.
- **Vínculos terminados se borran**, igual que las amistades; su historia queda en `accountAudit`.
- **Sin dependencias nuevas.**

### Limitaciones conocidas

- El registro deja elegir Pro sin pagar (HU-46a): el gating es tan fuerte como ese dato.
- `events` y `users` se pueden leer con solo iniciar sesión, como hasta ahora. Las consultas actuales
  (por ejemplo, la actividad de amigos) no funcionarían con reglas de lectura más estrictas.
- `accountAudit` se consulta en la consola; no tiene pantalla.

### Cambios fuera de la HU

- `ProfileScreen`: ya no da por guardada una edición que el servidor rechazó.
