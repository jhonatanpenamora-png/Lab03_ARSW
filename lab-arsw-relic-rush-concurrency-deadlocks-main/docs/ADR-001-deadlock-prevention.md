# ADR-001: Deadlock prevention strategy

## Context

En Relic Rush, cada *adventurer* (hilo de plataforma) necesita dos `ForgeStation` exclusivas para fabricar una reliquia. La implementación inicial de `LockPair.withBoth` adquiría los monitores en el orden que el jugador solicitaba las estaciones, con un `sleep(2 ms)` entre la primera y la segunda adquisición. Esto permite que dos hilos tomen las mismas dos estaciones en orden opuesto (p. ej., hilo A: Anvil→Furnace, hilo B: Furnace→Anvil), generando una **espera circular** y, por tanto, deadlock. El juego debe preservar la concurrencia entre operaciones que usan estaciones disjuntas; no se permite un único lock global.

## Decision

Establecer un **orden global determinístico por `ForgeStation.id()`** para la adquisición de cualquier par de monitores:

1. Validar que `first`, `second` y `action` no sean `null`.
2. Si ambas referencias son el mismo objeto (`first == second`), adquirir ese monitor una sola vez.
3. Si son objetos distintos pero comparten el mismo `id()`, lanzar `IllegalArgumentException` (orden ambiguo).
4. Ordenar: `lower = id menor`, `higher = id mayor`.
5. Adquirir `synchronized(lower) { synchronized(higher) { action.run(); } }`.

Esto rompe la **condición de espera circular** de Coffman y mantiene concurrencia fina para pares de estaciones que no se superponen.

## Alternatives considered

| Alternativa | Descripción | Por qué se descarta |
|-------------|-------------|---------------------|
| **Un único lock global** | Un `ReentrantLock` o `synchronized` en todo `LockPair.withBoth`. | Serializa todas las fabricaciones, aun las que usan estaciones disjuntas. Destruye el paralelismo que el lab exige preservar. |
| **`tryLock` con timeout y reintentos** | Usar `ReentrantLock.tryLock(timeout)` y, en caso de fallo, soltar y reintentar con backoff. | Cambia el modelo de monitores intrínsecos a locks explícitos, agrega complejidad de reintentos y *livelock* potencial. No elimina la causa raíz (orden inconsistente). |
| **Asignación centralizada (árbitro)** | Un coordinador que otorga pares de estaciones atómicamente. | Aumenta acoplamiento, introduce un cuello de botella y contención en el árbitro. Over-engineering para este problema. |
| **Orden global por ID (elegida)** | Ordenar estaciones por `id` antes de adquirir. | Cambio mínimo, razonable, mantiene monitores intrínsecos, elimina el ciclo de espera y conserva paralelismo para estaciones disjuntas. Requiere IDs únicos (validado). |

## Quality attributes affected

- **Correctness / Reliability**: Se elimina el deadlock por espera circular; la invariante de liveness (progreso) se cumple.
- **Performance / Throughput**: Estaciones disjuntas (`{1,2}` vs `{3,4}`) siguen ejecutándose concurrentemente; contención solo cuando hay overlap real.
- **Maintainability**: Regla simple y explícita ("siempre adquiere el ID menor primero"), fácil de auditar y preservar en futuras modificaciones.
- **Scalability**: Al crecer el número de jugadores con estaciones fijas, la contención aumenta solo en las estaciones compartidas, no globalmente.

## Evidence

- **Test unitario `LockPairTest.deadlockReproduction_oppositeOrder_shouldNotDeadlock`**: Dos hilos con órdenes opuestos completan dentro de 3 s (antes deadlock).
- **Test `LockPairTest.disjointPairs_shouldRunConcurrently`**: Pares `{1,2}` y `{3,4}` avanzan en paralelo sin bloquearse.
- **Sonda oficial `DeadlockProbe`**:
  ```bash
  java -cp target/classes edu.eci.arsw.relicrush.app.DeadlockProbe
  ```
  Resultado esperado:
  ```
  NO DEADLOCK DETECTED within 2 seconds.
  If you already fixed LockPair, this is the expected result.
  ```
- **Ejecución completa `mvn clean test`**: Todas las pruebas pasan (incluye `StarterSmokeTest`, `LockPairTest`, y las del integrante 1 cuando se integren).

## Consequences

**Positivas**
- Deadlock eliminado sin sacrificar paralelismo legítimo.
- Código simple, sin dependencias extra, compatible con Java 21.
- La validación de IDs duplicados falla rápido en tiempo de desarrollo.

**Negativas**
- Requiere que `ForgeStation.id()` sea único y estable (se valida en `LockPair`).
- Cualquier nuevo código que adquiera múltiples monitores *fuera* de `LockPair` debe respetar la misma regla; convención no forzada por el compilador.

## Risks

- **Uso directo de `synchronized(station)` en otro lugar**: Si otro componente adquiere monitores en orden distinto, reaparece el ciclo. Mitigación: documentar la convención en el ADR y en `LockPair`; code review.
- **IDs no únicos en configuración dinámica**: Si en el futuro se crean estaciones con IDs duplicados, `LockPair` lanza excepción. Mitigación: `GameEngine.createStations` ya asigna IDs secuenciales únicos; test `sameStationTwice` cubre el caso de misma referencia.
- **Cambio a `ReentrantLock` en el futuro**: La estrategia de orden global sigue aplicando; solo cambia el primitivo de sincronización.