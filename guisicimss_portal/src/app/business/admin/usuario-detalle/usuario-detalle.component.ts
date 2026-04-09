import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AdminService } from '../services/admin.service';
import { AlertService } from '../../../shared/services/alert.service';
import { LoaderService } from '../../../shared/services/loader.service';
import { ModalService } from '../../../shared/services/modal.service';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-usuario-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './usuario-detalle.component.html'
})
export class UsuarioDetalleComponent implements OnInit {
  // Inyecciones mediante inject() para mayor limpieza
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private adminService = inject(AdminService);
  private alert = inject(AlertService);
  private loader = inject(LoaderService);
  private modalService = inject(ModalService);

  // Estados de control de la UI
  isEditMode = false;
  idUsuarioSeleccionado: number | null = null;
  cambiosRealizados = false;
  estaCargado = false; // Seguro para evitar falsos positivos durante el binding inicial

  // Modelo del Usuario (Sincronizado con el esquema ampliado de base de datos)
  usuario: any = {};

  ngOnInit(): void {
    // Escuchamos parámetros de la ruta de forma reactiva
    this.route.params.subscribe(params => {
        const id = params['id'];
        if (id) {
            this.isEditMode = true;
            this.idUsuarioSeleccionado = +id;
            this.cargarDetalle(this.idUsuarioSeleccionado);
        } else {
            this.prepararNuevoRegistro();
        }
    });
  }

  /**
   * Inicializa un modelo limpio para una nueva Identidad.
   */
  prepararNuevoRegistro() {
    this.isEditMode = false;
    this.idUsuarioSeleccionado = null;
    this.usuario = {
        cveUsuarioApi: '',
        desPasswordApi: '',
        nomNombre: '',
        nomApellido1: '',
        nomApellido2: '',
        desUsoCuenta: '',
        desCorreo: '',
        numTelefono: '',
        numExtension: '',
        cveUsuarioMainframe: '',
        desPasswordMainframe: '',
        cveRol: '', // Forzamos selección inicial
        indActivo: 1,
        permisos: []
    };
    this.cambiosRealizados = false;
    this.estaCargado = true; // En alta nueva activamos el monitor de inmediato
  }

  /**
   * Recupera la información de Oracle y mapea a minúsculas para Angular.
   */
  cargarDetalle(id: number) {
    this.loader.show();
    this.estaCargado = false;

    this.adminService.obtenerUsuarioPorId(id).subscribe({
      next: (data) => {
        console.log("DATOS RECIBIDOS DE ORACLE:", data);

        this.usuario = {
          idUsuarioCics: data.ID_USUARIO_CICS,
          cveUsuarioApi: data.CVE_USUARIO_API,
          desPasswordApi: '', // Vaciamos para que el input use el placeholder
          nomNombre: data.NOM_NOMBRE,
          nomApellido1: data.NOM_APELLIDO_1,
          nomApellido2: data.NOM_APELLIDO_2,
          desUsoCuenta: data.DES_USO_CUENTA,
          desCorreo: data.DES_CORREO,
          numTelefono: data.NUM_TELEFONO,
          numExtension: data.NUM_EXTENSION,
          cveUsuarioMainframe: data.CVE_USUARIO_MAINFRAME,
          desPasswordMainframe: '', // Vaciamos para que el input use el placeholder
          cveRol: data.CVE_ROL,
          indActivo: data.IND_ACTIVO,
          stpRegistro: data.STP_REGISTRO,
          stpActualizacion: data.STP_ACTUALIZACION,
          // Mapeo de la lista de programas con sus propias fechas de auditoría
          permisos: (data.permisos || []).map((p: any) => ({
            idPermisoCics: p.ID_PERMISO_CICS,
            nomPrograma: p.NOM_PROGRAMA,
            nomTransaccion: p.NOM_TRANSACCION,
            numTimeoutSec: p.NUM_TIMEOUT_SEC,
            indActivo: p.IND_ACTIVO,
            stpRegistro: p.STP_REGISTRO,
            stpActualizacion: p.STP_ACTUALIZACION
          }))
        };

        // Seguro contra binding: Esperamos a que el DOM se estabilice antes de monitorear cambios
        setTimeout(() => {
          this.estaCargado = true;
          this.cambiosRealizados = false;
          this.loader.hide();
        }, 300);
      },
      error: () => {
        this.loader.hide();
        this.alert.error("No se pudo recuperar la información del servidor.");
        this.regresar();
      }
    });
  }

  registrarCambio() {
    if (this.estaCargado) {
      this.cambiosRealizados = true;
    }
  }

  validarEmail(email: string): boolean {
    const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    return emailRegex.test(email);
  }

  toggleEstadoUsuario() {
    this.usuario.indActivo = (this.usuario.indActivo === 1) ? 0 : 1;
    this.registrarCambio();
  }

  /**
   * Interceptor de cambio de perfil: Alarma sobre la pérdida de programas al pasar a ADMIN.
   */
  onRolChange() {
    if (!this.estaCargado) return;

    if (this.usuario.cveRol === 'ADMIN' && this.isEditMode && this.usuario.permisos.some((p:any) => p.indActivo === 1)) {
        this.modalService.showDialog('confirm', 'warning', 'Cambio de Perfil',
            'Al asignar el rol ADMIN, todos los programas y credenciales RACF se darán de baja automáticamente. <br><br>¿Desea aplicar y guardar ahora?',
            (conf: boolean) => {
                if (conf) {
                    this.usuario.permisos.forEach((p: any) => p.indActivo = 0);
                    this.usuario.cveUsuarioMainframe = null;
                    this.usuario.desPasswordMainframe = null;
                    this.cambiosRealizados = true;
                    this.guardar(); // Ejecutamos persistencia inmediata
                } else {
                    this.usuario.cveRol = 'CLIENTE'; // Revertimos selección
                }
            }, 'Aceptar y Guardar', 'Cancelar');
    } else {
        this.registrarCambio();
    }
  }

  agregarFilaPermiso() {
    this.usuario.permisos.push({
        idPermisoCics: null,
        nomPrograma: '',
        nomTransaccion: '',
        numTimeoutSec: 30,
        indActivo: 1
    });
    this.registrarCambio();
  }

  eliminarODesactivarPermiso(index: number) {
    const p = this.usuario.permisos[index];
    if (p.idPermisoCics) {
      p.indActivo = (p.indActivo === 1) ? 0 : 1;
    } else {
      this.usuario.permisos.splice(index, 1);
    }
    this.registrarCambio();
  }

  guardar() {
    // 1. Validaciones de Usuario
    if (!this.usuario.nomNombre || !this.usuario.nomApellido1 || !this.usuario.desUsoCuenta) {
        this.alert.warn("Los datos del responsable y el propósito son obligatorios."); return;
    }

    // 2. Validación de Contacto
    if (!this.usuario.desCorreo || !this.validarEmail(this.usuario.desCorreo) || !this.usuario.numTelefono || this.usuario.numTelefono.length < 10) {
        this.alert.warn("Proporcione un correo válido y un teléfono de 10 dígitos."); return;
    }

    // 3. Validación de Rol y Acceso Portal
    if (!this.usuario.cveRol) {
        this.alert.warn("Seleccione un Rol del Sistema."); return;
    }
    if (!this.usuario.cveUsuarioApi || (!this.isEditMode && !this.usuario.desPasswordApi)) {
      this.alert.warn("El Usuario y la Contraseña del portal son obligatorios."); return;
    }

    // 4. Validaciones obligatorias para perfil CLIENTE (RACF y Programas)
    if (this.usuario.cveRol === 'CLIENTE') {
        if (!this.usuario.cveUsuarioMainframe || (!this.isEditMode && !this.usuario.desPasswordMainframe)) {
            this.alert.warn("Las credenciales RACF (Mainframe) son obligatorias para el perfil CLIENTE."); return;
        }
        // Evitar ORA-01400: Validar filas de la tabla
        if (this.usuario.permisos.some((p:any) => p.indActivo === 1 && (!p.nomPrograma?.trim() || !p.nomTransaccion?.trim()))) {
            this.alert.warn("Hay filas incompletas en la tabla de programas. Complete los datos obligatorios (*).");
            return;
        }
    }

    this.loader.show();
    const op = this.isEditMode ? this.adminService.actualizarUsuario(this.usuario) : this.adminService.registrarUsuario(this.usuario);

    op.subscribe({
      next: () => {
        this.loader.hide();
        this.cambiosRealizados = false;
        this.alert.success("Información guardada exitosamente en el servidor.");
        setTimeout(() => this.regresar(), 1500);
      },
      error: (err) => {
        this.loader.hide();
        this.alert.error(err.error?.message || "Ocurrió un error al procesar la solicitud.");
      }
    });
  }

  regresar() {
    this.router.navigate(['/admin/usuarios']);
  }
}
