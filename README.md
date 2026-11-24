# Sistema de gestión para una empresa maderera

Aplicación web para registrar ventas, compras y traslados, con control de existencias por sede y acceso según el rol del usuario.

## Tecnologías

Java 21, Spring Boot, Thymeleaf, MySQL, JPA/Hibernate y Flyway.

## Inicio

1. Tener Java 21 o superior y MySQL instalados.
2. Crear la base con `database/crear_base.sql` y configurar la conexión en `config/application-local.properties`. Hay un archivo de ejemplo en la misma carpeta.
3. Ejecutar desde la raíz del proyecto:

```powershell
.\mvnw.cmd spring-boot:run
```

Abrir http://localhost:8080 en el navegador. Flyway aplica las migraciones pendientes al iniciar.

## Cuentas y sedes

El registro de una empresa exige elegir un plan y guarda su capacidad junto con la primera sede y el administrador general. Las empresas sin plan no pueden ingresar al sistema ni crear o activar cuentas o sedes hasta que la plataforma les asigne uno.

- El administrador general gestiona todas las sedes de su empresa y asigna los accesos desde Usuarios.
- El administrador de sede y el contador pueden tener una o varias sedes autorizadas, con permiso de consulta o de gestión en cada una.
- El contador consulta inventario y movimientos, exporta el inventario y, con gestión, registra compras y proveedores. No realiza ventas ni ajustes manuales de stock.

Con una sola sede autorizada se ingresa directamente; con varias se elige la sede de trabajo. Cambiar los permisos requiere iniciar sesión de nuevo. La migración V19 convierte el antiguo rol Consulta en Contador conservando sus sedes y su permiso de lectura.

El administrador general puede eliminar usuarios sin operaciones asociadas y sedes sin usuarios, inventario ni documentos vinculados, después de confirmarlo. La eliminación es definitiva; los registros con historial se conservan y pueden desactivarse. La cuenta propia y la última sede activa están protegidas.
