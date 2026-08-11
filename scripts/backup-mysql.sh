#!/usr/bin/env bash
set -euo pipefail

# Backup de la base MySQL: dump comprimido, rotación local de los últimos días y
# copia opcional fuera del servidor.
#
# Requiere correr en el mismo host que docker-compose.prod.yml, con las mismas
# variables de entorno cargadas (MYSQL_DATABASE, MYSQL_ROOT_PASSWORD) y, opcionalmente,
# BACKUP_S3_BUCKET / BACKUP_S3_ENDPOINT para subir la copia fuera del servidor.

CONTENEDOR="${MYSQL_CONTAINER:-ecommerce-mysql}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/ecommerce-mysql}"
RETENCION_DIAS="${BACKUP_RETENCION_DIAS:-7}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
ARCHIVO="${BACKUP_DIR}/ecommerce_db-${TIMESTAMP}.sql.gz"

# Mismo patrón que docker-compose.prod.yml: fallar temprano y con un mensaje claro
# en vez de generar un dump vacío porque faltaba cargar el .env.
: "${MYSQL_DATABASE:?Falta MYSQL_DATABASE (cargar el .env de producción antes de correr el script)}"
: "${MYSQL_ROOT_PASSWORD:?Falta MYSQL_ROOT_PASSWORD (cargar el .env de producción antes de correr el script)}"

mkdir -p "${BACKUP_DIR}"

# Se escribe primero a un .tmp y recién se renombra si el dump terminó bien. Un
# backup truncado con el nombre definitivo es peor que no tener backup: parece
# válido hasta el día que hay que restaurarlo.
PARCIAL="${ARCHIVO}.tmp"
trap 'rm -f "${PARCIAL}"' EXIT

# --single-transaction: dump consistente sin bloquear las tablas mientras la tienda
# sigue operando (InnoDB).
# MYSQL_PWD en lugar de -p: evita que la contraseña quede visible en la lista de
# procesos del contenedor.
docker exec -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" "${CONTENEDOR}" \
  mysqldump -u root --single-transaction --quick "${MYSQL_DATABASE}" \
  | gzip > "${PARCIAL}"

# Verificar que el gzip quedó completo antes de darlo por bueno.
gzip -t "${PARCIAL}"
mv "${PARCIAL}" "${ARCHIVO}"
trap - EXIT

echo "Backup creado: ${ARCHIVO} ($(du -h "${ARCHIVO}" | cut -f1))"

# Rotación: borrar backups locales más viejos que RETENCION_DIAS
find "${BACKUP_DIR}" -name "ecommerce_db-*.sql.gz" -mtime "+${RETENCION_DIAS}" -delete

# Copia fuera del servidor (opcional pero fuertemente recomendado — un backup que
# vive solo en el mismo disco que la base no protege contra la pérdida del servidor)
if [[ -n "${BACKUP_S3_BUCKET:-}" ]]; then
  DESTINO="s3://${BACKUP_S3_BUCKET}/$(basename "${ARCHIVO}")"
  if [[ -n "${BACKUP_S3_ENDPOINT:-}" ]]; then
    aws s3 cp "${ARCHIVO}" "${DESTINO}" --endpoint-url "${BACKUP_S3_ENDPOINT}"
  else
    aws s3 cp "${ARCHIVO}" "${DESTINO}"
  fi
  echo "Backup subido a ${DESTINO}"
else
  echo "AVISO: BACKUP_S3_BUCKET no está configurado — el backup solo existe en este servidor."
fi
