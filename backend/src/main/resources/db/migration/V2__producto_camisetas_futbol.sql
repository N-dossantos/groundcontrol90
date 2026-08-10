-- tipo se declara como ENUM y no como VARCHAR porque es como Hibernate materializa
-- un @Enumerated(EnumType.STRING) en MySQL: con VARCHAR, el ddl-auto=validate de
-- producción rechaza el esquema. Mismo criterio que role y estado en el baseline.
ALTER TABLE productos
    ADD COLUMN club VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN liga VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN temporada VARCHAR(50) NOT NULL DEFAULT '',
    ADD COLUMN tipo ENUM('CAMISETA','SHORT') NOT NULL DEFAULT 'CAMISETA';

-- Los DEFAULT son transitorios: existen solo para no romper las filas ya cargadas.
-- Hacia adelante estos campos son obligatorios y sin default implícito.
ALTER TABLE productos ALTER COLUMN club DROP DEFAULT;
ALTER TABLE productos ALTER COLUMN liga DROP DEFAULT;
ALTER TABLE productos ALTER COLUMN temporada DROP DEFAULT;
ALTER TABLE productos ALTER COLUMN tipo DROP DEFAULT;
