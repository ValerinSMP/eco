# Escritor nativo durable — 2026-10-15
SPI privilegiada ProfileMutationAccess: supported/install(Factory)/flush(UUID)/flushAll/publish(UUID,ownedKeys,committedValues). Solo plugins de confianza; no API web, SQL ni pool expuesto a consumidores públicos de XP.
Factory conecta DataSource/prefijo del MySQL YA inicializado. No pool nuevo. Instalación/publicación MAIN; JDBC async. Pool mínimo3, adquisición limitada3s al instalar.
PreparedProfileWrite captura origen antes de caché, preservado por coalescer/ejecutor. flush retira solo capturas vigentes y espera pendientes. publish valida tipos antes de cambiar únicamente claves propias del perfil existente; sin SQL/defaults.
EcoSkills aporta cola de operaciones completas, leases/revisiones y recuperación; el interceptor aislado no basta. Warmup de mutaciones explícitas no cambia observación read-only. Métodos default conservan ABI.
eco-api37 y eco-native6 tests pasan, incluyendo decimal100.02/publicación sin I/O/rechazo sin alterar caché. MySQL/Paper reales pendientes.
Runtime candidato, no instalado/activo. Default apagado desde EcoSkills. Contrato EcoSkills/docs/ENCHANTING-PROVIDER-CONTRACT.md. Ningún permiso de migración/lifecycle por este documento.
