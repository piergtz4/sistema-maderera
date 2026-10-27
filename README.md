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
