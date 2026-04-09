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
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private adminService = inject(AdminService);
  private alert = inject(AlertService);
  private loader = inject(LoaderService);
  private modalService = inject(ModalService);

  isEditMode = false;
  idUsuarioSeleccionado: number | null = null;
  cambiosRealizados = false;
  estaCargado = false;

  usuario: any = {};

  ngOnInit(): void {
    this.route.params.subscribe(params => {
        const id = params['id'];
        if (id) {
            this.isEditMode = true;
            this.idUsuarioSeleccionado = +id;
            this.cargarDetalle(this.idUsuarioSeleccionado);
        } else {
            this.prepararNuevo();
        }
    });
  }

  /**
   * Inicializa el modelo para un registro nuevo incluyendo los campos de contacto y propósito.
   */
  prepararNuevo() {
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
        cveRol: '', 
        indActivo: 1,
        permisos: []
    };
    this.cambiosRealizados = false;
    this.estaCargado = true;
  }

  /**
   * Carga los datos desde el backend y mapea los nuevos campos de Oracle.
   */
  cargarDetalle(id: number) {
    this.loader.show();
    this.estaCargado = false;
    this.adminService.obtenerUsuarioPorId(id).subscribe({
      next: (data) => {
        this.usuario = {
          idUsuarioCics: data.ID_USUARIO_CICS,
          cveUsuarioApi: data.CVE_USUARIO_API,
          desPasswordApi: '', // Se mantiene vacío por seguridad
          nomNombre: data.NOM_NOMBRE,
          nomApellido1: data.NOM_APELLIDO_1,
          nomApellido2: data.NOM_APELLIDO_2,
          desUsoCuenta: data.DES_USO_CUENTA,
          desCorreo: data.DES_CORREO,
          numTelefono: data.NUM_TELEFONO,
          numExtension: data.NUM_EXTENSION,
          cveUsuarioMainframe: data.CVE_USUARIO_MAINFRAME,
          desPasswordMainframe: '', // Se mantiene vacío por seguridad
          cveRol: data.CVE_ROL,
          indActivo: data.IND_ACTIVO,
          // Mapeo de permisos incluyendo fechas de auditoría si se desean mostrar
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
        
        setTimeout(() => {
          this.estaCargado = true;
          this.cambiosRealizados = false;
          this.loader.hide();
        }, 300);
      },
      error: () => {
        this.loader.hide();
        this.alert.error("No se pudo cargar la información del usuario.");
        this.regresar();
      }
    });
  }

  registrarCambio() {
    if (this.estaCargado) this.cambiosRealizados = true;
  }

  toggleEstadoUsuario() {
    this.usuario.indActivo = (this.usuario.indActivo === 1) ? 0 : 1;
    this.registrarCambio();
  }

  onRolChange() {
    if (!this.estaCargado) return;

    if (this.usuario.cveRol === 'ADMIN' && this.isEditMode && this.usuario.permisos.some((p:any) => p.indActivo === 1)) {
        this.modalService.showDialog('confirm', 'warning', 'Atención',
            'Al cambiar a perfil ADMIN, se darán de baja los programas autorizados de Mainframe. ¿Desea continuar?',
            (conf: boolean) => {
                if (conf) {
                    this.usuario.permisos.forEach((p: any) => p.indActivo = 0);
                    this.usuario.cveUsuarioMainframe = null;
                    this.usuario.desPasswordMainframe = null;
                    this.cambiosRealizados = true;
                    this.guardar();
                } else { this.usuario.cveRol = 'CLIENTE'; }
            }, 'Aceptar', 'Cancelar');
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
    if (p.idPermisoCics) p.indActivo = (p.indActivo === 1) ? 0 : 1;
    else this.usuario.permisos.splice(index, 1);
    this.registrarCambio();
  }

  guardar() {
    // 1. Validaciones de Identidad (Nuevas)
    if (!this.usuario.nomNombre || !this.usuario.nomApellido1 || !this.usuario.desUsoCuenta) {
        this.alert.warn("Los datos del responsable y el propósito de la cuenta son obligatorios.");
        return;
    }

    // 2. Validaciones de Contacto (Nuevas)
    if (!this.usuario.desCorreo || !this.usuario.numTelefono) {
        this.alert.warn("El correo y el teléfono de contacto son obligatorios.");
        return;
    }

    // 3. Validar Rol
    if (!this.usuario.cveRol) {
        this.alert.warn("Debe seleccionar un Rol del Sistema."); 
        return;
    }

    // 4. Validar Usuario y Password API
    if (!this.usuario.cveUsuarioApi || (!this.isEditMode && !this.usuario.desPasswordApi)) {
      this.alert.warn("Los datos de acceso al portal son obligatorios."); 
      return;
    }

    // 5. Validaciones de Rol CLIENTE (RACF y Programas)
    if (this.usuario.cveRol === 'CLIENTE') {
        if (!this.usuario.cveUsuarioMainframe || (!this.isEditMode && !this.usuario.desPasswordMainframe)) {
            this.alert.warn("Los datos RACF son obligatorios para perfiles de ejecución."); 
            return;
        }
        if (this.usuario.permisos.some((p:any) => p.indActivo === 1 && (!p.nomPrograma || !p.nomTransaccion))) {
            this.alert.warn("Existen programas incompletos en la tabla."); 
            return;
        }
    }

    this.loader.show();
    const op = this.isEditMode ? this.adminService.actualizarUsuario(this.usuario) : this.adminService.registrarUsuario(this.usuario);

    op.subscribe({
      next: () => {
        this.loader.hide();
        this.cambiosRealizados = false;
        this.alert.success("Información guardada exitosamente.");
        setTimeout(() => this.regresar(), 1500);
      },
      error: (err) => {
        this.loader.hide();
        this.alert.error(err.error?.message || "Ocurrió un error al procesar la solicitud.");
      }
    });
  }

  regresar() { this.router.navigate(['/admin/usuarios']); }
}