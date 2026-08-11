-- Nullable porque los usuarios ya existentes en una base real no tienen este dato
-- retroactivo: no se puede inventar un timestamp de aceptación que nunca ocurrió.
ALTER TABLE usuarios ADD COLUMN terminos_aceptados_at DATETIME NULL;
